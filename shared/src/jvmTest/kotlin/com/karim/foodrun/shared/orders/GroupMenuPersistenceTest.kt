package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlin.test.*

class GroupMenuPersistenceTest {
    private class Device : GroupPlatform {
        var request: RoomCommand? = null
        var destination: HubPairing? = null
        var callback: GroupReplyCallback? = null
        override fun read(key: String) = ""
        override fun write(key: String, value: String) = true
        override fun now() = 1000L
        override fun uuid() = java.util.UUID.randomUUID().toString()
        override fun request(hub: HubPairing, body: String, callback: GroupReplyCallback) { destination = hub; request = orderJson.decodeFromString(body); this.callback = callback }
        override fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback): GroupSubscription = object : GroupSubscription { override fun cancel() {} }
        override fun share(text: String, fileName: String) {}
        override fun openLink(url: String) {}
        override fun importMenu(callback: GroupReplyCallback) {}
        override fun scanPairing(callback: GroupReplyCallback) {}
        override fun discover(callback: GroupReplyCallback) {}
    }
    @Test fun quickAddSavesToTheAccountCatalogAndKeepsTheDraftWhenRejected() {
        val device = Device()
        val restaurant = BuiltInRestaurants.all.first().restaurant
        val c = GroupController(device)
        c.library = c.library.copy(identityToken = "identity-session", identityHub = HubPairing("https://account.example.test"), selectedHub = HubPairing("https://lan.example.test"),
            home = HomePayload(FoodProfile("me", "Me"), restaurants = listOf(restaurant)))
        c.restaurantEditor(RestaurantExport(exportId = restaurant.id, restaurant = restaurant))
        c.dispatch(GroupAction.MENU_OPEN)
        assertTrue(c.state.fields.any { it.key == GroupFieldKey.MENU_ITEM_NAME })
        c.update(GroupFieldKey.MENU_ITEM_NAME, "Breakfast sandwich")
        c.update(GroupFieldKey.MENU_ITEM_PRICE, "7.50")
        c.dispatch(GroupAction.ADD_MENU_ITEM)
        assertEquals(CommandKind.ADD_MENU_ITEMS, device.request!!.kind)
        assertEquals("identity-session", device.request!!.identityToken)
        assertEquals("https://account.example.test", device.destination!!.url)
        assertEquals(750L, device.request!!.restaurant!!.menu.items.single().basePriceMinor)
        device.callback!!.complete(orderJson.encodeToString(RoomReply(ok = false, code = "VALIDATION", error = "Refresh")), "")
        assertEquals("Breakfast sandwich", c.text(GroupFieldKey.MENU_ITEM_NAME))
        c.dispatch(GroupAction.ADD_MENU_ITEM)
        val saved = restaurant.copy(menu = restaurant.menu.copy(items = restaurant.menu.items + device.request!!.restaurant!!.menu.items))
        device.callback!!.complete(orderJson.encodeToString(RoomReply(home = c.library.home!!.copy(restaurants = listOf(saved)))), "")
        assertEquals(GroupPage.MENU_EDITOR, c.page)
        assertEquals("", c.text(GroupFieldKey.MENU_ITEM_NAME))
        assertTrue(c.library.restaurants.single { it.restaurant.id == saved.id }.restaurant.menu.items.any { it.name == "Breakfast sandwich" })
    }
}
