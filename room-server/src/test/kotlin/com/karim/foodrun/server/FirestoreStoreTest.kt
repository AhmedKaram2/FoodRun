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
                expectedOrderNumber = 1, expectedRevision = created.room!!.revision, memberId = membership.memberId, amount = 2000, text = "Cash")
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
}
