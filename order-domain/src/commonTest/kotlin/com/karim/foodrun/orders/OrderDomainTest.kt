package com.karim.foodrun.orders

import kotlin.test.*

class OrderDomainTest {
    private fun restaurant() = Restaurant("r", "Kitchen", contact = RestaurantContact("+971501234567"), menu = Menu(
        categories = listOf(MenuCategory("c", "Food")),
        items = listOf(MenuItem("i", "c", "Meal", basePriceMinor = 100)),
    ))
    private fun room() = Room("r", "123456", "a", "Lunch", restaurant(), fees = FeePolicy(delivery = 1000, discount = 500),
        members = listOf(Member("a", "Karim", approved = true), Member("b", "Karam", approved = true), Member("c", "Hassan", approved = true)),
        carts = listOf(MemberCart("a", lines = listOf(CartLine("a", "i", 45))), MemberCart("b", lines = listOf(CartLine("b", "i", 30))), MemberCart("c", lines = listOf(CartLine("c", "i", 25)))), payerId = "a")

    @Test fun automaticDeliveryCountsPeopleWithFoodIncludingPayerAndSplitsEveryFil() {
        for(count in 1..8) {
            val members = (1..count).map { Member("m$it", "Person $it", approved = true) } + Member("no-food", "No food", approved = true)
            val carts = (1..count).map { MemberCart("m$it", lines = listOf(CartLine("l$it", "i", it))) } + MemberCart("no-food")
            val r = Room("auto", "123456", "m1", "Delivery", restaurant(), deliveryMode = true,
                fees = FeePolicy(delivery = 9999, proportionalDelivery = true, automaticDelivery = true), members = members, carts = carts, payerId = "m1")
            val receipts = Billing.receipts(r)
            val deliveryTotal = if(count < 5) 500L else count * 100L
            assertEquals(deliveryTotal, receipts.sumOf { it.delivery })
            assertEquals(0, receipts.single { it.memberId == "no-food" }.delivery)
            assertEquals(0, receipts.single { it.memberId == "m1" }.balance)
            assertEquals(receipts.sumOf { it.food } + deliveryTotal, receipts.sumOf { it.total })
            assertTrue(receipts.filter { it.memberId != "no-food" }.all { it.delivery in deliveryTotal / count..(deliveryTotal + count - 1) / count })
            if(count >= 5) assertTrue(receipts.filter { it.memberId != "no-food" }.all { it.delivery == 100L })
            assertEquals(receipts.associate { it.memberId to it.delivery }, Billing.receipts(r.copy(carts = carts.reversed())).associate { it.memberId to it.delivery })
        }
        val empty = room().copy(deliveryMode = true, fees = FeePolicy(automaticDelivery = true), carts = emptyList())
        assertTrue(Billing.receipts(empty).isEmpty())
    }

    @Test fun documentedReceiptAddsUpExactly() {
        val receipts = Billing.receipts(room())
        assertEquals(listOf(4609L, 3183L, 2708L), receipts.map { it.total })
        assertEquals(10500L, receipts.sumOf { it.total })
        assertEquals(5891L, receipts.sumOf { it.balance })
    }
    @Test fun allocationConservesAmountsForDifferentWeightsAndInputOrders() {
        for (amount in 0L..150L) for (count in 1..10) {
            val weights = (1..count).associate { it.toString() to ((it * 13L) % 17) }
            val parts = Billing.allocate(amount, weights)
            assertEquals(amount, parts.values.sum())
            assertEquals(parts, Billing.allocate(amount, weights.entries.reversed().associate { it.toPair() }))
            assertTrue(parts.values.all { it >= 0 })
        }
    }
    @Test fun allocationUsesStableTieBreakAndSupportsZeroWeights() {
        assertEquals(mapOf("a" to 2L, "b" to 1L), Billing.allocate(3, mapOf("b" to 0, "a" to 0)))
        assertEquals(emptyMap(), Billing.allocate(0, emptyMap()))
        assertFailsWith<IllegalArgumentException> { Billing.allocate(1, emptyMap()) }
    }
    @Test fun allocationRejectsNegativeAndExcessiveAmounts() {
        assertFailsWith<IllegalArgumentException> { Billing.allocate(-1, mapOf("a" to 1)) }
        assertFailsWith<IllegalArgumentException> { Billing.allocate(Long.MAX_VALUE, mapOf("a" to 1)) }
        assertFailsWith<IllegalArgumentException> { Billing.allocate(1, mapOf("a" to Long.MAX_VALUE)) }
    }
    @Test fun proportionalDeliveryAndDiscountConserveTheBill() {
        val receipts = Billing.receipts(room().copy(fees = FeePolicy(delivery = 1000, discount = 333, proportionalDelivery = true)))
        assertEquals(listOf(450L, 300L, 250L), receipts.map { it.delivery })
        assertEquals(10667L, receipts.sumOf { it.total })
        assertEquals(333L, receipts.sumOf { it.discount })
    }
    @Test fun emptyPayerCartPaysNoFeeAndCreatesNoSelfTransferBalance() {
        val r = room().copy(carts = room().carts.map { if (it.memberId == "a") it.copy(lines = emptyList()) else it })
        val receipts = Billing.receipts(r)
        assertEquals(0L, receipts.first().total)
        assertEquals(0L, receipts.first().balance)
        assertEquals(6000L, receipts.sumOf { it.total })
        assertEquals(1000L, receipts.sumOf { it.delivery })
    }
    @Test fun noFoodHasZeroReceiptsAndNoFees() {
        val r = room().copy(carts = room().carts.map { it.copy(lines = emptyList()) })
        assertTrue(Billing.receipts(r).all { it.total == 0L && it.lines.isEmpty() })
    }
    @Test fun pendingDiscountDoesNotBlockAnEarlySmallCart() {
        val r = room().copy(carts = listOf(MemberCart("a", lines = listOf(CartLine("l", "i", 1)))))
        assertEquals(1000L, Billing.receipts(r).single().total)
    }
    @Test fun aFullRefundNeverMakesAnyReceiptNegative() {
        val receipts = Billing.receipts(room().copy(adjustment = -10500))
        assertTrue(receipts.all { it.total == 0L })
        assertFailsWith<IllegalArgumentException> { Billing.receipts(room().copy(adjustment = -10501)) }
        assertFailsWith<IllegalArgumentException> { Billing.receipts(room().copy(adjustment = Long.MIN_VALUE)) }
    }
    @Test fun onlyConfirmedTransfersReduceBalancesAndConfirmedRefundsRestoreThem() {
        val account = ReceivingAccount("account", "Karim", "Bank", "12345678")
        val r = room().copy(transfers = listOf(
            Transfer("1", "b", 1000, "bank", account, status = TransferStatus.CONFIRMED),
            Transfer("2", "b", 500, "bank", account),
            Transfer("3", "b", 200, "cash", account, refund = true, status = TransferStatus.CONFIRMED),
            Transfer("4", "b", 100, "wrong", account, status = TransferStatus.REJECTED),
        ))
        assertEquals(2383L, Billing.receipts(r).single { it.memberId == "b" }.balance)
    }
    @Test fun taxIsRoundedOnceThenAllocated() {
        val r = room().copy(restaurant = restaurant().copy(pricing = RestaurantPricing(taxTreatment = TaxTreatment.ADDED, taxRateBasisPoints = 500)))
        assertEquals(525L, Billing.receipts(r).sumOf { it.tax })
        assertEquals(11025L, Billing.receipts(r).sumOf { it.total })
    }
    @Test fun variantsReplaceBaseAndOptionsApplyPerUnit() {
        val r = restaurant().copy(menu = restaurant().menu.copy(
            optionGroups = listOf(OptionGroup("extras", "Extras", minSelections = 1, options = listOf(MenuOption("cheese", "Cheese", 50)))),
            items = listOf(MenuItem("i", "c", "Meal", basePriceMinor = 100, variants = listOf(MenuVariant("large", "Large", 200)), optionGroupIds = listOf("extras"))),
        ))
        MenuValidation.validate(r)
        val line = CartLine("line", "i", 2, "large", listOf("cheese"), "No onion")
        assertEquals(500L, Billing.lines(r, MemberCart("a", lines = listOf(line))).single().amount)
        assertFailsWith<IllegalArgumentException> { Billing.lines(r, MemberCart("a", lines = listOf(line.copy(variantId = null)))) }
        assertFailsWith<IllegalArgumentException> { Billing.lines(r, MemberCart("a", lines = listOf(line.copy(optionIds = emptyList())))) }
        assertFailsWith<IllegalArgumentException> { Billing.lines(r, MemberCart("a", lines = listOf(line.copy(optionIds = listOf("cheese", "cheese"))))) }
    }
    @Test fun unavailableItemsUnknownExtrasQuantitiesAndDuplicateLinesAreRejected() {
        val r = restaurant()
        val line = CartLine("l", "i", 1)
        val invalid = listOf(line.copy(quantity = 0), line.copy(quantity = 100), line.copy(optionIds = listOf("bad")), line.copy(notes = "x".repeat(501)))
        invalid.forEach { assertFailsWith<IllegalArgumentException> { Billing.lines(r, MemberCart("a", lines = listOf(it))) } }
        assertFailsWith<IllegalArgumentException> { Billing.lines(r, MemberCart("a", lines = listOf(line, line))) }
        assertFailsWith<IllegalArgumentException> { Billing.lines(r.copy(menu = r.menu.copy(items = r.menu.items.map { it.copy(available = false) })), MemberCart("a", lines = listOf(line))) }
    }
    @Test fun combinedBillLimitsAreValidated() {
        assertFailsWith<IllegalArgumentException> { Billing.receipts(room().copy(fees = FeePolicy(delivery = MenuValidation.MAX_MONEY))) }
        val r = restaurant().copy(menu = restaurant().menu.copy(items = restaurant().menu.items.map { it.copy(basePriceMinor = MenuValidation.MAX_MONEY) }))
        assertFailsWith<IllegalArgumentException> { Billing.lines(r, MemberCart("a", lines = listOf(CartLine("l", "i", 2)))) }
    }
    @Test fun currenciesHaveExactPrecisionWithoutFloatingPoint() {
        assertEquals(1234, Money.parse("12.34", "AED"))
        assertEquals(12345, Money.parse("12.345", "KWD"))
        assertEquals(12, Money.parse("12", "JPY"))
        assertEquals("KWD -12.345", Money.format(-12345, "KWD"))
        assertEquals("JPY 12", Money.format(12, "JPY"))
        assertFailsWith<IllegalArgumentException> { Money.parse("12.3", "JPY") }
        assertFailsWith<IllegalArgumentException> { Money.parse("12.345", "AED") }
        assertFailsWith<IllegalArgumentException> { Money.parse("12", "FAKE") }
        assertFailsWith<IllegalArgumentException> { Money.parse("1e5", "AED") }
    }
    @Test fun ibanChecksumAndAccountBoundsAreValidated() {
        ReceivingAccount("a", "Person", "Bank", "AE07 0331 2345 6789 0123 456", "AED").validate()
        assertFailsWith<IllegalArgumentException> { ReceivingAccount("a", "Person", "Bank", "AE08 0331 2345 6789 0123 456", "AED").validate() }
        assertFailsWith<IllegalArgumentException> { ReceivingAccount("a", "Person", "Bank", "１２３４５６７８", "AED").validate() }
        assertFailsWith<IllegalArgumentException> { ReceivingAccount("a", "Person", "Bank", "GB82 WEST 1234 5698 7654 32", "GBP").validate() }
    }
    @Test fun spinIsStableAcrossClocksAndReconnections() {
        val spin = SpinRound("spin", listOf("a", "b", "c"), "b", 1000)
        assertEquals(0.0, spin.rotation(0))
        assertEquals(7 * 360.0 + 240, spin.rotation(spin.endAt))
        assertEquals(spin.rotation(spin.endAt), spin.rotation(spin.endAt + 9000))
        assertTrue(spin.rotation(4000) < spin.rotation(5000))
        assertFailsWith<IllegalArgumentException> { spin.copy(memberIds = emptyList()) }
        assertFailsWith<IllegalArgumentException> { spin.copy(winnerId = "bad") }
        assertFailsWith<IllegalArgumentException> { spin.copy(duration = 0) }
    }
    @Test fun persistentMembersCanSkipAnOrderWithoutLeavingTheRoom() {
        val r = room().copy(expectedNames = listOf("Karim"), members = room().members.map { it.copy(ready = true, eligible = true, lastSeen = 1000, participating = it.id == "a") })
        assertEquals(3, r.activeMembers.size)
        assertEquals(1, r.orderingMembers.size)
        RoomRules.spinReady(r)
        assertFailsWith<IllegalArgumentException> { RoomRules.spinReady(r.copy(expectedNames = listOf("Hassan"))) }
        assertFailsWith<IllegalArgumentException> { RoomRules.spinReady(r.copy(restaurantPollOpen = true)) }
    }
    @Test fun expectedNamesMustBeUniqueIgnoringCaseAndWhitespace() {
        assertFailsWith<IllegalArgumentException> { RoomRules.validateRoom(room().copy(expectedNames = listOf(" Karim", "karim"))) }
    }
    @Test fun favoriteIdentityUsesTheSameUnambiguousWireKeyAsWeb() {
        val favorite = FavoriteOrder("f", "r", "R", "Usual", listOf(FavoriteOrderLine("meal", 2, optionIds = listOf("a", "b"), notes = "بدون بصل", label = "Meal")), 0)
        assertEquals("""["r","[\"meal\",\"\",\"[\\\"a\\\",\\\"b\\\"]\",\"\",\"2\",\"بدون بصل\"]"]""", favorite.selectionKey())
    }

    @Test fun previousOrderIdentityCollapsesExactRepeatsButKeepsMeaningfulChanges() {
        fun receipt(lines: List<ReceiptLine>) = Receipt("a", "Karim", lines, 0, 0, 0, 0, 0, 0, 0, 0, 1, "AED")
        val meal = ReceiptLine("Meal", 2, 200, "No onion", "i", optionIds = listOf("sauce", "cheese"))
        val drink = ReceiptLine("Tea", 1, 100, itemId = "tea")
        val key = receipt(listOf(meal, drink)).orderSelectionKey("RESTAURANT")

        assertEquals(key, receipt(listOf(drink, meal.copy(optionIds = meal.optionIds.reversed()))).orderSelectionKey("restaurant"))
        assertNotEquals(key, receipt(listOf(meal.copy(quantity = 3), drink)).orderSelectionKey("restaurant"))
        assertNotEquals(key, receipt(listOf(meal.copy(notes = "Extra sauce"), drink)).orderSelectionKey("restaurant"))

        val favorite = FavoriteOrder("favorite", "restaurant", "Kitchen", "Usual", listOf(
            FavoriteOrderLine("i", 2, optionIds = meal.optionIds, notes = meal.notes, label = meal.description),
            FavoriteOrderLine("tea", 1, label = drink.description),
        ), 1)
        assertEquals(key, favorite.selectionKey())
        favorite.validate()
    }
}
