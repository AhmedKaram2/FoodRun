package com.karim.foodrun.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import com.karim.foodrun.orders.orderJson

/** Failures must reach HTTP's retryable HUB_UNAVAILABLE response, never VALIDATION/STATE. */
class StorageUnavailable(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

@Serializable
data class StoredRow(val table: String, val cells: List<String>) {
    val key: String get() = orderJson.encodeToString(cells.take(schema.primaryKeys))
    val schema: StoredTable get() = storedTables.single { it.name == table }
    val id: String get() = RoomService.hash("$table:$key")
}
data class StoredTable(val name: String, val columns: List<String>, val primaryKeys: Int, val body: Int = -1)
internal val storedTables = listOf(
    StoredTable("rooms", listOf("id", "code", "body", "phase"), 1, 2),
    StoredTable("sessions", listOf("hash", "room_id", "member_id"), 1),
    StoredTable("commands", listOf("id", "digest", "body"), 1, 2),
    StoredTable("orders", listOf("room_id", "number", "body"), 2, 2),
    StoredTable("account_records", listOf("key", "body"), 1, 1),
)
interface DurableStore : AutoCloseable {
    /** Atomically claims this single-writer hub. Null means a newly initialized empty store. */
    fun load(): List<StoredRow>?
    /** Atomic durable commit; null rows are deletions. Returns only after persistence is confirmed. */
    fun commit(changes: Map<String, StoredRow?>)
    override fun close() {}
}
