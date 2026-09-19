package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlin.test.Test
import kotlin.test.assertEquals

class RestaurantReadyTextTest {
    @Test fun combinesMatchingItemsAndUsesArabicDigitsForArabicDescriptions() {
        val room = Room(
            id = "room", code = "123456", ownerId = "owner", name = "Lunch",
            restaurant = Restaurant("restaurant", "مطعم البيت", openOrdering = true), payerId = "owner",
        )
        fun receipt(id: String, lines: List<ReceiptLine>) = Receipt(
            memberId = id, name = id, lines = lines, food = 0, delivery = 0, service = 0,
            discount = 0, tax = 0, total = 0, paid = 0, balance = 0, revision = 1, currency = "AED",
        )
        val text = restaurantReadyText(room, listOf(
            receipt("one", listOf(ReceiptLine("فول", 1, 100), ReceiptLine("طعمية", 4, 400, "بدون سلطة"))),
            receipt("two", listOf(ReceiptLine("فول", 1, 100), ReceiptLine("Water", 1, 100))),
        ))

        assertEquals("مطعم البيت\nPickup\n\n٢ فول\n٤ طعمية — بدون سلطة\n1 Water", text)
    }
}
