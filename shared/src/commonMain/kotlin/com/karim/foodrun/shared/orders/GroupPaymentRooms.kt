package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

/** Native payment-room forms share the existing receipt and transfer commands. */
internal class GroupPaymentRooms(private val c: GroupController) {
    private val shares = linkedMapOf<String, PaymentShare>()
    private var editing = ""
    private var mode = "create"
    private var boundRoom = ""
    private var boundOrder = 0L
    private fun tr(en: String, ar: String) = if(c.library.language == "ar") ar else en
    private fun field(key: GroupFieldKey, en: String, ar: String, multiline: Boolean = false) = GroupField(key, tr(en, ar), c.text(key), multiline)
    private fun profile() = requireNotNull(c.library.home?.profile) { "Sign in first." }
    private fun people(): List<FoodPerson> = listOf(FoodPerson(profile().userId, profile().name)) + c.library.home!!.people.filterNot { it.userId == profile().userId }
    private fun amount(key: GroupFieldKey) = Money.parse(c.text(key).ifBlank { "0" }, "AED")
    private fun display(amount: Long) = Money.format(amount, "AED").substringAfter(' ')
    fun create() {
        profile(); requireNotNull(c.library.identityHub)
        mode = "create"; shares.clear(); editing = ""
        shares[profile().userId] = PaymentShare(profile().userId, tr("My share", "حصتي"), 0)
        c.draft[GroupFieldKey.PAYMENT_ROOM_NAME] = "Mohre"
        c.draft[GroupFieldKey.PAYMENT_RESTAURANT] = ""
        c.draft[GroupFieldKey.PAYMENT_DETAILS] = ""
        c.draft[GroupFieldKey.RECEIPT_PHOTO] = ""
        c.draft[GroupFieldKey.PAYMENT_TOTAL] = ""
        c.page = GroupPage.PAYMENT_ROOM
    }
    fun editReceipt() {
        require(c.room().payerId == c.me())
        val details = requireNotNull(c.room().paymentRoom)
        mode = "receipt"; boundRoom = c.room().id; boundOrder = c.room().orderNumber
        c.draft[GroupFieldKey.PAYMENT_DETAILS] = details.orderDetails
        c.draft[GroupFieldKey.RECEIPT_PHOTO] = details.receiptPhoto
        c.page = GroupPage.PAYMENT_ROOM
    }
    fun editShare(id: String, existing: Boolean = false) {
        mode = if(existing) "share" else "create"
        editing = id
        val share = if(existing) {
            require(c.room().payerId == c.me() && c.room().paymentRoom != null)
            boundRoom = c.room().id; boundOrder = c.room().orderNumber
            val receipt = c.reply!!.receipts.single { it.memberId == id }
            PaymentShare(id, receipt.lines.firstOrNull()?.description ?: tr("Food share", "حصة الطعام"), receipt.food)
        } else shares[id] ?: PaymentShare(id, tr("Food share", "حصة الطعام"), 0)
        c.draft[GroupFieldKey.PAYMENT_DESCRIPTION] = share.description
        c.draft[GroupFieldKey.PAYMENT_SHARE] = display(share.amount)
        c.draft[GroupFieldKey.PAYMENT_RECEIVED] = display(share.received)
        c.page = GroupPage.PAYMENT_SHARE
    }
    fun removeShare(id: String) { require(id != profile().userId); shares.remove(id) }
    fun saveShare() {
        val share = PaymentShare(editing, c.text(GroupFieldKey.PAYMENT_DESCRIPTION).trim(), amount(GroupFieldKey.PAYMENT_SHARE), if(mode == "create") amount(GroupFieldKey.PAYMENT_RECEIVED) else 0)
        MenuValidation.label(share.description); MenuValidation.price(share.amount)
        require(share.received <= share.amount && (editing != profile().userId || share.received == 0L)) { "Received payments must not exceed the share. Your own share needs no transfer." }
        if(mode == "create") { require(people().any { it.userId == editing }); shares[editing] = share; c.page = GroupPage.PAYMENT_ROOM }
        else { current(); c.command(CommandKind.UPDATE_PAYMENT_SHARE, memberId = editing, text = share.description, amount = share.amount) }
    }
    private fun current() { require(c.online && c.room().id == boundRoom && c.room().orderNumber == boundOrder) { "Refresh the current order first." } }
    fun save() {
        val details = PaymentRoomDetails(c.text(GroupFieldKey.PAYMENT_DETAILS).trim(), c.text(GroupFieldKey.RECEIPT_PHOTO)).also { it.validate() }
        if(mode == "receipt") { current(); c.command(CommandKind.UPDATE_PAYMENT_RECEIPT, paymentRoom = PaymentRoomRequest(details, emptyList())); return }
        val total = amount(GroupFieldKey.PAYMENT_TOTAL)
        require(shares.size in 2..30) { "Choose yourself and 1–29 other people." }
        require(total > 0 && shares.values.sumOf { it.amount } == total) { "The shares must add up to the receipt total." }
        val account = requireNotNull(profile().payment) { "Add your receiving details in your profile first." }.normalized().also { it.validate() }
        c.replaceLibrary(c.library.copy(selectedHub = c.library.identityHub))
        c.send(RoomCommand(commandId = c.platform.uuid(), kind = CommandKind.CREATE_PAYMENT_ROOM,
            text = c.text(GroupFieldKey.PAYMENT_ROOM_NAME).trim(), name = c.text(GroupFieldKey.PAYMENT_RESTAURANT).trim(), amount = total,
            account = account, paymentRoom = PaymentRoomRequest(details, shares.values.toList())))
    }
    fun record(id: String) {
        require(c.room().payerId == c.me() && c.room().phase == RoomPhase.FULFILLED)
        val receipt = c.reply!!.receipts.single { it.memberId == id }
        require(receipt.balance > 0 && id != c.me())
        boundRoom = c.room().id; boundOrder = c.room().orderNumber; editing = id
        c.draft[GroupFieldKey.PAYMENT_RECEIVED] = display(receipt.balance)
        c.draft[GroupFieldKey.REFERENCE] = ""
        c.page = GroupPage.RECORD_PAYMENT
    }
    fun saveRecord() { current(); c.command(CommandKind.RECORD_PAYMENT, memberId = editing, amount = amount(GroupFieldKey.PAYMENT_RECEIVED), text = c.text(GroupFieldKey.REFERENCE).ifBlank { tr("Payment received", "تم استلام الدفعة") }) }
    fun back() { c.page = if(c.page == GroupPage.PAYMENT_SHARE && mode == "create") GroupPage.PAYMENT_ROOM else if(mode == "create" && c.page == GroupPage.PAYMENT_ROOM) GroupPage.HOME else GroupPage.ROOM }
    fun form(): GroupFlowContent {
        if(c.page == GroupPage.RECORD_PAYMENT) return GroupFlowContent(
            fields = listOf(field(GroupFieldKey.PAYMENT_RECEIVED, "Amount received · AED", "المبلغ المستلم · درهم"), field(GroupFieldKey.REFERENCE, "Reference / cash note", "مرجع التحويل أو ملاحظة النقد")),
            cards = listOf(GroupCard("received-check", c.room().members.single { it.id == editing }.name, tr("Confirm only after checking the money reached you. This records a payment and does not transfer money.", "أكد بعد التحقق من وصول الأموال إليك. هذا يسجل الدفع ولا يحول الأموال."))),
            buttons = listOf(GroupButton(tr("Confirm payment received", "تأكيد استلام الدفعة"), GroupAction.SAVE_RECORDED_PAYMENT, primary = true, enabled = c.online)))
        if(c.page == GroupPage.PAYMENT_SHARE) return GroupFlowContent(fields = listOf(
            field(GroupFieldKey.PAYMENT_DESCRIPTION, "Person's order", "طلب الشخص", true), field(GroupFieldKey.PAYMENT_SHARE, "Share · AED", "الحصة · درهم")) +
            if(mode == "create" && editing != profile().userId) listOf(field(GroupFieldKey.PAYMENT_RECEIVED, "Already received · AED", "المستلم بالفعل · درهم")) else emptyList(),
            buttons = listOf(GroupButton(tr("Save share", "حفظ الحصة"), GroupAction.SAVE_PAYMENT_SHARE, primary = true)))
        val fields = mutableListOf<GroupField>()
        if(mode == "create") fields += listOf(field(GroupFieldKey.PAYMENT_ROOM_NAME, "Room name", "اسم الغرفة"), field(GroupFieldKey.PAYMENT_RESTAURANT, "Restaurant", "المطعم"), field(GroupFieldKey.PAYMENT_TOTAL, "Receipt total · AED", "إجمالي الإيصال · درهم"))
        fields += field(GroupFieldKey.PAYMENT_DETAILS, "Order details", "تفاصيل الطلب", true)
        fields += field(GroupFieldKey.RECEIPT_PHOTO, "Receipt photo (optional)", "صورة الإيصال (اختياري)")
        val cards = mutableListOf<GroupCard>()
        if(mode == "create") {
            cards += GroupCard("payment-shares-total", tr("Assigned shares", "الحصص المحددة"), Money.format(shares.values.sumOf { it.amount }, "AED"))
            people().forEach { person ->
                val share = shares[person.userId]
                cards += GroupCard("share:${person.userId}", person.name, share?.let { "${it.description}\n${Money.format(it.amount, "AED")}" }.orEmpty(),
                    buttons = listOf(GroupButton(if(share == null) tr("Add person", "إضافة الشخص") else tr("Edit share", "تعديل الحصة"), GroupAction.EDIT_PAYMENT_SHARE, person.userId)) +
                        if(share != null && person.userId != profile().userId) listOf(GroupButton(tr("Remove", "إزالة"), GroupAction.REMOVE_PAYMENT_SHARE, person.userId)) else emptyList())
            }
            if(profile().payment == null) cards += GroupCard("payment-account-missing", tr("Add receiving details", "أضف بيانات الاستلام"), buttons = listOf(GroupButton(tr("Open profile", "فتح الملف الشخصي"), GroupAction.OPEN_PROFILE)))
        }
        return GroupFlowContent(fields, cards, listOf(GroupButton(if(mode == "create") tr("Create payment room", "إنشاء غرفة دفع") else tr("Save receipt", "حفظ الإيصال"), GroupAction.SAVE_PAYMENT_ROOM, primary = true)))
    }
    fun roomCards(): List<GroupCard> {
        val r = c.room(); val details = r.paymentRoom ?: return emptyList()
        return listOf(GroupCard("payment-room-receipt", tr("Receipt and order details", "الإيصال وتفاصيل الطلب"), details.orderDetails,
            buttons = if(r.payerId == c.me() && r.phase == RoomPhase.FULFILLED) listOf(GroupButton(tr("Edit receipt", "تعديل الإيصال"), GroupAction.EDIT_PAYMENT_RECEIPT)) else emptyList(), image = details.receiptPhoto)) +
            if(r.payerId == c.me() && r.phase == RoomPhase.FULFILLED) c.reply!!.receipts.map { receipt -> GroupCard("payment-room-share:${receipt.memberId}", receipt.name,
                "${receipt.totalText} · ${receipt.balanceText}", buttons = listOf(GroupButton(tr("Edit share", "تعديل الحصة"), GroupAction.EDIT_EXISTING_PAYMENT_SHARE, receipt.memberId)) +
                    if(receipt.memberId != c.me() && receipt.balance > 0 && r.transfers.none { it.memberId == receipt.memberId && it.status == TransferStatus.DECLARED }) listOf(GroupButton(tr("Record payment received", "تسجيل دفعة مستلمة"), GroupAction.RECORD_PAYMENT, receipt.memberId)) else emptyList()) } else emptyList()
    }
}
