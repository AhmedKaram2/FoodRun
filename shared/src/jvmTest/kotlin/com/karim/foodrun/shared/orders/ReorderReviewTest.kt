package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlin.test.*

class ReorderReviewTest {
    private val restaurant = Restaurant("r", "Kitchen", openOrdering = true, menu = Menu(
        items = listOf(MenuItem("meal", "main", "Meal", basePriceMinor = 1000, optionGroupIds = listOf("extras"))),
        optionGroups = listOf(OptionGroup("extras", "Extras", minSelections = 1, maxSelections = 1, options = listOf(MenuOption("cheese", "Cheese", 200))))))
    @Test fun currentPricesAndRequiredOptionsAreReviewedIndividually() {
        val source = listOf(FavoriteOrderLine("meal", 2, optionIds = listOf("cheese"), notes = "No onion"),
            FavoriteOrderLine("missing", 1), FavoriteOrderLine("meal", 1), FavoriteOrderLine("meal", 1, optionIds = listOf("cheese", "cheese")))
        val entries = prepareNativeReorder(restaurant, source)
        assertEquals(2400L, entries.first().total)
        assertEquals("No onion", entries.first().line!!.notes)
        assertNull(entries.first().line!!.unitPrice)
        assertTrue(entries.drop(1).all { it.line == null && it.issue.isNotEmpty() })
        assertEquals(source.first().optionIds, listOf("cheese"))
    }
    @Test fun customItemsNeedNewPricesAndClosedMenusRejectThem() {
        val source = listOf(FavoriteOrderLine("", 3, description = "Special meal"))
        val entry = prepareNativeReorder(restaurant, source).single()
        assertNotNull(entry.line); assertNull(entry.total)
        assertNull(prepareNativeReorder(restaurant.copy(openOrdering = false), source).single().line)
    }
}
