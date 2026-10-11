package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class SimpleTransferTest {
    @Test fun amountOnlyTransfersRetainValidationAndWaitForRecipientConfirmation() = RoomFixture().use { f ->
        val member = f.placed(); f.pay()
        val original = f.state(member).receipts.single().balance
        for(amount in listOf(0L,-1L,original+1)) assertFalse(f.service.execute(f.command(member,CommandKind.DECLARE_TRANSFER).copy(amount=amount)).ok)
        f.send(member,CommandKind.DECLARE_TRANSFER) { it.copy(amount=300) }
        var claim = f.state().room!!.transfers.single()
        assertEquals(TransferStatus.DECLARED,claim.status)
        assertContains(claim.reference,"Order #1"); assertContains(claim.reference,"Daily lunch"); assertContains(claim.reference,"AED 3.00")
        assertEquals(original,f.state(member).receipts.single().balance)
        assertFalse(f.service.execute(f.command(member,CommandKind.DECLARE_TRANSFER).copy(amount=original)).ok)
        assertFalse(f.service.execute(f.command(member,CommandKind.CONFIRM_TRANSFER).copy(transferId=claim.id)).ok)
        f.send(f.owner,CommandKind.CONFIRM_TRANSFER) { it.copy(transferId=claim.id) }
        val remaining = f.state(member).receipts.single().balance
        assertEquals(original-300,remaining)
        f.send(member,CommandKind.DECLARE_TRANSFER) { it.copy(amount=remaining) }
        claim = f.state().room!!.transfers.last()
        assertEquals(remaining,claim.amount)
        f.send(f.owner,CommandKind.CONFIRM_TRANSFER) { it.copy(transferId=claim.id) }
        assertEquals(0,f.state(member).receipts.single().balance)
        f.restart(); assertEquals(0,f.state(member).receipts.single().balance)
    }
    @Test fun refundsAlsoFillTheOrderNoteAndRequireTheMemberToConfirm() = RoomFixture().use { f ->
        val member = f.placed(); f.pay()
        f.send(member,CommandKind.DECLARE_TRANSFER) { it.copy(amount=f.state(member).receipts.single().balance) }
        f.send(f.owner,CommandKind.CONFIRM_TRANSFER) { it.copy(transferId=f.state().room!!.transfers.single().id) }
        f.send(f.owner,CommandKind.ADJUST_BILL) { it.copy(amount=-1500,text="Restaurant discount") }
        f.pay()
        val refund = -f.state(member).receipts.single().balance
        assertTrue(refund>0)
        f.send(f.owner,CommandKind.DECLARE_REFUND) { it.copy(memberId=member.memberId,amount=refund) }
        val claim=f.state().room!!.transfers.last()
        assertContains(claim.reference,"Refund · Order #1")
        assertFalse(f.service.execute(f.command(f.owner,CommandKind.CONFIRM_REFUND).copy(transferId=claim.id)).ok)
        f.send(member,CommandKind.CONFIRM_REFUND) { it.copy(transferId=claim.id) }
        assertEquals(0,f.state(member).receipts.single().balance)
    }
}
