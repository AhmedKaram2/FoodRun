package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlinx.serialization.Serializable

@Serializable internal data class AccountMergePlan(
    val sourceUserId: String, val targetUserId: String,
    val expectedSourceName: String, val expectedTargetName: String, val name: String,
)
@Serializable internal data class AccountMergeResult(val sourceUserId: String, val targetUserId: String, val name: String,
    val rooms: List<String>, val walletRecords: Int, val at: Long)

/** Operator-configured, atomic and idempotent repair. Names alone never authorize an account merge. */
internal object AccountMergeRepair {
    fun configured(db: RoomDatabase) {
        val config = System.getenv("FOODRUN_ACCOUNT_MERGES")?.takeIf { it.isNotBlank() } ?: return
        orderJson.decodeFromString<List<AccountMergePlan>>(config).forEach { plan ->
            apply(db, plan, System.currentTimeMillis())
        }
    }

    fun apply(db: RoomDatabase, plan: AccountMergePlan, now: Long): AccountMergeResult = db.transaction {
        require(plan.sourceUserId != plan.targetUserId && listOf(plan.sourceUserId, plan.targetUserId).all { it.matches(Regex("[A-Za-z0-9_-]{1,128}")) })
        val repairKey = "account-merge:${plan.sourceUserId}"
        db.record(repairKey)?.let {
            val previous = orderJson.decodeFromString<AccountMergeResult>(it)
            require(previous.targetUserId == plan.targetUserId && previous.name == plan.name) { "This account was already merged into another identity." }
            return@transaction previous
        }
        require(AccountAliases.resolve(db, plan.sourceUserId) == plan.sourceUserId && AccountAliases.resolve(db, plan.targetUserId) == plan.targetUserId) { "Choose original account identities for this repair." }
        require(listOf(plan.sourceUserId, plan.targetUserId).all { uid ->
            AccountRestrictions.current(db, uid, now) == null && db.records("admin:room-restriction:$uid:").none {
                val restriction = orderJson.decodeFromString<AccountRestriction>(it.second)
                restriction.until == 0L || restriction.until > now
            }
        }) { "Resolve account restrictions before merging." }
        fun profile(uid: String) = requireNotNull(db.record("profile:$uid")) { "A merge account was not found." }.let { orderJson.decodeFromString<FoodProfile>(it) }
        val source = profile(plan.sourceUserId)
        val target = profile(plan.targetUserId)
        require(source.name == plan.expectedSourceName && target.name == plan.expectedTargetName) { "The account profiles changed. Review this repair again." }
        MenuValidation.label(plan.name)
        val methods = (target.receivingAccounts + source.receivingAccounts).distinct()
        require(methods.map { it.id }.distinct().size == methods.size) { "Resolve conflicting payment method IDs before merging." }
        val favorites = (target.favoriteOrders + source.favoriteOrders).distinct()
        require(favorites.map { it.id }.distinct().size == favorites.size) { "Resolve conflicting favorite IDs before merging." }
        val merged = target.copy(name = plan.name, phone = target.phone.ifBlank { source.phone }, photo = target.photo.ifBlank { source.photo },
            payment = methods.firstOrNull(), paymentAccounts = methods.drop(1), favoriteOrders = favorites).normalized().also(FoodProfile::validate)
        fun uid(value: String) = if (value == plan.sourceUserId) plan.targetUserId else value
        fun name(id: String, original: String) = if (id == plan.sourceUserId || id == plan.targetUserId) merged.name else original
        fun payment(value: WalletPayment) = value.copy(customerId = uid(value.customerId), customerName = name(value.customerId, value.customerName),
            holderId = uid(value.holderId), holderName = name(value.holderId, value.holderName), recipientId = uid(value.recipientId), recipientName = name(value.recipientId, value.recipientName))

        val affected = mutableListOf<String>()
        db.allRooms().forEach { room ->
            val sourceLink = db.record("membership:${source.userId}:${room.id}")?.let { orderJson.decodeFromString<AccountRoom>(it) }
            val targetLink = db.record("membership:${target.userId}:${room.id}")?.let { orderJson.decodeFromString<AccountRoom>(it) }
            val related = room.members.filter { member -> member.id == sourceLink?.memberId || member.id == targetLink?.memberId ||
                db.record("member-user:${room.id}:${member.id}") in listOf(source.userId, target.userId) }
            val wallets = room.walletPayments.map(::payment)
            if (related.isEmpty()) {
                if (wallets != room.walletPayments) { db.save(room.copy(walletPayments = wallets, revision = room.revision + 1, updatedAt = now)); affected += room.id }
                return@forEach
            }
            fun hasReferences(id: String) = room.ownerId == id || room.payerId == id ||
                room.carts.any { it.memberId == id && it.lines.isNotEmpty() } || room.transfers.any { it.memberId == id } ||
                room.walletPayments.any { it.memberId == id } || room.wheelProtections.any { it.memberId == id } ||
                room.halfItemOffers.any { it.memberId == id || it.acceptedById == id }
            val referenced = related.filter { hasReferences(it.id) }
            require(referenced.size <= 1) { "Both identities have orders or payment duties in ${room.name}. Review their bills before merging." }
            val selected = referenced.singleOrNull() ?: related.firstOrNull { !it.removed && it.id == targetLink?.memberId }
                ?: related.firstOrNull { !it.removed } ?: related.first()
            val discarded = related.filterNot { it.id == selected.id }.map { it.id }.toSet()
            require(room.phase !in listOf(RoomPhase.PREPARING_SPIN, RoomPhase.SPINNING, RoomPhase.ACCEPTING) ||
                discarded.none { it in room.preparedIds || it in room.spin?.memberIds.orEmpty() }) { "Finish the current selection before merging duplicate members." }
            val next = room.copy(members = room.members.map { if (it.id in discarded) it.copy(removed = true, participating = false, eligible = false, ready = false) else it },
                walletPayments = wallets, revision = room.revision + 1, updatedAt = now)
            require(Billing.receipts(room) == Billing.receipts(next)) { "This merge would change a bill. Review it before merging." }
            db.save(next)
            val existing = listOfNotNull(targetLink, sourceLink).firstOrNull { it.memberId == selected.id }
            db.putRecord("membership:${target.userId}:${room.id}", orderJson.encodeToString(AccountRoom(room.id, room.name, selected.id, existing?.token.orEmpty())))
            db.deleteRecord("membership:${source.userId}:${room.id}")
            related.forEach { db.putRecord("member-user:${room.id}:${it.id}", target.userId) }
            affected += room.id
        }
        db.allHistory().forEach { history ->
            val wallets = history.walletPayments.map(::payment)
            if (wallets != history.walletPayments) db.archive(history.copy(walletPayments = wallets))
        }
        var walletRecords = 0
        WalletService.topUps(db).forEach { value ->
            val updated = value.copy(customerId = uid(value.customerId), customerName = name(value.customerId, value.customerName), holderId = uid(value.holderId), holderName = name(value.holderId, value.holderName))
            if (updated != value) { db.putRecord("wallet:top-up:${value.id}", orderJson.encodeToString(updated)); walletRecords++ }
        }
        WalletService.payments(db).forEach { value ->
            val updated = payment(value)
            if (updated != value) { db.putRecord("wallet:payment:${value.id}", orderJson.encodeToString(updated)); walletRecords++ }
        }
        db.records("wallet:batch:").forEach { (key, body) ->
            val value = orderJson.decodeFromString<WalletBatch>(body)
            val updated = value.copy(holderId = uid(value.holderId), holderName = name(value.holderId, value.holderName), recipientId = uid(value.recipientId), recipientName = name(value.recipientId, value.recipientName))
            if (updated != value) { db.putRecord(key, orderJson.encodeToString(updated)); walletRecords++ }
        }
        for (prefix in listOf("notification", "invitation")) db.records("$prefix:${source.userId}:").forEach { (key, body) ->
            val destination = key.replaceFirst("$prefix:${source.userId}:", "$prefix:${target.userId}:")
            val updated = if (prefix == "invitation") orderJson.encodeToString(orderJson.decodeFromString<FoodInvitation>(body).copy(userId = target.userId)) else body
            if (db.record(destination) == null) db.putRecord(destination, updated)
            db.deleteRecord(key)
        }
        db.records("push-device:").forEach { (key, body) ->
            val value = orderJson.decodeFromString<PushDevice>(body)
            if (value.userId == source.userId) db.putRecord(key, orderJson.encodeToString(value.copy(userId = target.userId)))
        }
        db.records("push-job:").forEach { (key, body) ->
            val value = orderJson.decodeFromString<PushJob>(body)
            if (value.userId == source.userId) db.putRecord(key, orderJson.encodeToString(value.copy(userId = target.userId)))
        }
        db.records("email-job:").forEach { (key, body) ->
            val value = orderJson.decodeFromString<EmailJob>(body)
            if (value.userId == source.userId) db.putRecord(key, orderJson.encodeToString(value.copy(userId = target.userId)))
        }
        db.records(AdminService.CONTRIBUTOR_PREFIX).filter { it.second == source.userId }.forEach { db.putRecord(it.first, target.userId) }
        for (prefix in listOf("email-contact", "wallet-search-email")) {
            db.record("$prefix:${source.userId}")?.let { if (db.record("$prefix:${target.userId}") == null) db.putRecord("$prefix:${target.userId}", it) }
            db.deleteRecord("$prefix:${source.userId}")
        }
        db.deleteRecord("profile:${source.userId}")
        db.deleteRecord("profile-sync:${source.userId}")
        db.deleteRecord("admin:profile-name:${source.userId}")
        db.putRecord("account-alias:${source.userId}", target.userId)
        affected += ProfileUpdates(db) { now }.save(merged)
        WalletService.snapshot(db, target.userId) // Reject an inconsistent ledger before committing any part of the repair.
        val result = AccountMergeResult(source.userId, target.userId, merged.name, affected.distinct(), walletRecords, now)
        db.putRecord(repairKey, orderJson.encodeToString(result))
        result
    }
}
