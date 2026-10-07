package com.karim.foodrun.orders

/** Sending a payment removes it from the sender's to-do list; receipt confirmation still governs the ledger. */
fun Receipt.amountStillToSend(room: Room): Long {
    val refund = balance < 0
    val pending = room.transfers.filter { it.memberId == memberId && it.status == TransferStatus.DECLARED && it.refund == refund }.sumOf { it.amount }
    return (kotlin.math.abs(balance) - pending).coerceAtLeast(0)
}
