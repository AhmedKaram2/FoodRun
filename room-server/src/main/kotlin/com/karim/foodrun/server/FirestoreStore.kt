package com.karim.foodrun.server

import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.firestore.Blob
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.firestore.FieldPath
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.ListenerRegistration
import com.google.cloud.firestore.Query
import com.google.cloud.firestore.QueryDocumentSnapshot
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
    @Volatile private var usable = true
    override val available: Boolean get() = usable
    private val writerId = UUID.randomUUID().toString()
    private var ownershipListener: ListenerRegistration? = null
    private var restoreGeneration: String? = null
    private var restoreCache: FirestoreRestoreCache? = null

    init {
        require(namespace.matches(Regex("[a-zA-Z0-9_-]{1,80}")))
        require(dailyWriteLimit in 10..12_000)
    }

    override fun load(): List<StoredRow>? {
        val rows = mutableListOf<StoredRow>()
        return if (restore(rows::add)) rows else null
    }

    override fun restore(accept: (StoredRow) -> Unit): Boolean = protect {
        val claim = UUID.randomUUID().toString()
        // Claim only the metadata in a transaction. A retry must never repeat a
        // scan of every stored row, and the old writer is fenced before restore.
        val meta = firestore.runTransaction { tx ->
            val meta = tx.get(metadata).get()
            if ((!meta.exists() || meta.getBoolean("initialized") != true) && !allowCreate) throw StorageUnavailable("Firestore has not been initialized. Explicit bootstrap is required.")
            if (meta.exists() && meta.getLong("format") != 1L) throw StorageUnavailable("Unsupported Firestore storage format.")
            val next = (meta.getLong("revision") ?: 0) + 1
            val writes = if (meta.getString("day") == clockDay()) meta.getLong("writes") ?: 0 else 0
            if (writes + 1 > dailyWriteLimit) throw StorageUnavailable("Daily storage allowance reached. Try again tomorrow.")
            val values = mutableMapOf<String, Any>("format" to 1L, "initialized" to (meta.getBoolean("initialized") == true), "revision" to next, "commit" to claim, "writer" to writerId, "day" to clockDay(), "writes" to writes + 1, "bytes" to (meta.getLong("bytes") ?: 0L))
            if (meta.getLong("restoreRevision") == meta.getLong("revision") && meta.getString("restoreGeneration") != null && meta.get("restoreBuckets") is Map<*, *>) {
                values["restoreGeneration"] = meta.getString("restoreGeneration")!!
                values["restoreRevision"] = next
                values["restoreRows"] = meta.getLong("restoreRows") ?: -1L
                values["restoreParts"] = meta.getLong("restoreParts") ?: -1L
                values["restoreBuckets"] = meta.get("restoreBuckets")!!
            }
            tx.set(metadata, values)
            values
        }.get(90, TimeUnit.SECONDS)
        revision = meta["revision"] as Long
        restoreGeneration = meta["restoreGeneration"] as? String
        val loadedSizes = mutableMapOf<String, List<Int>>()
        var rowCount = 0L
        fun acceptRow(row: StoredRow) { rowCount++; accept(row) }
        if (restoreGeneration != null) {
            val manifest = meta["restoreBuckets"] as Map<*, *>
            val references = manifest.flatMap { (key, value) ->
                val bucket = key as? String ?: throw StorageUnavailable("Invalid restore manifest.")
                val count = (value as? Number)?.toInt() ?: throw StorageUnavailable("Invalid restore manifest.")
                if (!bucket.matches(Regex("[0-3][0-9a-f]")) || count !in 1..FirestoreRestoreCache.MAX_PARTS) throw StorageUnavailable("Invalid restore manifest.")
                (0 until count).map { metadata.collection("foodrun_restore").document("$restoreGeneration-$bucket-$it") }
            }
            if (references.isEmpty() || references.size > FirestoreRestoreCache.MAX_PARTS) throw StorageUnavailable("Invalid restore manifest.")
            val parts = firestore.getAll(*references.toTypedArray()).get(90, TimeUnit.SECONDS)
            if (parts.any { !it.exists() }) throw StorageUnavailable("Incomplete Firestore restore cache.")
            if (parts.size.toLong() != meta["restoreParts"]) throw StorageUnavailable("Incomplete Firestore restore cache.")
            val buckets = parts.groupBy { it.getString("bucket") ?: throw StorageUnavailable("Invalid restore bucket.") }
                .mapValues { (_, parts) -> joinParts(parts) }
            restoreCache = FirestoreRestoreCache.fromBuckets(buckets)
            restoreCache!!.forEachRow { row ->
                if (row.cells.size != row.schema.columns.size) throw StorageUnavailable("Invalid Firestore row identity.")
                val bytes = encode(row)
                loadedSizes[row.id] = split(bytes).map { it.size }
                acceptRow(row)
            }
            if (rowCount != meta["restoreRows"]) throw StorageUnavailable("Incomplete Firestore restore cache.")
        } else {
            FirestoreRestoreCache.Builder().use { builder ->
                val parts = mutableListOf<QueryDocumentSnapshot>()
                var rowId: String? = null
                fun finishRow() {
                    if (parts.isEmpty()) return
                    val row = decode(joinParts(parts))
                    if (row.id != rowId || row.cells.size != row.schema.columns.size) throw StorageUnavailable("Invalid Firestore row identity.")
                    loadedSizes[row.id] = parts.sortedBy { it.getLong("part") }.map { it.getBlob("payload")!!.toBytes().size }
                    builder.add(row)
                    acceptRow(row)
                    parts.clear()
                }
                readPages(documents, 15_000) { page -> page.forEach { part ->
                    val id = part.getString("row") ?: throw StorageUnavailable("Invalid stored row.")
                    if (part.id != "$id-${part.getLong("part")}") throw StorageUnavailable("Invalid stored row identity.")
                    if (id != rowId) { finishRow(); rowId = id }
                    parts.add(part)
                } }
                finishRow()
                restoreCache = if (rowCount >= FirestoreRestoreCache.MIN_ROWS) builder.finish() else null
            }
        }
        if (meta["initialized"] != true && rowCount != 0L) throw StorageUnavailable("Firestore metadata is missing; restore the complete store.")
        val current = metadata.get().get(15, TimeUnit.SECONDS)
        if (current.getString("writer") != writerId || current.getLong("revision") != revision) throw StorageUnavailable("Another server has taken over. Reconnect to the active server.")
        sizes = loadedSizes
        if (restoreGeneration == null && restoreCache != null) {
            val cache = restoreCache!!
            restoreCache = null
            publishRestoreCache(cache, rowCount)
        }
        ownershipListener?.remove()
        ownershipListener = metadata.addSnapshotListener { value, error ->
            // One small metadata listener fences old instances during deployments. Menus,
            // wallet rows and photos continue to load only once on the active writer.
            if (error != null || value?.exists() != true || value.getString("writer") != writerId) usable = false
        }
        org.slf4j.LoggerFactory.getLogger(FirestoreStore::class.java).info("Firestore restore complete: rows={} compactParts={}", rowCount, restoreCache?.parts ?: 0)
        meta["initialized"] == true
    }

    private fun readPages(query: Query, limit: Int, receive: (List<QueryDocumentSnapshot>) -> Unit) {
        var count = 0
        var cursor: QueryDocumentSnapshot? = null
        while (true) {
            val pageSize = minOf(256, limit + 1 - count)
            var page = query.orderBy(FieldPath.documentId()).limit(pageSize)
            cursor?.let { page = page.startAfter(it) }
            val docs = page.get().get(90, TimeUnit.SECONDS).documents
            count += docs.size
            if (count > limit) throw StorageUnavailable("Firestore restore exceeds its document limit.")
            receive(docs)
            if (docs.size < pageSize) return
            cursor = docs.last()
        }
    }

    private fun joinParts(parts: List<DocumentSnapshot>): ByteArray {
        val sorted = parts.sortedBy { it.getLong("part") }
        if (sorted.indices.any { sorted[it].getLong("part") != it.toLong() || sorted[it].getLong("count") != sorted.size.toLong() }) throw StorageUnavailable("Incomplete Firestore row.")
        return ByteArrayOutputStream().also { out -> sorted.forEach { out.write(it.getBlob("payload")!!.toBytes()) } }.toByteArray()
    }

    private fun split(bytes: ByteArray) = (bytes.indices step CHUNK_BYTES).map { offset -> bytes.copyOfRange(offset, minOf(offset + CHUNK_BYTES, bytes.size)) }

    private fun cacheDocuments(cache: FirestoreRestoreCache, generation: String, buckets: Set<String> = cache.buckets.keys) = buckets.flatMap { bucket ->
        cache.buckets[bucket]?.let { bytes -> split(bytes).mapIndexed { index, part ->
            "$generation-$bucket-$index" to mapOf("generation" to generation, "bucket" to bucket, "part" to index.toLong(), "count" to ((bytes.size + CHUNK_BYTES - 1) / CHUNK_BYTES).toLong(), "payload" to Blob.fromBytes(part))
        } }.orEmpty()
    }.toMap()

    private fun cacheManifest(cache: FirestoreRestoreCache): Map<String, Long> = cache.buckets.mapValues { (_, bytes) ->
        ((bytes.size + CHUNK_BYTES - 1) / CHUNK_BYTES).toLong()
    }

    private fun publishRestoreCache(cache: FirestoreRestoreCache, rowCount: Long) {
        // Reuse bounded slots. Retired parts are ignored by the manifest, so
        // repeated upgrades cannot accumulate entire obsolete cache generations.
        val generation = "v1"
        val pending = cacheDocuments(cache, generation).entries.toList()
        val meta = metadata.get().get(15, TimeUnit.SECONDS)
        val writes = if (meta.getString("day") == clockDay()) meta.getLong("writes") ?: 0L else 0L
        if (writes + pending.size + (pending.size + 7) / 8 + 1 > dailyWriteLimit) return
        // Stage bounded batches; advertise only the complete generation. Canonical
        // rows stay intact, including when staging fails or another writer wins.
        for (batch in pending.chunked(8)) {
            val expected = revision
            firestore.runTransaction { tx ->
                val meta = tx.get(metadata).get()
                if (meta.getString("writer") != writerId || meta.getLong("revision") != expected) throw StorageUnavailable("Another server has taken over.")
                val writes = if (meta.getString("day") == clockDay()) meta.getLong("writes") ?: 0L else 0L
                if (writes + batch.size + 1 > dailyWriteLimit) throw StorageUnavailable("Daily storage allowance reached. Try again tomorrow.")
                batch.forEach { (id, values) -> tx.set(metadata.collection("foodrun_restore").document(id), values) }
                tx.update(metadata, mapOf("revision" to expected + 1, "day" to clockDay(), "writes" to writes + batch.size + 1))
            }.get(45, TimeUnit.SECONDS)
            revision++
        }
        val expected = revision
        firestore.runTransaction { tx ->
            val meta = tx.get(metadata).get()
            if (meta.getString("writer") != writerId || meta.getLong("revision") != expected) throw StorageUnavailable("Another server has taken over.")
            val writes = if (meta.getString("day") == clockDay()) meta.getLong("writes") ?: 0L else 0L
            if (writes + 1 > dailyWriteLimit) throw StorageUnavailable("Daily storage allowance reached. Try again tomorrow.")
            tx.update(metadata, mapOf("revision" to expected + 1, "day" to clockDay(), "writes" to writes + 1,
                "restoreGeneration" to generation, "restoreRevision" to expected + 1, "restoreRows" to rowCount, "restoreParts" to cache.parts.toLong(), "restoreBuckets" to cacheManifest(cache)))
        }.get(45, TimeUnit.SECONDS)
        revision++
        restoreGeneration = generation
        restoreCache = cache
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
        val baseOperations = 1L + encoded.entries.sumOf { (id, parts) -> maxOf(parts.size, sizes[id]?.size ?: 0).toLong() }
        val baseBytes = encoded.values.sumOf { it.sumOf { part -> part.size.toLong() } }
        if (baseOperations > 450 || baseBytes > 7_000_000) throw StorageUnavailable("This change exceeds the atomic storage limit. Split it into smaller changes.")
        val nextSizes = sizes.toMutableMap().apply { encoded.forEach { (id, parts) -> if (parts.isEmpty()) remove(id) else put(id, parts.map { it.size }) } }
        val totalBytes = nextSizes.values.sumOf { p -> p.sumOf { it.toLong() + 1024 } }
        if (totalBytes > 600L * 1024 * 1024 || nextSizes.values.sumOf { it.size } > 15_000) throw StorageUnavailable("FoodRun's free storage allowance is full.")
        val commitId = UUID.randomUUID().toString()
        val expected = revision
        val affected = changes.keys.map(FirestoreRestoreCache::bucket).toSet()
        val nextCache = restoreCache?.update(changes)?.takeIf { it.bounded && it.buckets.isNotEmpty() }
        val generation = restoreGeneration
        val oldCacheDocs = if (restoreCache != null && generation != null) cacheDocuments(restoreCache!!, generation, affected) else emptyMap()
        val newCacheDocs = if (nextCache != null && generation != null) cacheDocuments(nextCache, generation, affected) else emptyMap()
        val cacheDeletes = oldCacheDocs.keys - newCacheDocs.keys
        val cacheOperations = newCacheDocs.size + cacheDeletes.size
        val cacheBytes = newCacheDocs.values.sumOf { (it.getValue("payload") as Blob).toBytes().size.toLong() }
        var keepCache = nextCache != null && generation != null && baseOperations + cacheOperations <= 450 && baseBytes + cacheBytes <= 7_000_000
        try {
            firestore.runTransaction { tx ->
                val meta = tx.get(metadata).get()
                // A timeout retry may observe the already-committed operation.
                if (meta.getString("commit") == commitId) return@runTransaction Unit
                if (meta.getString("writer") != writerId || meta.getLong("revision") != expected) throw StorageUnavailable("Another server has taken over. Reconnect to the active server.")
                val writes = if (meta.getString("day") == clockDay()) meta.getLong("writes") ?: 0 else 0
                if (writes + baseOperations > dailyWriteLimit) throw StorageUnavailable("Daily storage allowance reached. Try again tomorrow.")
                if (writes + baseOperations + cacheOperations > dailyWriteLimit) keepCache = false
                encoded.forEach { (id, parts) ->
                    parts.forEachIndexed { index, part -> tx.set(documents.document("$id-$index"), mapOf("row" to id, "part" to index, "count" to parts.size, "payload" to Blob.fromBytes(part))) }
                    for (index in parts.size until (sizes[id]?.size ?: 0)) tx.delete(documents.document("$id-$index"))
                }
                val values = mutableMapOf<String, Any>("format" to 1L, "initialized" to true, "revision" to expected + 1, "commit" to commitId, "writer" to writerId, "day" to clockDay(), "writes" to writes + baseOperations + (if (keepCache) cacheOperations else 0), "bytes" to totalBytes)
                if (keepCache) {
                    newCacheDocs.forEach { (id, body) -> tx.set(metadata.collection("foodrun_restore").document(id), body) }
                    cacheDeletes.forEach { tx.delete(metadata.collection("foodrun_restore").document(it)) }
                    values.putAll(mapOf("restoreGeneration" to generation!!, "restoreRevision" to expected + 1, "restoreRows" to nextSizes.size.toLong(), "restoreParts" to nextCache!!.parts.toLong(), "restoreBuckets" to cacheManifest(nextCache)))
                }
                tx.set(metadata, values)
                Unit
            }.get(45, TimeUnit.SECONDS)
        } catch (error: Exception) {
            // Resolve an ambiguous response without ever acknowledging an unconfirmed write.
            val confirmed = runCatching { metadata.get().get(15, TimeUnit.SECONDS).getString("commit") == commitId }.getOrDefault(false)
            if (!confirmed) { usable = false; throw StorageUnavailable("Storage could not confirm this change. Reconnect and retry.", error) }
        }
        revision = expected + 1
        sizes = nextSizes
        restoreCache = if (keepCache) nextCache else null
        restoreGeneration = if (keepCache) generation else null
    }

    private fun <T> protect(block: () -> T): T = try { block() } catch (error: StorageUnavailable) { usable = false; throw error }
        catch (error: Exception) { usable = false; throw StorageUnavailable("Firestore storage is unavailable.", error) }
    override fun close() { ownershipListener?.remove(); firestore.close() }

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
