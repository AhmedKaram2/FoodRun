package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlinx.serialization.json.jsonArray
import kotlin.test.*

class SelectionHistoryTest {
    private fun anotherRoom(f: RoomFixture, userId: String, otherId: String = "new-user"): RoomReply {
        val created = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.CREATE,
            name = "Same name", text = "Other room", restaurant = f.restaurant))
        val room = created.room!!
        val other = Member(f.id(), "Same name", approved = true, eligible = true, ready = true)
        f.db.save(room.copy(members = room.members + other))
        f.db.putRecord("member-user:${room.id}:${created.memberId}", userId)
        f.db.putRecord("member-user:${room.id}:${other.id}", otherId)
        return created
    }

    private fun spin(f: RoomFixture, owner: RoomReply): RoomReply {
        val room = f.db.room(owner.room!!.id)!!
        return f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.PREPARE_SPIN,
            roomId = room.id, token = owner.token, expectedRevision = room.revision, expectedOrderNumber = room.orderNumber))
    }

    @Test fun anotherRoomsWinnerHasLowerChanceAndPublicWeightsMatchTheRandomDraw() = RoomFixture().use { f ->
        f.db.putRecord("member-user:${f.owner.room!!.id}:${f.owner.memberId}", "repeat-user")
        val previous = f.command(f.owner, CommandKind.SELECT_PAYER).copy(memberId = f.owner.memberId)
        f.execute(previous)
        f.execute(previous) // A retried request must not count as another selection.
        val next = anotherRoom(f, "repeat-user")
        val result = spin(f, next).room!!.spin!!
        assertEquals(listOf(3, 12), result.weights)
        assertEquals(next.memberId, result.winnerId) // The fixture's secure-random ticket is zero.
        // One previous choice plus this draw; the replay added nothing.
        assertEquals(2, orderJson.parseToJsonElement(f.db.record("wheel-history:repeat-user")!!).jsonArray.size)
    }

    @Test fun repeatedSelectionsSurviveRoomDeletionAndBlankDiskCloudRestore() {
        val store = DurableStorageTest.MemoryStore()
        RoomFixture(durableFactory = { store }).use { f ->
            val first = anotherRoom(f, "repeat-user")
            spin(f, first)
            val second = anotherRoom(f, "repeat-user")
            assertEquals(listOf(3, 12), spin(f, second).room!!.spin!!.weights)
            f.db.deleteRoom(first.room!!.id)
            f.db.deleteRoom(second.room!!.id)
            f.restart(clearCache = true)
            val third = anotherRoom(f, "repeat-user")
            assertEquals(listOf(1, 12), spin(f, third).room!!.spin!!.weights)
        }
    }

    @Test fun backfillIncludesArchivedOrdersAndAccountAliasesWithoutDoubleCounting() = RoomFixture().use { f ->
        val old = f.owner.room!!.copy(phase = RoomPhase.ARCHIVED, spin = SpinRound("legacy", listOf(f.owner.memberId),
            f.owner.memberId, f.now), payerId = f.owner.memberId, updatedAt = f.now)
        f.db.save(old); f.db.archive(old)
        f.db.putRecord("member-user:${old.id}:${f.owner.memberId}", "old-login")
        f.db.deleteRecord("migration:wheel-history-v1")
        f.restart()
        f.db.putRecord("account-alias:old-login", "current-login")
        val next = anotherRoom(f, "current-login")
        assertEquals(listOf(3, 12), spin(f, next).room!!.spin!!.weights)
    }

    @Test fun automaticTimerUsesHistoryAndUnrelatedAccountsWithMatchingNamesStayIndependent() = RoomFixture().use { f ->
        f.db.putRecord("member-user:${f.owner.room!!.id}:${f.owner.memberId}", "repeat-user")
        f.send(f.owner, CommandKind.SELECT_PAYER) { it.copy(memberId = f.owner.memberId) }
        val next = anotherRoom(f, "repeat-user")
        val room = f.db.room(next.room!!.id)!!
        f.db.save(room.copy(joinDeadlineAt = f.now))
        f.restart()
        f.service.tick()
        assertEquals(listOf(3, 12), f.db.room(room.id)!!.spin!!.weights)
        val unrelated = anotherRoom(f, "different-user", "another-new-user")
        assertEquals(listOf(12, 12), spin(f, unrelated).room!!.spin!!.weights)
    }
}
