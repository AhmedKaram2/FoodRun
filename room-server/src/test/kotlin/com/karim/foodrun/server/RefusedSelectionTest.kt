package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class RefusedSelectionTest {
    private fun refused(f:RoomFixture): Pair<RoomReply,RoomReply> {
        val selected=f.join("Selected")
        val other=f.join("Other")
        f.send(f.owner,CommandKind.READY) { it.copy(flag=true,eligible=false) }
        val spin=f.send(f.owner,CommandKind.PREPARE_SPIN).room!!.spin!!
        assertEquals(selected.memberId,spin.winnerId)
        f.now=spin.endAt;f.service.tick()
        f.send(selected,CommandKind.DECLINE_DUTY)
        assertEquals(RoomPhase.LOBBY,f.state().room!!.phase)
        f.send(f.owner,CommandKind.READY) { it.copy(flag=true,eligible=true) }
        return selected to other
    }
    @Test fun retryExcludesTheRefusedPersonWhileKeepingTheirRoomMembership() = RoomFixture().use { f ->
        val (selected,other)=refused(f)
        val retry=f.send(f.owner,CommandKind.PREPARE_SPIN).room!!
        assertFalse(selected.memberId in retry.spin!!.memberIds)
        assertTrue(other.memberId in retry.spin!!.memberIds)
        assertFalse(retry.members.single { it.id == selected.memberId }.removed)
        assertEquals(f.now+3000,retry.spin!!.startAt)
    }
    @Test fun onlyTheOwnerCanRemoveARefusedPersonWithoutANote() = RoomFixture().use { f ->
        val (selected,other)=refused(f)
        assertFalse(f.service.execute(f.command(other,CommandKind.REMOVE).copy(memberId=selected.memberId)).ok)
        assertFalse(f.service.execute(f.command(f.owner,CommandKind.REMOVE).copy(memberId=other.memberId)).ok)
        val removed=f.send(f.owner,CommandKind.REMOVE) { it.copy(memberId=selected.memberId) }.room!!
        assertTrue(removed.members.single { it.id == selected.memberId }.removed)
        assertEquals("Declined ordering duty.",f.db.room(removed.id)!!.audit.last().reason)
        val retry=f.send(f.owner,CommandKind.PREPARE_SPIN).room!!
        assertFalse(selected.memberId in retry.spin!!.memberIds)
        assertEquals(2,retry.activeMembers.size)
    }
}
