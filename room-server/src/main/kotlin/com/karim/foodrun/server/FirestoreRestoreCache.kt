package com.karim.foodrun.server

import com.karim.foodrun.orders.orderJson
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** A derived copy of the canonical rows, updated in the same Firestore transaction. */
internal class FirestoreRestoreCache private constructor(val buckets: Map<String, ByteArray>) {
    val bytes: Long get() = buckets.values.sumOf { it.size.toLong() }
    val parts: Int get() = buckets.values.sumOf { (it.size + FirestoreStore.CHUNK_BYTES - 1) / FirestoreStore.CHUNK_BYTES }
    val bounded: Boolean get() = bytes <= MAX_BYTES && parts <= MAX_PARTS && buckets.values.none { it.isEmpty() }

    fun rows(): List<StoredRow> = buildList { forEachRow { add(it) } }

    fun forEachRow(accept: (StoredRow) -> Unit) {
        buckets.forEach { (bucket, bytes) ->
            val rows = decode(bytes)
            if (rows.any { bucket(it.id) != bucket } || rows.map { it.id }.distinct().size != rows.size)
                throw StorageUnavailable("Invalid Firestore restore cache.")
            rows.forEach(accept)
        }
    }

    /** Compress rows as they arrive; never retain the complete decoded store. */
    class Builder : AutoCloseable {
        private val values = linkedMapOf<String, Bucket>()
        private var valid = true
        fun add(row: StoredRow) {
            if (!valid) return
            if (!values.getOrPut(bucket(row.id)) { Bucket() }.add(row) || values.values.sumOf { it.output.size().toLong() } > MAX_BYTES) {
                valid = false
                close()
                values.clear()
            }
        }
        fun finish(): FirestoreRestoreCache? {
            if (!valid) return null
            val cache = FirestoreRestoreCache(values.mapValues { (_, value) -> value.finish() })
            return cache.takeIf { it.bounded }
        }
        override fun close() { values.values.forEach { it.close() } }
    }

    private class Bucket : AutoCloseable {
        val output = ByteArrayOutputStream()
        private val gzip = GZIPOutputStream(output).also { it.write('['.code) }
        private var size = 2L
        private var first = true
        private var closed = false
        fun add(row: StoredRow): Boolean {
            val raw = orderJson.encodeToString(row).toByteArray()
            size += raw.size + if (first) 0 else 1
            if (size > MAX_DECODED_BYTES) return false
            if (!first) gzip.write(','.code)
            gzip.write(raw)
            first = false
            return true
        }
        fun finish(): ByteArray { close(); return output.toByteArray() }
        override fun close() {
            if (closed) return
            closed = true
            gzip.write(']'.code)
            gzip.close()
        }
    }

    fun update(changes: Map<String, StoredRow?>): FirestoreRestoreCache {
        val next = buckets.toMutableMap()
        changes.entries.groupBy { bucket(it.key) }.forEach { (bucket, values) ->
            val rows = next[bucket]?.let(::decode)?.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
            values.forEach { (id, row) -> if (row == null) rows.remove(id) else rows[id] = row }
            if (rows.isEmpty()) next.remove(bucket) else next[bucket] = encode(rows.values.sortedBy { it.id })
        }
        return FirestoreRestoreCache(next)
    }

    companion object {
        const val MIN_ROWS = 128
        const val MAX_BYTES = 32L * 1024 * 1024
        const val MAX_PARTS = 128
        private const val MAX_DECODED_BYTES = 64 * 1024 * 1024
        fun bucket(id: String): String = (id.take(2).toInt(16) % 64).toString(16).padStart(2, '0')
        fun fromRows(rows: List<StoredRow>): FirestoreRestoreCache = Builder().use { builder ->
            rows.forEach(builder::add)
            builder.finish() ?: FirestoreRestoreCache(mapOf("00" to byteArrayOf()))
        }
        fun fromBuckets(buckets: Map<String, ByteArray>) = FirestoreRestoreCache(buckets).also {
            if (!it.bounded) throw StorageUnavailable("Firestore restore cache exceeds its size limit.")
        }
        private fun encode(rows: List<StoredRow>): ByteArray = Bucket().use { value ->
            for (row in rows) if (!value.add(row)) return byteArrayOf()
            value.finish()
        }
        private fun decode(bytes: ByteArray): List<StoredRow> {
            val raw = GZIPInputStream(bytes.inputStream()).use { it.readNBytes(MAX_DECODED_BYTES + 1) }
            if (raw.size > MAX_DECODED_BYTES) throw StorageUnavailable("Firestore restore bucket is too large.")
            return orderJson.decodeFromString(raw.toString(Charsets.UTF_8))
        }
    }
}
