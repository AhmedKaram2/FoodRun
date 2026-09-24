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

    @Test fun ordinaryMembersCannotOverrideAndCartsCannotForgePrices() = RoomFixture().use { f ->
        val member = f.join()
        val ordinary = f.join("Ordinary member")
        f.send(f.owner, CommandKind.SELECT_PAYER) { it.copy(memberId = member.memberId) }
        f.cart(f.owner, 2)
        val cart = f.state().room!!.carts.single { it.memberId == f.owner.memberId }
        val line = cart.lines.single()
        val denied = f.service.execute(f.command(ordinary, CommandKind.PRICE_ITEM).copy(memberId = f.owner.memberId, text = line.id, amount = 1))
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

    @Test fun repeatedPriceAndBillChangesNeedOnlyTheOriginalSubmission() = RoomFixture().use { f ->
        val member = f.join(); val empty = f.join("No food"); f.start(member)
        f.send(f.owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = f.account) }
        f.cart(f.owner, 1); f.cart(member, 2); f.cart(empty, 0)
        val original = f.state().room!!
        val line = original.carts.single { it.memberId == member.memberId }.lines.single()
        fun price(amount: Long) = f.send(f.owner, CommandKind.PRICE_ITEM) { it.copy(memberId = member.memberId, text = line.id, amount = amount) }
        price(120); price(80)
        f.send(f.owner, CommandKind.PLACE) { it.copy(text = "Confirmed") }
        f.pay()
        val transfer = f.send(member, CommandKind.DECLARE_TRANSFER) { it.copy(amount = 160, text = "Paid share") }.room!!.transfers.single()
        f.send(f.owner, CommandKind.CONFIRM_TRANSFER) { it.copy(transferId = transfer.id) }
        price(50)
        assertEquals(RoomPhase.PLACED, f.state().room!!.phase)
        assertFalse(f.state().room!!.restaurantPaid)
        assertEquals(-60L, f.state(member).receipts.single().balance)
        f.send(f.owner, CommandKind.ADJUST_BILL) { it.copy(amount = -20, text = "Restaurant discount") }
        f.pay()
        assertEquals(-70L, f.state(member).receipts.single().balance)
        f.send(f.owner, CommandKind.FULFILL)
        price(75)
        assertEquals(RoomPhase.FULFILLED, f.state().room!!.phase)
        assertEquals(230L, f.state().receipts.sumOf { it.total })
        assertEquals(-22L, f.state(member).receipts.single().balance)
        f.pay()
        // Saving the same price does not invalidate recorded restaurant payment.
        val revision = f.state().room!!.billRevision
        price(75)
        assertTrue(f.state().room!!.restaurantPaid)
        assertEquals(revision, f.state().room!!.billRevision)
        assertTrue(f.state().room!!.carts.all { it.submitted })
        assertEquals(original.carts.map { c -> c.lines.map { it.copy(unitPrice = null) } },
            f.state().room!!.carts.map { c -> c.lines.map { it.copy(unitPrice = null) } })
        assertEquals(0L, f.state(empty).receipts.single().total)
        f.restart()
        assertTrue(f.state().room!!.carts.all { it.submitted })
        assertTrue(f.state().room!!.restaurantPaid)
        assertEquals(230L, f.state().receipts.sumOf { it.total })
        assertTrue(f.db.room(original.id)!!.audit.none { it.action in listOf("CONFIRM_QUOTE", "APPROVE_ADJUSTMENT") })
    }

    @Test fun placedPricesRejectOrdinaryMembersAndCompletedOrdersAreImmutable() = RoomFixture().use { f ->
        val member = f.placed()
        val line = f.state().room!!.carts.single { it.memberId == member.memberId }.lines.single()
        assertFalse(f.service.execute(f.command(member, CommandKind.PRICE_ITEM).copy(memberId = member.memberId, text = line.id, amount = 50)).ok)
        val command = f.command(f.owner, CommandKind.PRICE_ITEM).copy(memberId = member.memberId, text = line.id, amount = 50)
        f.send(f.owner, CommandKind.PRICE_ITEM) { command }
        assertFalse(f.service.execute(command.copy(commandId = f.id(), amount = 60)).ok)
        for (phase in listOf(RoomPhase.ARCHIVED, RoomPhase.CANCELLED)) {
            val room = f.db.room(f.owner.room!!.id)!!
            f.db.save(room.copy(phase = phase))
            assertFalse(f.service.execute(f.command(f.owner, CommandKind.PRICE_ITEM).copy(memberId = member.memberId, text = line.id, amount = 70)).ok)
        }
    }

    @Test fun ownerCanReadAndPriceOtherMembersItemsWhenSomeoneElsePays() = RoomFixture().use { f ->
        val payer = f.join("Payer"); val member = f.join("Member")
        f.send(f.owner, CommandKind.SELECT_PAYER) { it.copy(memberId = payer.memberId) }
        f.send(payer, CommandKind.SHARE_ACCOUNT) { it.copy(account = f.account) }
        f.cart(f.owner, 1); f.cart(payer, 2); f.cart(member, 3)
        val original = f.state().room!!
        val line = original.carts.single { it.memberId == member.memberId }.lines.single()
        assertTrue(original.carts.all { it.lines.isNotEmpty() })
        assertEquals(listOf(f.owner.memberId), f.state().receipts.map { it.memberId })
        assertTrue(f.state(member).room!!.carts.filter { it.memberId != member.memberId }.all { it.lines.isEmpty() })
        fun price(value: Long) = f.send(f.owner, CommandKind.PRICE_ITEM) { it.copy(memberId = member.memberId, text = line.id, amount = value) }
        price(75)
        assertEquals(225L, f.state(member).receipts.single().food)
        f.send(f.owner, CommandKind.REVIEW)
        price(60)
        assertEquals(RoomPhase.COLLECTING, f.state().room!!.phase)
        assertTrue(f.state().room!!.carts.all { it.submitted })
        f.send(payer, CommandKind.PLACE)
        f.send(payer, CommandKind.PAY_RESTAURANT) { it.copy(amount = f.state(payer).receipts.sumOf { receipt -> receipt.total }) }
        f.send(member, CommandKind.DECLARE_TRANSFER) { it.copy(amount = 180, text = "Paid") }
        f.send(payer, CommandKind.CONFIRM_TRANSFER) { it.copy(transferId = f.state(payer).room!!.transfers.single().id) }
        val transfers = f.state(payer).room!!.transfers
        price(50)
        assertEquals(-30L, f.state(member).receipts.single().balance)
        assertEquals(transfers, f.state(payer).room!!.transfers)
        assertFalse(f.state().room!!.restaurantPaid)
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.PAY_RESTAURANT).copy(amount = 450)).ok)
        f.send(payer, CommandKind.FULFILL)
        price(80)
        assertEquals(RoomPhase.FULFILLED, f.state().room!!.phase)
        assertEquals(240L, f.state(member).receipts.single().food)
        assertEquals(original.restaurant, f.state().room!!.restaurant)
        f.restart()
        assertEquals(80L, f.state().room!!.carts.single { it.memberId == member.memberId }.lines.single().unitPrice)
        for (phase in listOf(RoomPhase.ARCHIVED, RoomPhase.CANCELLED)) {
            f.db.save(f.db.room(original.id)!!.copy(phase = phase))
            assertTrue(f.state().room!!.carts.single { it.memberId == member.memberId }.lines.isEmpty())
            assertFalse(f.service.execute(f.command(f.owner, CommandKind.PRICE_ITEM).copy(memberId = member.memberId, text = line.id, amount = 70)).ok)
        }
    }

    @Test fun ownerCanPriceCustomItemsWithoutJoiningTheMeal() = RoomFixture().use { f ->
        val payer = f.join("Payer")
        f.send(f.owner, CommandKind.PARTICIPATE) { it.copy(flag = false) }
        f.send(f.owner, CommandKind.SELECT_PAYER) { it.copy(memberId = payer.memberId) }
        val room = f.db.room(f.owner.room!!.id)!!
        f.db.save(room.copy(restaurant = room.restaurant.copy(openOrdering = true)))
        f.send(payer, CommandKind.CART) { it.copy(expectedRevision = 0, cart = MemberCart(payer.memberId,
            lines = listOf(CartLine("custom", "", 2, description = "Breakfast")))) }
        assertEquals("custom", f.state().room!!.carts.single().lines.single().id)
        f.send(f.owner, CommandKind.PRICE_ITEM) { it.copy(memberId = payer.memberId, text = "custom", amount = 350) }
        assertEquals(700L, f.state(payer).receipts.single { it.memberId == payer.memberId }.food)
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
