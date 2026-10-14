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

    fun rows(): List<StoredRow> = buckets.flatMap { (bucket, bytes) ->
        decode(bytes).also { rows ->
            if (rows.any { bucket(it.id) != bucket } || rows.map { it.id }.distinct().size != rows.size)
                throw StorageUnavailable("Invalid Firestore restore cache.")
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
        fun bucket(id: String): String = (id.take(2).toInt(16) % 64).toString(16).padStart(2, '0')
        fun fromRows(rows: List<StoredRow>) = FirestoreRestoreCache(rows.groupBy { bucket(it.id) }
            .mapValues { (_, values) -> encode(values.sortedBy { it.id }) })
        fun fromBuckets(buckets: Map<String, ByteArray>) = FirestoreRestoreCache(buckets).also {
            if (!it.bounded) throw StorageUnavailable("Firestore restore cache exceeds its size limit.")
        }
        private fun encode(rows: List<StoredRow>): ByteArray {
            val raw = orderJson.encodeToString(rows).toByteArray()
            // A highly compressible bucket must still fit the decoder's limit.
            // The empty sentinel makes the optional cache ineligible for publication.
            if (raw.size > 64 * 1024 * 1024) return byteArrayOf()
            return ByteArrayOutputStream().also { output -> GZIPOutputStream(output).use { it.write(raw) } }.toByteArray()
        }
        private fun decode(bytes: ByteArray): List<StoredRow> {
            val raw = GZIPInputStream(bytes.inputStream()).use { it.readNBytes(64 * 1024 * 1024 + 1) }
            if (raw.size > 64 * 1024 * 1024) throw StorageUnavailable("Firestore restore bucket is too large.")
            return orderJson.decodeFromString(raw.toString(Charsets.UTF_8))
        }
    }
}
