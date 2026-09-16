package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

internal data class GroupFlowContent(
    val fields: List<GroupField> = emptyList(),
    val cards: List<GroupCard> = emptyList(),
    val buttons: List<GroupButton> = emptyList(),
)

/** Derives the next useful action from confirmed room state, including payment wait states. */
internal class GroupSettlementPresentation(private val c: GroupController) {
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

    fun review(): GroupFlowContent {
        val fields = mutableListOf<GroupField>()
        val cards = mutableListOf<GroupCard>()
        val buttons = mutableListOf<GroupButton>()
        val waiting = r.orderingMembers.filterNot { quoteConfirmed(r, it.id) }
        val ownConfirmed = quoteConfirmed(r, me)
        r.orderingMembers.forEach { member ->
            cards += GroupCard("quote:${member.id}", member.name,
                if (quoteConfirmed(r, member.id)) "Total and recipient confirmed" else "Awaiting total and recipient confirmation")
        }
        val progress = when {
            waiting.isNotEmpty() -> "Waiting for ${waiting.joinToString { it.name }} to confirm their total and recipient. Members choosing no food must also confirm."
            payer -> "Everyone has confirmed. Contact the restaurant, then enter its confirmation and ETA below."
            else -> "Everyone has confirmed. ${name(r.payerId)} will contact the restaurant and confirm placement here."
        }
        cards += GroupCard("review-next-step", if (orderer && ownConfirmed) "Your total is confirmed" else "Next step", progress)
        if (payer) {
            val blocker = placementBlocker()
            fields += field(GroupFieldKey.REFERENCE, "Restaurant confirmation / ETA")
            cards += GroupCard("review-total", "Total to pay the restaurant", "${money(receipts.sumOf { it.total })}\nYour own food share is included. Other members reimburse their individual shares after restaurant payment.", buttons = listOf(
                GroupButton("I accept the total — order placed", GroupAction.PLACE,
                    primary = true, enabled = waiting.isEmpty() && blocker.isEmpty()),
            ))
            if (blocker.isNotEmpty()) cards += GroupCard("placement-blocked", "Before placing the order", blocker,
                buttons = if (r.restaurant.contact.phoneE164.isNullOrBlank() && r.restaurant.contact.whatsappE164.isNullOrBlank() || r.restaurant.pricing.taxTreatment == TaxTreatment.UNSPECIFIED)
                    listOf(GroupButton("Edit restaurant details", GroupAction.EDIT_ROOM_RESTAURANT)) else emptyList())
            buttons += GroupButton("Change receiving account", GroupAction.OPEN_ACCOUNT)
        }
        if (owner || payer) buttons += GroupButton("Reopen for changes", GroupAction.REOPEN)
        return GroupFlowContent(fields, cards, buttons)
    }

    private fun placementBlocker(): String = when {
        r.account == null -> "Share a receiving account before placing the order."
        r.restaurant.contact.phoneE164.isNullOrBlank() && r.restaurant.contact.whatsappE164.isNullOrBlank() ->
            "Add a restaurant contact to this order. Updating only contact details keeps everyone's food and confirmations."
        r.restaurant.pricing.taxTreatment == TaxTreatment.UNSPECIFIED ->
            "Set whether menu prices include tax in the restaurant details before confirming placement."
        receipts.none { it.lines.isNotEmpty() } -> "No food has been ordered. Reopen ordering to add food."
        r.fees.discount > receipts.sumOf { it.food } -> "The discount exceeds the food total. Update fees before confirming placement."
        receipts.sumOf { it.food } < r.restaurant.pricing.minimumOrderMinor ->
            "The restaurant minimum is ${money(r.restaurant.pricing.minimumOrderMinor)} in food. Reopen ordering to add food."
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
        cards += GroupCard("placed", if (r.restaurantPaid) "Restaurant payment confirmed" else "Restaurant payment pending", r.restaurantReference)
        if (r.billRevision > 1) {
            val detail = buildString {
                append("Total adjustment from the original bill: ${money(r.adjustment)}. ")
                append(if (awaitingApproval.isEmpty()) "Everyone has approved the revised bill." else "Waiting for ${awaitingApproval.joinToString { it.name }} to approve the revised bill.")
                if (orderer && me in r.adjustmentApprovals) append(" Your approval is saved.")
            }
            cards += GroupCard("bill-adjustment", "Revised bill · revision ${r.billRevision}", detail)
            if (orderer && me !in r.adjustmentApprovals) buttons += GroupButton("Approve revised bill", GroupAction.APPROVE_ADJUSTMENT, primary = true)
        }
        if (payer) {
            if (!r.restaurantPaid) {
                cards += GroupCard("restaurant-balance", "Restaurant bill: ${money(receipts.sumOf { it.total })}",
                    if (awaitingApproval.isEmpty()) "Pay the restaurant, then record the full amount paid. This only records payment; it does not move money." else "Wait for everyone's revised-bill approval before recording restaurant payment.")
                fields += field(GroupFieldKey.AMOUNT, "Full amount paid to restaurant")
                buttons += GroupButton("Confirm restaurant paid", GroupAction.PAY_RESTAURANT, primary = true, enabled = awaitingApproval.isEmpty())
            }
            if (r.phase == RoomPhase.PLACED) buttons += GroupButton("Food collected / delivered", GroupAction.FULFILL, primary = r.restaurantPaid)
            if (r.restaurantPaid && receipts.any { refundAvailable(r, it) }) {
                fields += field(GroupFieldKey.AMOUNT, "Refund amount sent")
                fields += field(GroupFieldKey.REFERENCE, "Refund reference / cash note")
            }
            fields += field(GroupFieldKey.BILL_ADJUSTMENT, "Total bill adjustment · minus reduces · 0 removes")
            val adjustmentText = c.text(GroupFieldKey.BILL_ADJUSTMENT).trim()
            val proposedAdjustment = runCatching {
                Money.parse(adjustmentText.removePrefix("-"), currency) * if (adjustmentText.startsWith('-')) -1 else 1
            }.getOrNull()
            buttons += GroupButton("Propose bill adjustment", GroupAction.ADJUST_BILL,
                enabled = proposedAdjustment == null || proposedAdjustment != r.adjustment)
            if (r.restaurantPaid) {
                val owed = receipts.filter { it.balance > 0 }
                val refunds = receipts.filter { it.balance < 0 }
                cards += GroupCard("settlement-next-step", "Payment progress", when {
                    pending.isNotEmpty() -> "${pending.size} payment or refund ${if (pending.size == 1) "claim needs" else "claims need"} confirmation. Review Payment activity below."
                    refunds.isNotEmpty() -> "Refunds are due to ${refunds.joinToString { it.name }}. Send each refund, record it, and wait for the recipient to confirm."
                    owed.isNotEmpty() -> "Waiting for reimbursements from ${owed.joinToString { it.name }}. Your own share needs no transfer."
                    r.phase == RoomPhase.PLACED -> "Every share is settled. Mark the food collected or delivered when it arrives."
                    owner -> "Every share is settled and the food has arrived. Complete this order to prepare for the next meal."
                    else -> "Every share is settled and the food has arrived. ${name(r.ownerId)} can complete this order."
                })
            }
        } else if (orderer) {
            val detail = when {
                ownPending != null -> if (ownPending.refund) "A refund of ${money(ownPending.amount)} is awaiting your confirmation. Check your account, then confirm it in Payment activity below." else "Your payment claim of ${money(ownPending.amount)} is saved. Waiting for ${name(r.payerId)} to confirm receipt. Do not send the same payment again."
                !r.restaurantPaid -> "Wait for ${name(r.payerId)} to confirm the restaurant payment. Your receipt shows your current share."
                ownReceipt == null -> "Your receipt is not available yet. Reconnect to refresh this order."
                ownReceipt.balance < 0 -> "${name(r.payerId)} owes you a refund of ${money(-ownReceipt.balance)}. Wait for it to arrive, then confirm the refund claim."
                ownReceipt.balance == 0L -> "Your share is settled. No further payment is needed for the current bill."
                else -> "Send up to ${money(ownReceipt.balance)} to ${r.account?.holder ?: name(r.payerId)} using your bank app or cash, then record the amount below. Food Run only records payments."
            }
            cards += GroupCard("my-payment-status", if (ownPending != null) "Payment awaiting confirmation" else "Your payment", detail)
            if (r.restaurantPaid && ownPending == null && ownReceipt != null && ownReceipt.balance > 0) {
                fields += field(GroupFieldKey.AMOUNT, "Amount sent · remaining ${money(ownReceipt.balance)}")
                fields += field(GroupFieldKey.REFERENCE, "Transfer reference / cash note")
                buttons += GroupButton("I sent my payment", GroupAction.DECLARE_TRANSFER, primary = true)
            }
        } else cards += GroupCard("settlement-observer", "Order progress", "${name(r.payerId)} is handling the restaurant order and payments. You have no food or payment due for this order.")
        if (owner && r.phase == RoomPhase.FULFILLED) {
            // Nonpayer organizers receive only their own private receipt. Use the hub's safe summary.
            val visiblySettled = receipts.all { it.balance == 0L } && pending.isEmpty()
            val progress = c.reply!!.progress
            val canComplete = progress?.canArchive ?: (r.restaurantPaid && visiblySettled)
            buttons += GroupButton("Complete this order", GroupAction.ARCHIVE, primary = canComplete, enabled = canComplete)
            cards += GroupCard("complete-order-guidance", "Finish this order", when {
                progress != null && !canComplete -> progress.archiveBlocker
                progress?.canArchive == true -> "Every reimbursement and refund is settled. Complete this order to prepare for the next meal."
                else -> "Complete the order once everyone has settled with ${name(r.payerId)}. The hub checks all reimbursements, refunds and pending confirmations."
            })
        }
        return GroupFlowContent(fields, cards, buttons)
    }

    companion object {
        fun quoteConfirmed(room: Room, memberId: String): Boolean = room.carts.any { it.memberId == memberId && it.submitted && it.confirmedQuote == room.quoteRevision }
        fun quoteConfirmationAction(room: Room, memberId: String): GroupButton {
            val confirmed = quoteConfirmed(room, memberId)
            return GroupButton(if (confirmed) "Total and recipient confirmed" else "Confirm my total and recipient",
                GroupAction.CONFIRM_QUOTE, primary = !confirmed, enabled = !confirmed)
        }
        fun refundAvailable(room: Room, receipt: Receipt): Boolean = room.phase in listOf(RoomPhase.PLACED, RoomPhase.FULFILLED) && room.restaurantPaid && receipt.balance < 0 && room.transfers.none { it.memberId == receipt.memberId && it.status == TransferStatus.DECLARED }
        fun receiptBalanceText(receipt: Receipt, payerId: String?): String = when {
            receipt.memberId == payerId -> "Your own contribution — no transfer to yourself"
            receipt.balance < 0 -> "Refund due ${Money.format(-receipt.balance, receipt.currency)}"
            receipt.balance == 0L -> "Settled · remaining ${receipt.balanceText}"
            else -> "Remaining ${receipt.balanceText}"
        }
    }
}
