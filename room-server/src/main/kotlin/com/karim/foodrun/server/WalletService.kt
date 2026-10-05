package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import java.util.UUID

/** Manual custody: no credit until receipt, no cash settlement until the final recipient confirms. */
internal class WalletService(private val db: RoomDatabase, private val accounts: AccountService, private val notifications: NotificationService,
    private val clock: () -> Long, private val changed: (String) -> Unit) {
    fun execute(c: RoomCommand, actor: String? = null): RoomReply {
        val uid = accounts.userId(c.identityToken)
        require(c.userId.length <= 128 && c.currency.length <= 3 && c.text.length <= if (c.kind == CommandKind.WALLET_PEOPLE) 254 else 160) { "Invalid wallet details." }
        Money.precision(c.currency)
        when (c.kind) {
            CommandKind.WALLET_PEOPLE -> {
                val query = c.text.trim()
                if (query.isEmpty()) return RoomReply(walletPeople = emptyList())
                return RoomReply(walletPeople = db.records("profile:").map { orderJson.decodeFromString<FoodProfile>(it.second) }
                    .filter { it.userId != uid && it.discoverable && it.name.isNotBlank() && AccountRestrictions.current(db, it.userId, clock()) == null }
                    .filter { it.name.contains(query, true) || EmailContacts.searchAddress(db, it.userId)?.contains(query, true) == true }
                    .sortedBy { it.name.lowercase() }.take(50).map { FoodPerson(it.userId, it.name) })
            }
            CommandKind.WALLET_RECIPIENT -> {
                val person = accounts.paymentRoomProfile(c.userId)
                val related = payments(db).any { it.holderId == uid && it.recipientId == c.userId && it.status != WalletPaymentStatus.SETTLED }
                require(person.userId != uid && (person.discoverable || related)) { "Choose an available user." }
                return RoomReply(walletRecipient = WalletRecipient(FoodPerson(person.userId, person.name), methods(person, c.currency)))
            }
            CommandKind.WALLET_TOP_UP -> {
                val holder = accounts.paymentRoomProfile(c.userId)
                require(holder.userId != uid && holder.discoverable) { "Choose another available user to hold your wallet money." }
                require(c.amount in 1..MAX_AMOUNT) { "Enter a positive wallet amount within the supported limit." }
                require(topUps(db).count { it.customerId == uid && it.status == WalletStatus.PENDING } < 20) { "Resolve your pending top-ups first." }
                val method = methods(holder, c.currency).singleOrNull { it.id == c.accountId } ?: error("Choose a current receiving method in this currency.")
                val customer = accounts.paymentRoomProfile(uid)
                val value = WalletTopUp(id(), uid, customer.name, holder.userId, holder.name, c.amount, c.currency, method, c.text.trim(), createdAt = clock())
                put(value)
                notifications.wallet(holder.userId, value.id, "wallet_top_up", "Wallet top-up: confirm receipt" to "شحن محفظة: أكد الاستلام",
                    "${customer.name} · ${Money.format(value.amount, value.currency)}" to "${customer.name} · ${Money.format(value.amount, value.currency)}")
            }
            CommandKind.WALLET_REVIEW_TOP_UP -> {
                val value = requireNotNull(db.record("wallet:top-up:${c.transferId}")) { "Top-up was not found." }.let { orderJson.decodeFromString<WalletTopUp>(it) }
                require(value.holderId == uid) { "Only the cash holder can confirm this top-up." }
                require(value.status == WalletStatus.PENDING) { "This top-up was already reviewed." }
                put(value.copy(status = if (c.flag) WalletStatus.CONFIRMED else WalletStatus.REJECTED, resolvedAt = clock()))
                notifications.wallet(value.customerId, "${value.id}:review", "wallet_top_up_reviewed",
                    if(c.flag) "Wallet credited" to "تم شحن المحفظة" else "Top-up not received" to "لم يتم استلام الشحن",
                    "${value.holderName} · ${Money.format(value.amount, value.currency)}" to "${value.holderName} · ${Money.format(value.amount, value.currency)}")
            }
            CommandKind.PAY_WITH_WALLET -> pay(c, uid, requireNotNull(actor))
            CommandKind.WALLET_DECLARE_BATCH -> declareBatch(c, uid)
            CommandKind.WALLET_REVIEW_BATCH -> reviewBatch(c, uid)
            else -> error("Unknown wallet action.")
        }
        return accounts.home(c.identityToken)
    }
    private fun pay(c: RoomCommand, uid: String, actor: String) {
        val room = requireNotNull(db.room(c.roomId))
        require(db.record("member-user:${room.id}:$actor") == uid) { "Sign in with the account that owns this room membership." }
        require(room.orderNumber == c.expectedOrderNumber && room.revision == c.expectedRevision) { "The order changed. Refresh and try again." }
        require(room.settlementOpen && room.restaurantPaid) { "Wait until the order and restaurant payment are settled." }
        require(room.orderingMembers.any { it.id == actor } && room.payerId != actor) { "Your own contribution needs no transfer." }
        require(room.transfers.none { it.memberId == actor && it.status == TransferStatus.DECLARED }) { "Resolve your pending direct payment first." }
        val receipt = Billing.receipts(room).single { it.memberId == actor }
        require(receipt.balance > 0 && c.amount == receipt.balance) { "Pay the current remaining amount with your wallet." }
        val recipientId = requireNotNull(db.record("member-user:${room.id}:${room.payerId}")) { "The selected person must link their account before wallet payment." }
        val recipient = accounts.paymentRoomProfile(recipientId)
        val funds = snapshot(db, uid).balances.filter { it.customerId == uid && it.currency == receipt.currency && it.available > 0 }
            .sortedWith(compareByDescending<WalletBalance> { it.holderId == recipientId }.thenByDescending { it.available }.thenBy { it.holderId })
        require(funds.sumOf { it.available } >= receipt.balance) { "Your confirmed wallet balance is too low. Top up or pay directly." }
        var left = receipt.balance
        val allocations = funds.mapNotNull { fund ->
            if (left == 0L) null else {
                val amount = minOf(left, fund.available); left -= amount
                WalletPayment(id(), uid, receipt.name, fund.holderId, fund.holderName, recipientId, recipient.name, room.id, room.name,
                    room.orderNumber, actor, amount, receipt.currency, if(fund.holderId == recipientId) WalletPaymentStatus.SETTLED else WalletPaymentStatus.OWING, createdAt = clock())
            }
        }
        allocations.forEach { value ->
            put(value)
            if(value.status != WalletPaymentStatus.SETTLED) notifications.wallet(value.holderId, value.id, "wallet_payment_due",
                "Wallet payment to send" to "دفعة محفظة للإرسال", "${value.customerName} → ${value.recipientName} · ${Money.format(value.amount, value.currency)}" to "${value.customerName} ← ${value.recipientName} · ${Money.format(value.amount, value.currency)}")
        }
        val updated = room.copy(walletPayments = room.walletPayments + allocations, transfers = room.transfers + allocations.map {
            Transfer("wallet-${it.id}", actor, it.amount, "Wallet · ${it.holderName}", requireNotNull(room.account), status = TransferStatus.CONFIRMED, createdAt = clock())
        }, revision = room.revision + 1, updatedAt = clock(), audit = room.audit + AuditEntry(c.commandId, actor, "PAY_WITH_WALLET", "Wallet funds allocated; holders remain responsible for cash settlement.", clock()))
        db.save(updated); changed(room.id)
    }
    private fun declareBatch(c: RoomCommand, uid: String) {
        val pending = payments(db).filter { it.holderId == uid && it.recipientId == c.userId && it.currency == c.currency && it.status == WalletPaymentStatus.OWING }.sortedBy { it.id }
        require(pending.isNotEmpty() && pending.size <= 500) { "There are no wallet payments to send in this group." }
        val total = pending.sumOf { it.amount }
        require(c.amount == total) { "The group amount changed. Refresh before marking the full payment sent." }
        val recipient = accounts.paymentRoomProfile(c.userId)
        val method = methods(recipient, c.currency).singleOrNull { it.id == c.accountId } ?: error("Choose the recipient's current receiving method.")
        val batch = WalletBatch(id(), uid, pending.first().holderName, c.userId, recipient.name, total, c.currency, pending.map { it.id }, method, c.text.trim(), createdAt = clock())
        put(batch)
        updatePayments(pending.map { it.copy(status = WalletPaymentStatus.SENT, batchId = batch.id) })
        notifications.wallet(batch.recipientId, batch.id, "wallet_batch", "Grouped wallet payment: confirm receipt" to "دفعة محافظ مجمعة: أكد الاستلام",
            "${batch.holderName} · ${Money.format(total, c.currency)} · ${pending.size} payments" to "${batch.holderName} · ${Money.format(total, c.currency)} · ${pending.size} دفعات")
    }
    private fun reviewBatch(c: RoomCommand, uid: String) {
        val batch = requireNotNull(db.record("wallet:batch:${c.transferId}")) { "Grouped payment was not found." }.let { orderJson.decodeFromString<WalletBatch>(it) }
        require(batch.recipientId == uid) { "Only the receiving person can confirm this payment." }
        require(batch.status == WalletStatus.PENDING) { "This payment was already reviewed." }
        val values = batch.paymentIds.map { paymentId -> requireNotNull(db.record("wallet:payment:$paymentId")).let { orderJson.decodeFromString<WalletPayment>(it) } }
        require(values.all { it.status == WalletPaymentStatus.SENT && it.batchId == batch.id } && values.sumOf { it.amount } == batch.amount) { "The grouped payment changed." }
        put(batch.copy(status = if(c.flag) WalletStatus.CONFIRMED else WalletStatus.REJECTED, resolvedAt = clock()))
        updatePayments(values.map { it.copy(status = if(c.flag) WalletPaymentStatus.SETTLED else WalletPaymentStatus.OWING, batchId = if(c.flag) batch.id else "") })
        (values.map { it.customerId } + batch.holderId).distinct().forEach { person -> notifications.wallet(person, "${batch.id}:review", "wallet_batch_reviewed",
            if(c.flag) "Wallet payment received" to "تم استلام دفعة المحفظة" else "Wallet transfer not received" to "لم يتم استلام تحويل المحفظة",
            "${batch.recipientName} · ${Money.format(batch.amount, batch.currency)}" to "${batch.recipientName} · ${Money.format(batch.amount, batch.currency)}") }
    }
    private fun updatePayments(values: List<WalletPayment>) {
        values.groupBy { it.roomId }.forEach { (roomId, group) ->
            val room = requireNotNull(db.room(roomId)) { "The order for this wallet payment is unavailable." }
            require(group.all { it.orderNumber == room.orderNumber && room.walletPayments.any { current -> current.id == it.id } }) { "This wallet payment belongs to another order." }
            val byId = group.associateBy { it.id }
            group.forEach(::put)
            db.save(room.copy(walletPayments = room.walletPayments.map { byId[it.id] ?: it }, revision = room.revision + 1, updatedAt = clock()))
            changed(roomId)
        }
    }
    private fun methods(profile: FoodProfile, currency: String) = profile.receivingAccounts.filter { it.currency == currency }.map { it.normalized().also(ReceivingAccount::validate) }
    private fun put(value: WalletTopUp) = db.putRecord("wallet:top-up:${value.id}", orderJson.encodeToString(value))
    private fun put(value: WalletPayment) = db.putRecord("wallet:payment:${value.id}", orderJson.encodeToString(value))
    private fun put(value: WalletBatch) = db.putRecord("wallet:batch:${value.id}", orderJson.encodeToString(value))
    private fun id() = UUID.randomUUID().toString()
    companion object {
        const val MAX_AMOUNT = 100_000_000L
        fun topUps(db: RoomDatabase) = db.records("wallet:top-up:").map { orderJson.decodeFromString<WalletTopUp>(it.second) }
        fun payments(db: RoomDatabase) = db.records("wallet:payment:").map { orderJson.decodeFromString<WalletPayment>(it.second) }
        fun snapshot(db: RoomDatabase, uid: String): WalletSnapshot {
            val credits = topUps(db).filter { it.customerId == uid || it.holderId == uid }
            val debits = payments(db).filter { it.customerId == uid || it.holderId == uid || it.recipientId == uid }
            val balances = credits.filter { it.status == WalletStatus.CONFIRMED }.groupBy { Triple(it.customerId, it.holderId, it.currency) }.map { (key, group) ->
                val credit = group.first()
                val amount = group.sumOf { it.amount } - debits.filter { Triple(it.customerId, it.holderId, it.currency) == key }.sumOf { it.amount }
                check(amount >= 0) { "Wallet ledger is inconsistent." }
                WalletBalance(key.first, credit.customerName, key.second, credit.holderName, key.third, amount)
            }
            val batches = db.records("wallet:batch:").map { orderJson.decodeFromString<WalletBatch>(it.second) }.filter { it.holderId == uid || it.recipientId == uid }
            // Pending confirmations must never disappear behind completed history.
            return WalletSnapshot(balances, credits.filter { it.status == WalletStatus.PENDING }.sortedByDescending { it.createdAt } + credits.filter { it.status != WalletStatus.PENDING }.sortedByDescending { it.createdAt }.take(100),
                debits.filter { it.status != WalletPaymentStatus.SETTLED }.sortedByDescending { it.createdAt } + debits.filter { it.status == WalletPaymentStatus.SETTLED }.sortedByDescending { it.createdAt }.take(100),
                batches.filter { it.status == WalletStatus.PENDING }.sortedByDescending { it.createdAt } + batches.filter { it.status != WalletStatus.PENDING }.sortedByDescending { it.createdAt }.take(100))
        }
    }
}
