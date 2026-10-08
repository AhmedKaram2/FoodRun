package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

@kotlinx.serialization.Serializable data class GroupWalletHistoryPrompt(val title: String, val subtitle: String, val balance: String, val cards: List<GroupCard>, val loading: Boolean, val error: String, val hasMore: Boolean)

/** Read requests stay outside the mutation queue; history is never persisted on disk. */
internal class GroupWalletHistory(private val c: GroupController) {
    private var key: WalletKey? = null
    private var origin = GroupPage.HOME
    private var userId = ""
    private var version = 0L
    private var history: WalletHistory? = null
    private var loading = false
    private var failure = ""
    private fun tr(en: String, ar: String) = if(c.library.language == "ar") ar else en
    fun open(value: String) {
        val selected = orderJson.decodeFromString<WalletKey>(value)
        val wallet = c.library.home?.wallet ?: error("Open your wallet after signing in.")
        require(wallet.balances.any { it.customerId == selected.customerId && it.holderId == selected.holderId && it.currency == selected.currency } || wallet.topUps.any { it.customerId == selected.customerId && it.holderId == selected.holderId && it.currency == selected.currency }) { "Choose one of your wallets." }
        dismiss(); key = selected; origin = c.page; userId = c.library.home!!.profile.userId; load()
    }
    fun dismiss() { version++; key = null; history = null; loading = false; failure = "" }
    fun more() { history?.nextCursor?.takeIf { it.isNotEmpty() }?.let(::load) }
    fun refresh() { if(key != null) load() }
    private fun load(cursor: String = "") {
        if(loading) return
        val selected = key ?: return
        val hub = requireNotNull(c.library.identityHub); val token = c.library.identityToken
        val epoch = ++version; loading = true; failure = ""; if(cursor.isEmpty()) history = null
        c.publish()
        fun failed(message: String) { loading = false; failure = message; c.publish() }
        try {
            val command = RoomCommand(commandId = c.platform.uuid(), kind = CommandKind.WALLET_HISTORY, identityToken = token, walletDetails = true, walletKey = selected, currency = selected.currency, walletHistoryCursor = cursor)
            c.platform.request(hub, orderJson.encodeToString(command), object : GroupReplyCallback {
                override fun complete(body: String, error: String) {
                    if(epoch != version || key != selected || c.library.identityToken != token || c.library.home?.profile?.userId != userId || c.page != origin || c.library.identityHub != hub) return
                    if(error.isNotBlank()) { failed(error); return }
                    try {
                        val reply = orderJson.decodeFromString<RoomReply>(body); require(reply.ok) { reply.error }
                        val next = requireNotNull(reply.walletHistory) { "Could not load wallet transactions." }
                        require(next.balance.customerId == selected.customerId && next.balance.holderId == selected.holderId && next.balance.currency == selected.currency)
                        history = next.copy(transactions = if(cursor.isEmpty()) next.transactions else (history?.transactions.orEmpty() + next.transactions).distinctBy { it.id })
                        loading = false; c.publish()
                    } catch(e: Exception) { failed(e.message ?: "Could not load wallet transactions.") }
                }
            })
        } catch(e: Exception) { failed(e.message ?: "Could not load wallet transactions.") }
    }
    private fun date(value: Long) = PickupDateFormatterForGroups.format(value)
    private fun card(v: WalletTransaction): GroupCard {
        val title = when(v.kind) { WalletTransactionKind.TOP_UP -> tr("Wallet top-up", "شحن المحفظة"); WalletTransactionKind.PAYMENT -> tr("Wallet payment", "دفعة من المحفظة"); WalletTransactionKind.CASH_TRANSFER -> tr("Holder cash transfer", "تحويل نقدي من حامل الأموال") }
        val status = when(v.status) {
            WalletTransactionStatus.PENDING -> tr("Awaiting top-up confirmation", "بانتظار تأكيد الشحن")
            WalletTransactionStatus.CONFIRMED -> tr("Wallet credited", "تم شحن المحفظة")
            WalletTransactionStatus.REJECTED -> tr("Not received", "لم يتم الاستلام")
            WalletTransactionStatus.APPROVAL_PENDING -> tr("Awaiting wallet approval", "بانتظار الموافقة على دفعة المحفظة")
            WalletTransactionStatus.OWING -> tr("Holder still needs to send cash", "على حامل الأموال إرسال المبلغ")
            WalletTransactionStatus.SENT -> tr("Cash sent; awaiting confirmation", "أُرسل المبلغ؛ بانتظار التأكيد")
            WalletTransactionStatus.SETTLED -> tr("Cash settled", "تمت تسوية المبلغ")
            WalletTransactionStatus.RETURNED -> tr("Payment declined; funds returned", "رُفضت الدفعة وأُعيد الرصيد")
        }
        val lines = mutableListOf((if(v.balanceChange > 0) "+" else if(v.balanceChange < 0) "−" else "") + Money.format(v.amount,v.currency), date(v.createdAt), "${tr("From", "من")}: ${v.fromName}", "${tr("To", "إلى")}: ${v.toName}")
        lines += v.orders.map { "${it.roomName} #${it.orderNumber} · ${Money.format(it.amount,v.currency)}" }
        v.account?.let { lines += "${it.holder} · ${it.bank}\n${it.identifier}" }
        if(v.note.isNotEmpty()) lines += "${tr("Note", "ملاحظة")}: ${v.note}"
        if(v.resolvedAt > 0) lines += "${tr("Reviewed", "تمت المراجعة")}: ${date(v.resolvedAt)}"
        if(v.kind == WalletTransactionKind.CASH_TRANSFER) lines += tr("Already deducted from the wallet; this transfer does not deduct it again.", "خُصم المبلغ من المحفظة سابقاً؛ لا يخصمه هذا التحويل مرة أخرى.")
        if(v.status == WalletTransactionStatus.RETURNED) lines += tr("The reserved amount was returned to this wallet.", "أُعيد المبلغ المحجوز إلى هذه المحفظة.")
        return GroupCard(v.id,title,lines.joinToString("\n"),status)
    }
    val prompt: GroupWalletHistoryPrompt? get() {
        val selected = key ?: return null
        if(c.page != origin || c.library.home?.profile?.userId != userId) { dismiss(); return null }
        val balance = history?.balance
        return GroupWalletHistoryPrompt(tr("Wallet transactions", "معاملات المحفظة"), balance?.let { "${it.customerName} · ${tr("Held by", "لدى")} ${it.holderName}" }.orEmpty(), balance?.let { "${tr("Available balance", "الرصيد المتاح")}: ${Money.format(it.available,selected.currency)}" }.orEmpty(), history?.transactions.orEmpty().map(::card), loading,failure,history?.nextCursor?.isNotEmpty() == true)
    }
}
