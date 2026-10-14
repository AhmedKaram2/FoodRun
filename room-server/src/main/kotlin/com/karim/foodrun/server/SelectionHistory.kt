package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlinx.serialization.Serializable

@Serializable private data class PreviousSelection(val id: String, val at: Long)

/** Private account history; public spins expose only the final candidate weights. */
internal class SelectionHistory(private val db: RoomDatabase, private val clock: () -> Long) {
    private fun identity(room: Room, memberId: String): String = db.record("member-user:${room.id}:$memberId")
        ?.let { AccountAliases.resolve(db, it) } ?: "room:${room.id}:$memberId"

    private fun choices(identity: String): List<PreviousSelection> = db.record("wheel-history:$identity")
        ?.let { orderJson.decodeFromString<List<PreviousSelection>>(it) }.orEmpty()

    fun backfill() {
        if (db.record("migration:wheel-history-v1") != null) return
        db.allRooms().forEach { room ->
            remember(room)
            var offset = 0
            do {
                val page = db.history(room.id, offset, 10)
                page.forEach(::remember)
                offset += page.size
            } while (page.size == 10)
        }
        db.putRecord("migration:wheel-history-v1", "1")
    }

    fun weights(room: Room): List<Int> {
        val candidates = WheelProtectionRules.candidates(room)
        val aliases = db.records("account-alias:").map { it.first.removePrefix("account-alias:") }
            .groupBy { AccountAliases.resolve(db, it) }
        val times = candidates.associateWith { memberId ->
            val identity = identity(room, memberId)
            (listOf(identity) + aliases[identity].orEmpty()).flatMap(::choices)
                .distinctBy { it.id }.map { it.at }
        }
        return PayerSelection.historyWeights(candidates, times, clock(), room.lastChosenMemberId)
    }

    fun remember(room: Room) {
        if (room.paymentRoom != null) return
        val now = clock()
        fun record(memberId: String, event: String, at: Long) {
            if (at < now - PayerSelection.HISTORY_WINDOW_MS) return
            val identity = identity(room, memberId)
            val previous = choices(identity).filter { it.at >= now - PayerSelection.HISTORY_WINDOW_MS }
            if (previous.any { it.id == event }) return
            db.putRecord("wheel-history:$identity", orderJson.encodeToString(previous + PreviousSelection(event, minOf(at, now))))
        }
        (room.pastSpins + listOfNotNull(room.spin)).forEach { spin ->
            record(spin.winnerId, "${room.id}:${room.orderNumber}:spin:${spin.id}", spin.startAt)
        }
        if (room.spin == null && room.payerId != null) {
            val selection = room.audit.lastOrNull { it.action == CommandKind.SELECT_PAYER.name }
            record(requireNotNull(room.payerId), "${room.id}:${room.orderNumber}:direct", selection?.at ?: room.updatedAt)
        }
    }
}
