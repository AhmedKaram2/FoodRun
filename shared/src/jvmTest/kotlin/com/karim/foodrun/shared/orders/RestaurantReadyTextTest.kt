package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlin.test.Test
import kotlin.test.assertEquals

class RestaurantReadyTextTest {
    @Test fun combinesMatchingItemsAndUsesTheSelectedLanguagesDigits() {
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

        assertEquals("مطعم البيت\nPickup\nExpected delivery / pickup: To be confirmed by restaurant\n\n2 فول\n4 طعمية — بدون سلطة\n1 Water", text)
    }
    @Test fun whatsappPrefersExplicitNumberAndUsesOnlyMobileFallback() {
        fun number(phone: String, whatsapp: String? = null) = restaurantWhatsAppNumber(
            Restaurant("r", "Restaurant", contact = RestaurantContact(phoneE164 = phone, whatsappE164 = whatsapp)))
        assertEquals("971501234567", number("+97165569877", "+971501234567"))
        assertEquals("971501234567", number("+971501234567"))
        assertEquals("", number("+97165569877"))
    }

}
