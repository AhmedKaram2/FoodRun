package com.karim.foodrun.server

import com.karim.foodrun.orders.*
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
}
