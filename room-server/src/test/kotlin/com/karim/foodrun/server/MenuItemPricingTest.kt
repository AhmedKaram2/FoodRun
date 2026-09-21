package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class MenuItemPricingTest {
    @Test fun selectedPayerCanPriceMenuItemsAndSplitSharedDiscountExactly() = RoomFixture().use { f ->
        val member = f.join()
        f.start(member)
        f.cart(f.owner, 2)
        f.cart(member, 3)
        val line = f.state().room!!.carts.single { it.memberId == f.owner.memberId }.lines.single()
        f.send(f.owner, CommandKind.PRICE_ITEM) { it.copy(memberId = f.owner.memberId, text = line.id, amount = 75) }
        f.send(f.owner, CommandKind.SET_FEES) { it.copy(fees = FeePolicy(discount = 90), text = "Restaurant discount") }
        val receipts = f.state().receipts
        assertEquals(150L, receipts.single { it.memberId == f.owner.memberId }.food)
        assertEquals(30L, receipts.single { it.memberId == f.owner.memberId }.discount)
        assertEquals(300L, receipts.single { it.memberId == member.memberId }.food)
        assertEquals(60L, receipts.single { it.memberId == member.memberId }.discount)
        assertEquals(360L, receipts.sumOf { it.total })
        assertEquals(100L, f.state().room!!.restaurant.menu.items.single().basePriceMinor)
        f.restart()
        assertEquals(360L, f.state().receipts.sumOf { it.total })
    }

    @Test fun onlySelectedPayerCanOverrideAndCartCannotForgePrices() = RoomFixture().use { f ->
        val member = f.join()
        f.send(f.owner, CommandKind.SELECT_PAYER) { it.copy(memberId = member.memberId) }
        f.cart(f.owner, 2)
        val cart = f.state().room!!.carts.single { it.memberId == f.owner.memberId }
        val line = cart.lines.single()
        val denied = f.service.execute(f.command(f.owner, CommandKind.PRICE_ITEM).copy(memberId = f.owner.memberId, text = line.id, amount = 1))
        assertFalse(denied.ok)
        assertTrue(denied.error.contains("selected payer"))
        f.send(f.owner, CommandKind.CART) { it.copy(expectedRevision = cart.revision, cart = cart.copy(lines = listOf(line.copy(unitPrice = 1)))) }
        assertNull(f.state().room!!.carts.single().lines.single().unitPrice)
        f.send(member, CommandKind.PRICE_ITEM) { it.copy(memberId = f.owner.memberId, text = line.id, amount = 50) }
        val priced = f.state().room!!.carts.single()
        f.send(f.owner, CommandKind.CART) { it.copy(expectedRevision = priced.revision, cart = priced.copy(lines = listOf(priced.lines.single().copy(unitPrice = 1)))) }
        assertEquals(50L, f.state().room!!.carts.single().lines.single().unitPrice)
        val unchanged = f.state().room!!.carts.single()
        f.send(f.owner, CommandKind.CART) { it.copy(expectedRevision = unchanged.revision, cart = unchanged.copy(lines = listOf(unchanged.lines.single().copy(quantity = 3, unitPrice = 1)))) }
        assertNull(f.state().room!!.carts.single().lines.single().unitPrice)
        assertEquals(300L, f.state().receipts.single().food)
    }

    @Test fun changesDuringReviewReopenTotalsAndMenuPriceCanBeRestored() = RoomFixture().use { f ->
        val member = f.join(); f.start(member)
        f.send(f.owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = f.account) }
        f.cart(f.owner, 1); f.cart(member, 2)
        f.send(f.owner, CommandKind.REVIEW)
        val before = f.state().room!!
        val line = before.carts.single { it.memberId == member.memberId }.lines.single()
        f.send(member, CommandKind.CONFIRM_QUOTE) { it.copy(expectedRevision = before.quoteRevision) }
        val result = f.send(f.owner, CommandKind.PRICE_ITEM) { it.copy(memberId = member.memberId, text = line.id, amount = 60) }.room!!
        assertEquals(RoomPhase.COLLECTING, result.phase)
        assertTrue(result.quoteRevision > before.quoteRevision)
        assertEquals(-1, result.carts.single { it.memberId == member.memberId }.confirmedQuote)
        assertEquals(120L, f.state(member).receipts.single().food)
        f.send(f.owner, CommandKind.PRICE_ITEM) { it.copy(memberId = member.memberId, text = line.id, flag = true) }
        assertEquals(200L, f.state(member).receipts.single().food)
        assertNull(f.state().room!!.carts.single { it.memberId == member.memberId }.lines.single().unitPrice)
    }

    @Test fun staleNegativeAndPostPlacementPriceChangesAreRejected() = RoomFixture().use { f ->
        val member = f.placed()
        val line = f.state().room!!.carts.single { it.memberId == member.memberId }.lines.single()
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.PRICE_ITEM).copy(memberId = member.memberId, text = line.id, amount = 50)).ok)
    }

    @Test fun staleAndInvalidPricesLeaveTheBillUntouched() = RoomFixture().use { f ->
        val member = f.join(); f.start(member); f.cart(member, 2)
        val line = f.state().room!!.carts.single().lines.single()
        val command = f.command(f.owner, CommandKind.PRICE_ITEM).copy(memberId = member.memberId, text = line.id, amount = 50)
        assertFalse(f.service.execute(command.copy(commandId = f.id(), amount = -1)).ok)
        assertFalse(f.service.execute(command.copy(commandId = f.id(), amount = MenuValidation.MAX_MONEY)).ok)
        f.send(f.owner, CommandKind.PRICE_ITEM) { it.copy(memberId = member.memberId, text = line.id, amount = 70) }
        assertFalse(f.service.execute(command).ok)
        assertEquals(140L, f.state(member).receipts.single().food)
    }
}
