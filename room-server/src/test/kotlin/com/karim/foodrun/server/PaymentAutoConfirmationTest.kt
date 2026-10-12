package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class PaymentAutoConfirmationTest {
    @Test fun fullPaymentBecomesPaidAtTwelveHoursWithoutRecipientActionAndSurvivesRestart() = RoomFixture().use { f ->
        val member=f.placed();f.pay()
        val balance=f.state(member).receipts.single().balance
        val claim=f.send(member,CommandKind.DECLARE_TRANSFER) { it.copy(amount=balance) }.room!!.transfers.single()
        f.now=claim.createdAt+PaymentConfirmation.WAIT_MS-1;f.service.tick()
        assertEquals(TransferStatus.DECLARED,f.state(member).room!!.transfers.single().status)
        assertEquals(balance,f.state(member).receipts.single().balance)
        f.now++;f.restart();f.service.tick()
        val paid=f.state(member)
        assertEquals(TransferStatus.CONFIRMED,paid.room!!.transfers.single().status)
        assertEquals(0,paid.receipts.single().balance)
        assertEquals(balance,paid.receipts.single().paid)
        assertEquals(1,f.db.room(paid.room!!.id)!!.audit.count { it.action=="AUTO_CONFIRM_TRANSFER" })
        val revision=paid.room!!.revision
        f.service.tick();f.restart();f.service.tick()
        assertEquals(revision,f.state(member).room!!.revision)
        assertEquals(1,f.db.room(paid.room!!.id)!!.audit.count { it.action=="AUTO_CONFIRM_TRANSFER" })
        assertFalse(f.service.execute(f.command(f.owner,CommandKind.CONFIRM_TRANSFER).copy(transferId=claim.id)).ok)
    }
    @Test fun partialPaymentsOnlySettleTheirReportedAmountAndRejectedClaimsStayRejected() = RoomFixture().use { f ->
        val member=f.placed();f.pay()
        val balance=f.state(member).receipts.single().balance
        val rejected=f.send(member,CommandKind.DECLARE_TRANSFER) { it.copy(amount=100) }.room!!.transfers.single()
        f.send(f.owner,CommandKind.REJECT_TRANSFER) { it.copy(transferId=rejected.id,text="Not received") }
        val claim=f.send(member,CommandKind.DECLARE_TRANSFER) { it.copy(amount=500) }.room!!.transfers.last()
        f.now=claim.createdAt+PaymentConfirmation.WAIT_MS
        // A read catches up even before the next maintenance tick.
        val paid=f.state(member)
        assertEquals(TransferStatus.REJECTED,paid.room!!.transfers.first().status)
        assertEquals(TransferStatus.CONFIRMED,paid.room!!.transfers.last().status)
        assertEquals(balance-500,paid.receipts.single().balance)
        assertEquals(500,paid.receipts.single().paid)
    }
    @Test fun pendingPaymentFinishesAfterAutomaticRoomArchiving() = RoomFixture().use { f ->
        val member=f.placed();f.pay()
        f.now=f.owner.room!!.createdAt+22*60*60*1000L
        val claim=f.send(member,CommandKind.DECLARE_TRANSFER) { it.copy(amount=f.state(member).receipts.single().balance) }.room!!.transfers.single()
        f.now=f.owner.room!!.createdAt+RoomExpiry.LIFETIME_MS;f.service.tick()
        assertEquals(RoomPhase.ARCHIVED,f.state(member).room!!.phase)
        assertTrue(f.state(member).room!!.paymentsPending)
        f.now=claim.createdAt+PaymentConfirmation.WAIT_MS;f.restart();f.service.tick()
        assertEquals(0,f.state(member).receipts.single().balance)
        assertFalse(f.state(member).room!!.paymentsPending)
    }
    @Test fun walletClaimsRefundsAndUnknownTimestampsDoNotAutoConfirm() = RoomFixture().use { f ->
        f.placed();f.pay()
        val room=f.state().room!!
        val direct=Transfer("direct","member",100,"Sent",f.account,createdAt=f.now)
        val excluded=listOf(direct.copy(id="wallet-batch"),direct.copy(id="refund",refund=true),direct.copy(id="legacy",createdAt=0),direct.copy(id="rejected",status=TransferStatus.REJECTED))
        val unchanged=room.copy(transfers=excluded)
        assertNull(PaymentConfirmation.nextDeadline(unchanged))
        assertEquals(unchanged,PaymentConfirmation.confirm(unchanged,f.now+PaymentConfirmation.WAIT_MS))
        assertEquals(room.copy(phase=RoomPhase.CANCELLED,transfers=listOf(direct)),PaymentConfirmation.confirm(room.copy(phase=RoomPhase.CANCELLED,transfers=listOf(direct)),f.now+PaymentConfirmation.WAIT_MS))
    }
}
