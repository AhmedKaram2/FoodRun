package com.karim.foodrun.orders

/** A reported direct payment becomes settled when the recipient leaves it unanswered. */
object PaymentConfirmation {
    const val WAIT_MS = 12 * 60 * 60 * 1000L

    fun deadline(transfer: Transfer): Long? = transfer.createdAt.takeIf {
        transfer.status == TransferStatus.DECLARED && !transfer.refund && !transfer.id.startsWith("wallet-") &&
            it > 0 && it <= Long.MAX_VALUE - WAIT_MS
    }?.plus(WAIT_MS)

    fun nextDeadline(room: Room): Long? = if(room.settlementOpen && room.restaurantPaid)
        room.transfers.mapNotNull(::deadline).minOrNull() else null

    fun confirm(room: Room, now: Long): Room {
        if(!room.settlementOpen || !room.restaurantPaid) return room
        val due = room.transfers.filter { deadline(it)?.let { at -> now >= at } == true }
        if(due.isEmpty()) return room
        val ids = due.map { it.id }.toSet()
        return room.copy(transfers = room.transfers.map { if(it.id in ids) it.copy(status = TransferStatus.CONFIRMED) else it },
            revision = room.revision + 1, updatedAt = now,
            audit = room.audit + due.map { AuditEntry("auto-confirm-transfer:${room.orderNumber}:${it.id}", "", "AUTO_CONFIRM_TRANSFER",
                "Reported payment automatically confirmed after 12 hours without recipient response.", now) })
    }
}
