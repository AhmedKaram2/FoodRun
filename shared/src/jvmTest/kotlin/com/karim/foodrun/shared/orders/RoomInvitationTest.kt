package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlin.test.*

class RoomInvitationTest {
    private val room = Room("room", "123456", "owner", "Lunch", BuiltInRestaurants.sultan.restaurant, orderNumber = 3)
    private val link = "https://intrvioo.com/?room=123456"

    @Test fun invitationIncludesCurrentRestaurantOrderAndDirectJoinLink() {
        listOf("en", "ar").forEach { language ->
            val text = roomInvitation(room, link, language)
            assertTrue(text.contains(room.restaurant.localizedName(language)))
            assertTrue(text.contains(if (language == "ar") "طلب رقم 3" else "Order #3"))
            assertTrue(text.contains(room.code))
            assertTrue(text.endsWith(link))
            assertFalse(text.contains("account"))
        }
    }
    @Test fun invitationDescribesOpenPollInsteadOfClaimingItsWinner() {
        val text = roomInvitation(room.copy(restaurantPollOpen = true,
            restaurantOptions = listOf(room.restaurant, BuiltInRestaurants.alKalha.restaurant)), link, "en")
        assertTrue(text.contains("Restaurant poll:"))
        assertTrue(text.contains(BuiltInRestaurants.alKalha.restaurant.name))
        assertFalse(text.contains("Restaurant:"))
    }
}
