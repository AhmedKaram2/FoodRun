package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class RoomExpiryServiceTest {
    private fun expire(f: RoomFixture) {
        f.now = f.owner.room!!.createdAt + RoomExpiry.LIFETIME_MS
        f.service.tick()
    }
    @Test fun archivesAtCreationBoundaryWithoutLosingMembershipAndCatchesUpAfterRestart() = RoomFixture().use { f ->
        val member = f.join()
        f.now = f.owner.room!!.createdAt + RoomExpiry.LIFETIME_MS - 1
        assertEquals(RoomPhase.LOBBY, f.state().room!!.phase)
        f.now++
        f.restart()
        val archived = f.state(member).room!!
        assertEquals(RoomPhase.ARCHIVED, archived.phase)
        assertEquals(RoomPhase.LOBBY, archived.autoArchiveFrom)
        assertEquals(f.now, archived.autoArchivedAt)
        assertEquals(f.owner.room!!.code, archived.code)
        assertTrue(archived.members.any { it.id == member.memberId })
        val revision = archived.revision
        f.service.tick(); f.restart()
        assertEquals(revision, f.state(member).room!!.revision)
        f.send(f.owner, CommandKind.NEXT_ORDER)
        assertEquals(f.now, f.state().room!!.orderCreatedAt)
        f.now += RoomExpiry.LIFETIME_MS - 1
        assertEquals(RoomPhase.LOBBY, f.state().room!!.phase)
        f.now++
        assertEquals(RoomPhase.ARCHIVED, f.state().room!!.phase)
    }
    @Test fun unpaidBalancesAndPendingClaimsSurviveAndRemainPayableAfterArchiving() = RoomFixture().use { f ->
        val member = f.placed(); f.pay()
        val declared = f.send(member, CommandKind.DECLARE_TRANSFER) { it.copy(amount = 500, text = "Cash") }
        val before = f.state(member).receipts.single()
        expire(f)
        val archived = f.state(member)
        assertEquals(RoomPhase.ARCHIVED, archived.room!!.phase)
        assertEquals(before, archived.receipts.single())
        assertEquals(declared.room!!.transfers, archived.room!!.transfers)
        assertTrue(archived.room!!.paymentsPending)
        assertTrue(archived.room!!.shouldStayLive)
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.NEXT_ORDER)).ok)
        assertFalse(f.service.execute(f.command(member, CommandKind.CART).copy(cart = MemberCart(member.memberId))).ok)
        f.send(f.owner, CommandKind.CONFIRM_TRANSFER) { it.copy(transferId = declared.room!!.transfers.single().id) }
        assertEquals(before.balance - 500, f.state(member).receipts.single().balance)
        f.send(f.owner, CommandKind.RECORD_PAYMENT) { it.copy(memberId = member.memberId, amount = before.balance - 500, text = "Remaining cash") }
        assertEquals(0, f.state(member).receipts.single().balance)
        assertFalse(f.state().room!!.paymentsPending)
        assertFalse(f.state().room!!.shouldStayLive)
        f.send(f.owner, CommandKind.NEXT_ORDER)
        assertEquals(0, f.state(member).history.single().receipts.single().balance)
    }
    @Test fun unrecordedRestaurantPaymentRemainsRequiredAndCanBeRecordedAfterArchiving() = RoomFixture().use { f ->
        val member = f.placed()
        expire(f)
        assertFalse(f.state().room!!.restaurantPaid)
        assertFalse(f.service.execute(f.command(member, CommandKind.DECLARE_TRANSFER).copy(amount = 500, text = "Cash")).ok)
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.NEXT_ORDER)).ok)
        f.pay()
        val balance = f.state(member).receipts.single().balance
        val claim = f.send(member, CommandKind.DECLARE_TRANSFER) { it.copy(amount = balance, text = "Cash") }
        f.send(f.owner, CommandKind.CONFIRM_TRANSFER) { it.copy(transferId = claim.room!!.transfers.single().id) }
        assertEquals(0, f.state(member).receipts.single().balance)
    }
    @Test fun refundsRemainDueAndOnlyTheRecipientCanConfirmAfterArchiving() = RoomFixture().use { f ->
        val member = f.placed(); f.pay()
        f.send(f.owner, CommandKind.RECORD_PAYMENT) { it.copy(memberId = member.memberId, amount = 3000, text = "Cash") }
        f.send(f.owner, CommandKind.ADJUST_BILL) { it.copy(amount = -1000, text = "Restaurant discount") }; f.pay()
        val refund = -f.state(member).receipts.single().balance
        assertTrue(refund > 0)
        expire(f)
        val claim = f.send(f.owner, CommandKind.DECLARE_REFUND) { it.copy(memberId = member.memberId, amount = refund, text = "Cash refund") }
        val transfer = claim.room!!.transfers.last()
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.CONFIRM_REFUND).copy(transferId = transfer.id)).ok)
        f.send(member, CommandKind.CONFIRM_REFUND) { it.copy(transferId = transfer.id) }
        assertEquals(0, f.state(member).receipts.single().balance)
    }
    @Test fun pendingWheelPaymentMustBeResolvedBeforeTheArchivedOrderIsReplaced() = RoomFixture().use { f ->
        val member = f.join()
        val request = f.send(member, CommandKind.REQUEST_WHEEL_PROTECTION) { it.copy(text = "EXCLUDE") }.room!!.wheelProtections.single()
        f.send(f.owner, CommandKind.REVIEW_WHEEL_PROTECTION) { it.copy(transferId = request.id, flag = true) }
        f.send(member, CommandKind.DECLARE_WHEEL_PAYMENT) { it.copy(transferId = request.id, amount = request.amount, text = "Cash") }
        expire(f)
        assertTrue(f.state().room!!.paymentsPending)
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.NEXT_ORDER)).ok)
        f.send(f.owner, CommandKind.CONFIRM_WHEEL_PAYMENT) { it.copy(transferId = request.id, amount = request.amount, flag = false) }
        f.send(f.owner, CommandKind.NEXT_ORDER)
        assertEquals(WheelProtectionStatus.REJECTED, f.db.history(f.owner.room!!.id).single().wheelProtections.single().status)
    }
    @Test fun oldClientsKeepTheirWireSchemaWhileUpdatedClientsGetArchiveMetadata() = RoomFixture().use { f ->
        f.placed(); f.pay(); expire(f)
        val snapshot = f.state()
        val legacy = orderJson.encodeToString(snapshot.forClient(true, true, true))
        listOf("autoArchivedAt", "autoArchiveFrom", "orderCreatedAt", "paymentsPending").forEach { assertFalse(legacy.contains(it)) }
        assertTrue(snapshot.forClient(true, autoArchiveDetails = true).room!!.paymentsPending)
    }
}
