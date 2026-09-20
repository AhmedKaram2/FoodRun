package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlin.test.*

class BilingualOrderTextTest {
    @Test fun restaurantReadyTextUsesArabicMenuNamesAndDigits() {
        val restaurant = BuiltInRestaurants.sultan.restaurant
        val falafel = restaurant.menu.items.single { it.name == "Falafel" }
        val member = Member("member", "Karam", approved = true, eligible = true, ready = true)
        val room = Room("room", "123456", "member", "Lunch", restaurant,
            members = listOf(member), carts = listOf(MemberCart("member", lines = listOf(CartLine("line", falafel.id, 2)), submitted = true)))
        val text = restaurantReadyText(room, Billing.receipts(room), "ar")
        assertTrue(text.startsWith("مطعم سلطان"))
        assertTrue(text.contains("٢ طعمية"))
        assertTrue(text.contains("استلام من المطعم"))
    }
}
