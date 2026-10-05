package com.karim.foodrun.orders

object RoomExpiry {
    const val LIFETIME_MS = 24 * 60 * 60 * 1000L
    fun due(room: Room, now: Long): Boolean {
        val created = room.orderCreatedAt.takeIf { it > 0 } ?: room.createdAt
        return room.phase.ongoing && created > 0 && now >= created && now - created >= LIFETIME_MS
    }
    fun archive(room: Room, now: Long): Room {
        if (!due(room, now)) return room
        return room.copy(phase = RoomPhase.ARCHIVED, autoArchiveFrom = room.phase, autoArchivedAt = now,
            revision = room.revision + 1, updatedAt = now, preparationId = "", preparedIds = emptyList(),
            audit = room.audit + AuditEntry("auto-archive:${room.orderNumber}", "", "AUTO_ARCHIVE", "Archived after 24 hours; outstanding payments remain due.", now))
    }
    fun requireNextOrder(room: Room) {
        if (room.autoArchivedAt > 0 && room.settlementOpen) RoomRules.requireArchive(room)
        require(room.wheelProtections.none { it.status == WheelProtectionStatus.PAYMENT_DECLARED }) { "Confirm or reject pending wheel payments before starting the next order." }
    }
    fun paymentsPending(room: Room): Boolean = room.autoArchivedAt > 0 && (
        room.wheelProtections.any { it.status == WheelProtectionStatus.PAYMENT_DECLARED } ||
            room.settlementOpen && runCatching { RoomRules.requireArchive(room) }.isFailure)
}
