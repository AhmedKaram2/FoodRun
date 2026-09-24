package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class OrderProgressTest {
    @Test fun ordersCanBePlacedWithoutAnExpectedArrival(): Unit {
        for (phase in listOf(RoomPhase.COLLECTING, RoomPhase.REVIEW)) {
            for (arrival in listOf("", "   \n ")) RoomFixture().use { f ->
                val member = f.join(); f.approve(member); f.start(member)
                f.send(f.owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = f.account) }
                f.cart(f.owner, 2); f.cart(member, 1)
                if (phase == RoomPhase.REVIEW) f.send(f.owner, CommandKind.REVIEW)
                val before = f.state().room!!
                assertEquals(phase, before.phase)
                val placed = f.send(f.owner, CommandKind.PLACE) { it.copy(text = arrival) }.room!!
                assertEquals(RoomPhase.PLACED, placed.phase)
                assertEquals("", placed.restaurantReference)
                assertEquals(before.carts, placed.carts)
                f.restart()
                assertEquals(RoomPhase.PLACED, f.state().room!!.phase)
                assertEquals("", f.state().room!!.restaurantReference)
            }
        }
    }

    @Test fun optionalExpectedArrivalStillEnforcesTheLengthLimit(): Unit = RoomFixture().use { f ->
        f.confirmedReview()
        val rejected = f.service.execute(f.command(f.owner, CommandKind.PLACE).copy(text = "x".repeat(501)))
        assertFalse(rejected.ok)
        assertTrue(rejected.error.contains("length"))
        assertEquals(RoomPhase.REVIEW, f.state().room!!.phase)
        val arrival = "x".repeat(500)
        val placed = f.send(f.owner, CommandKind.PLACE) { it.copy(text = arrival) }.room!!
        assertEquals(RoomPhase.PLACED, placed.phase)
        assertEquals(arrival, placed.restaurantReference)
    }

    private fun RoomFixture.confirmedReview(): RoomReply {
        val member = join(); approve(member); start(member)
        send(owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = account) }
        cart(owner, 2); cart(member, 1)
        send(owner, CommandKind.REVIEW)
        listOf(owner, member).forEach { actor ->
            send(actor, CommandKind.CONFIRM_QUOTE) { it.copy(expectedRevision = state(actor).room!!.quoteRevision) }
        }
        return member
    }

    @Test fun sharingTheSameAccountPreservesConfirmedQuotes(): Unit = RoomFixture().use { f ->
        val member = f.confirmedReview()
        // The saved local copy keeps its own version even after the hub increments it.
        f.send(f.owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = f.account.copy(identifier = "AE770331234567890123457")) }
        val changed = f.state().room!!
        assertTrue(changed.carts.all { it.confirmedQuote != changed.quoteRevision })
        listOf(f.owner, member).forEach { actor ->
            f.send(actor, CommandKind.CONFIRM_QUOTE) { it.copy(expectedRevision = changed.quoteRevision) }
        }
        val before = f.state().room!!
        val account = before.account!!
        val same = f.send(f.owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = account.copy(version = 1)) }.room!!
        assertEquals(before.quoteRevision, same.quoteRevision)
        assertEquals(account.version, same.account!!.version)
        assertEquals(before.carts, same.carts)
        f.send(f.owner, CommandKind.PLACE) { it.copy(text = "Confirmed") }
    }

    @Test fun addingOnlyTheRestaurantContactDoesNotRestartReview(): Unit = RoomFixture().use { f ->
        val missingContact = f.restaurant.copy(contact = RestaurantContact())
        f.send(f.owner, CommandKind.UPDATE_RESTAURANT) { it.copy(restaurant = missingContact, text = "Contact not entered yet") }
        f.confirmedReview()
        val before = f.state().room!!
        val rejected = f.service.execute(f.command(f.owner, CommandKind.PLACE).copy(text = "Confirmed"))
        assertFalse(rejected.ok)
        assertTrue(rejected.error.contains("phone"))
        val after = f.send(f.owner, CommandKind.UPDATE_RESTAURANT) { it.copy(restaurant = f.restaurant, text = "Added the contact") }.room!!
        assertEquals(RoomPhase.REVIEW, after.phase)
        assertEquals(before.quoteRevision, after.quoteRevision)
        assertEquals(before.carts, after.carts)
        assertEquals(RoomPhase.PLACED, f.send(f.owner, CommandKind.PLACE) { it.copy(text = "Confirmed; 20 minutes") }.room!!.phase)
    }

    @Test fun fixingContactInTheLobbyPreservesReadiness(): Unit = RoomFixture().use { f ->
        f.send(f.owner, CommandKind.READY) { it.copy(flag = true, eligible = true) }
        val before = f.state().room!!
        val after = f.send(f.owner, CommandKind.UPDATE_RESTAURANT) {
            it.copy(restaurant = f.restaurant.copy(contact = RestaurantContact("+971509876543")), text = "Corrected phone")
        }.room!!
        assertEquals(before.members, after.members)
        assertEquals(before.quoteRevision, after.quoteRevision)
        f.send(f.owner, CommandKind.PREPARE_SPIN)
    }

    @Test fun savingUnchangedFeesKeepsTheOrderReadyToPlace(): Unit = RoomFixture().use { f ->
        f.confirmedReview()
        val before = f.state().room!!
        val after = f.send(f.owner, CommandKind.SET_FEES) { it.copy(fees = before.fees, text = "Checked the fees") }.room!!
        assertEquals(RoomPhase.REVIEW, after.phase)
        assertEquals(before.quoteRevision, after.quoteRevision)
        assertEquals(before.carts, after.carts)
        f.send(f.owner, CommandKind.PLACE) { it.copy(text = "Confirmed") }
    }

    @Test fun savingTheSameBillAdjustmentDoesNotRequirePaymentAgain(): Unit = RoomFixture().use { f ->
        val member = f.placed(); f.pay()
        f.send(f.owner, CommandKind.ADJUST_BILL) { it.copy(amount = -750, text = "Discount") }
        f.send(member, CommandKind.APPROVE_ADJUSTMENT) { it.copy(expectedRevision = f.state().room!!.billRevision) }
        f.pay()
        val before = f.state().room!!
        val after = f.send(f.owner, CommandKind.ADJUST_BILL) { it.copy(amount = -750, text = "Checked discount") }.room!!
        assertTrue(after.restaurantPaid)
        assertEquals(before.billRevision, after.billRevision)
        assertEquals(before.adjustmentApprovals, after.adjustmentApprovals)
        f.send(member, CommandKind.DECLARE_TRANSFER) { it.copy(amount = 2700, text = "Paid the agreed total") }
    }

    @Test fun admissionOfAViewOnlyGuestDoesNotChangeTheQuote(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member); f.start(member)
        f.send(f.owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = f.account) }
        f.cart(f.owner, 2); f.cart(member, 1)
        val guest = f.join("Watcher", guest = true)
        val before = f.state().room!!
        val after = f.approve(guest).room!!
        assertEquals(before.quoteRevision, after.quoteRevision)
        assertEquals(before.carts, after.carts)
        assertEquals(before.orderingMembers, after.orderingMembers)
        assertTrue(f.state(guest).receipts.isEmpty())
        assertNull(f.state(guest).room!!.account)
    }

    @Test fun skippedOrganizerCanSeeSafeProgressAndCompleteAnotherPayersOrder(): Unit = RoomFixture().use { f ->
        val payer = f.join("Payer"); f.approve(payer)
        val member = f.join("Ordering member"); f.approve(member)
        f.send(f.owner, CommandKind.PARTICIPATE) { it.copy(flag = false) }
        listOf(payer, member).forEach { actor ->
            f.send(actor, CommandKind.READY) { it.copy(flag = true, eligible = actor == payer) }
        }
        val preparation = f.send(f.owner, CommandKind.PREPARE_SPIN).room!!.preparationId
        f.send(payer, CommandKind.ACK_SPIN) { it.copy(text = preparation) }
        val spinning = f.send(member, CommandKind.ACK_SPIN) { it.copy(text = preparation) }.room!!
        f.now = spinning.spin!!.endAt; f.service.tick()
        f.send(payer, CommandKind.ACCEPT_DUTY)
        assertFalse(f.state().progress!!.accountShared)
        assertFalse(f.state().progress!!.canReview)
        f.send(payer, CommandKind.SHARE_ACCOUNT) { it.copy(account = f.account) }
        f.cart(payer, 2); f.cart(member, 1)
        val reviewable = f.state()
        assertTrue(reviewable.progress!!.accountShared)
        assertTrue(reviewable.progress!!.canReview)
        assertTrue(reviewable.receipts.isEmpty())
        assertNull(reviewable.room!!.account)
        assertTrue(reviewable.room!!.carts.all { it.lines.isEmpty() })
        assertTrue(f.state(payer).progress!!.canReview)
        f.send(f.owner, CommandKind.REVIEW)
        listOf(payer, member).forEach { actor ->
            f.send(actor, CommandKind.CONFIRM_QUOTE) { it.copy(expectedRevision = f.state(actor).room!!.quoteRevision) }
        }
        f.send(payer, CommandKind.PLACE) { it.copy(text = "Confirmed") }
        f.send(payer, CommandKind.PAY_RESTAURANT) { it.copy(amount = 300) }
        f.send(payer, CommandKind.FULFILL)
        assertFalse(f.state().progress!!.canArchive)
        assertTrue(f.state().progress!!.archiveBlocker.contains("Settle"))
        val transfer = f.send(member, CommandKind.DECLARE_TRANSFER) { it.copy(amount = 100, text = "Paid") }.room!!.transfers.single()
        assertFalse(f.state().progress!!.canArchive)
        assertTrue(f.state().room!!.transfers.isEmpty())
        f.send(payer, CommandKind.CONFIRM_TRANSFER) { it.copy(transferId = transfer.id) }
        assertTrue(f.state().progress!!.canArchive)
        assertTrue(f.state(payer).progress!!.canArchive)
        assertTrue(f.state().receipts.isEmpty())
        f.send(f.owner, CommandKind.ARCHIVE)
        f.restart()
        assertEquals(RoomPhase.ARCHIVED, f.state().room!!.phase)
        assertFalse(f.state().progress!!.canArchive)
    }

    @Test fun newViewersCannotReviewOrSeePaymentDetails(): Unit = RoomFixture().use { f ->
        f.placed()
        val pending = f.join("Pending")
        assertFalse(f.state(pending).progress!!.canReview)
        assertFalse(f.state(pending).progress!!.canArchive)
        assertNull(f.state(pending).room!!.account)
    }
}
