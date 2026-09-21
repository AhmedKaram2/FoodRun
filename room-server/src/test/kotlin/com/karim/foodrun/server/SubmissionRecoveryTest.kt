package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class SubmissionRecoveryTest {
    @Test fun restartRestoresPreviouslySubmittedFoodAndEmptyCartAfterTaxReset() = RoomFixture().use { f ->
        val member = f.join(); f.start(member)
        f.send(f.owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = f.account) }
        f.cart(f.owner, 0); f.cart(member, 3)
        val current = f.state().room!!
        f.send(f.owner, CommandKind.UPDATE_RESTAURANT) { it.copy(restaurant = current.restaurant.copy(pricing = current.restaurant.pricing.copy(taxTreatment = TaxTreatment.ADDED, taxRateBasisPoints = 500)), text = "Confirmed tax") }
        // Reproduce the old server's invalidation, with the original explicit submission records intact.
        val taxUpdated = f.db.room(current.id)!!
        f.db.save(taxUpdated.copy(carts = taxUpdated.carts.map { it.copy(submitted = false, confirmedQuote = -1) }))
        f.restart()
        val recovered = f.state().room!!
        assertTrue(recovered.carts.all { it.submitted })
        assertTrue(f.state().progress!!.canReview)
        assertEquals(315L, f.state().receipts.sumOf { it.total })
        assertEquals(0L, f.state().receipts.single { it.memberId == f.owner.memberId }.total)
        f.restart()
        assertEquals(recovered, f.state().room)
        f.send(f.owner, CommandKind.PLACE) { it.copy(text = "50 minutes") }
    }

    @Test fun laterFoodEditsAndNeverSubmittedDraftsStayUnsubmitted() = RoomFixture().use { f ->
        val member = f.join(); f.start(member); f.cart(member, 3)
        val current = f.state().room!!
        f.send(f.owner, CommandKind.UPDATE_RESTAURANT) { it.copy(restaurant = current.restaurant.copy(pricing = current.restaurant.pricing.copy(taxTreatment = TaxTreatment.ADDED, taxRateBasisPoints = 500)), text = "Tax") }
        val cart = f.state(member).room!!.carts.single { it.memberId == member.memberId }
        f.send(member, CommandKind.CART) { it.copy(expectedRevision = cart.revision, cart = cart.copy(lines = cart.lines.map { line -> line.copy(quantity = 4) })) }
        f.send(f.owner, CommandKind.CART) { it.copy(expectedRevision = 0, cart = MemberCart(f.owner.memberId)) }
        f.restart()
        assertTrue(f.state().room!!.carts.none { it.submitted })
    }

    @Test fun changedMenuAndMissingSubmissionEvidenceAreNotRecovered() = RoomFixture().use { f ->
        val member = f.join(); f.start(member); f.cart(member, 3)
        val before = f.state().room!!
        f.send(f.owner, CommandKind.UPDATE_RESTAURANT) { it.copy(restaurant = before.restaurant.copy(menu = before.restaurant.menu.copy(items = before.restaurant.menu.items.map { item -> item.copy(basePriceMinor = 150) })), text = "Changed menu price") }
        // Reproduce the old menu-update reset; current price changes preserve submission.
        val updated = f.db.room(before.id)!!
        f.db.save(updated.copy(carts = updated.carts.map { it.copy(submitted = false) }))
        f.restart()
        val after = f.state().room!!
        assertFalse(after.carts.single().submitted)
        assertEquals(after, recoverTaxResetSubmissions(after) { null })
        assertEquals(after.copy(phase = RoomPhase.PLACED), recoverTaxResetSubmissions(after.copy(phase = RoomPhase.PLACED), f.db::recordedReply))
    }
}
