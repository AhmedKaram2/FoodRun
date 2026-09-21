package com.karim.foodrun.server

import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.firestore.Blob
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.karim.foodrun.orders.orderJson
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Firestore is authoritative; SQLite is a disposable, encrypted query cache. No polling reads. */
class FirestoreStore(
    private val firestore: Firestore,
    namespace: String,
    private val allowCreate: Boolean = false,
    private val dailyWriteLimit: Long = 12_000,
    private val clockDay: () -> String = { LocalDate.now(ZoneId.of("America/Los_Angeles")).toString() },
) : DurableStore {
    private val metadata = firestore.collection("foodrunServers").document(namespace)
    private val documents = metadata.collection("foodrun_rows")
    private var revision = -1L
    private var sizes = mutableMapOf<String, List<Int>>()
    private var usable = true

    init {
        require(namespace.matches(Regex("[a-zA-Z0-9_-]{1,80}")))
        require(dailyWriteLimit in 10..12_000)
    }

    override fun load(): List<StoredRow>? = protect {
        val claim = UUID.randomUUID().toString()
        val snapshot = firestore.runTransaction { tx ->
            val meta = tx.get(metadata).get()
            if ((!meta.exists() || meta.getBoolean("initialized") != true) && !allowCreate) throw StorageUnavailable("Firestore has not been initialized. Explicit bootstrap is required.")
            if (meta.exists() && meta.getLong("format") != 1L) throw StorageUnavailable("Unsupported Firestore storage format.")
            val docs = tx.get(documents.limit(15_001)).get().documents
            if ((!meta.exists() || meta.getBoolean("initialized") != true) && docs.isNotEmpty()) throw StorageUnavailable("Firestore metadata is missing; restore the complete store.")
            if (docs.size > 15_000) throw StorageUnavailable("Firestore restore exceeds the configured free-tier read budget.")
            val grouped = docs.groupBy { it.getString("row") ?: throw StorageUnavailable("Invalid stored row.") }
            val loadedSizes = mutableMapOf<String, List<Int>>()
            val rows = grouped.map { (id, parts) ->
                val sorted = parts.sortedBy { it.getLong("part") }
                if (sorted.indices.any { sorted[it].getLong("part") != it.toLong() || sorted[it].getLong("count") != sorted.size.toLong() }) throw StorageUnavailable("Incomplete Firestore row.")
                val blobs = sorted.map { it.getBlob("payload")!!.toBytes() }
                val bytes = ByteArrayOutputStream().also { out -> blobs.forEach(out::write) }.toByteArray()
                val row = decode(bytes)
                if (row.id != id || row.cells.size != row.schema.columns.size) throw StorageUnavailable("Invalid Firestore row identity.")
                loadedSizes[id] = blobs.map { it.size }
                row
            }
            val next = (meta.getLong("revision") ?: 0) + 1
            val writes = if (meta.getString("day") == clockDay()) meta.getLong("writes") ?: 0 else 0
            if (writes + 1 > dailyWriteLimit) throw StorageUnavailable("Daily storage allowance reached. Try again tomorrow.")
            tx.set(metadata, mapOf("format" to 1L, "initialized" to (meta.getBoolean("initialized") == true), "revision" to next, "commit" to claim, "day" to clockDay(), "writes" to writes + 1, "bytes" to loadedSizes.values.sumOf { p -> p.sumOf { it.toLong() + 1024 } }))
            Triple(if (meta.getBoolean("initialized") == true) rows else null, next, loadedSizes)
        }.get(90, TimeUnit.SECONDS)
        revision = snapshot.second
        sizes = snapshot.third
        snapshot.first
    }

    override fun commit(changes: Map<String, StoredRow?>) = protect<Unit> {
        if (!usable || revision < 0) throw StorageUnavailable("Firestore storage must be reloaded.")
        if (changes.isEmpty()) return@protect
        val encoded = changes.mapValues { (id, row) ->
            if (row != null && row.id != id) throw StorageUnavailable("Invalid storage mutation.")
            row?.let {
                val bytes = encode(it)
                (bytes.indices step CHUNK_BYTES).map { offset -> bytes.copyOfRange(offset, minOf(offset + CHUNK_BYTES, bytes.size)) }
            } ?: emptyList()
        }
        val operations = 1L + encoded.entries.sumOf { (id, parts) -> maxOf(parts.size, sizes[id]?.size ?: 0).toLong() }
        val bytes = encoded.values.sumOf { it.sumOf { part -> part.size.toLong() } }
        if (operations > 450 || bytes > 7_000_000) throw StorageUnavailable("This change exceeds the atomic storage limit. Split it into smaller changes.")
        val nextSizes = sizes.toMutableMap().apply { encoded.forEach { (id, parts) -> if (parts.isEmpty()) remove(id) else put(id, parts.map { it.size }) } }
        val totalBytes = nextSizes.values.sumOf { p -> p.sumOf { it.toLong() + 1024 } }
        if (totalBytes > 600L * 1024 * 1024 || nextSizes.values.sumOf { it.size } > 15_000) throw StorageUnavailable("FoodRun's free storage allowance is full.")
        val commitId = UUID.randomUUID().toString()
        val expected = revision
        try {
            firestore.runTransaction { tx ->
                val meta = tx.get(metadata).get()
                // A timeout retry may observe the already-committed operation.
                if (meta.getString("commit") == commitId) return@runTransaction Unit
                if (meta.getLong("revision") != expected) throw StorageUnavailable("Another server has taken over. Reconnect to the active server.")
                val writes = if (meta.getString("day") == clockDay()) meta.getLong("writes") ?: 0 else 0
                if (writes + operations > dailyWriteLimit) throw StorageUnavailable("Daily storage allowance reached. Try again tomorrow.")
                encoded.forEach { (id, parts) ->
                    parts.forEachIndexed { index, part -> tx.set(documents.document("$id-$index"), mapOf("row" to id, "part" to index, "count" to parts.size, "payload" to Blob.fromBytes(part))) }
                    for (index in parts.size until (sizes[id]?.size ?: 0)) tx.delete(documents.document("$id-$index"))
                }
                tx.set(metadata, mapOf("format" to 1L, "initialized" to true, "revision" to expected + 1, "commit" to commitId, "day" to clockDay(), "writes" to writes + operations, "bytes" to totalBytes))
                Unit
            }.get(45, TimeUnit.SECONDS)
        } catch (error: Exception) {
            // Resolve an ambiguous response without ever acknowledging an unconfirmed write.
            val confirmed = runCatching { metadata.get().get(15, TimeUnit.SECONDS).getString("commit") == commitId }.getOrDefault(false)
            if (!confirmed) { usable = false; throw StorageUnavailable("Storage could not confirm this change. Reconnect and retry.", error) }
        }
        revision = expected + 1
        sizes = nextSizes
    }

    private fun <T> protect(block: () -> T): T = try { block() } catch (error: StorageUnavailable) { throw error }
        catch (error: Exception) { usable = false; throw StorageUnavailable("Firestore storage is unavailable.", error) }
    override fun close() { firestore.close() }

    companion object {
        internal const val CHUNK_BYTES = 600_000
        internal fun encode(row: StoredRow): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(orderJson.encodeToString(row).toByteArray()) } }.toByteArray()
        internal fun decode(bytes: ByteArray): StoredRow {
            val raw = GZIPInputStream(bytes.inputStream()).use { it.readNBytes(16 * 1024 * 1024 + 1) }
            if (raw.size > 16 * 1024 * 1024) throw StorageUnavailable("Stored row is too large.")
            return orderJson.decodeFromString(raw.toString(Charsets.UTF_8))
        }
        fun configured(): FirestoreStore? {
            val mode = System.getenv("FOODRUN_STORAGE") ?: "sqlite"
            require(mode in listOf("sqlite", "firestore")) { "Unsupported FOODRUN_STORAGE." }
            if (mode != "firestore") return null
            val project = requireNotNull(System.getenv("FOODRUN_FIREBASE_PROJECT_ID"))
            require(System.getenv("FIRESTORE_EMULATOR_HOST").isNullOrBlank()) { "Production storage cannot use an emulator." }
            val credential = requireNotNull(System.getenv("FOODRUN_FIRESTORE_CREDENTIALS")) { "Firestore service credentials are required." }
            val credentials = ServiceAccountCredentials.fromStream(credential.byteInputStream())
            require(credentials.projectId == project) { "Firestore credentials belong to a different project." }
            val client = FirestoreOptions.newBuilder().setProjectId(project).setCredentials(credentials).build().service
            return FirestoreStore(client, requireNotNull(System.getenv("FOODRUN_FIRESTORE_NAMESPACE")), System.getenv("FOODRUN_FIRESTORE_BOOTSTRAP") == "true")
        }
    }
}
