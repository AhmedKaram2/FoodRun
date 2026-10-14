package com.karim.foodrun.server

import com.google.cloud.firestore.FirestoreOptions
import com.karim.foodrun.orders.*
import java.util.UUID
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.*

/** Real SDK/Firestore protocol tests, isolated to the local emulator. */
class FirestoreStoreTest {
    private fun client(): com.google.cloud.firestore.Firestore {
        val host = System.getenv("FIRESTORE_EMULATOR_HOST")
        assumeTrue(host != null, "Run with the Firestore emulator for protocol coverage")
        require(host.startsWith("127.0.0.1:") || host.startsWith("localhost:"))
        return FirestoreOptions.newBuilder().setProjectId("demo-foodrun").setEmulatorHost(host).build().service
    }
    private fun namespace() = "test-${UUID.randomUUID()}"
    @Test fun firestoreRestoresFullMealWalletAfterCompleteDiskLoss() {
        val namespace = namespace()
        RoomFixture(durableFactory = { FirestoreStore(client(), namespace, true) }).use { f ->
            val member = f.placed(); f.pay()
            val before = f.state(member).receipts.single()
            f.restart(clearCache = true)
            assertEquals(before, f.state(member).receipts.single())
            val command = f.command(member, com.karim.foodrun.orders.CommandKind.DECLARE_TRANSFER).copy(amount = before.balance, text = "Bank")
            val transfer = f.execute(command).room!!.transfers.single()
            f.restart(clearCache = true)
            assertEquals(transfer.id, f.execute(command).room!!.transfers.single().id)
        }
    }
    @Test fun takeoverFencesOldWriterAndLargeRowsShrinkAndDeleteAtomically() {
        val namespace = namespace()
        val first = FirestoreStore(client(), namespace, true)
        val second = FirestoreStore(client(), namespace, true)
        try {
            assertNull(first.load())
            val data = java.util.Random(1).let { r -> ByteArray(1_200_000).also(r::nextBytes) }
            val row = StoredRow("account_records", listOf("large", java.util.Base64.getEncoder().encodeToString(data)))
            assertTrue(FirestoreStore.encode(row).size > FirestoreStore.CHUNK_BYTES)
            first.commit(mapOf(row.id to row))
            assertEquals(row, second.load()!!.single())
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10)
            while (first.available && System.nanoTime() < deadline) Thread.sleep(20)
            assertFalse(first.available, "The old instance must stop serving cached reads after takeover")
            assertTrue(second.available)
            assertFailsWith<StorageUnavailable> { first.commit(mapOf(row.id to row.copy(cells = listOf("large", "stale")))) }
            val short = row.copy(cells = listOf("large", "small"))
            second.commit(mapOf(short.id to short))
            FirestoreStore(client(), namespace).use { third ->
                assertEquals(listOf(short), third.load())
                third.commit(mapOf(short.id to null))
            }
            FirestoreStore(client(), namespace).use { fourth -> assertTrue(fourth.load()!!.isEmpty()) }
        } finally { first.close(); second.close() }
    }
    @Test fun dailyBudgetRejectsTheWholeChangeAndDoesNotErasePreviousData() {
        val namespace = namespace()
        val row = StoredRow("account_records", listOf("balance", "100"))
        FirestoreStore(client(), namespace, true, 10).use { store ->
            store.load()
            repeat(4) { store.commit(mapOf(row.id to row)) } // claim + 4 x (row + metadata) = 9
            assertFailsWith<StorageUnavailable> { store.commit(mapOf(row.id to row.copy(cells = listOf("balance", "0")))) }
        }
        FirestoreStore(client(), namespace, false, 12_000, { "next-day" }).use { restored -> assertEquals(listOf(row), restored.load()) }
    }
    @Test fun paymentRoomProfilesMembershipsAndReceivedPaymentsSurviveDiskLoss() {
        val identity = object : IdentityProvider {
            override fun signIn(email: String, password: String, register: Boolean) = CloudIdentity(email.substringBefore('@'), "id-token")
            override fun exchange(idToken: String) = CloudIdentity(idToken, "id-token")
            override fun refresh(refreshToken: String): CloudIdentity = error("Unused")
            override fun profile(identity: CloudIdentity) = FoodProfile(userId = identity.userId, name = identity.userId, phone = "+971501234567")
            override fun saveProfile(identity: CloudIdentity, profile: FoodProfile) = Unit
            override fun resetPassword(email: String) = Unit
            override fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String) = Unit
        }
        val namespace = namespace()
        RoomFixture(identity, { FirestoreStore(client(), namespace, true) }).use { f ->
            fun signIn(name: String) = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY,
                identity = IdentityRequest(IdentityAction.FIREBASE_SIGN_IN, firebaseToken = name)))
            val payer = signIn("payer"); val friend = signIn("friend")
            val command = RoomCommand(commandId = f.id(), kind = CommandKind.CREATE_PAYMENT_ROOM, identityToken = payer.identityToken,
                text = "Already ordered", name = "Restaurant", amount = 3000, account = f.account,
                paymentRoom = PaymentRoomRequest(PaymentRoomDetails("Lunch", "data:image/png;base64,iVBORw0KGgo="),
                    listOf(PaymentShare("payer", "No food", 0), PaymentShare("friend", "Lunch", 3000, 1000))))
            val created = f.execute(command)
            f.restart(clearCache = true)
            val home = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.HOME, identityToken = friend.identityToken)).home!!
            assertEquals("friend", home.profile.name)
            val membership = home.rooms.single()
            val restored = f.service.snapshot(membership.roomId, membership.token)
            assertEquals(2000L, restored.receipts.single().balance)
            assertEquals(command.paymentRoom!!.details, restored.room!!.paymentRoom)
            assertEquals(created.token, f.execute(command).token)
            val payment = RoomCommand(commandId = f.id(), kind = CommandKind.RECORD_PAYMENT, roomId = created.room!!.id, token = created.token,
                expectedOrderNumber = 1, expectedRevision = f.service.snapshot(created.room!!.id, created.token).room!!.revision, memberId = membership.memberId, amount = 2000, text = "Cash")
            f.execute(payment)
            f.restart(clearCache = true)
            assertEquals(0L, f.service.snapshot(membership.roomId, membership.token).receipts.single().balance)
            f.execute(payment)
            assertEquals(0L, f.service.snapshot(membership.roomId, membership.token).receipts.single().balance)
        }
    }
    @Test fun missingMetadataRequiresExplicitBootstrap() {
        FirestoreStore(client(), namespace()).use { assertFailsWith<StorageUnavailable> { it.load() } }
    }

    private fun cacheRows() = (0 until 384).map { StoredRow("account_records", listOf("restore-$it", "value-$it")) }

    @Test fun compactRestoreCachePreservesUpdatesDeletionsAndCanonicalRowsAcrossTakeover() {
        val namespace = namespace()
        val rows = cacheRows()
        FirestoreStore(client(), namespace, true).use { store -> store.load(); store.commit(rows.associateBy { it.id }) }
        client().use { firestore ->
            val metadata = firestore.collection("foodrunServers").document(namespace)
            FirestoreStore(client(), namespace).use { store -> assertEquals(rows.toSet(), store.load()!!.toSet()) }
            val ready = metadata.get().get()
            assertEquals(ready.getLong("revision"), ready.getLong("restoreRevision"))
            assertTrue(ready.getLong("restoreParts")!! <= 64, "Hundreds of rows should restore from at most 64 small documents")
            val updated = rows.first().copy(cells = listOf(rows.first().cells.first(), "updated balance"))
            val added = StoredRow("account_records", listOf("new-account", "new value"))
            FirestoreStore(client(), namespace).use { store ->
                assertEquals(rows.toSet(), store.load()!!.toSet())
                store.commit(mapOf(updated.id to updated, rows[1].id to null, added.id to added))
            }
            val canonical = metadata.collection("foodrun_rows").document("${updated.id}-0").get().get()
            assertEquals(updated, FirestoreStore.decode(canonical.getBlob("payload")!!.toBytes()))
            assertFalse(metadata.collection("foodrun_rows").document("${rows[1].id}-0").get().get().exists())
            FirestoreStore(client(), namespace).use { store ->
                val expected = (rows.drop(2) + updated + added).associateBy { it.id }
                assertEquals(expected, store.load()!!.associateBy { it.id })
                store.commit(expected.mapValues { null })
            }
            FirestoreStore(client(), namespace).use { assertTrue(it.load()!!.isEmpty(), "Deleting the final rows must not leave an active empty cache manifest") }
        }
    }

    @Test fun missingCachePartsFailClosedAndLegacyMetadataCanStillRestoreCanonicalRows() {
        val namespace = namespace()
        val rows = cacheRows()
        FirestoreStore(client(), namespace, true).use { store -> store.load(); store.commit(rows.associateBy { it.id }) }
        FirestoreStore(client(), namespace).use { it.load() }
        client().use { firestore ->
            val metadata = firestore.collection("foodrunServers").document(namespace)
            metadata.collection("foodrun_restore").document("v1-${FirestoreRestoreCache.bucket(rows.first().id)}-0").delete().get()
            FirestoreStore(client(), namespace).use { assertFailsWith<StorageUnavailable> { it.load() } }
            val legacy = metadata.get().get().data!!.filterKeys { !it.startsWith("restore") }
            metadata.set(legacy).get() // Older servers still maintain the original format-1 rows.
            FirestoreStore(client(), namespace).use { assertEquals(rows.toSet(), it.load()!!.toSet()) }
        }
    }

    @Test fun optionalCacheCannotConsumeTheBudgetNeededForACanonicalWrite() {
        val namespace = namespace()
        val rows = cacheRows()
        FirestoreStore(client(), namespace, true).use { store -> store.load(); store.commit(rows.associateBy { it.id }) }
        FirestoreStore(client(), namespace).use { it.load() }
        val changed = rows.first().copy(cells = listOf(rows.first().cells.first(), "saved near budget limit"))
        FirestoreStore(client(), namespace, false, 10, { "budget-day" }).use { store ->
            store.load()
            client().use { it.collection("foodrunServers").document(namespace).update("writes", 8L).get() }
            store.commit(mapOf(changed.id to changed))
        }
        client().use { firestore ->
            val metadata = firestore.collection("foodrunServers").document(namespace).get().get()
            assertEquals(10L, metadata.getLong("writes"))
            assertNull(metadata.get("restoreRevision"), "A stale cache must be invalidated when its optional writes cannot fit")
        }
        FirestoreStore(client(), namespace, false, 12_000, { "next-day" }).use { store ->
            assertEquals(changed, store.load()!!.single { it.id == changed.id })
        }
    }

    @Test fun compactRestoreSupportsMultiPartPayloadsAndAtomicShrinking() {
        val namespace = namespace()
        val payload = ByteArray(1_200_000).also(java.util.Random(14)::nextBytes)
        val large = StoredRow("account_records", listOf("large-cache-row", java.util.Base64.getEncoder().encodeToString(payload)))
        val rows = cacheRows().take(128) + large
        FirestoreStore(client(), namespace, true).use { store -> store.load(); store.commit(rows.associateBy { it.id }) }
        FirestoreStore(client(), namespace).use { assertEquals(rows.toSet(), it.load()!!.toSet()) }
        val small = large.copy(cells = listOf("large-cache-row", "small"))
        FirestoreStore(client(), namespace).use { store ->
            assertEquals(large, store.load()!!.single { it.id == large.id })
            store.commit(mapOf(small.id to small))
        }
        FirestoreStore(client(), namespace).use { assertEquals((rows.dropLast(1) + small).toSet(), it.load()!!.toSet()) }
    }
}
