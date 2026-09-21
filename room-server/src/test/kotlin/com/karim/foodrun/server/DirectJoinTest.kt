package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class DirectJoinTest {
    @Test fun codeAdmitsMemberAndAllowsFoodImmediately(): Unit = RoomFixture().use { f ->
        val member = f.join()
        val joined = member.room!!.members.single { it.id == member.memberId }
        assertTrue(joined.approved && joined.participating && joined.ready && joined.eligible)
        assertEquals(f.restaurant, member.room!!.restaurant)
        f.cart(member, 2)
        f.restart()
        assertEquals(2, f.state(member).room!!.carts.single { it.memberId == member.memberId }.lines.single().quantity)
    }

    @Test fun joiningDuringSelectionDoesNotChangeThePreparedParticipants(): Unit = RoomFixture().use { f ->
        val preparation = f.send(f.owner, CommandKind.PREPARE_SPIN).room!!.preparationId
        val joined = f.join()
        assertTrue(joined.room!!.members.single { it.id == joined.memberId }.approved)
        assertFalse(joined.room!!.members.single { it.id == joined.memberId }.participating)
        val spin = f.send(f.owner, CommandKind.ACK_SPIN) { it.copy(text = preparation) }.room!!.spin!!
        assertEquals(listOf(f.owner.memberId), spin.memberIds)
    }

    @Test fun joiningAfterPlacementPreservesBillsAndCanParticipateNextTime(): Unit = RoomFixture().use { f ->
        val member = f.placed()
        val receipts = f.state().receipts
        val newcomer = f.join("Newcomer")
        assertTrue(newcomer.room!!.members.single { it.id == newcomer.memberId }.approved)
        assertFalse(newcomer.room!!.members.single { it.id == newcomer.memberId }.participating)
        assertEquals(receipts, f.state().receipts)
        assertTrue(newcomer.receipts.isEmpty())
        assertNull(newcomer.room!!.account)
        f.pay()
        val transfer = f.send(member, CommandKind.DECLARE_TRANSFER) { it.copy(amount = 3000, text = "Paid") }.room!!.transfers.single()
        f.send(f.owner, CommandKind.CONFIRM_TRANSFER) { it.copy(transferId = transfer.id) }
        f.send(f.owner, CommandKind.FULFILL)
        f.send(f.owner, CommandKind.ARCHIVE)
        f.send(f.owner, CommandKind.NEXT_ORDER)
        f.send(newcomer, CommandKind.PARTICIPATE) { it.copy(flag = true) }
        assertTrue(f.state(newcomer).room!!.orderingMembers.any { it.id == newcomer.memberId })
    }

    @Test fun restartAdmitsLegacyPendingMembersWithoutRewritingHistoryOrRemovedMembers() {
        RoomPhase.entries.forEach { phase -> RoomFixture().use { f ->
            val member = f.join()
            val raw = f.db.room(f.owner.room!!.id)!!
            val old = raw.copy(phase = phase, members = raw.members.map {
                if (it.id == member.memberId) it.copy(approved = false, ready = false) else it
            } + Member("removed", "Removed", removed = true))
            f.db.save(old); f.db.archive(old)
            f.restart()
            val room = f.state(member).room!!
            val admitted = room.members.single { it.id == member.memberId }
            assertTrue(admitted.approved, phase.name)
            assertEquals(phase in listOf(RoomPhase.LOBBY, RoomPhase.COLLECTING), admitted.participating, phase.name)
            assertTrue(f.db.room(room.id)!!.members.single { it.id == "removed" }.removed)
            assertFalse(f.db.history(room.id).single().members.single { it.id == member.memberId }.approved)
            val revision = room.revision
            f.restart()
            assertEquals(revision, f.state(member).room!!.revision)
        } }
    }
}
