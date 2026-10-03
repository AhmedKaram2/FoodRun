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
        assertFalse(joined.room!!.members.single { it.id == joined.memberId }.approved)
        assertFalse(joined.room!!.members.single { it.id == joined.memberId }.participating)
        val spin = f.send(f.owner, CommandKind.ACK_SPIN) { it.copy(text = preparation) }.room!!.spin!!
        assertEquals(listOf(f.owner.memberId), spin.memberIds)
        f.approve(joined)
        assertTrue(f.state(joined).room!!.orderingMembers.any { it.id == joined.memberId })
        assertEquals(spin, f.state().room!!.spin)
    }

    @Test fun joiningAfterPlacementPreservesBillsAndCanParticipateNextTime(): Unit = RoomFixture().use { f ->
        val member = f.placed()
        val receipts = f.state().receipts
        val newcomer = f.join("Newcomer")
        assertFalse(newcomer.room!!.members.single { it.id == newcomer.memberId }.approved)
        assertFalse(newcomer.room!!.members.single { it.id == newcomer.memberId }.participating)
        assertEquals(receipts, f.state().receipts)
        assertTrue(newcomer.receipts.isEmpty())
        assertNull(newcomer.room!!.account)
        f.approve(newcomer)
        assertFalse(f.state(newcomer).room!!.members.single { it.id == newcomer.memberId }.participating)
        assertEquals(receipts, f.state().receipts)
        f.pay()
        val transfer = f.send(member, CommandKind.DECLARE_TRANSFER) { it.copy(amount = 3000, text = "Paid") }.room!!.transfers.single()
        f.send(f.owner, CommandKind.CONFIRM_TRANSFER) { it.copy(transferId = transfer.id) }
        f.send(f.owner, CommandKind.FULFILL)
        f.send(f.owner, CommandKind.ARCHIVE)
        f.send(f.owner, CommandKind.NEXT_ORDER)
        f.send(newcomer, CommandKind.PARTICIPATE) { it.copy(flag = true) }
        assertTrue(f.state(newcomer).room!!.orderingMembers.any { it.id == newcomer.memberId })
    }

    @Test fun restartPreservesPendingApprovalWithoutRewritingHistoryOrRemovedMembers() {
        RoomPhase.entries.forEach { phase -> RoomFixture().use { f ->
            val member = f.join()
            val raw = f.db.room(f.owner.room!!.id)!!
            val old = raw.copy(phase = phase, members = raw.members.map {
                if (it.id == member.memberId) it.copy(approved = false, ready = false) else it
            } + Member("removed", "Removed", removed = true))
            f.db.save(old); f.db.archive(old)
            f.restart()
            val room = f.state(member).room!!
            val pending = room.members.single { it.id == member.memberId }
            assertFalse(pending.approved, phase.name)
            assertEquals(old.members.single { it.id == member.memberId }, pending, phase.name)
            assertTrue(f.db.room(room.id)!!.members.single { it.id == "removed" }.removed)
            assertFalse(f.db.history(room.id).single().members.single { it.id == member.memberId }.approved)
            val revision = room.revision
            f.restart()
            assertEquals(revision, f.state(member).room!!.revision)
        } }
    }

    @Test fun lateJoinRequiresCreatorOrSelectedPersonAndSurvivesRestart(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.start(member)
        val late = f.join("Late member")
        val revision = f.state().room!!.quoteRevision
        assertFalse(late.room!!.members.single().approved)
        assertTrue(late.room!!.receivingAccounts.isEmpty())
        assertTrue(late.receipts.isEmpty())
        assertEquals(revision, f.state().room!!.quoteRevision)
        assertFalse(f.service.execute(f.command(late, CommandKind.CART).copy(expectedRevision = 0, cart = MemberCart(late.memberId))).ok)
        assertFalse(f.service.execute(f.command(late, CommandKind.APPROVE).copy(memberId = late.memberId)).ok)
        assertFalse(f.service.execute(f.command(member, CommandKind.APPROVE).copy(memberId = late.memberId)).ok)
        f.restart()
        assertFalse(f.state(late).room!!.members.single().approved)
        f.send(f.owner, CommandKind.APPROVE_LATE_JOIN) { it.copy(memberId = late.memberId) }
        assertTrue(f.state(late).room!!.orderingMembers.any { it.id == late.memberId })
        assertEquals(revision + 1, f.state().room!!.quoteRevision)
        f.cart(late, 2)
        val denied = f.join("Declined member")
        f.send(f.owner, CommandKind.REMOVE) { it.copy(memberId = denied.memberId, text = "Join request declined") }
        assertFails { f.service.snapshot(f.owner.room!!.id, denied.token) }
    }

    @Test fun selectedPersonCanApproveBeforeAcceptingDuty(): Unit = RoomFixture().use { f ->
        val selected = f.join("Selected")
        f.send(f.owner, CommandKind.READY) { it.copy(flag = true, eligible = false) }
        val round = f.send(f.owner, CommandKind.PREPARE_SPIN).room!!.spin!!
        f.now = round.endAt; f.service.tick()
        val late = f.join("Late")
        f.send(selected, CommandKind.APPROVE) { it.copy(memberId = late.memberId) }
        assertTrue(f.state(late).room!!.orderingMembers.any { it.id == late.memberId })
        assertEquals(round, f.state().room!!.spin)
        f.send(selected, CommandKind.ACCEPT_DUTY)
        val nextLate = f.join("Next late")
        f.send(selected, CommandKind.APPROVE_LATE_JOIN) { it.copy(memberId = nextLate.memberId) }
        assertTrue(f.state(nextLate).room!!.members.single { it.id == nextLate.memberId }.approved)
    }

    @Test fun approvedLateJoinReopensReviewAndMustSubmitBeforePlacement(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.start(member)
        f.send(f.owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = f.account) }
        f.cart(f.owner, 1); f.cart(member, 1)
        f.send(f.owner, CommandKind.REVIEW)
        val late = f.join("Late")
        f.approve(late)
        assertEquals(RoomPhase.COLLECTING, f.state().room!!.phase)
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.PLACE)).ok)
        f.cart(late, 1)
        assertTrue(f.state().progress!!.canReview)
    }

    @Test fun decliningSelectionDoesNotReenableAutomaticJoining(): Unit = RoomFixture().use { f ->
        f.join()
        val round = f.send(f.owner, CommandKind.PREPARE_SPIN).room!!.spin!!
        f.now = round.endAt; f.service.tick()
        f.send(f.owner, CommandKind.DECLINE_DUTY) { it.copy(text = "Unavailable") }
        assertEquals(RoomPhase.LOBBY, f.state().room!!.phase)
        val late = f.join("Late after decline")
        assertFalse(f.state(late).room!!.members.single().approved)
        f.approve(late)
        assertTrue(f.state(late).room!!.orderingMembers.any { it.id == late.memberId })
    }
}
