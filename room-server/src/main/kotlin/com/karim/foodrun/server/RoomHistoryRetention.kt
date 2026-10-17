package com.karim.foodrun.server

import com.karim.foodrun.orders.*

/** Six completed rooms per account; shared and unsettled records are never removed on one user's behalf. */
internal class RoomHistoryRetention(private val db: RoomDatabase, private val clock: () -> Long) {
    data class Selection(val entries: List<Pair<AccountRoom, Room>>, val excludedIds: Set<String>)
    private val memberships = AccountMemberships(db)
    private val completedFirst = compareByDescending<Room> { it.updatedAt }.thenByDescending { it.createdAt }.thenBy { it.id }

    fun forUser(uid: String): Selection {
        val entries = db.allRooms().mapNotNull { room -> memberships.find(uid, room)?.let { it to room } }
        val pending = pendingWalletRooms()
        val excluded = entries.map { it.second }.filter { it.id !in pending && completed(it) }.sortedWith(completedFirst).drop(LIMIT).map { it.id }.toSet()
        return Selection(entries.filterNot { it.second.id in excluded }, excluded)
    }

    /** One small atomic maintenance step. Main enables this only after a verified copy exists. */
    fun cleanupStep(): String? {
        val pending = pendingWalletRooms()
        val rooms = db.allRooms().filter { it.id !in pending && completed(it) }
        val owners = rooms.associate { room -> room.id to room.members.filter { !it.removed && !it.guest }.map { member ->
            db.record("member-user:${room.id}:${member.id}")
        } }
        val retained = mutableSetOf<String>()
        rooms.sortedWith(completedFirst).flatMap { room -> owners.getValue(room.id).filterNotNull().distinct().map { it to room.id } }
            .groupBy({ it.first }, { it.second }).values.forEach { retained += it.take(LIMIT) }
        val candidate = rooms.sortedWith(completedFirst.reversed()).firstOrNull { room ->
            val linked = owners.getValue(room.id)
            room.id !in retained && linked.isNotEmpty() && linked.none { it == null }
        } ?: return null
        val id = candidate.id
        val changed = db.maintenance {
            when {
                db.retireRoomCommandBatch(id, 10) > 0 -> Unit
                else -> {
                    val previous = db.history(id, limit = 1).firstOrNull()
                    when {
                        previous != null -> {
                            SpendingService(db, clock).record(previous)
                            SelectionHistory(db, clock).remember(previous)
                            db.deleteHistory(id, previous.orderNumber)
                        }
                        db.deleteRoomSessionBatch(id, 25) > 0 -> Unit
                        else -> {
                            SpendingService(db, clock).record(candidate)
                            SelectionHistory(db, clock).remember(candidate)
                            db.deleteRoom(id)
                        }
                    }
                }
            }
        }
        return id.takeIf { changed }
    }

    private fun completed(room: Room): Boolean {
        if (room.phase !in listOf(RoomPhase.ARCHIVED, RoomPhase.CANCELLED) || !settled(room)) return false
        // Old orders may still contain obligations even when the current order is closed.
        var offset = 0
        while (true) {
            val page = db.history(room.id, offset, 10)
            if (page.any { !settled(it) }) return false
            if (page.size < 10) return true
            offset += page.size
        }
    }

    private fun pendingWalletRooms(): Set<String> = WalletService.payments(db).filter { it.status != WalletPaymentStatus.SETTLED }.map { it.roomId }.toSet()

    private fun settled(room: Room): Boolean {
        if (room.walletPayments.any { it.status != WalletPaymentStatus.SETTLED } ||
            room.wheelProtections.any { it.status == WheelProtectionStatus.PAYMENT_DECLARED }) return false
        val payment = room.restaurantPaid || room.settlementOpen || room.transfers.any { it.status in listOf(TransferStatus.CONFIRMED, TransferStatus.DECLARED) }
        return if (payment || room.phase == RoomPhase.ARCHIVED && room.autoArchivedAt == 0L || room.phase == RoomPhase.FULFILLED)
            runCatching { RoomRules.requireArchive(room) }.isSuccess else true
    }

    companion object { const val LIMIT = 6 }
}
