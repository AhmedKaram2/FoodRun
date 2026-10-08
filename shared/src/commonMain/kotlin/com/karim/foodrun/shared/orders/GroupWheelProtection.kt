package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

internal class GroupWheelProtection(private val c: GroupController) {
    private fun tr(en: String, ar: String) = if (c.library.language == "ar") ar else en
    fun open() { require(c.room().paymentRoom == null && c.room().wheelProtections.isNotEmpty()); c.draft.remove(GroupFieldKey.WHEEL_PAYMENT_REFERENCE); c.page = GroupPage.WHEEL_PROTECTION }
    private fun request(value: String) = c.room().wheelProtections.single { it.id == value }
    private fun command(kind: CommandKind, value: String = "", text: String = "", flag: Boolean = false, amount: Long = 0) {
        val room = c.room()
        c.send(RoomCommand(commandId = c.platform.uuid(), kind = kind, roomId = room.id, token = c.session!!.token,
            expectedOrderNumber = room.orderNumber, expectedRevision = room.revision,
            transferId = value, text = text, flag = flag, amount = amount), returnPage = GroupPage.WHEEL_PROTECTION)
    }
    fun dispatch(action: GroupAction, value: String) {
        when (action) {
            GroupAction.REQUEST_WHEEL_PROTECTION -> error("Paid wheel options are no longer available.")
            GroupAction.APPROVE_WHEEL_PROTECTION, GroupAction.REJECT_WHEEL_PROTECTION -> command(CommandKind.REVIEW_WHEEL_PROTECTION, value, flag = action == GroupAction.APPROVE_WHEEL_PROTECTION)
            GroupAction.DECLARE_WHEEL_PAYMENT -> command(CommandKind.DECLARE_WHEEL_PAYMENT, value, amount = request(value).amount,
                text = c.text(GroupFieldKey.WHEEL_PAYMENT_REFERENCE).ifBlank { tr("Paid the room owner", "تم الدفع لصاحب الغرفة") })
            GroupAction.CONFIRM_WHEEL_PAYMENT, GroupAction.REJECT_WHEEL_PAYMENT -> command(CommandKind.CONFIRM_WHEEL_PAYMENT, value,
                amount = request(value).amount, flag = action == GroupAction.CONFIRM_WHEEL_PAYMENT)
            GroupAction.COPY_WHEEL_ACCOUNT -> c.platform.copyToClipboard(c.room().wheelProtectionAccounts.single { it.id == value }.identifier)
            else -> error("Unsupported wheel action.")
        }
    }
    fun content(): GroupFlowContent {
        val room = c.room(); val me = room.members.single { it.id == c.me() }; val owner = room.ownerId == me.id
        val editable = room.phase == RoomPhase.LOBBY && c.online
        val confirmable = c.online && (room.phase == RoomPhase.LOBBY || room.phase == RoomPhase.ARCHIVED && room.autoArchivedAt > 0)
        val cards = mutableListOf<GroupCard>()
        val buttons = mutableListOf<GroupButton>(); val fields = mutableListOf<GroupField>()
        room.wheelProtections.filter { (owner && it.status != WheelProtectionStatus.REJECTED) || it.memberId == me.id || it.status == WheelProtectionStatus.ACTIVE }.forEach { request ->
            val actions = mutableListOf<GroupButton>()
            if (editable && owner && request.status == WheelProtectionStatus.REQUESTED) actions += GroupButton(tr("Approve request", "وافق على الطلب"), GroupAction.APPROVE_WHEEL_PROTECTION, request.id)
            if (editable && request.status in listOf(WheelProtectionStatus.REQUESTED, WheelProtectionStatus.AWAITING_PAYMENT) && (owner || request.memberId == me.id)) actions += GroupButton(tr(if(owner) "Decline" else "Cancel request", if(owner) "رفض" else "إلغاء الطلب"), GroupAction.REJECT_WHEEL_PROTECTION, request.id)
            if (confirmable && owner && request.status == WheelProtectionStatus.PAYMENT_DECLARED) {
                actions += GroupButton(if(editable) tr("Confirm received & activate", "أكّد الاستلام وفعّل الاختيار") else tr("Confirm received", "تأكيد الاستلام"), GroupAction.CONFIRM_WHEEL_PAYMENT, request.id, primary = true)
                actions += GroupButton(tr("Not received", "الفلوس موصلتش"), GroupAction.REJECT_WHEEL_PAYMENT, request.id)
            }
            if (editable && request.memberId == me.id && request.status == WheelProtectionStatus.AWAITING_PAYMENT) {
                fields += GroupField(GroupFieldKey.WHEEL_PAYMENT_REFERENCE, tr("Payment note (optional)", "ملاحظة الدفع (اختياري)"), c.text(GroupFieldKey.WHEEL_PAYMENT_REFERENCE))
                actions += GroupButton(tr("I paid the room owner", "دفعت لصاحب الغرفة"), GroupAction.DECLARE_WHEEL_PAYMENT, request.id, primary = true)
                room.wheelProtectionAccounts.forEach { account -> cards += GroupCard("wheel-account:${account.id}", "${account.holder} · ${account.bank}", account.identifier,
                    buttons = listOf(GroupButton(tr("Copy payment details", "نسخ بيانات الدفع"), GroupAction.COPY_WHEEL_ACCOUNT, account.id))) }
            }
            val status = when(request.status) {
                WheelProtectionStatus.REQUESTED -> tr("Waiting for owner approval", "في انتظار موافقة صاحب الغرفة")
                WheelProtectionStatus.AWAITING_PAYMENT -> tr("Approved · pay the room owner", "تمت الموافقة · ادفع لصاحب الغرفة")
                WheelProtectionStatus.PAYMENT_DECLARED -> tr("Waiting for owner to confirm payment", "في انتظار تأكيد صاحب الغرفة لاستلام الفلوس")
                WheelProtectionStatus.ACTIVE -> tr("Active for this order", "مفعّل للطلب الحالي")
                WheelProtectionStatus.REJECTED -> tr("Request declined", "تم رفض الطلب")
            }
            cards += GroupCard("wheel-request:${request.id}", room.members.first { it.id == request.memberId }.name,
                tr(if(request.plan == WheelProtectionPlan.EXCLUDE) "Excluded from selection" else "50% less chance", if(request.plan == WheelProtectionPlan.EXCLUDE) "استبعاد من الاختيار" else "فرصة أقل بنسبة ٥٠٪"),
                "${Money.format(request.amount, "AED")} · $status${if(request.reference.isEmpty()) "" else "\n${request.reference}"}", actions)
        }
        cards += GroupCard("wheel-payment-note", tr("Pay only after approval", "ادفع بعد الموافقة بس"),
            tr("Pay the room owner outside Intrvioo. The fee is separate from your food bill. At least one eligible member must keep their normal chance.", "ادفع لصاحب الغرفة خارج التطبيق. الرسوم منفصلة عن حساب الأكل. لازم يفضل عضو مؤهل واحد على الأقل بفرصته العادية."))
        return GroupFlowContent(cards = cards, fields = fields, buttons = buttons)
    }
}
