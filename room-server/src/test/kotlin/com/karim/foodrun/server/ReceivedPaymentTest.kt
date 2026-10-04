package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class ReceivedPaymentTest {
    @Test fun chosenPayerCanSettleFoodOrdersDirectlyBeforeOrAfterArrivalAndReplayIsSafe() {
        for (fulfilled in listOf(false, true)) RoomFixture().use { f ->
            val member = f.placed(); f.pay()
            if (fulfilled) f.send(f.owner, CommandKind.FULFILL)
            assertNull(f.state().room!!.paymentRoom)
            val before = f.state(member).receipts.single().balance
            val partial = f.command(f.owner, CommandKind.RECORD_PAYMENT).copy(memberId = member.memberId, amount = 500, text = "Cash received")
            f.execute(partial); f.execute(partial)
            assertEquals(before - 500, f.state(member).receipts.single().balance)
            assertEquals(1, f.state().room!!.transfers.size)
            f.send(f.owner, CommandKind.RECORD_PAYMENT) { it.copy(memberId = member.memberId, amount = before - 500, text = "Bank payment received") }
            val paid = f.state(member)
            assertEquals(0L, paid.receipts.single().balance)
            assertTrue(paid.room!!.transfers.all { it.status == TransferStatus.CONFIRMED })
            f.restart()
            assertEquals(0L, f.state(member).receipts.single().balance)
        }
    }

    @Test fun roomOwnershipDoesNotReplacePayerAuthorityAndBadAmountsCannotChangeLedger() = RoomFixture().use { f ->
        val member = f.placed(); f.pay()
        f.db.save(f.state().room!!.copy(ownerId = member.memberId))
        val due = f.state().receipts.single { it.memberId == member.memberId }.balance
        val command = f.command(f.owner, CommandKind.RECORD_PAYMENT).copy(memberId = member.memberId, amount = due, text = "Payment received")
        val invalid = listOf(command.copy(token = member.token), command.copy(memberId = f.owner.memberId),
            command.copy(memberId = "missing"), command.copy(amount = 0), command.copy(amount = -1), command.copy(amount = due + 1),
            command.copy(expectedRevision = -1), command.copy(expectedOrderNumber = 0))
        invalid.forEach { assertFalse(f.service.execute(it.copy(commandId = f.id())).ok) }
        assertTrue(f.state().room!!.transfers.isEmpty())
        assertEquals(due, f.state(member).receipts.single().balance)
    }

    @Test fun restaurantPaymentAndPendingClaimsMustBeResolvedBeforeDirectSettlement() = RoomFixture().use { f ->
        val member = f.placed()
        fun record() = f.command(f.owner, CommandKind.RECORD_PAYMENT).copy(memberId = member.memberId, amount = 100, text = "Cash received")
        assertFalse(f.service.execute(record()).ok)
        f.pay()
        f.send(member, CommandKind.DECLARE_TRANSFER) { it.copy(amount = 100, text = "Bank transfer") }
        assertFalse(f.service.execute(record()).ok)
        assertEquals(1, f.state().room!!.transfers.size)
        assertEquals(TransferStatus.DECLARED, f.state().room!!.transfers.single().status)
    }
}
