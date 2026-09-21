package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import java.nio.file.Files
import kotlin.test.*

class DurableStorageTest {
    internal class MemoryStore : DurableStore {
        var rows: MutableMap<String, StoredRow>? = null
        var commits = 0
        var failBefore = false
        var failAfter = false
        override fun load() = rows?.values?.toList()
        override fun commit(changes: Map<String, StoredRow?>) {
            if (failBefore) throw StorageUnavailable("Offline")
            val next = (rows ?: mutableMapOf()).toMutableMap()
            changes.forEach { (id, row) -> if (row == null) next.remove(id) else next[id] = row }
            rows = next; commits++
            if (failAfter) throw StorageUnavailable("Lost response")
        }
    }
    @Test fun blankDiskRestoresWalletHistorySessionsAndIdempotency() {
        val store = MemoryStore()
        RoomFixture(durableFactory = { store }).use { f ->
            val member = f.placed(); f.pay()
            val command = f.command(member, CommandKind.DECLARE_TRANSFER).copy(amount = 3000, text = "Paid")
            val transfer = f.execute(command).room!!.transfers.single()
            f.send(f.owner, CommandKind.CONFIRM_TRANSFER) { it.copy(transferId = transfer.id) }
            f.send(f.owner, CommandKind.FULFILL); f.send(f.owner, CommandKind.ARCHIVE)
            val archived = f.state().room!!
            f.send(f.owner, CommandKind.NEXT_ORDER)
            f.restart(clearCache = true)
            assertEquals(2, f.state(member).room!!.orderNumber)
            assertEquals(1, f.state(member).history.size)
            assertEquals(0, f.state(member).history.single().receipts.single().balance)
            assertEquals(transfer.id, f.execute(command).room!!.transfers.single().id)
            assertNull(f.db.pendingCloud())
            val before = store.commits
            repeat(5) { f.service.tick(); f.state(member) }
            assertEquals(before, store.commits, "Idle ticks and snapshots must not consume Firestore writes")
            f.db.deleteHistory(archived.id, 1)
            f.restart(clearCache = true)
            assertTrue(f.state(member).history.isEmpty())
            assertEquals(setOf(1L), f.db.deletedHistory(archived.id))
        }
    }
    @Test fun rejectedWriteCannotBeAcknowledgedOrReadAsSaved() {
        val store = MemoryStore()
        RoomFixture(durableFactory = { store }).use { f ->
            val before = f.state().room!!
            store.failBefore = true
            val command = f.command(f.owner, CommandKind.READY).copy(flag = true)
            assertFailsWith<StorageUnavailable> { f.service.execute(command) }
            assertFalse(f.db.available)
            assertFailsWith<StorageUnavailable> { f.state() }
            store.failBefore = false
            f.restart(clearCache = true)
            assertEquals(before.revision, f.state().room!!.revision)
            assertTrue(f.execute(command).ok)
        }
    }
    @Test fun ambiguousCommitRestoresRetryRecordWithoutRepeatingPayment() {
        val store = MemoryStore()
        RoomFixture(durableFactory = { store }).use { f ->
            val member = f.placed(); f.pay()
            val command = f.command(member, CommandKind.DECLARE_TRANSFER).copy(amount = 3000, text = "Paid")
            store.failAfter = true
            assertFailsWith<StorageUnavailable> { f.service.execute(command) }
            store.failAfter = false
            f.restart(clearCache = true)
            val retry = f.execute(command)
            assertEquals(1, retry.room!!.transfers.size)
            assertEquals(1, f.state().room!!.transfers.size)
        }
    }
    @Test fun nestedTransactionsRollbackAllRecordsAndPreserveDeletion() {
        val store = MemoryStore()
        val directory = Files.createTempDirectory("durable-records-").toFile()
        try {
            RoomDatabase(directory, store).use { db ->
                val before = store.commits
                assertFailsWith<IllegalArgumentException> { db.transaction { db.putRecord("a", "1"); db.transaction { db.putRecord("b", "2") }; require(false) } }
                assertNull(db.record("a")); assertNull(db.record("b")); assertEquals(before, store.commits)
                db.transaction { db.putRecord("profile:user", "profile"); db.putRecord("membership:user:room", "member"); db.putRecord("identity:hash", "session") }
                assertEquals(before + 1, store.commits)
                db.deleteRecord("identity:hash")
            }
            directory.deleteRecursively()
            RoomDatabase(directory, store).use { db ->
                assertEquals("profile", db.record("profile:user")); assertEquals("member", db.record("membership:user:room")); assertNull(db.record("identity:hash"))
            }
        } finally { directory.deleteRecursively() }
    }
}
