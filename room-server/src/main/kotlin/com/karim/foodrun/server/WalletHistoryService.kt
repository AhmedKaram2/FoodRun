package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlinx.serialization.Serializable
import java.util.Base64

/** A private, paged view of one owner/holder/currency ledger. Reading never changes funds. */
internal class WalletHistoryService(private val db: RoomDatabase) {
    @Serializable private data class Cursor(val key: WalletKey, val createdAt: Long, val id: String)
    fun read(c: RoomCommand, uid: String): WalletHistory {
        val key = requireNotNull(c.walletKey) { "Choose a wallet." }
        require(key.customerId.length in 1..128 && key.holderId.length in 1..128) { "Choose a valid wallet." }
        require(uid == key.customerId || uid == key.holderId) { "Only the wallet owner or money holder can view its transactions." }
        Money.precision(key.currency)
        require(c.walletHistoryCursor.length <= 1024) { "Invalid wallet history page." }
        val cursor = c.walletHistoryCursor.takeIf { it.isNotEmpty() }?.let {
            runCatching { orderJson.decodeFromString<Cursor>(String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8)) }.getOrElse { error("Invalid wallet history page.") }
        }
        require(cursor == null || cursor.key == key && cursor.createdAt >= 0 && cursor.id.length in 1..200) { "Choose a history page for this wallet." }
        fun matches(customer: String, holder: String, currency: String) = customer == key.customerId && holder == key.holderId && currency == key.currency
        val topUps = WalletService.topUps(db).filter { matches(it.customerId, it.holderId, it.currency) }
        val payments = WalletService.payments(db).filter { matches(it.customerId, it.holderId, it.currency) }
        val cancelled = db.records("wallet:cancelled-payment:").map { orderJson.decodeFromString<WalletPayment>(it.second) }.filter { matches(it.customerId, it.holderId, it.currency) }
        require(topUps.isNotEmpty() || payments.isNotEmpty() || cancelled.isNotEmpty()) { "This wallet has no recorded transactions." }
        fun name(id: String, fallback: String) = db.record("profile:$id")?.let { orderJson.decodeFromString<FoodProfile>(it).name }?.takeIf { it.isNotBlank() } ?: fallback
        val available = topUps.filter { it.status == WalletStatus.CONFIRMED }.sumOf { it.amount } - payments.sumOf { it.amount }
        check(available >= 0) { "Wallet ledger is inconsistent." }
        val first = topUps.firstOrNull()
        val balance = WalletBalance(key.customerId, name(key.customerId, first?.customerName ?: payments.firstOrNull()?.customerName.orEmpty()),
            key.holderId, name(key.holderId, first?.holderName ?: payments.firstOrNull()?.holderName.orEmpty()), key.currency, available)
        val entries = topUps.map { value ->
            WalletTransaction("top-up:${value.id}", WalletTransactionKind.TOP_UP, value.amount, value.currency,
                WalletTransactionStatus.valueOf(value.status.name), if(value.status == WalletStatus.CONFIRMED) value.amount else 0,
                value.createdAt, value.resolvedAt, value.customerName, value.holderName, value.note, value.account)
        }.toMutableList()
        fun order(value: WalletPayment) = WalletTransactionOrder(value.roomName, value.orderNumber, value.amount)
        payments.forEach { value ->
            val awaiting = db.room(value.roomId)?.transfers?.any { it.id == "wallet-${value.id}" && it.status == TransferStatus.DECLARED } == true
            val status = if(value.status == WalletPaymentStatus.OWING && awaiting) WalletTransactionStatus.APPROVAL_PENDING else WalletTransactionStatus.valueOf(value.status.name)
            entries += WalletTransaction("payment:${value.id}", WalletTransactionKind.PAYMENT, value.amount, value.currency, status, -value.amount,
                value.createdAt, fromName = value.customerName, toName = value.recipientName, orders = listOf(order(value)))
        }
        cancelled.forEach { value ->
            entries += WalletTransaction("payment:${value.id}", WalletTransactionKind.PAYMENT, value.amount, value.currency, WalletTransactionStatus.RETURNED, 0,
                value.createdAt, db.record("wallet:cancelled-at:${value.id}")?.toLongOrNull() ?: 0,
                value.customerName, value.recipientName, db.record("wallet:cancelled-note:${value.id}").orEmpty(), orders = listOf(order(value)))
        }
        val byId = payments.associateBy { it.id }
        db.records("wallet:batch:").map { orderJson.decodeFromString<WalletBatch>(it.second) }.filter { it.currency == key.currency }.forEach { batch ->
            val covered = batch.paymentIds.mapNotNull { byId[it] }
            if(covered.isNotEmpty()) entries += WalletTransaction("cash:${batch.id}", WalletTransactionKind.CASH_TRANSFER, covered.sumOf { it.amount }, batch.currency,
                when(batch.status) { WalletStatus.PENDING -> WalletTransactionStatus.SENT; WalletStatus.CONFIRMED -> WalletTransactionStatus.SETTLED; WalletStatus.REJECTED -> WalletTransactionStatus.REJECTED },
                0, batch.createdAt, batch.resolvedAt, batch.holderName, batch.recipientName, batch.note, batch.account, covered.map(::order))
        }
        val ordered = entries.sortedWith(compareByDescending<WalletTransaction> { it.createdAt }.thenByDescending { it.id })
        val page = ordered.filter { cursor == null || it.createdAt < cursor.createdAt || it.createdAt == cursor.createdAt && it.id < cursor.id }.take(PAGE_SIZE + 1)
        val visible = page.take(PAGE_SIZE)
        val next = if(page.size > PAGE_SIZE) visible.last().let { Base64.getUrlEncoder().withoutPadding().encodeToString(orderJson.encodeToString(Cursor(key, it.createdAt, it.id)).toByteArray(Charsets.UTF_8)) } else ""
        return WalletHistory(balance, visible, next)
    }
    companion object { const val PAGE_SIZE = 20 }
}
