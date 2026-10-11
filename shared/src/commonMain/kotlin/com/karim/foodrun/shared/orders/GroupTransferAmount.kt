package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlin.math.abs

/** Full uses the latest receipt balance, including confirmed partial payments and bill changes. */
internal fun GroupController.transferAmount(memberId: String): Long {
    val receipt = requireNotNull(reply?.receipts?.singleOrNull { it.memberId == memberId }) { "No receipt exists for this member." }
    val remaining = abs(receipt.balance)
    val amount = if(text(GroupFieldKey.TRANSFER_AMOUNT_MODE) == "other") Money.parse(text(GroupFieldKey.AMOUNT),receipt.currency) else remaining
    require(amount > 0 && amount <= remaining) { "Amount exceeds the remaining balance." }
    return amount
}
