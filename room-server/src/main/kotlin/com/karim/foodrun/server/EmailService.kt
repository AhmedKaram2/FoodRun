package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlinx.serialization.Serializable

/** Private delivery address, never part of a public profile, people list or room snapshot. */
@Serializable internal data class EmailContact(val address: String, val verifiedAt: Long)
internal object EmailContacts {
    fun valid(address: String) = address.length in 3..254 && address.matches(Regex("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+"))
    fun capture(db: RoomDatabase, identity: CloudIdentity, now: Long) {
        val key = "email-contact:${identity.userId}"
        if (!identity.emailVerified || !valid(identity.email)) { db.deleteRecord(key); return }
        db.putRecord(key, orderJson.encodeToString(EmailContact(identity.email, now)))
    }
    fun address(db: RoomDatabase, uid: String): String? = db.record("email-contact:$uid")
        ?.let { orderJson.decodeFromString<EmailContact>(it).address }?.takeIf(::valid)
}

@Serializable internal data class EmailJob(
    val id: String, val userId: String, val roomId: String, val orderNumber: Long,
    val subject: String, val body: String, val createdAt: Long, val invitationId: String = "",
    val attempts: Int = 0, val nextAt: Long = 0,
    val reminderMemberId: String = "", val reminderPayerId: String = "",
)
internal data class EmailDelivery(val job: EmailJob, val address: String)
internal enum class EmailResult { SENT, RETRY, FAILED }
internal fun interface EmailSender { fun send(delivery: EmailDelivery): EmailResult }

/** Enqueued in the room transaction; Gmail runs outside the room lock. */
internal class EmailService(private val db: RoomDatabase, private val clock: () -> Long, private val enabled: Boolean) {
    fun remind(room: Room, actorId: String, command: RoomCommand) {
        require(enabled) { "Email reminders are not available on this server yet." }
        require(room.payerId == actorId) { "Only the chosen payer can send payment reminders." }
        require(command.expectedOrderNumber == room.orderNumber && command.expectedRevision == room.revision) { "The bill changed. Refresh the room and try again." }
        val receipt = Billing.receipts(room).singleOrNull { it.memberId == command.memberId }
        require(receipt != null && PaymentReminderRules.eligible(room, actorId, receipt)) { "Reminders are only available for unpaid balances with no payment awaiting confirmation." }
        val uid = requireNotNull(db.record("member-user:${room.id}:${receipt.memberId}")) { "This member needs to sign in again before receiving email reminders." }
        require(EmailContacts.address(db, uid) != null) { "This member needs to sign in again with a verified email before receiving reminders." }
        val key = "email-reminder:${PaymentReminderRules.key(room, receipt.memberId)}"
        val previous = db.record(key)?.toLong()
        require(previous == null || clock() - previous >= PaymentReminderRules.COOLDOWN_MS) { "A reminder was already queued for this person. Please wait 24 hours before sending another." }
        val job = EmailJob(RoomService.hash("reminder:${command.commandId}"), uid, room.id, room.orderNumber,
            PaymentReminderEmail.SUBJECT, PaymentReminderEmail.body(room, receipt), clock(),
            reminderMemberId = receipt.memberId, reminderPayerId = actorId)
        require(accessible(job)) { "This member is not available for payment reminders." }
        db.putRecord("email-job:${job.id}", orderJson.encodeToString(job))
        db.putRecord(key, clock().toString())
    }
    fun notification(uid: String, room: Room, item: FoodNotification) {
        enqueue(EmailJob(item.id, uid, room.id, room.orderNumber, item.title, item.body, clock()))
    }
    fun invitation(invitation: FoodInvitation) {
        val ar = db.record("profile:${invitation.userId}")?.let { orderJson.decodeFromString<FoodProfile>(it).language == "ar" } == true
        enqueue(EmailJob(RoomService.hash("invite:${invitation.userId}:${invitation.id}"), invitation.userId,
            invitation.roomId, invitation.orderNumber, if (ar) "دعوة إلى Food Run" else "You are invited to Food Run",
            if (ar) "${invitation.invitedBy} يدعوك إلى ${invitation.roomName}. افتح Food Run لقبول الدعوة."
            else "${invitation.invitedBy} invited you to ${invitation.roomName}. Open Food Run to accept the invitation.",
            clock(), invitation.id))
    }
    private fun enqueue(job: EmailJob) {
        if (!enabled || EmailContacts.address(db, job.userId) == null || !accessible(job)) return
        if (db.record("email-job:${job.id}") != null || db.record("email-result:${job.id}") != null) return
        db.putRecord("email-job:${job.id}", orderJson.encodeToString(job))
    }
    private fun accessible(job: EmailJob): Boolean {
        if (db.record("profile:${job.userId}") == null || AccountRestrictions.current(db, job.userId, clock()) != null ||
            AccountRestrictions.forRoom(db, job.userId, job.roomId, clock()) != null) return false
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
    fun pending(dailyLimit: Int): EmailDelivery? {
        require(dailyLimit in 1..450)
        if (!enabled) return null
        val now = clock()
        val attempts = db.record("email:attempts")?.let { orderJson.decodeFromString<List<Long>>(it) }.orEmpty().filter { it > now - 86_400_000 }
        if (attempts.size >= dailyLimit) return null
        for ((key, body) in db.records("email-job:")) {
            val job = orderJson.decodeFromString<EmailJob>(body)
            val address = EmailContacts.address(db, job.userId)
            if (job.createdAt < now - 86_400_000 || !accessible(job) || address == null) { db.deleteRecord(key); continue }
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
        if (result == EmailResult.RETRY && job.attempts < 6) {
            db.putRecord(key, orderJson.encodeToString(job.copy(nextAt = clock() + minOf(3_600_000L, 60_000L * (1L shl job.attempts)))))
        } else {
            db.deleteRecord(key)
            // No recipient address or message content in the delivery receipt.
            db.putRecord("email-result:${job.id}", "${clock()}:${if (result == EmailResult.SENT) "sent" else "failed"}")
            db.records("email-result:").filter { it.second.substringBefore(':').toLong() < clock() - 30L * 86_400_000 }.forEach { db.deleteRecord(it.first) }
        }
    }
}
