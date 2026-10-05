package com.karim.foodrun.orders

/** Only the current collector may remind another active member about a payable balance. */
object PaymentReminderRules {
    const val COOLDOWN_MS = 86_400_000L
    fun eligible(room: Room, actorId: String, receipt: Receipt): Boolean =
        room.payerId == actorId && receipt.memberId != actorId && receipt.balance > 0 && room.restaurantPaid &&
            room.settlementOpen &&
            room.orderingMembers.any { it.id == receipt.memberId } &&
            room.transfers.none { it.memberId == receipt.memberId && it.status == TransferStatus.DECLARED }

    fun key(room: Room, memberId: String) = "${room.id}:${room.orderNumber}:$memberId"
}
