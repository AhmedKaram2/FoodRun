package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

internal data class GroupFlowContent(
    val fields: List<GroupField> = emptyList(),
    val cards: List<GroupCard> = emptyList(),
    val buttons: List<GroupButton> = emptyList(),
)

/** Derives the next useful action from confirmed room state, including payment wait states. */
internal class GroupSettlementPresentation(private val c: GroupController) {
    private fun tr(en: String, ar: String) = if(c.library.language == "ar") ar else en
    private val r = c.room()
    private val me = c.me()
    private val payer = r.payerId == me
    private val owner = r.ownerId == me
    private val orderer = r.orderingMembers.any { it.id == me }
    private val receipts = c.reply!!.receipts
    private val currency = r.restaurant.currency
    private fun name(id: String?) = r.members.firstOrNull { it.id == id }?.name ?: "the organizer"
    private fun money(amount: Long) = Money.format(amount, currency)
    private fun field(key: GroupFieldKey, label: String) = GroupField(key, label, c.text(key))

    private fun walletCards(): List<GroupCard> {
        val pending = r.transfers.filter { it.status == TransferStatus.DECLARED }
        if (payer) {
            val restaurantTotal = receipts.sumOf { it.total }
            val own = receipts.firstOrNull { it.memberId == me }?.total ?: 0
            val confirmed = receipts.filterNot { it.memberId == me }.sumOf { it.paid }
            val remaining = receipts.filterNot { it.memberId == me }.sumOf { maxOf(0, it.balance) }
            val refunds = receipts.sumOf { maxOf(0, -it.balance) }
            return listOf(GroupCard("wallet-summary", tr("Room wallet", "محفظة الغرفة"),
                "Restaurant total ${money(restaurantTotal)}\nYour own order ${money(own)}\nConfirmed from others ${money(confirmed)}\nMembers still owe ${money(remaining)}${if(refunds > 0) "\nRefunds you owe ${money(refunds)}" else ""}")) +
                receipts.map { receipt ->
                    val claim = pending.firstOrNull { it.memberId == receipt.memberId }
                    val status = when {
                        receipt.memberId == me -> tr("Your own contribution", "حصتك الشخصية")
                        receipt.balance < 0 -> tr("Refund due ${money(-receipt.balance)}", "مبلغ مرتجع مستحق ${money(-receipt.balance)}")
                        receipt.balance == 0L -> tr("Settled", "تمت التسوية")
                        else -> tr("Still owes ${money(receipt.balance)}", "المتبقي عليه ${money(receipt.balance)}")
                    }
                    GroupCard("wallet:${receipt.memberId}", receipt.name,
                        "Order ${money(receipt.total)} · confirmed ${money(receipt.paid)}${claim?.let { "\n${money(it.amount)} awaiting confirmation" } ?: ""}", status)
                }
        }
        val own = receipts.firstOrNull { it.memberId == me } ?: return emptyList()
        val status = when {
            own.balance < 0 -> tr("Owed back to you ${money(-own.balance)}", "لك مبلغ مرتجع ${money(-own.balance)}")
            own.balance == 0L -> tr("Settled", "تمت التسوية")
            else -> tr("You need to pay ${money(own.balance)}", "عليك دفع ${money(own.balance)}")
        }
        val claim = pending.firstOrNull { it.memberId == me }
        return listOf(GroupCard("wallet:$me", "My wallet",
            "My order ${money(own.total)}\nConfirmed paid ${money(own.paid)}${claim?.let { "\n${money(it.amount)} marked sent · awaiting confirmation" } ?: ""}", status))
    }

    fun review(): GroupFlowContent {
        val fields = mutableListOf<GroupField>()
        val cards = mutableListOf<GroupCard>()
        val buttons = mutableListOf<GroupButton>()
        cards += walletCards()
        cards += GroupCard("review-next-step", tr("Ready for the restaurant", "جاهز للمطعم"),
            if (payer) tr("Copy or share the order, then record the restaurant confirmation and expected arrival. No second confirmation is needed from members.", "انسخ الطلب أو ابعته، وبعدها سجّل تأكيد المطعم ومعاد الوصول. مش محتاج تأكيد تاني من الناس.")
            else tr("${name(r.payerId)} is sending the order to the restaurant.", "${name(r.payerId)} بيبعت الطلب للمطعم."))
        if (payer) {
            val blocker = placementBlocker()
            fields += field(GroupFieldKey.REFERENCE, tr("Restaurant confirmation / ETA", "تأكيد المطعم ووقت الوصول المتوقع"))
            cards += GroupCard("review-total", tr("Total to pay the restaurant", "إجمالي المبلغ المطلوب للمطعم"), "${money(receipts.sumOf { it.total })}\nYour own food share is included. Other members reimburse their individual shares after restaurant payment.")
            buttons += GroupButton(tr("Order sent · save expected arrival", "الطلب اتبعت · سجّل معاد الوصول"), GroupAction.PLACE,
                primary = true, enabled = blocker.isEmpty())
            if (blocker.isNotEmpty()) cards += GroupCard("placement-blocked", tr("Before placing the order", "قبل إرسال الطلب"), blocker,
                buttons = if (r.restaurant.contact.phoneE164.isNullOrBlank() && r.restaurant.contact.whatsappE164.isNullOrBlank() || r.restaurant.pricing.taxTreatment == TaxTreatment.UNSPECIFIED)
                    listOf(GroupButton(tr("Edit restaurant details", "تعديل بيانات المطعم"), GroupAction.EDIT_ROOM_RESTAURANT)) else emptyList())
            if (r.phase == RoomPhase.REVIEW) buttons += GroupButton(tr("Change receiving account", "تغيير حساب الاستلام"), GroupAction.OPEN_ACCOUNT)
        }
        if (r.phase == RoomPhase.REVIEW && (owner || payer)) buttons += GroupButton(tr("Reopen for changes", "إعادة فتح التعديل"), GroupAction.REOPEN)
        return GroupFlowContent(fields, cards, buttons)
    }

    private fun placementBlocker(): String = c.reply?.progress?.reviewBlocker?.takeIf { it.isNotBlank() } ?: when {
        r.account == null -> tr("Share a receiving account before placing the order.", "شارك حساب استلام الأموال قبل إرسال الطلب.")
        r.restaurant.contact.phoneE164.isNullOrBlank() && r.restaurant.contact.whatsappE164.isNullOrBlank() ->
            tr("Add a restaurant contact to this order. Updating only contact details keeps everyone's food and confirmations.", "أضف رقم المطعم إلى الطلب. تعديل بيانات التواصل فقط يحافظ على الطلبات والتأكيدات.")
        r.restaurant.pricing.taxTreatment == TaxTreatment.UNSPECIFIED ->
            tr("Set whether menu prices include tax in the restaurant details before confirming placement.", "حدد في بيانات المطعم إن كانت الأسعار تشمل الضريبة قبل إرسال الطلب.")
        receipts.none { it.lines.isNotEmpty() } -> tr("No food has been ordered. Reopen ordering to add food.", "لم يُضف طعام. أعد فتح الطلبات لإضافة أصناف.")
        r.fees.discount > receipts.sumOf { it.food } -> tr("The discount exceeds the food total. Update fees before confirming placement.", "الخصم أكبر من قيمة الطعام. عدّل الرسوم قبل إرسال الطلب.")
        receipts.sumOf { it.food } < r.restaurant.pricing.minimumOrderMinor ->
            tr("The restaurant minimum is ${money(r.restaurant.pricing.minimumOrderMinor)} in food. Reopen ordering to add food.", "الحد الأدنى للمطعم ${money(r.restaurant.pricing.minimumOrderMinor)} للطعام. أعد فتح الطلبات لإضافة أصناف.")
        else -> ""
    }

    fun settlement(): GroupFlowContent {
        val fields = mutableListOf<GroupField>()
        val cards = mutableListOf<GroupCard>()
        val buttons = mutableListOf<GroupButton>()
        val awaitingApproval = if (r.billRevision > 1) r.orderingMembers.filterNot { it.id in r.adjustmentApprovals } else emptyList()
        val pending = r.transfers.filter { it.status == TransferStatus.DECLARED }
        val ownPending = pending.firstOrNull { it.memberId == me }
        val ownReceipt = receipts.firstOrNull { it.memberId == me }
        cards += walletCards()
        cards += GroupCard("placed", if (r.restaurantPaid) tr("Restaurant payment confirmed", "تم تأكيد دفع المطعم") else tr("Restaurant payment pending", "بانتظار دفع المطعم"), r.restaurantReference)
        if (r.billRevision > 1) {
            val detail = buildString {
                append(tr("Total adjustment from the original bill: ${money(r.adjustment)}. ", "إجمالي التعديل على الفاتورة الأصلية: ${money(r.adjustment)}. "))
                append(if (awaitingApproval.isEmpty()) tr("Everyone has approved the revised bill.", "وافق الجميع على الفاتورة المعدلة.") else tr("Waiting for ${awaitingApproval.joinToString { it.name }} to approve the revised bill.", "بانتظار موافقة ${awaitingApproval.joinToString { it.name }} على الفاتورة المعدلة."))
                if (orderer && me in r.adjustmentApprovals) append(" Your approval is saved.")
            }
            cards += GroupCard("bill-adjustment", tr("Revised bill · revision ${r.billRevision}", "الفاتورة المعدلة · إصدار ${r.billRevision}"), detail)
            if (orderer && me !in r.adjustmentApprovals) buttons += GroupButton(tr("Approve revised bill", "الموافقة على الفاتورة المعدلة"), GroupAction.APPROVE_ADJUSTMENT, primary = true)
        }
        if (payer) {
            if (!r.restaurantPaid) {
                cards += GroupCard("restaurant-balance", tr("Restaurant bill: ${money(receipts.sumOf { it.total })}", "فاتورة المطعم: ${money(receipts.sumOf { it.total })}"),
                    if (awaitingApproval.isEmpty()) tr("Pay the restaurant, then record the full amount paid. This only records payment; it does not move money.", "ادفع للمطعم ثم سجل المبلغ الكامل. هذا يوثق الدفع ولا يحول الأموال.") else tr("Wait for everyone's revised-bill approval before recording restaurant payment.", "انتظر موافقة الجميع على الفاتورة المعدلة قبل تسجيل دفع المطعم."))
                fields += field(GroupFieldKey.AMOUNT, tr("Full amount paid to restaurant", "كامل المبلغ المدفوع للمطعم"))
                buttons += GroupButton(tr("Confirm restaurant paid", "تأكيد دفع المطعم"), GroupAction.PAY_RESTAURANT, primary = true, enabled = awaitingApproval.isEmpty())
            }
            if (r.phase == RoomPhase.PLACED) buttons += GroupButton(tr("Food collected / delivered", "تم استلام الطعام أو توصيله"), GroupAction.FULFILL, primary = r.restaurantPaid)
            if (r.restaurantPaid && receipts.any { refundAvailable(r, it) }) {
                fields += field(GroupFieldKey.AMOUNT, tr("Refund amount sent", "المبلغ المرتجع المرسل"))
                fields += field(GroupFieldKey.REFERENCE, tr("Refund reference / cash note", "مرجع الإرجاع أو ملاحظة النقد"))
            }
            fields += field(GroupFieldKey.BILL_ADJUSTMENT, tr("Total bill adjustment · minus reduces · 0 removes", "تعديل إجمالي الفاتورة · السالب يخفضها · صفر يلغي التعديل"))
            val adjustmentText = c.text(GroupFieldKey.BILL_ADJUSTMENT).trim()
            val proposedAdjustment = runCatching {
                Money.parse(adjustmentText.removePrefix("-"), currency) * if (adjustmentText.startsWith('-')) -1 else 1
            }.getOrNull()
            buttons += GroupButton(tr("Propose bill adjustment", "اقتراح تعديل الفاتورة"), GroupAction.ADJUST_BILL,
                enabled = proposedAdjustment == null || proposedAdjustment != r.adjustment)
            if (r.restaurantPaid) {
                val owed = receipts.filter { it.balance > 0 }
                val refunds = receipts.filter { it.balance < 0 }
                cards += GroupCard("settlement-next-step", tr("Payment progress", "تقدم الدفعات"), when {
                    pending.isNotEmpty() -> "${pending.size} payment or refund ${if (pending.size == 1) "claim needs" else "claims need"} confirmation. Review Payment activity below."
                    refunds.isNotEmpty() -> tr("Refunds are due to ${refunds.joinToString { it.name }}. Send each refund, record it, and wait for the recipient to confirm.", "مبالغ مرتجعة مستحقة إلى ${refunds.joinToString { it.name }}. أرسلها وسجلها وانتظر تأكيد المستلم.")
                    owed.isNotEmpty() -> tr("Waiting for reimbursements from ${owed.joinToString { it.name }}. Your own share needs no transfer.", "بانتظار دفعات ${owed.joinToString { it.name }}. حصتك الشخصية لا تحتاج تحويلاً.")
                    r.phase == RoomPhase.PLACED -> tr("Every share is settled. Mark the food collected or delivered when it arrives.", "تمت تسوية جميع الحصص. أكد استلام الطعام عند وصوله.")
                    owner || payer -> tr("Every share is settled and the food has arrived. Complete this order to prepare for the next meal.", "تمت تسوية الحصص ووصل الطعام. أكمل الطلب للتحضير للوجبة القادمة.")
                    else -> tr("Every share is settled and the food has arrived. ${name(r.ownerId)} can complete this order.", "تمت تسوية الحصص ووصل الطعام. يستطيع ${name(r.ownerId)} إكمال الطلب.")
                })
            }
        } else if (orderer) {
            val detail = when {
                ownPending != null -> if (ownPending.refund) tr("A refund of ${money(ownPending.amount)} is awaiting your confirmation. Check your account, then confirm it in Payment activity below.", "مبلغ مرتجع بقيمة ${money(ownPending.amount)} بانتظار تأكيدك. راجع حسابك ثم أكد الاستلام في حركة الدفعات.") else tr("Your payment claim of ${money(ownPending.amount)} is saved. Waiting for ${name(r.payerId)} to confirm receipt. Do not send the same payment again.", "سُجل إرسال دفعتك ${money(ownPending.amount)}. بانتظار تأكيد ${name(r.payerId)} للاستلام. لا ترسل الدفعة نفسها مجدداً.")
                !r.restaurantPaid -> tr("Wait for ${name(r.payerId)} to confirm the restaurant payment. Your receipt shows your current share.", "انتظر تأكيد ${name(r.payerId)} لدفع المطعم. يعرض إيصالك حصتك الحالية.")
                ownReceipt == null -> tr("Your receipt is not available yet. Reconnect to refresh this order.", "إيصالك غير متاح بعد. أعد الاتصال لتحديث الطلب.")
                ownReceipt.balance < 0 -> tr("${name(r.payerId)} owes you a refund of ${money(-ownReceipt.balance)}. Wait for it to arrive, then confirm the refund claim.", "لك لدى ${name(r.payerId)} مبلغ مرتجع ${money(-ownReceipt.balance)}. انتظر وصوله ثم أكد الاستلام.")
                ownReceipt.balance == 0L -> tr("Your share is settled. No further payment is needed for the current bill.", "تمت تسوية حصتك ولا توجد دفعات أخرى للفاتورة الحالية.")
                else -> tr("Send up to ${money(ownReceipt.balance)} to ${r.account?.holder ?: name(r.payerId)} using your bank app or cash, then record the amount below. Food Run only records payments.", "أرسل حتى ${money(ownReceipt.balance)} إلى ${r.account?.holder ?: name(r.payerId)} عبر البنك أو نقداً ثم سجل المبلغ. فود رن يوثق الدفعات فقط.")
            }
            cards += GroupCard("my-payment-status", if (ownPending != null) tr("Payment awaiting confirmation", "الدفعة بانتظار التأكيد") else tr("Your payment", "دفعتك"), detail)
            if (r.restaurantPaid && ownPending == null && ownReceipt != null && ownReceipt.balance > 0) {
                fields += field(GroupFieldKey.AMOUNT, tr("Amount sent · remaining ${money(ownReceipt.balance)}", "المبلغ المرسل · المتبقي ${money(ownReceipt.balance)}"))
                fields += field(GroupFieldKey.REFERENCE, tr("Transfer reference / cash note", "مرجع التحويل أو ملاحظة الدفع النقدي"))
                buttons += GroupButton(if(c.library.language == "ar") "استخدام كامل المبلغ المتبقي" else tr("Use full remaining amount", "استخدام كامل المبلغ المتبقي"), GroupAction.USE_REMAINING_AMOUNT)
                buttons += GroupButton(tr("I paid · notify recipient", "دفعت · إشعار المستلم"), GroupAction.DECLARE_TRANSFER, primary = true)
            }
        } else cards += GroupCard("settlement-observer", tr("Order progress", "تقدم الطلب"), tr("${name(r.payerId)} is handling the restaurant order and payments. You have no food or payment due for this order.", "يتولى ${name(r.payerId)} طلب المطعم والدفعات. لا يوجد عليك طعام أو دفع لهذا الطلب."))
        if ((owner || payer) && r.phase == RoomPhase.FULFILLED) {
            // Nonpayer organizers receive only their own private receipt. Use the hub's safe summary.
            val visiblySettled = receipts.all { it.balance == 0L } && pending.isEmpty()
            val progress = c.reply!!.progress
            val canComplete = progress?.canArchive ?: (r.restaurantPaid && visiblySettled)
            buttons += GroupButton(tr("Complete this order", "إكمال هذا الطلب"), GroupAction.ARCHIVE, primary = canComplete, enabled = canComplete)
            cards += GroupCard("complete-order-guidance", tr("Finish this order", "إنهاء هذا الطلب"), when {
                progress != null && !canComplete -> progress.archiveBlocker
                progress?.canArchive == true -> tr("Every reimbursement and refund is settled. Complete this order to prepare for the next meal.", "تمت تسوية الدفعات والمبالغ المرتجعة. أكمل الطلب للتحضير للوجبة القادمة.")
                else -> tr("Complete the order once everyone has settled with ${name(r.payerId)}. The hub checks all reimbursements, refunds and pending confirmations.", "أكمل الطلب بعد تسوية الجميع مع ${name(r.payerId)}. يتحقق الخادم من الدفعات والمبالغ المرتجعة والتأكيدات المعلقة.")
            })
        }
        return GroupFlowContent(fields, cards, buttons)
    }

    companion object {
        fun quoteConfirmed(room: Room, memberId: String): Boolean = room.carts.any { it.memberId == memberId && it.submitted && it.confirmedQuote == room.quoteRevision }
        fun quoteConfirmationAction(room: Room, memberId: String, language: String = "en"): GroupButton {
            val confirmed = quoteConfirmed(room, memberId)
            return GroupButton(if(language == "ar") { if(confirmed) "تم تأكيد الإجمالي والمستلم" else "تأكيد إجمالي طلبي والمستلم" } else if (confirmed) "Total and recipient confirmed" else "Confirm my total and recipient",
                GroupAction.CONFIRM_QUOTE, primary = !confirmed, enabled = !confirmed)
        }
        fun refundAvailable(room: Room, receipt: Receipt): Boolean = room.phase in listOf(RoomPhase.PLACED, RoomPhase.FULFILLED) && room.restaurantPaid && receipt.balance < 0 && room.transfers.none { it.memberId == receipt.memberId && it.status == TransferStatus.DECLARED }
        fun receiptBalanceText(receipt: Receipt, payerId: String?, language: String = "en"): String = if(language == "ar") when {
            receipt.memberId == payerId -> "حصتك الشخصية — لا حاجة للتحويل لنفسك"
            receipt.balance < 0 -> "مبلغ مرتجع مستحق ${Money.format(-receipt.balance, receipt.currency)}"
            receipt.balance == 0L -> "تمت التسوية · المتبقي ${receipt.balanceText}"
            else -> "المتبقي ${receipt.balanceText}"
        } else when {
            receipt.memberId == payerId -> "Your own contribution — no transfer to yourself"
            receipt.balance < 0 -> "Refund due ${Money.format(-receipt.balance, receipt.currency)}"
            receipt.balance == 0L -> "Settled · remaining ${receipt.balanceText}"
            else -> "Remaining ${receipt.balanceText}"
        }
    }
}
