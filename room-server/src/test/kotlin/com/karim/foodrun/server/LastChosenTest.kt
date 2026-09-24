package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class LastChosenTest {
    @Test fun completedSelectionSurvivesTheNextOrderAndRestart() = RoomFixture().use { f ->
        val member = f.join(); f.approve(member); f.start(member)
        assertEquals(f.owner.memberId, f.state().room!!.lastChosenMemberId)
        assertEquals("Owner", f.state().room!!.lastChosenName)
        f.send(f.owner, CommandKind.CANCEL) { it.copy(text = "Next meal") }
        f.send(f.owner, CommandKind.NEXT_ORDER)
        f.restart()
        assertEquals(f.owner.memberId, f.state().room!!.lastChosenMemberId)
        f.send(member, CommandKind.PARTICIPATE) { it.copy(flag = true) }
        f.send(member, CommandKind.READY) { it.copy(flag = true, eligible = true) }
        val spin = f.send(f.owner, CommandKind.PREPARE_SPIN).room!!.spin!!
        assertEquals(20, spin.weights[spin.memberIds.indexOf(f.owner.memberId)])
        assertEquals(80, spin.weights[spin.memberIds.indexOf(member.memberId)])
    }

    @Test fun directChoiceAndOlderRoomHistoryAreRemembered() = RoomFixture().use { f ->
        val member = f.join(); f.approve(member)
        f.send(f.owner, CommandKind.SELECT_PAYER) { it.copy(memberId = member.memberId) }
        assertEquals(member.memberId, f.state().room!!.lastChosenMemberId)
        f.send(f.owner, CommandKind.CANCEL) { it.copy(text = "Next meal") }
        f.send(f.owner, CommandKind.NEXT_ORDER)
        val current = f.state().room!!
        // Simulate a room saved by an older version without the persisted choice.
        f.db.save(current.copy(lastChosenMemberId = null, lastChosenName = ""))
        f.restart()
        assertEquals(member.memberId, f.state().room!!.lastChosenMemberId)
        assertEquals("Member", f.state().room!!.lastChosenName)
    }
}
