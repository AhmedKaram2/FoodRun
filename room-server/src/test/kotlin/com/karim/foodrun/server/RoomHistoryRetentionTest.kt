package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class RoomHistoryRetentionTest {
    private class Identity : IdentityProvider {
        override fun signIn(email: String, password: String, register: Boolean) = CloudIdentity(email.substringBefore('@'), "fixture", name = email.substringBefore('@'))
        override fun exchange(idToken: String) = error("Unused")
        override fun refresh(refreshToken: String) = error("Unused")
        override fun profile(identity: CloudIdentity): FoodProfile? = null
        override fun saveProfile(identity: CloudIdentity, profile: FoodProfile) = Unit
        override fun resetPassword(email: String) = Unit
        override fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String) = Unit
    }
    private fun login(f: RoomFixture, uid: String = "fixture-owner") = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY,
        identity = IdentityRequest(IdentityAction.SIGN_IN, email = "$uid@example.test", password = "fixture-password")))
    private fun room(f: RoomFixture, at: Long, users: List<String> = listOf("fixture-owner"), phase: RoomPhase = RoomPhase.CANCELLED): Room {
        val id = f.id()
        val members = users.map { Member(AccountMemberships.memberId(id, it), it, approved = true) }
        val room = requireNotNull(f.db.room(f.owner.room!!.id)).copy(id = id, code = (100000 + f.db.allRooms().size).toString(), name = "Room $at",
            ownerId = members.first().id, members = members, phase = phase, createdAt = at - 1, updatedAt = at)
        f.db.save(room)
        users.zip(members).forEach { (uid, member) ->
            val token = f.id() + f.id()
            f.db.addSession(RoomService.hash(token), room.id, member.id)
            AccountMemberships(f.db).save(uid, room, member.id, token)
        }
        return room
    }

    @Test fun homeKeepsSixCompletedRoomsAndEveryActiveOrUnsettledRoom() = RoomFixture(Identity()).use { f ->
        val completed = (1..9).map { room(f, it * 1000L) }
        val active = room(f, 50, phase = RoomPhase.LOBBY)
        val unpaid = room(f, 40, phase = RoomPhase.ARCHIVED).copy(autoArchivedAt = 1, autoArchiveFrom = RoomPhase.PLACED)
        f.db.save(unpaid)
        val signed = login(f)
        val home = signed.home!!
        assertEquals(completed.takeLast(6).map { it.id }.toSet() + setOf(active.id, unpaid.id, f.owner.room!!.id), home.rooms.map { it.roomId }.toSet())
        assertEquals(completed.take(3).map { it.id }.toSet(), home.deletedRoomIds)
        assertTrue(completed.all { f.db.room(it.id) != null }, "Filtering one account must not delete shared data")
    }

    @Test fun sharedRoomStaysForAnotherUsersSixRoomHistory() = RoomFixture(Identity()).use { f ->
        val shared = room(f, 10, listOf("fixture-owner", "other"))
        repeat(7) { room(f, 100L + it) }
        assertTrue(shared.id in login(f).home!!.deletedRoomIds)
        assertEquals(listOf(shared.id), login(f, "other").home!!.rooms.map { it.roomId })
        val retention = RoomHistoryRetention(f.db) { f.now }
        repeat(20) { retention.cleanupStep() }
        assertNotNull(f.db.room(shared.id))
        assertEquals(listOf(shared.id), login(f, "other").home!!.rooms.map { it.roomId })
    }

    @Test fun limitCountsRoomsRatherThanOrdersAndUsesCompletionTime() = RoomFixture(Identity()).use { f ->
        val olderCreatedButRecentlyCompleted = room(f, 9999).copy(createdAt = 1)
        f.db.save(olderCreatedButRecentlyCompleted)
        repeat(13) { index -> f.db.archive(olderCreatedButRecentlyCompleted.copy(orderNumber = index + 1L)) }
        val others = (1..6).map { room(f, it * 1000L).copy(createdAt = 50_000L + it).also(f.db::save) }
        val home = login(f).home!!
        assertTrue(olderCreatedButRecentlyCompleted.id in home.rooms.map { it.roomId })
        assertEquals(setOf(others.first().id), home.deletedRoomIds)
        assertEquals(13, f.db.history(olderCreatedButRecentlyCompleted.id, limit = 20).size)
    }

    @Test fun pendingOlderOrderAndIndependentWalletDebtAreNeverHiddenOrCleaned() = RoomFixture(Identity()).use { f ->
        val previousDebt = room(f, 10)
        f.db.archive(previousDebt.copy(phase = RoomPhase.FULFILLED, restaurantPaid = false))
        val walletDebt = room(f, 20)
        val payment = WalletPayment("owing", "fixture-owner", "Owner", "holder", "Holder", "recipient", "Recipient", walletDebt.id, walletDebt.name,
            1, walletDebt.ownerId, 100, "AED", WalletPaymentStatus.OWING, createdAt = f.now)
        f.db.putRecord("wallet:payment:${payment.id}", orderJson.encodeToString(payment))
        repeat(8) { room(f, 100L + it) }
        val home = login(f).home!!
        assertTrue(setOf(previousDebt.id, walletDebt.id).all { id -> home.rooms.any { it.roomId == id } && id !in home.deletedRoomIds })
        repeat(30) { RoomHistoryRetention(f.db) { f.now }.cleanupStep() }
        assertNotNull(f.db.room(previousDebt.id)); assertNotNull(f.db.room(walletDebt.id))
        assertNotNull(f.db.record("wallet:payment:owing"))
    }

    @Test fun cleanupPreservesSpendingWalletRecordsAndPrivateRequestRecovery() = RoomFixture(Identity()).use { f ->
        val old = room(f, 10).let { room -> room.copy(phase = RoomPhase.ARCHIVED, payerId = room.ownerId, restaurantPaid = true,
            account = f.account, carts = listOf(MemberCart(room.ownerId, lines = listOf(CartLine("own", "meal", 2)))),
            audit = listOf(AuditEntry("paid", room.ownerId, "PAY_RESTAURANT", "", f.now))) }
        f.db.save(old)
        f.db.archive(old.copy(orderNumber = 2))
        val owner = old.ownerId
        val commandId = f.id()
        val digest = "original-digest"
        f.db.record(commandId, digest, RoomReply(room = old, memberId = owner))
        val topUp = WalletTopUp("credit", "fixture-owner", "Owner", "holder", "Holder", 5000, "AED", f.account, "", status = WalletStatus.CONFIRMED, createdAt = f.now)
        f.db.putRecord("wallet:top-up:credit", orderJson.encodeToString(topUp))
        val wallet = WalletService.snapshot(f.db, "fixture-owner")
        SpendingService(f.db) { f.now }.record(old)
        SpendingService(f.db) { f.now }.record(old.copy(orderNumber = 2))
        val before = SpendingService(f.db) { f.now }.summary("fixture-owner")
        assertEquals(400, before.periods.first().total)
        repeat(6) { room(f, 100L + it) }
        repeat(20) { RoomHistoryRetention(f.db) { f.now }.cleanupStep() }
        assertNull(f.db.room(old.id)); assertTrue(f.db.history(old.id).isEmpty())
        assertEquals(wallet, WalletService.snapshot(f.db, "fixture-owner"))
        assertEquals(before, SpendingService(f.db) { f.now }.summary("fixture-owner"))
        assertNotNull(f.db.recordedReply(commandId))
        assertEquals("REMOVED", f.db.previous(commandId, digest)!!.code)
        assertFailsWith<IllegalArgumentException> { f.db.previous(commandId, "changed-digest") }
        val signed = login(f)
        val recovered = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.COMMAND_STATUS, text = commandId, identityToken = signed.identityToken))
        assertEquals("REMOVED", recovered.code); assertNotNull(recovered.home); assertNull(recovered.room)
        val other = login(f, "outsider")
        assertFalse(f.service.execute(RoomCommand(commandId = f.id(), kind = CommandKind.COMMAND_STATUS, text = commandId, identityToken = other.identityToken)).ok)
    }

    @Test fun deniedCleanupBudgetRollsBackWithoutDisablingStorageOrLosingHistory() {
        val store = object : DurableStore {
            var rows: MutableMap<String, StoredRow>? = null
            override fun load() = rows?.values?.toList()
            override fun commit(changes: Map<String, StoredRow?>) {
                rows = (rows ?: mutableMapOf()).toMutableMap().also { next -> changes.forEach { (id, row) -> if (row == null) next.remove(id) else next[id] = row } }
            }
        }
        RoomFixture(Identity(), durableFactory = { store }).use { f ->
            val old = room(f, 10)
            val command = f.id()
            f.db.record(command, "digest", RoomReply(room = old, memberId = old.ownerId))
            repeat(6) { room(f, 100L + it) }
            assertNull(RoomHistoryRetention(f.db) { f.now }.cleanupStep())
            assertTrue(f.db.available); assertNotNull(f.db.room(old.id))
            assertEquals(old.id, f.db.recordedReply(command)!!.room!!.id)
            f.restart(clearCache = true)
            assertNotNull(f.db.room(old.id)); assertEquals(6, login(f).home!!.rooms.count { it.phase == RoomPhase.CANCELLED })
        }
    }

    @Test fun backgroundCleanupIsOffUntilAnExplicitVerifiedCopyConfiguration() = RoomFixture(Identity()).use { f ->
        val old = room(f, 10)
        repeat(6) { room(f, 100L + it) }
        f.service.tick()
        assertNotNull(f.db.room(old.id))
        val enabled = RoomService(f.db, { f.now }, historyCleanupEnabled = true)
        enabled.tick()
        val afterOne = f.db.allRooms().size
        repeat(10) { enabled.tick() }
        assertEquals(afterOne, f.db.allRooms().size, "Maintenance must not run on every room tick")
    }
}
