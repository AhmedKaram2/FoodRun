package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlinx.serialization.Serializable

/** Private delivery address, never part of a public profile, people list or room snapshot. */
@Serializable internal data class EmailContact(val address: String, val verifiedAt: Long)
internal object EmailContacts {
    fun valid(address: String) = address.length in 3..254 && address.matches(Regex("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+"))
    fun capture(db: RoomDatabase, identity: CloudIdentity, now: Long) {
        // The authenticated provider's account email can be searched privately. Only a
        // verified address is eligible for automatic reminder delivery below.
        if(valid(identity.email)) db.putRecord("account-email:${RoomService.hash(identity.email.lowercase())}", identity.userId)
        val searchKey = "wallet-search-email:${identity.userId}"
        if (valid(identity.email)) db.putRecord(searchKey, identity.email) else db.deleteRecord(searchKey)
        val key = "email-contact:${identity.userId}"
        if (!identity.emailVerified || !valid(identity.email)) { db.deleteRecord(key); return }
        db.putRecord(key, orderJson.encodeToString(EmailContact(identity.email, now)))
    }
    fun address(db: RoomDatabase, uid: String): String? = db.record("email-contact:$uid")
        ?.let { orderJson.decodeFromString<EmailContact>(it).address }?.takeIf(::valid)
    fun searchAddress(db: RoomDatabase, uid: String): String? = db.record("wallet-search-email:$uid")?.takeIf(::valid) ?: address(db, uid)
}

@Serializable internal data class EmailJob(
    val id: String, val userId: String, val roomId: String, val orderNumber: Long,
    val subject: String, val body: String, val createdAt: Long, val invitationId: String = "",
    val attempts: Int = 0, val nextAt: Long = 0,
    val reminderMemberId: String = "", val reminderPayerId: String = "",
    val recipientAddress: String = "",
    val friendGroupId: String = "",
    val friendGroupOwnerId: String = "",
    val recipientUserId: String = "",
)
internal data class EmailDelivery(val job: EmailJob, val address: String)
internal enum class EmailResult { SENT, RETRY, FAILED, AUTH_REQUIRED }
internal fun interface EmailSender { fun send(delivery: EmailDelivery): EmailResult }
internal class ReminderEmailRequired : IllegalArgumentException("Enter an email address for this payment reminder.")

/** Enqueued in the room transaction; Gmail runs outside the room lock. */
internal class EmailService(private val db: RoomDatabase, private val clock: () -> Long, private val enabled: Boolean) {
    // Explicit friend invitations and requested payment reminders are the only email types.
    private fun allowed(job: EmailJob) = job.friendGroupId.isNotEmpty() || job.reminderMemberId.isNotEmpty() && job.reminderPayerId.isNotEmpty() && job.invitationId.isEmpty()
    fun friendInvitation(ownerId: String, group: FriendGroup, member: FriendContact, commandId: String, ownerName: String, room: Room? = null, groupOwnerId: String = ownerId) {
        if(member.email.isEmpty() || !NotificationPreferencesStore.emailAllowed(db,member.userId,member.email)) return
        require(enabled) { "Email invitations are not available on this server yet." }
        val id = RoomService.hash("friend:$commandId:${member.email}:${room?.id.orEmpty()}")
        if(db.record("email-job:$id") != null || db.record("email-result:$id") != null) return
        val site = (System.getenv("FOODRUN_EMAIL_APP_URL") ?: "https://intrvioo.com").trimEnd('/')
        val link = if(room == null) site else "$site/?room=${room.code}${System.getenv("FOODRUN_EMAIL_API_URL")?.let { "&hub=" + java.net.URLEncoder.encode(it, Charsets.UTF_8) }.orEmpty()}"
        val subject = if(room == null) "$ownerName invited you to Intrvioo" else "$ownerName invited you to ${room.name}"
        val body = if(room == null) "$ownerName added you to the friend group ${group.name}. Join Intrvioo using this email to order food together: $link"
        else "$ownerName created ${room.name}.\nRestaurant: ${room.restaurant.name}${if(room.restaurantPollOpen) " (room poll)" else ""}\nRoom code: ${room.code}\n${if(room.deliveryMode) "Delivery" else "Pickup"}${room.destination.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}\n${if(room.joinDeadlineAt > 0) "Join before ${java.time.Instant.ofEpochMilli(room.joinDeadlineAt)} (UTC). The wheel starts when the timer ends.\n" else ""}Join and start your order: $link"
        db.putRecord("email-job:$id", orderJson.encodeToString(EmailJob(id, ownerId, room?.id.orEmpty(), room?.orderNumber ?: 0,
            subject, body, clock(), recipientAddress = member.email, friendGroupId = group.id, friendGroupOwnerId = groupOwnerId.takeUnless { it == ownerId }.orEmpty(), recipientUserId = member.userId)))
    }
    fun remind(room: Room, actorId: String, command: RoomCommand): String {
        require(room.payerId == actorId) { "Only the chosen payer can send payment reminders." }
        require(command.expectedOrderNumber == room.orderNumber && command.expectedRevision == room.revision) { "The bill changed. Refresh the room and try again." }
        val receipt = Billing.receipts(room).singleOrNull { it.memberId == command.memberId }
        require(receipt != null && PaymentReminderRules.eligible(room, actorId, receipt)) { "Reminders are only available for unpaid balances with no payment awaiting confirmation." }
        val uid = requireNotNull(db.record("member-user:${room.id}:${receipt.memberId}")) { "This member needs to sign in again before receiving email reminders." }
        val key = "email-reminder:${PaymentReminderRules.key(room, receipt.memberId)}"
        val previous = db.record(key)?.toLong()
        require(previous == null || clock() - previous >= PaymentReminderRules.COOLDOWN_MS) { "A reminder was already queued for this person. Please wait 24 hours before sending another." }
        val jobId = RoomService.hash("reminder:${command.commandId}")
        if(!NotificationPreferencesStore.read(db,uid).emailEnabled) {
            db.putRecord(key,clock().toString()); db.putRecord("email-reminder-job:${PaymentReminderRules.key(room,receipt.memberId)}",jobId)
            db.putRecord("email-result:$jobId","${clock()}:notified")
            NotificationService(db,clock).reminder(room,receipt.memberId,command.commandId,"${room.name} · ${Money.format(receipt.balance,receipt.currency)}")
            return "REMINDER_NOTIFIED"
        }
        require(enabled) { "Email reminders are not available on this server yet." }
        val recipient = if (EmailContacts.address(db, uid) != null) "" else command.text.trim().also {
            if (it.isEmpty()) throw ReminderEmailRequired()
            require(EmailContacts.valid(it)) { "Enter a valid email address." }
        }
        val job = EmailJob(RoomService.hash("reminder:${command.commandId}"), uid, room.id, room.orderNumber,
            PaymentReminderEmail.SUBJECT, PaymentReminderEmail.body(room, receipt), clock(),
            reminderMemberId = receipt.memberId, reminderPayerId = actorId, recipientAddress = recipient, recipientUserId = uid)
        require(accessible(job)) { "This member is not available for payment reminders." }
        db.putRecord("email-job:${job.id}", orderJson.encodeToString(job))
        db.putRecord(key, clock().toString())
        db.putRecord("email-reminder-job:${PaymentReminderRules.key(room, receipt.memberId)}", job.id)
        NotificationService(db,clock).reminder(room,receipt.memberId,command.commandId,"${room.name} · ${Money.format(receipt.balance,receipt.currency)}")
        return "REMINDER_QUEUED"
    }
    private fun accessible(job: EmailJob): Boolean {
        if (db.record("profile:${job.userId}") == null || AccountRestrictions.current(db, job.userId, clock()) != null ||
            AccountRestrictions.forRoom(db, job.userId, job.roomId, clock()) != null) return false
        if(job.friendGroupId.isNotEmpty()) {
            val groupOwnerId = job.friendGroupOwnerId.ifEmpty { job.userId }
            val group = db.record("friend-group:$groupOwnerId:${job.friendGroupId}")?.let { orderJson.decodeFromString<FriendGroup>(it) } ?: return false
            if(!FriendGroupService.canAnnounce(db, groupOwnerId, group, job.userId)) return false
            if(group.members.none { it.email.equals(job.recipientAddress, true) } &&
                !(job.roomId.isNotEmpty() && EmailContacts.searchAddress(db, groupOwnerId)?.equals(job.recipientAddress, true) == true)) return false
            if(job.roomId.isEmpty()) return true
            val invitedRoom = db.room(job.roomId) ?: return false
            return invitedRoom.orderNumber == job.orderNumber && invitedRoom.phase == RoomPhase.LOBBY &&
                (invitedRoom.joinDeadlineAt == 0L || invitedRoom.joinDeadlineAt > clock())
        }
        val room = db.room(job.roomId) ?: return false
        if (room.orderNumber != job.orderNumber) return false
        if (job.reminderMemberId.isNotEmpty()) {
            if (db.record("member-user:${room.id}:${job.reminderMemberId}") != job.userId) return false
            val receipt = Billing.receipts(room).singleOrNull { it.memberId == job.reminderMemberId } ?: return false
            if (!PaymentReminderRules.eligible(room, job.reminderPayerId, receipt)) return false
        }
        if (job.invitationId.isNotEmpty()) return room.phase == RoomPhase.LOBBY && db.record("invitation:${job.userId}:${job.invitationId}") != null
        val member = db.record("membership:${job.userId}:${room.id}")?.let { orderJson.decodeFromString<AccountRoom>(it) } ?: return false
        return room.activeMembers.any { it.id == member.memberId }
    }
    fun reminderStatus(room: Room, actorId: String, memberId: String): String {
        require(room.payerId == actorId && room.activeMembers.any { it.id == memberId && it.id != actorId }) { "Only the chosen payer can check payment reminders." }
        val id = db.record("email-reminder-job:${PaymentReminderRules.key(room, memberId)}") ?: return "REMINDER_NONE"
        return status(id)
    }
    fun status(id: String): String = when (db.record("email-result:$id")?.substringAfter(':')) {
        "sent" -> "REMINDER_SENT"
        "failed" -> "REMINDER_FAILED"
        "notified" -> "REMINDER_NOTIFIED"
        else -> if (db.record("email-job:$id") != null) "REMINDER_PENDING" else "REMINDER_FAILED"
    }
    fun pending(dailyLimit: Int, jobId: String? = null): EmailDelivery? {
        require(dailyLimit in 1..450)
        if (!enabled) return null
        val now = clock()
        val attempts = db.record("email:attempts")?.let { orderJson.decodeFromString<List<Long>>(it) }.orEmpty().filter { it > now - 86_400_000 }
        if (attempts.size >= dailyLimit) return null
        val jobs = if (jobId == null) db.records("email-job:") else db.record("email-job:$jobId")?.let { listOf("email-job:$jobId" to it) }.orEmpty()
        for ((key, body) in jobs) {
            val job = orderJson.decodeFromString<EmailJob>(body)
            val address = job.recipientAddress.takeIf(EmailContacts::valid) ?: EmailContacts.address(db, job.userId)
            if(address != null && !NotificationPreferencesStore.emailAllowed(db,job.recipientUserId,address)) {
                db.deleteRecord(key); db.putRecord("email-result:${job.id}","${clock()}:notified"); continue
            }
            if (!allowed(job) || job.createdAt < now - 86_400_000 || !accessible(job) || address == null) {
                db.deleteRecord(key)
                failReminder(job)
                continue
            }
            if (job.nextAt > now) continue
            if (job.attempts >= 6) { delivered(EmailDelivery(job, address), EmailResult.FAILED); continue }
            // Reserve before sending, including failures and ambiguous network timeouts.
            val refreshed = if (job.reminderMemberId.isEmpty()) job else {
                val room = requireNotNull(db.room(job.roomId))
                job.copy(body = PaymentReminderEmail.body(room, Billing.receipts(room).single { it.memberId == job.reminderMemberId }))
            }
            val reserved = refreshed.copy(attempts = job.attempts + 1, nextAt = now + 300_000)
            db.putRecord(key, orderJson.encodeToString(reserved))
            db.putRecord("email:attempts", orderJson.encodeToString(attempts + now))
            return EmailDelivery(reserved, address)
        }
        return null
    }
    fun delivered(delivery: EmailDelivery, result: EmailResult) {
        val job = delivery.job
        val key = "email-job:${job.id}"
        if (db.record(key)?.let { orderJson.decodeFromString<EmailJob>(it) } != job) return
        if(result == EmailResult.AUTH_REQUIRED) {
            // OAuth failed before a Gmail send request. Preserve the invitation and quota.
            db.putRecord(key,orderJson.encodeToString(job.copy(attempts = maxOf(0,job.attempts-1),nextAt = clock()+300_000)))
            val attempts = db.record("email:attempts")?.let { orderJson.decodeFromString<List<Long>>(it) }.orEmpty().toMutableList()
            val reservation = attempts.lastIndexOf(job.nextAt-300_000)
            if(reservation >= 0) attempts.removeAt(reservation)
            db.putRecord("email:attempts",orderJson.encodeToString(attempts))
        } else if (result == EmailResult.RETRY && job.attempts < 6) {
            db.putRecord(key, orderJson.encodeToString(job.copy(nextAt = clock() + minOf(3_600_000L, 60_000L * (1L shl job.attempts)))))
        } else {
            db.deleteRecord(key)
            // No recipient address or message content in the delivery receipt.
            db.putRecord("email-result:${job.id}", "${clock()}:${if (result == EmailResult.SENT) "sent" else "failed"}")
            if (result != EmailResult.SENT) failReminder(job)
            db.records("email-result:").filter { it.second.substringBefore(':').toLong() < clock() - 30L * 86_400_000 }.forEach { db.deleteRecord(it.first) }
        }
    }
    private fun failReminder(job: EmailJob) {
        if (job.reminderMemberId.isEmpty()) return
        val key = "${job.roomId}:${job.orderNumber}:${job.reminderMemberId}"
        if (db.record("email-reminder-job:$key") == job.id) db.deleteRecord("email-reminder:$key")
    }
}
