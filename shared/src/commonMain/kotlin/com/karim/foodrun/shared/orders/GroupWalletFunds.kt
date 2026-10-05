package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

internal class GroupWalletFunds(private val c: GroupController) {
    private var people = emptyList<FoodPerson>()
    private var searched = false
    private var recipient: WalletRecipient? = null
    private var selectedUser = ""
    private var batchRecipient = ""
    private var batchCurrency = "AED"
    private val uid get() = c.library.home?.profile?.userId.orEmpty()
    private val wallet get() = c.library.home?.wallet ?: WalletSnapshot()
    private fun tr(en: String, ar: String) = if(c.library.language == "ar") ar else en
    private fun currency() = if(c.page == GroupPage.WALLET_BATCH) batchCurrency else c.text(GroupFieldKey.WALLET_CURRENCY).ifBlank { "AED" }
    private fun action(title: String, value: String, enabled: Boolean = true) = GroupButton(title, GroupAction.WALLET_FUNDS_ACTION, value, enabled = enabled)
    private fun send(kind: CommandKind, transferId: String = "", flag: Boolean = false, userId: String = selectedUser, amount: Long = 0, text: String = "", returnPage: GroupPage = c.page) {
        val methods = recipient?.accounts.orEmpty().filter { it.currency == currency() }
        val accountId = c.text(GroupFieldKey.WALLET_ACCOUNT).takeIf { choice -> methods.any { it.id == choice } } ?: methods.firstOrNull()?.id.orEmpty()
        c.send(RoomCommand(commandId = c.platform.uuid(), kind = kind, identityToken = c.library.identityToken, userId = userId,
            currency = currency(), amount = amount, accountId = accountId, transferId = transferId, flag = flag, text = text), returnPage = returnPage)
    }
    fun accept(reply: RoomReply, command: RoomCommand) {
        reply.walletPeople?.takeIf { c.page == GroupPage.WALLET_TOP_UP && command.text.trim().isNotEmpty() && command.text.trim() == c.text(GroupFieldKey.WALLET_SEARCH).trim() }?.let { people = it; searched = true }
        reply.walletRecipient?.takeIf { c.page in listOf(GroupPage.WALLET_TOP_UP, GroupPage.WALLET_BATCH) && command.currency == currency() && command.userId == if(c.page == GroupPage.WALLET_BATCH) batchRecipient else selectedUser }?.let { recipient = it; c.draft[GroupFieldKey.WALLET_ACCOUNT] = it.accounts.firstOrNull()?.id.orEmpty() }
    }
    fun searchChanged() { people = emptyList(); searched = false; recipient = null; selectedUser = ""; c.draft.remove(GroupFieldKey.WALLET_ACCOUNT) }
    fun dispatch(value: String) {
        val action = value.substringBefore('|'); val id = value.substringAfter('|', "")
        when(action) {
            "charge" -> {
                searchChanged()
                val parts = id.split('|')
                val currency = parts.firstOrNull()?.takeIf { it in Money.currencies } ?: c.library.home?.profile?.payment?.currency ?: "AED"
                val amount = parts.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0 }
                c.draft[GroupFieldKey.WALLET_AMOUNT] = amount?.let { Money.format(it, currency).substringAfter(' ') } ?: "100"
                c.draft[GroupFieldKey.WALLET_CURRENCY] = currency
                c.draft.remove(GroupFieldKey.WALLET_NOTE); c.draft.remove(GroupFieldKey.WALLET_SEARCH); c.page = GroupPage.WALLET_TOP_UP
            }
            "search" -> { val query = c.text(GroupFieldKey.WALLET_SEARCH).trim(); if (query.isNotEmpty()) send(CommandKind.WALLET_PEOPLE, text = query) }
            "person" -> { selectedUser = id; recipient = null; send(CommandKind.WALLET_RECIPIENT) }
            "methods" -> send(CommandKind.WALLET_RECIPIENT, userId = if(c.page == GroupPage.WALLET_BATCH) batchRecipient else selectedUser)
            "top-up" -> {
                require(recipient?.person?.userId == selectedUser && selectedUser.isNotEmpty())
                send(CommandKind.WALLET_TOP_UP, amount = Money.parse(c.text(GroupFieldKey.WALLET_AMOUNT), currency()), text = c.text(GroupFieldKey.WALLET_NOTE), returnPage = GroupPage.PROFILE)
            }
            "top-up-yes", "top-up-no" -> send(CommandKind.WALLET_REVIEW_TOP_UP, transferId = id, flag = action == "top-up-yes", returnPage = GroupPage.PROFILE)
            "batch" -> {
                val payment = wallet.payments.single { it.id == id }
                batchRecipient = payment.recipientId; batchCurrency = payment.currency; recipient = null; c.draft.remove(GroupFieldKey.WALLET_NOTE)
                c.page = GroupPage.WALLET_BATCH; send(CommandKind.WALLET_RECIPIENT, userId = batchRecipient)
            }
            "send-batch" -> send(CommandKind.WALLET_DECLARE_BATCH, userId = batchRecipient, amount = group().sumOf { it.amount }, text = c.text(GroupFieldKey.WALLET_NOTE), returnPage = GroupPage.PROFILE)
            "batch-yes", "batch-no" -> send(CommandKind.WALLET_REVIEW_BATCH, transferId = id, flag = action == "batch-yes", returnPage = GroupPage.PROFILE)
            "copy" -> c.platform.copyToClipboard(recipient!!.accounts.single { it.id == id }.identifier)
            else -> error("Unknown wallet action.")
        }
    }
    fun pay(roomId: String) {
        val session = c.library.sessions.single { it.roomId == roomId }
        val room = requireNotNull(c.library.snapshots[roomId]?.room)
        val receipt = c.library.snapshots.getValue(roomId).receipts.single { it.memberId == session.memberId }
        c.send(RoomCommand(commandId = c.platform.uuid(), kind = CommandKind.PAY_WITH_WALLET, roomId = roomId, token = session.token,
            expectedRevision = room.revision, expectedOrderNumber = room.orderNumber, amount = receipt.balance), returnPage = if(c.page == GroupPage.HOME || c.page == GroupPage.PROFILE) c.page else GroupPage.PAYMENT)
    }
    private fun group() = wallet.payments.filter { it.holderId == uid && it.recipientId == batchRecipient && it.currency == batchCurrency && it.status == WalletPaymentStatus.OWING }
    fun payButton(room: Room, memberId: String, receipt: Receipt): GroupButton? {
        if(c.library.home?.wallet == null || receipt.balance <= 0 || receipt.memberId != memberId || room.payerId == memberId || !room.restaurantPaid || !room.settlementOpen || room.transfers.any { it.memberId == memberId && it.status == TransferStatus.DECLARED }) return null
        val balance = wallet.balances.filter { it.customerId == uid && it.currency == receipt.currency }.sumOf { it.available }
        return GroupButton("${tr("Pay with wallet", "الدفع بالمحفظة")} · ${Money.format(receipt.balance, receipt.currency)}", GroupAction.PAY_WITH_WALLET, room.id, enabled = balance >= receipt.balance)
    }
    fun clear() { people = emptyList(); recipient = null; selectedUser = ""; batchRecipient = "" }
    fun topUpButton(room: Room, memberId: String, receipt: Receipt): GroupButton? {
        if (payButton(room, memberId, receipt)?.enabled != false) return null
        val balance = wallet.balances.filter { it.customerId == uid && it.currency == receipt.currency }.sumOf { it.available }
        val shortfall = receipt.balance - balance
        return action("${tr("Top up", "اشحن")} · ${Money.format(shortfall, receipt.currency)}", "charge|${receipt.currency}|$shortfall")
    }
    fun cards(prefix: String): List<GroupCard> {
        if(c.library.home?.wallet == null) return emptyList()
        val cards = mutableListOf<GroupCard>()
        val mine = wallet.balances.filter { it.customerId == uid }
        cards += GroupCard("${prefix}wallet-funds", tr("Wallet balance", "رصيد المحفظة"),
            mine.groupBy { it.currency }.map { (currency, values) -> Money.format(values.sumOf { it.available }, currency) }.joinToString("\n").ifEmpty { tr("No confirmed balance yet", "لا يوجد رصيد مؤكد بعد") },
            buttons = listOf(action(tr("Charge wallet", "شحن المحفظة"), "charge")))
        mine.forEach { cards += GroupCard("${prefix}wallet-held:${it.holderId}:${it.currency}", "${tr("Held by", "لدى")} ${it.holderName}", Money.format(it.available, it.currency)) }
        wallet.balances.filter { it.holderId == uid && it.customerId != uid }.forEach { cards += GroupCard("${prefix}wallet-holding:${it.customerId}:${it.currency}", "${tr("Money I hold for", "أموال لدي تخص")} ${it.customerName}", Money.format(it.available, it.currency), tr("Available for their orders", "متاح لطلباتهم")) }
        wallet.topUps.forEach { topUp ->
            val buttons = if(topUp.holderId == uid && topUp.status == WalletStatus.PENDING) listOf(action(tr("Confirm received", "تأكيد الاستلام"), "top-up-yes|${topUp.id}"), action(tr("Not received", "لم أستلم"), "top-up-no|${topUp.id}")) else emptyList()
            cards += GroupCard("${prefix}wallet-top-up:${topUp.id}", "${topUp.customerName} → ${topUp.holderName}",
                "${Money.format(topUp.amount, topUp.currency)}\n${topUp.account.bank} · ${topUp.account.identifier}\n${topUp.note}", when(topUp.status) {
                    WalletStatus.PENDING -> tr("Awaiting receipt confirmation", "بانتظار تأكيد الاستلام")
                    WalletStatus.CONFIRMED -> tr("Wallet credited", "تم شحن المحفظة")
                    WalletStatus.REJECTED -> tr("Not received", "لم يتم الاستلام")
                }, buttons)
        }
        wallet.payments.filter { it.holderId == uid && it.status == WalletPaymentStatus.OWING }.groupBy { it.recipientId to it.currency }.forEach { (_, values) ->
            cards += GroupCard("${prefix}wallet-group:${values.first().id}", "${tr("Pay", "ادفع إلى")} ${values.first().recipientName}",
                "${Money.format(values.sumOf { it.amount }, values.first().currency)}\n" + values.joinToString("\n") { "${it.customerName} · ${it.roomName} #${it.orderNumber} · ${Money.format(it.amount, it.currency)}" },
                buttons = listOf(action(tr("Pay full group amount", "دفع كامل المبلغ المجمع"), "batch|${values.first().id}")))
        }
        wallet.batches.filter { it.status == WalletStatus.PENDING }.forEach { batch ->
            cards += GroupCard("${prefix}wallet-batch:${batch.id}", "${batch.holderName} → ${batch.recipientName}",
                "${Money.format(batch.amount, batch.currency)}\n${batch.paymentIds.size} ${tr("payments", "دفعات")}\n${batch.note}", tr("Confirm only after receiving the full amount", "أكد فقط بعد استلام كامل المبلغ"),
                if(batch.recipientId == uid) listOf(action(tr("Confirm full amount received", "تأكيد استلام كامل المبلغ"), "batch-yes|${batch.id}"), action(tr("Not received", "لم أستلم"), "batch-no|${batch.id}")) else emptyList())
        }
        wallet.payments.filter { it.customerId == uid || it.recipientId == uid }.forEach { payment ->
            cards += GroupCard("${prefix}wallet-payment:${payment.id}", "${payment.customerName} · ${payment.roomName} #${payment.orderNumber}",
                "${Money.format(payment.amount, payment.currency)} · ${payment.holderName} → ${payment.recipientName}", status(payment))
        }
        return cards
    }
    fun status(payment: WalletPayment) = when(payment.status) {
        WalletPaymentStatus.OWING -> tr("Wallet paid; holder still needs to send cash", "تم الدفع بالمحفظة؛ على الشخص إرسال النقد")
        WalletPaymentStatus.SENT -> tr("Cash sent; awaiting recipient confirmation", "أُرسل النقد؛ بانتظار تأكيد المستلم")
        WalletPaymentStatus.SETTLED -> tr("Cash settled", "تمت تسوية النقد")
    }
    fun content(): GroupFlowContent {
        val fields = mutableListOf<GroupField>(); val cards = mutableListOf<GroupCard>(); val buttons = mutableListOf<GroupButton>()
        if(c.page == GroupPage.WALLET_TOP_UP) {
            fields += GroupField(GroupFieldKey.WALLET_AMOUNT, tr("Top-up amount", "مبلغ الشحن"), c.text(GroupFieldKey.WALLET_AMOUNT))
            fields += GroupField(GroupFieldKey.WALLET_CURRENCY, tr("Currency", "العملة"), currency(), choices = Money.currencies.map { GroupChoice(it, it) })
            val query = c.text(GroupFieldKey.WALLET_SEARCH).trim()
            fields += GroupField(GroupFieldKey.WALLET_SEARCH, tr("Search by name or email", "البحث بالاسم أو البريد الإلكتروني"), c.text(GroupFieldKey.WALLET_SEARCH))
            buttons += action(tr("Search users", "البحث عن المستخدمين"), "search", enabled = query.isNotEmpty())
            if (query.isEmpty()) cards += GroupCard("wallet-search-help", tr("Find your money holder", "ابحث عن الشخص الذي سيحتفظ بأموالك"), tr("Type a name or email, then search.", "اكتب اسماً أو بريداً إلكترونياً ثم ابحث."))
            else people.forEach { cards += GroupCard("wallet-person:${it.userId}", it.name, buttons = listOf(action(tr("Hold my wallet money", "الاحتفاظ بأموال محفظتي"), "person|${it.userId}"))) }
            if (query.isNotEmpty() && searched && people.isEmpty()) cards += GroupCard("wallet-search-empty", tr("No matching users", "لا يوجد مستخدمون مطابقون"), tr("Try another name or email.", "جرّب اسماً آخر أو بريداً إلكترونياً."))
        } else {
            val values = group()
            cards += GroupCard("wallet-group-total", tr("Full group amount", "كامل المبلغ المجمع"), Money.format(values.sumOf { it.amount }, batchCurrency))
            values.forEach { cards += GroupCard("wallet-group-person:${it.id}", it.customerName, "${it.roomName} #${it.orderNumber} · ${Money.format(it.amount, it.currency)}") }
        }
        recipient?.let { person ->
            val methods = person.accounts.filter { it.currency == currency() }
            cards += GroupCard("wallet-recipient", person.person.name, tr("Send money using their receiving method, then mark sent. This button records payment; it does not transfer money from your bank.", "أرسل المبلغ باستخدام وسيلة الاستلام ثم أكد الإرسال. هذا الزر يسجل الدفع ولا يحول الأموال من البنك."))
            fields += GroupField(GroupFieldKey.WALLET_ACCOUNT, tr("Receiving method", "وسيلة الاستلام"), c.text(GroupFieldKey.WALLET_ACCOUNT), choices = methods.map { GroupChoice(it.id, "${it.bank} · ${it.identifier}") })
            fields += GroupField(GroupFieldKey.WALLET_NOTE, tr("Payment note (optional)", "ملاحظة الدفع (اختياري)"), c.text(GroupFieldKey.WALLET_NOTE))
            methods.forEach { cards += GroupCard("wallet-method:${it.id}", "${it.holder} · ${it.bank}", it.identifier, buttons = listOf(action(tr("Copy", "نسخ"), "copy|${it.id}"))) }
            buttons += action(tr("Refresh receiving methods", "تحديث وسائل الاستلام"), "methods")
            if(methods.isEmpty()) cards += GroupCard("wallet-method-empty", tr("No receiving method in this currency", "لا توجد وسيلة استلام بهذه العملة"))
            else buttons += action(if(c.page == GroupPage.WALLET_TOP_UP) tr("I sent the top-up", "أرسلت مبلغ الشحن") else tr("I sent the full amount", "أرسلت كامل المبلغ"), if(c.page == GroupPage.WALLET_TOP_UP) "top-up" else "send-batch")
        }
        return GroupFlowContent(fields, cards, buttons)
    }
}
