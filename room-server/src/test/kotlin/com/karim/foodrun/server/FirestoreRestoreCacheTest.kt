package com.karim.foodrun.server

import java.util.Base64
import java.util.Random
import kotlin.test.*

class FirestoreRestoreCacheTest {
    @Test fun thousandsOfRowsUseAtMost64SmallRestoreDocuments() {
        val rows = (0 until 4_400).map { StoredRow("account_records", listOf("account-$it", "saved value $it")) }
        val cache = FirestoreRestoreCache.fromRows(rows)
        assertEquals(64, cache.parts)
        assertTrue(cache.bounded)
        assertEquals(rows.associateBy { it.id }, cache.rows().associateBy { it.id })
    }

    @Test fun largeRowsShrinkAndDisappearWithoutLosingOtherRowsInTheSameBucket() {
        val payload = ByteArray(1_200_000).also(Random(14)::nextBytes)
        val large = StoredRow("account_records", listOf("large", Base64.getEncoder().encodeToString(payload)))
        val other = (0..1000).map { StoredRow("account_records", listOf("other-$it", "value")) }
            .first { FirestoreRestoreCache.bucket(it.id) == FirestoreRestoreCache.bucket(large.id) }
        val before = FirestoreRestoreCache.fromRows(listOf(large, other))
        assertTrue(before.parts > 1)
        assertEquals(setOf(large, other), before.rows().toSet())
        val small = large.copy(cells = listOf("large", "small"))
        val shrunk = before.update(mapOf(small.id to small))
        assertEquals(1, shrunk.parts)
        assertEquals(setOf(small, other), shrunk.rows().toSet())
        assertEquals(listOf(other), shrunk.update(mapOf(small.id to null)).rows())
        assertTrue(shrunk.update(mapOf(small.id to null, other.id to null)).rows().isEmpty())
    }
}
