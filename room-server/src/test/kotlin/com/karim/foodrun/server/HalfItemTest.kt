package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

class HalfItemTest {
    private fun offer(f: RoomFixture, requester: RoomReply = f.owner, quantity: Int = 1): HalfItemOffer {
        f.cart(requester, quantity)
        val line = f.state(requester).room!!.carts.single { it.memberId == requester.memberId }.lines.single()
        return f.send(requester, CommandKind.REQUEST_HALF_ITEM) { it.copy(text = line.id) }.room!!.halfItemOffers.single()
    }
    private fun take(f: RoomFixture, member: RoomReply, offer: HalfItemOffer) =
        f.send(member, CommandKind.ACCEPT_HALF_ITEM) { it.copy(text = offer.id) }

    @Test fun availableHalfIsVisibleToOthersButUnrelatedCartsStayPrivate() = RoomFixture().use { f ->
        val member = f.join(); val offered = offer(f)
        val snapshot = f.state(member)
        assertEquals(offered, snapshot.room!!.halfItemOffers.single())
        assertTrue(snapshot.room!!.carts.single { it.memberId == f.owner.memberId }.lines.isEmpty())
        assertEquals(100L, f.state().receipts.single().food)
        assertFalse(f.state().receipts.single().lines.single().halfShare)
        assertTrue(snapshot.receipts.isEmpty())
    }

    @Test fun firstAcceptanceSplitsTheCostAndConcurrentSecondAcceptanceCannotTakeIt() = RoomFixture().use { f ->
        val first = f.join("First"); val second = f.join("Second"); val offered = offer(f)
        val competing = f.command(second, CommandKind.ACCEPT_HALF_ITEM).copy(text = offered.id)
        val command = f.command(first, CommandKind.ACCEPT_HALF_ITEM).copy(text = offered.id)
        assertTrue(f.execute(command).ok)
        assertTrue(f.execute(command).ok) // A lost response must not create a second share.
        assertFalse(f.service.execute(competing).ok)
        assertFalse(f.service.execute(f.command(second, CommandKind.ACCEPT_HALF_ITEM).copy(text = offered.id)).ok)
        val owner = f.state().receipts.single()
        val recipient = f.state(first).receipts.single()
        assertEquals(50L, owner.food); assertEquals(50L, recipient.food)
        assertTrue(owner.lines.single().halfShare && recipient.lines.single().halfShare)
        assertEquals(1, owner.lines.single().restaurantQuantity)
        assertEquals(0, recipient.lines.single().restaurantQuantity)
        assertTrue(f.state(first).room!!.carts.single { it.memberId == first.memberId }.submitted)
        f.restart()
        assertEquals(50L, f.state(first).receipts.single().food)
    }

    @Test fun oneItemFromMultipleSandwichesIsOfferedAndOddPriceIsConservedAfterRepricing() = RoomFixture().use { f ->
        val member = f.join(); val offered = offer(f, quantity = 3); take(f, member, offered)
        f.send(f.owner, CommandKind.SELECT_PAYER) { it.copy(memberId = f.owner.memberId) }
        f.send(f.owner, CommandKind.PRICE_ITEM) { it.copy(memberId = f.owner.memberId, text = offered.lineId, amount = 101) }
        val receipts = f.state().receipts
        assertEquals(301L, receipts.sumOf { it.food })
        assertEquals(251L, receipts.single { it.memberId == f.owner.memberId }.food)
        assertEquals(50L, receipts.single { it.memberId == member.memberId }.food)
        assertEquals(3, receipts.flatMap { it.lines }.sumOf { it.restaurantQuantity ?: it.quantity })
        assertEquals(101L, f.state(member).room!!.halfItemOffers.single().line.amount)
    }

    @Test fun acceptingWithAnEmptySubmittedCartAddsFoodAndSplitsDeliveryAndTaxExactly() = RoomFixture().use { f ->
        val member = f.join(); f.start(member)
        f.cart(member, 0)
        val offered = offer(f); take(f, member, offered)
        val room = f.db.room(f.owner.room!!.id)!!
        val billed = room.copy(deliveryMode = true, fees = FeePolicy(automaticDelivery = true, service = 100, discount = 20),
            restaurant = room.restaurant.copy(pricing = room.restaurant.pricing.copy(taxTreatment = TaxTreatment.ADDED, taxRateBasisPoints = 500)))
        val receipts = Billing.receipts(billed)
        assertEquals(2, receipts.size)
        assertEquals(500L, receipts.sumOf { it.delivery })
        assertEquals(34L, receipts.sumOf { it.tax })
        assertEquals(714L, receipts.sumOf { it.total })
        assertTrue(receipts.all { it.lines.single().halfShare })
    }

    @Test fun releaseMakesTheHalfAvailableAgainAndRequesterCancellationRestoresFullCost() = RoomFixture().use { f ->
        val member = f.join(); val other = f.join("Other"); val offered = offer(f); take(f, member, offered)
        f.send(member, CommandKind.CANCEL_HALF_ITEM) { it.copy(text = offered.id) }
        assertNull(f.state().room!!.halfItemOffers.single().acceptedById)
        assertEquals(0L, f.state(member).receipts.single().food)
        assertEquals(100L, f.state().receipts.single().food)
        take(f, other, offered)
        f.send(f.owner, CommandKind.CANCEL_HALF_ITEM) { it.copy(text = offered.id) }
        assertTrue(f.state().room!!.halfItemOffers.isEmpty())
        assertEquals(100L, f.state().receipts.single().food)
        assertEquals(0L, f.state(other).receipts.single().food)
    }

    @Test fun guestSelfAcceptanceUnrelatedCancellationAndEditingSharedSelectionsAreRejected() = RoomFixture().use { f ->
        val member = f.join(); val other = f.join("Other"); val guest = f.join("Guest", guest = true); val offered = offer(f)
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.ACCEPT_HALF_ITEM).copy(text = offered.id)).ok)
        assertFalse(f.service.execute(f.command(guest, CommandKind.ACCEPT_HALF_ITEM).copy(text = offered.id)).ok)
        assertFalse(f.service.execute(f.command(other, CommandKind.CANCEL_HALF_ITEM).copy(text = offered.id)).ok)
        take(f, member, offered)
        val cart = f.state().room!!.carts.single { it.memberId == f.owner.memberId }
        listOf(emptyList(), cart.lines.map { it.copy(quantity = 2) }, cart.lines.map { it.copy(notes = "Changed") }).forEach { lines ->
            assertFalse(f.service.execute(f.command(f.owner, CommandKind.CART).copy(expectedRevision = cart.revision, cart = cart.copy(lines = lines))).ok)
        }
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.REMOVE).copy(memberId = member.memberId, text = "Remove member")).ok)
    }

    @Test fun unclaimedItemFallsBackToWholeAtPlacementAndOffersCannotBeAcceptedAfterReview() = RoomFixture().use { f ->
        val member = f.join(); f.start(member)
        f.send(f.owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = f.account) }
        f.cart(member, 0); val offered = offer(f)
        f.send(f.owner, CommandKind.REVIEW)
        assertFalse(f.service.execute(f.command(member, CommandKind.ACCEPT_HALF_ITEM).copy(text = offered.id)).ok)
        f.send(f.owner, CommandKind.PLACE)
        val receipt = f.state().receipts.single { it.memberId == f.owner.memberId }
        assertEquals(100L, receipt.food); assertFalse(receipt.lines.single().halfShare)
        assertFalse(f.service.execute(f.command(member, CommandKind.ACCEPT_HALF_ITEM).copy(text = offered.id)).ok)
        f.pay(); f.send(f.owner, CommandKind.FULFILL); f.send(f.owner, CommandKind.ARCHIVE)
        val next = f.send(f.owner, CommandKind.NEXT_ORDER)
        assertTrue(next.room!!.halfItemOffers.isEmpty())
        assertEquals(1, next.history.single().receipts.single { it.memberId == f.owner.memberId }.lines.single().quantity)
        assertFalse(f.service.execute(f.command(member, CommandKind.ACCEPT_HALF_ITEM).copy(text = offered.id, expectedOrderNumber = 1)).ok)
    }

    @Test fun legacyWireReplyKeepsRestaurantQuantityAndOmitsNewFields() = RoomFixture().use { f ->
        val member = f.join(); val offered = offer(f); take(f, member, offered)
        f.send(f.owner, CommandKind.SELECT_PAYER) { it.copy(memberId = f.owner.memberId) }
        val current = f.state()
        val legacy = current.forClient(false)
        assertEquals(1, legacy.receipts.flatMap { it.lines }.sumOf { it.quantity })
        assertEquals(100L, legacy.receipts.sumOf { it.food })
        assertTrue(legacy.room!!.halfItemOffers.isEmpty())
        val json = orderJson.parseToJsonElement(orderJson.encodeToString(RoomReply.serializer(), legacy)).jsonObject
        assertFalse(json["room"]!!.jsonObject.containsKey("halfItemOffers"))
        assertFalse(orderJson.encodeToString(RoomReply.serializer(), legacy).contains("halfShare"))
        assertEquals(current, current.forClient(true, true, true, true, true, true, true, true))
    }

    @Test fun unpricedCustomItemCanBeSharedThenPricedWithoutChangingItsPhysicalQuantity() = RoomFixture().use { f ->
        val member = f.join()
        val room = f.db.room(f.owner.room!!.id)!!
        f.db.save(room.copy(restaurant = room.restaurant.copy(openOrdering = true)))
        val line = CartLine(f.id(), "", 1, description = "Custom sandwich", notes = "No onions")
        f.send(f.owner, CommandKind.CART) { it.copy(expectedRevision = 0, cart = MemberCart(f.owner.memberId, lines = listOf(line))) }
        val offered = f.send(f.owner, CommandKind.REQUEST_HALF_ITEM) { it.copy(text = line.id) }.room!!.halfItemOffers.single()
        take(f, member, offered)
        f.send(f.owner, CommandKind.SELECT_PAYER) { it.copy(memberId = f.owner.memberId) }
        f.send(f.owner, CommandKind.PRICE_ITEM) { it.copy(memberId = f.owner.memberId, text = line.id, amount = 999) }
        assertEquals(listOf(500L, 499L), f.state().receipts.map { it.food })
        assertEquals(1, f.state().receipts.flatMap { it.lines }.sumOf { it.restaurantQuantity ?: it.quantity })
        assertTrue(f.state().receipts.flatMap { it.lines }.all { it.notes == "No onions" && it.halfShare })
    }
}
