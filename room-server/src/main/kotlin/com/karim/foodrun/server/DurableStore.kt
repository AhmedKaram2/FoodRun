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
    val mode: String get() = "firestore"
    /** False after another writer takes ownership; stale cached reads must stop too. */
    val available: Boolean get() = true
    /** Atomically claims this single-writer hub. Null means a newly initialized empty store. */
    fun load(): List<StoredRow>?
    /** Stream a restored store into a caller-owned transaction; false means new storage. */
    fun restore(accept: (StoredRow) -> Unit): Boolean {
        val rows = load() ?: return false
        rows.forEach(accept)
        return true
    }
    /** Atomic durable commit; null rows are deletions. Returns only after persistence is confirmed. */
    fun commit(changes: Map<String, StoredRow?>)
    /** Optional cleanup must leave room for account/payment writes and may be skipped. */
    fun canCommitMaintenance(changes: Map<String, StoredRow?>): Boolean = false
    override fun close() {}
}

internal fun configuredDurableStore(environment: Map<String, String> = System.getenv()): DurableStore? =
    when (environment["FOODRUN_STORAGE"] ?: "sqlite") {
        "sqlite" -> null
        "firestore" -> FirestoreStore.configured(environment)
        "turso" -> TursoStore.configured(environment)
        else -> error("Unsupported FOODRUN_STORAGE.")
    }
