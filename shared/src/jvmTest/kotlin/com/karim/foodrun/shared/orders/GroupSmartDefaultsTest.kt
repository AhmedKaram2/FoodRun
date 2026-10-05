package com.karim.foodrun.shared.orders

import java.time.Instant
import com.karim.foodrun.orders.*
import kotlin.test.*

class GroupSmartDefaultsTest {
    private class Device : GroupPlatform {
        val saved = mutableMapOf<String, String>()
        var time = Instant.parse("2026-10-05T08:59:59Z").toEpochMilli()
        override fun read(key: String) = saved[key].orEmpty()
        override fun write(key: String, value: String): Boolean { saved[key] = value; return true }
        override fun now() = time
        override fun localOffsetSeconds(timeMillis: Long) = 14400
        override fun uuid() = "smart-default-test"
        override fun request(hub: HubPairing, body: String, callback: GroupReplyCallback) = Unit
        override fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback) = object : GroupSubscription { override fun cancel() = Unit }
        override fun share(text: String, fileName: String) = Unit
        override fun openLink(url: String) = Unit
        override fun importMenu(callback: GroupReplyCallback) = Unit
        override fun scanPairing(callback: GroupReplyCallback) = Unit
        override fun discover(callback: GroupReplyCallback) = Unit
    }
    private fun at(value: String) = Instant.parse(value).toEpochMilli()
    @Test fun mealChangesAtOnePmInDeviceTimeInBothLanguages() {
        assertEquals("Breakfast · 05 Oct 2026", mealRoomName(at("2026-10-05T08:59:59Z"), 14400, "en"))
        assertEquals("Lunch · 05 Oct 2026", mealRoomName(at("2026-10-05T09:00:00Z"), 14400, "en"))
        assertEquals("فطور · 05 أكتوبر 2026", mealRoomName(at("2026-10-05T08:59:59Z"), 14400, "ar"))
        assertEquals("غداء · 05 أكتوبر 2026", mealRoomName(at("2026-10-05T09:00:00Z"), 14400, "ar"))
    }
    @Test fun localDateHandlesMidnightYearBoundaryAndLeapDay() {
        assertEquals("Breakfast · 01 Jan 2027", mealRoomName(at("2026-12-31T21:00:00Z"), 14400, "en"))
        assertEquals("Lunch · 04 Oct 2026", mealRoomName(at("2026-10-05T00:00:00Z"), -18000, "en"))
        assertEquals("Breakfast · 29 Feb 2024", mealRoomName(at("2024-02-29T00:00:00Z"), 0, "en"))
    }
    @Test fun untouchedSuggestionsRefreshAtCreationButCustomNamesArePreserved() {
        val device = Device(); val c = GroupController(device)
        c.smartDefaults.seed()
        assertEquals("Breakfast · 05 Oct 2026", c.text(GroupFieldKey.ROOM_NAME))
        device.time = at("2026-10-05T09:00:00Z")
        assertEquals("Lunch · 05 Oct 2026", c.smartDefaults.submittedName(GroupFieldKey.ROOM_NAME))
        c.update(GroupFieldKey.ROOM_NAME, "Our team")
        assertEquals("Our team", c.smartDefaults.submittedName(GroupFieldKey.ROOM_NAME))
    }
    @Test fun restaurantAndAddressPreferencesStayWithTheirAccountAndIgnoreRemovedMenus() {
        val device = Device(); val c = GroupController(device)
        val restaurant = c.library.restaurants.first().restaurant
        val alice = HomePayload(FoodProfile(userId = "alice", name = "Alice"))
        c.library = c.library.copy(home = alice)
        c.smartDefaults.remember(Room("r", "123456", "alice", "Lunch", restaurant, destination = "Gate 2"))
        c.smartDefaults.seed()
        assertEquals(restaurant.id, c.selectedRestaurant?.restaurant?.id)
        assertEquals("Gate 2", c.text(GroupFieldKey.DESTINATION))
        c.library = c.library.copy(restaurants = emptyList()); c.smartDefaults.seed()
        assertNull(c.selectedRestaurant)
        c.library = c.library.copy(home = HomePayload(FoodProfile(userId = "bob", name = "Bob")))
        c.smartDefaults.seed()
        assertTrue(c.text(GroupFieldKey.DESTINATION).startsWith("Mohre"))
        device.saved["foodrun-room-preferences:bob"] = "corrupt"
        c.smartDefaults.seed()
        assertTrue(c.text(GroupFieldKey.ROOM_NAME).isNotBlank())
    }
}
