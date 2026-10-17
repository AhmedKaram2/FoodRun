package com.karim.foodrun.server

import com.karim.foodrun.orders.orderJson
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** The remote rows are authoritative; local encrypted SQLite remains a disposable cache. */
class TursoStore internal constructor(
    private val sql: TursoSql,
    private val namespace: String,
    private val allowCreate: Boolean = false,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : DurableStore {
    private val writer = UUID.randomUUID().toString()
    private var revision = -1L
    private var usable = true
    private var checkedAt = 0L
    override val mode: String get() = "turso"
    override val available: Boolean @Synchronized get() {
        if (usable && revision >= 0 && clock() - checkedAt >= 1_000) {
            try { verifyOwner(); checkedAt = clock() } catch (_: Exception) { usable = false }
        }
        return usable
    }
    init { require(namespace.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) { "Invalid Turso namespace." } }

    override fun load(): List<StoredRow>? {
        val rows = mutableListOf<StoredRow>()
        return if (restore(rows::add)) rows else null
    }

    @Synchronized override fun restore(accept: (StoredRow) -> Unit): Boolean = protect {
        initializeSchema()
        var meta = metadata()
        if (meta == null) {
            if (!allowCreate) throw StorageUnavailable("Turso has not been initialized. Migrate the existing store first.")
            sql.execute("INSERT OR IGNORE INTO foodrun_hubs(namespace,format,initialized,revision,writer,commit_id) VALUES(?,1,0,0,'','')", namespace)
            meta = metadata() ?: throw StorageUnavailable("Turso metadata is unavailable.")
        }
        if (meta[0].sqlText() != "1") throw StorageUnavailable("Unsupported Turso storage format.")
        val initialized = meta[1].sqlText() == "1"
        if (!initialized && !allowCreate) throw StorageUnavailable("Turso migration is incomplete.")
        // A single atomic claim fences every previous writer before reading any data.
        sql.transaction(listOf(
            stmt("UPDATE foodrun_hubs SET writer=?,revision=revision+1,commit_id=? WHERE namespace=? AND revision=?", writer, UUID.randomUUID().toString(), namespace, meta[2].sqlText().toLong()),
        ) + fence())
        val claimed = metadata() ?: throw StorageUnavailable("Turso metadata is unavailable.")
        if (claimed[3].sqlText() != writer) throw StorageUnavailable("Another server has taken over.")
        revision = claimed[2].sqlText().toLong(); checkedAt = clock()
        if (!initialized && sql.execute("SELECT 1 FROM foodrun_rows WHERE namespace=? LIMIT 1", namespace).sqlRows().isNotEmpty())
            throw StorageUnavailable("Turso contains an incomplete import; preserve it and use a fresh migration namespace.")
        readRows(accept)
        verifyOwner()
        initialized
    }

    @Synchronized override fun commit(changes: Map<String, StoredRow?>) = write(changes, initialized = true)

    /** Import is deliberately unpublished until a complete read-back matches every source row. */
    @Synchronized internal fun importFrom(source: DurableStore): Int = protect {
        initializeSchema()
        if (metadata() != null || sql.execute("SELECT 1 FROM foodrun_rows WHERE namespace=? LIMIT 1", namespace).sqlRows().isNotEmpty())
            throw StorageUnavailable("The Turso migration namespace must be new and empty; existing data will not be overwritten.")
        if (restore { error("The migration target must be empty.") }) throw StorageUnavailable("The Turso target is already initialized; it will not be overwritten.")
        val hashes = sortedMapOf<String, String>()
        val pending = linkedMapOf<String, StoredRow?>()
        var pendingBytes = 0
        fun flush() { if (pending.isNotEmpty()) { write(pending, initialized = false); pending.clear(); pendingBytes = 0 } }
        if (!source.restore { row ->
            val bytes = orderJson.encodeToString(row).toByteArray()
            check(hashes.put(row.id, RoomService.hash(bytes.toString(Charsets.UTF_8))) == null) { "Duplicate migration row." }
            if (pending.size >= 64 || pendingBytes + bytes.size > 1_000_000) flush()
            pending[row.id] = row; pendingBytes += bytes.size
        }) throw StorageUnavailable("The source store has not been initialized.")
        flush()
        val restored = sortedMapOf<String, String>()
        readRows { row -> restored[row.id] = RoomService.hash(orderJson.encodeToString(row)) }
        if (hashes.isEmpty() || hashes != restored || !source.available) throw StorageUnavailable("Migration verification failed; the target remains unpublished.")
        verifyOwner()
        write(emptyMap(), initialized = true)
        hashes.size
    }

    private fun write(changes: Map<String, StoredRow?>, initialized: Boolean) = protect {
        if (!usable || revision < 0) throw StorageUnavailable("Turso storage must be reloaded.")
        if (changes.size > 450) throw StorageUnavailable("This change exceeds the atomic storage limit.")
        val commit = UUID.randomUUID().toString()
        val expected = revision
        val statements = mutableListOf(stmt("UPDATE foodrun_hubs SET revision=revision+1,commit_id=?,initialized=? WHERE namespace=? AND writer=? AND revision=?", commit, if (initialized) 1 else 0, namespace, writer, expected))
        statements.addAll(fence())
        changes.forEach { (id, row) ->
            if (row == null) statements.add(stmt("DELETE FROM foodrun_rows WHERE namespace=? AND id=?", namespace, id))
            else {
                if (row.cells.size != row.schema.columns.size || row.id != id) throw StorageUnavailable("Invalid storage mutation.")
                statements.add(stmt("INSERT INTO foodrun_rows(namespace,id,payload) VALUES(?,?,?) ON CONFLICT(namespace,id) DO UPDATE SET payload=excluded.payload", namespace, id, encode(row)))
            }
        }
        try { sql.transaction(statements) }
        catch (_: Exception) {
            // Never replay an ambiguous mutation. Confirm its unique commit marker instead.
            val meta = runCatching { metadata() }.getOrNull()
            if (meta == null || meta[2].sqlText().toLong() != expected + 1 || meta[3].sqlText() != writer || meta[4].sqlText() != commit)
                throw StorageUnavailable("Storage could not confirm this change. Reconnect before retrying.")
        }
        revision = expected + 1; checkedAt = clock()
    }

    private fun readRows(accept: (StoredRow) -> Unit) {
        var after = ""
        var count = 0
        while (true) {
            val candidates = sql.execute("SELECT id,length(payload) FROM foodrun_rows WHERE namespace=? AND id>? ORDER BY id LIMIT 128", namespace, after).sqlRows()
            if (candidates.isEmpty()) return
            var bytes = 0L
            val selected = candidates.takeWhile { value ->
                val size = value[1].sqlText().toLong()
                if (size !in 1..MAX_COMPRESSED_BYTES) throw StorageUnavailable("Stored row exceeds the Turso size limit.")
                (bytes == 0L || bytes + size <= 3 * 1024 * 1024).also { if (it) bytes += size }
            }
            val rows = sql.execute("SELECT id,payload FROM foodrun_rows WHERE namespace=? AND id>? AND id<=? ORDER BY id", namespace, after, selected.last()[0].sqlText()).sqlRows()
            if (rows.size != selected.size) throw StorageUnavailable("Turso changed during restore.")
            for (value in rows) {
                val id = value[0].sqlText()
                if (id <= after || !id.matches(Regex("[a-f0-9]{64}")) || ++count > 15_000) throw StorageUnavailable("Invalid Turso row identity.")
                val payload = Base64.getDecoder().decode(value[1]["base64"]!!.jsonPrimitive.content)
                val raw = GZIPInputStream(payload.inputStream()).use { it.readNBytes(MAX_ROW_BYTES + 1) }
                if (raw.size > MAX_ROW_BYTES) throw StorageUnavailable("Stored row is too large.")
                val row = orderJson.decodeFromString<StoredRow>(raw.toString(Charsets.UTF_8))
                if (row.id != id || row.cells.size != row.schema.columns.size) throw StorageUnavailable("Invalid Turso row identity.")
                accept(row); after = id
            }
        }
    }
    private fun metadata() = sql.execute("SELECT format,initialized,revision,writer,commit_id FROM foodrun_hubs WHERE namespace=?", namespace).sqlRows().singleOrNull()
    private fun verifyOwner() {
        val meta = metadata()
        if (meta == null || meta[3].sqlText() != writer || meta[2].sqlText().toLong() != revision)
            throw StorageUnavailable("Another server has taken over. Reconnect to the active server.")
    }
    private fun initializeSchema() {
        sql.execute("CREATE TABLE IF NOT EXISTS foodrun_hubs(namespace TEXT PRIMARY KEY,format INTEGER NOT NULL,initialized INTEGER NOT NULL,revision INTEGER NOT NULL,writer TEXT NOT NULL,commit_id TEXT NOT NULL)")
        sql.execute("CREATE TABLE IF NOT EXISTS foodrun_rows(namespace TEXT NOT NULL,id TEXT NOT NULL,payload BLOB NOT NULL,PRIMARY KEY(namespace,id))")
    }
    private fun fence() = listOf(
        stmt("CREATE TEMP TABLE foodrun_writer_fence(valid INTEGER NOT NULL CHECK(valid=1))"),
        // changes() still refers to the metadata UPDATE; CREATE TABLE does not change it.
        stmt("INSERT INTO foodrun_writer_fence(valid) VALUES(changes())"),
    )
    private fun <T> protect(block: () -> T): T = try { block() }
        catch (error: StorageUnavailable) { usable = false; throw error }
        catch (_: Exception) { usable = false; throw StorageUnavailable("Turso storage is unavailable.") }
    @Synchronized override fun close() { usable = false }

    companion object {
        private const val MAX_ROW_BYTES = 16 * 1024 * 1024
        private const val MAX_COMPRESSED_BYTES = 5L * 1024 * 1024
        private fun stmt(sql: String, vararg args: Any) = TursoSql.Statement(sql, args.toList())
        private fun encode(row: StoredRow): ByteArray {
            val raw = orderJson.encodeToString(row).toByteArray()
            if (raw.size > MAX_ROW_BYTES) throw StorageUnavailable("Stored row is too large.")
            val bytes = ByteArrayOutputStream().also { output -> GZIPOutputStream(output).use { it.write(raw) } }.toByteArray()
            if (bytes.size > MAX_COMPRESSED_BYTES) throw StorageUnavailable("Stored row exceeds the Turso size limit.")
            return bytes
        }
        fun configured(environment: Map<String, String> = System.getenv()): TursoStore {
            val url = requireNotNull(environment["FOODRUN_TURSO_URL"]) { "FOODRUN_TURSO_URL is required." }
            val token = requireNotNull(environment["FOODRUN_TURSO_AUTH_TOKEN"]) { "FOODRUN_TURSO_AUTH_TOKEN is required." }
            return TursoStore(TursoSql(url, token), requireNotNull(environment["FOODRUN_TURSO_NAMESPACE"]) { "FOODRUN_TURSO_NAMESPACE is required." }, environment["FOODRUN_TURSO_BOOTSTRAP"] == "true")
        }
    }
}
