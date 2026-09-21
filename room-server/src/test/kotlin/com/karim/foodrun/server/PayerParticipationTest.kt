package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class PayerParticipationTest {
    @Test fun approvedOrderParticipantsAreReadyWithoutAnotherConfirmation(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member)
        val orderers = f.state().room!!.orderingMembers
        assertEquals(2, orderers.size)
        assertTrue(orderers.all { it.eligible && it.ready })
        f.restart()
        assertTrue(f.state(member).room!!.orderingMembers.all { it.eligible && it.ready })
    }

    @Test fun aViewOnlyGuestNeverEntersTheWheel(): Unit = RoomFixture().use { f ->
        val guest = f.join("Watcher", guest = true)
        assertFalse(guest.room!!.members.single { it.id == guest.memberId }.eligible)
        f.approve(guest)
        assertFalse(f.state(guest).room!!.members.single { it.id == guest.memberId }.eligible)
        assertFalse(f.service.execute(f.command(guest, CommandKind.READY).copy(flag = true, eligible = true)).ok)
        assertFalse(f.service.execute(f.command(guest, CommandKind.PARTICIPATE).copy(flag = true)).ok)
        assertTrue(f.state().room!!.orderingMembers.none { it.id == guest.memberId })
    }

    @Test fun optingOutOnlyExcludesTheWheelAndKeepsFoodAndPersonalReimbursement(): Unit = RoomFixture().use { f ->
        // The fixture opts the member out with READY(eligible=false), then completes ordering.
        val member = f.placed()
        val room = f.state(member).room!!
        val orderer = room.orderingMembers.single { it.id == member.memberId }
        assertTrue(orderer.ready)
        assertFalse(orderer.eligible)
        assertEquals(listOf(f.owner.memberId), room.spin!!.memberIds)
        assertEquals(3000, f.state(member).receipts.single().total)
        f.pay()
        val transfer = f.send(member, CommandKind.DECLARE_TRANSFER) { it.copy(amount = 3000, text = "My own food share") }.room!!.transfers.single()
        f.send(f.owner, CommandKind.CONFIRM_TRANSFER) { it.copy(transferId = transfer.id) }
        assertEquals(0, f.state(member).receipts.single().balance)
    }

    @Test fun anExplicitOptOutSurvivesReconnectApprovalAndRepeatedJoinCommands(): Unit = RoomFixture().use { f ->
        f.send(f.owner, CommandKind.READY) { it.copy(flag = true, eligible = false) }
        val another = f.join(); f.approve(another)
        f.restart()
        f.send(f.owner, CommandKind.PARTICIPATE) { it.copy(flag = true) }
        val owner = f.state().room!!.orderingMembers.single { it.id == f.owner.memberId }
        assertFalse(owner.eligible)
        assertTrue(owner.ready)
    }

    @Test fun skippingAndRejoiningStartsWithTheWheelOnAndReady(): Unit = RoomFixture().use { f ->
        f.send(f.owner, CommandKind.READY) { it.copy(flag = true, eligible = false) }
        f.send(f.owner, CommandKind.PARTICIPATE) { it.copy(flag = false) }
        assertTrue(f.state().room!!.orderingMembers.isEmpty())
        assertFalse(f.state().room!!.members.single().eligible)
        f.send(f.owner, CommandKind.PARTICIPATE) { it.copy(flag = true) }
        val joined = f.state().room!!.orderingMembers.single()
        assertTrue(joined.eligible)
        assertTrue(joined.ready)
    }

    @Test fun theNextOrderEnrollsOnlyTheOwnerUntilPermanentMembersJoinAgain(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member)
        val guest = f.join("Watcher", guest = true); f.approve(guest)
        f.send(f.owner, CommandKind.READY) { it.copy(flag = true, eligible = false) }
        f.send(f.owner, CommandKind.CANCEL) { it.copy(text = "New day") }
        val next = f.send(f.owner, CommandKind.NEXT_ORDER).room!!
        assertEquals(listOf(f.owner.memberId), next.orderingMembers.map { it.id })
        assertTrue(next.orderingMembers.single().eligible)
        assertTrue(next.members.filter { it.id != f.owner.memberId }.all { !it.eligible && !it.participating && !it.ready })
        f.send(member, CommandKind.PARTICIPATE) { it.copy(flag = true) }
        assertTrue(f.state(member).room!!.orderingMembers.single { it.id == member.memberId }.eligible)
        assertFalse(f.state(guest).room!!.members.single { it.id == guest.memberId }.eligible)
    }

    @Test fun decliningAnAssignedDutyPreservesParticipationAndTurnsTheWheelOff(): Unit = RoomFixture().use { f ->
        f.send(f.owner, CommandKind.READY) { it.copy(flag = true, eligible = true) }
        val preparation = f.send(f.owner, CommandKind.PREPARE_SPIN).room!!.preparationId
        val spinning = f.send(f.owner, CommandKind.ACK_SPIN) { it.copy(text = preparation) }.room!!
        f.now = spinning.spin!!.endAt; f.service.tick()
        val declined = f.send(f.owner, CommandKind.DECLINE_DUTY) { it.copy(text = "Unable to place the restaurant order") }.room!!
        assertEquals(RoomPhase.LOBBY, declined.phase)
        assertTrue(declined.members.single().participating)
        assertFalse(declined.members.single().eligible)
        assertTrue(declined.members.single().ready)
        f.restart()
        assertFalse(f.state().room!!.members.single().eligible)
    }

    @Test fun legacyStoredMembershipDoesNotGainConsentDuringLoading(): Unit = RoomFixture().use { f ->
        val legacy = orderJson.decodeFromString<Member>("""{"id":"legacy","name":"Legacy member","approved":true}""")
        assertFalse(legacy.eligible)
        val room = f.db.room(f.owner.room!!.id)!!
        f.db.save(room.copy(members = listOf(legacy.copy(id = f.owner.memberId))))
        f.restart()
        assertFalse(f.state().room!!.orderingMembers.single().eligible)
    }
}
