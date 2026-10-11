package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenOrderPricingTest {
    @Test fun payerPricesTypedItemsBeforeReview() = RoomFixture().use { fixture ->
        val openRestaurant = fixture.restaurant.copy(menu = Menu(), openOrdering = true)
        fixture.send(fixture.owner, CommandKind.UPDATE_RESTAURANT) {
            it.copy(restaurant = openRestaurant, text = "Use typed food items")
        }
        val member = fixture.join()
        fixture.approve(member)
        fixture.start(member)
        fixture.send(fixture.owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = fixture.account) }

        fun submit(actor: RoomReply, lines: List<CartLine>) {
            fixture.send(actor, CommandKind.CART) {
                it.copy(expectedRevision = 0, cart = MemberCart(actor.memberId, lines = lines))
            }
            fixture.send(actor, CommandKind.SUBMIT_CART) { it.copy(expectedRevision = 1) }
        }

        submit(fixture.owner, emptyList())
        val line = CartLine(fixture.id(), "", 2, notes = "No onion", description = "Chicken shawarma")
        submit(member, listOf(line))

        val blocked = fixture.service.execute(fixture.command(fixture.owner, CommandKind.REVIEW))
        assertFalse(blocked.ok)
        assertTrue(blocked.error.contains("price every custom item"))

        fixture.send(fixture.owner, CommandKind.PRICE_ITEM) {
            it.copy(memberId = member.memberId, text = line.id, amount = 1_250)
        }
        val reviewed = fixture.send(fixture.owner, CommandKind.REVIEW)
        assertEquals(RoomPhase.REVIEW, reviewed.room?.phase)

        val memberReceipt = fixture.state(member).receipts.single()
        assertEquals(2_500, memberReceipt.food)
        assertEquals("Chicken shawarma", memberReceipt.lines.single().description)
        assertEquals("No onion", memberReceipt.lines.single().notes)
    }
    @Test fun membersPriceOwnTypedItemsAndOwnerCorrectionsPreserveTheLobbyAndPrivacy() = RoomFixture().use { f ->
        f.send(f.owner, CommandKind.UPDATE_RESTAURANT) { it.copy(restaurant = f.restaurant.copy(openOrdering = true), text = "Open menu") }
        val member = f.join(); f.approve(member)
        val line = CartLine(f.id(), "", 2, description = "Falafel", unitPrice = 750, notes = "Extra sauce")
        f.send(member, CommandKind.CART) { it.copy(expectedRevision = 0, cart = MemberCart(member.memberId, lines = listOf(line))) }
        assertEquals(1500L, f.state(member).receipts.single().food)
        assertEquals(line, f.state().room!!.carts.single { it.memberId == member.memberId }.lines.single())
        val corrected = f.send(f.owner, CommandKind.PRICE_ITEM) { it.copy(memberId = member.memberId, text = line.id, amount = 800) }
        assertEquals(RoomPhase.LOBBY, corrected.room!!.phase)
        assertEquals(1600L, f.state(member).receipts.single().food)
        val other = f.join("Other"); f.approve(other)
        assertTrue(f.state(other).room!!.carts.single { it.memberId == member.memberId }.lines.isEmpty())
        val denied = f.service.execute(f.command(other, CommandKind.PRICE_ITEM).copy(memberId = member.memberId, text = line.id, amount = 1))
        assertFalse(denied.ok)
        f.restart()
        assertEquals(1600L, f.state(member).receipts.single().food)
    }
    @Test fun optionalPricesAndInvalidPricesAreHandledWithoutAcceptingMenuOverrides() = RoomFixture().use { f ->
        f.send(f.owner, CommandKind.UPDATE_RESTAURANT) { it.copy(restaurant = f.restaurant.copy(openOrdering = true), text = "Open menu") }
        val member = f.join(); f.approve(member)
        val typed = CartLine(f.id(), "", 1, description = "Shawarma")
        f.send(member, CommandKind.CART) { it.copy(expectedRevision = 0, cart = MemberCart(member.memberId, lines = listOf(typed, CartLine(f.id(), "meal", 1, unitPrice = 1)))) }
        assertNull(f.state(member).room!!.carts.single().lines[0].unitPrice)
        assertNull(f.state(member).room!!.carts.single().lines[1].unitPrice)
        val invalid = f.service.execute(f.command(member, CommandKind.CART).copy(expectedRevision = 1, cart = MemberCart(member.memberId, lines = listOf(typed.copy(unitPrice = -1)))))
        assertFalse(invalid.ok)
        assertEquals(1L, f.state(member).room!!.carts.single().revision)
    }
}
