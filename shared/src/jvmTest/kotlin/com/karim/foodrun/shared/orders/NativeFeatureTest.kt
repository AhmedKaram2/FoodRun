package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlin.test.*

class NativeFeatureTest {
    private class Device : GroupPlatform {
        var sent: RoomCommand? = null
        private var counter = 0
        override fun read(key: String) = ""
        override fun write(key: String, value: String) = true
        override fun now() = 10000L
        override fun uuid() = "fixture-command-${++counter}"
        override fun request(hub: HubPairing, body: String, callback: GroupReplyCallback) { sent = orderJson.decodeFromString(body); callback.complete(orderJson.encodeToString(RoomReply(ok = false, error = "Captured locally")), "") }
        override fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback) = object : GroupSubscription { override fun cancel() = Unit }
        override fun share(text: String, fileName: String) = Unit
        override fun openLink(url: String) = Unit
        override fun importMenu(callback: GroupReplyCallback) = Unit
        override fun scanPairing(callback: GroupReplyCallback) = Unit
        override fun discover(callback: GroupReplyCallback) = Unit
    }
    private val account = ReceivingAccount("account", "Owner", "Aani", "+971501234567", method = PaymentMethod.AANI)
    private val hub = HubPairing("https://localhost:8443", "a".repeat(64))
    @Test fun paymentRoomCreationRequiresBalancedSharesAndReusesProfileAccount() {
        val device = Device(); val c = GroupController(device)
        c.library = c.library.copy(identityToken = "identity-token", identityHub = hub, home = HomePayload(
            FoodProfile("owner", "Owner", "+971501234567", payment = account), people = listOf(FoodPerson("friend", "Friend"))))
        c.dispatch(GroupAction.CREATE_PAYMENT_ROOM)
        c.update(GroupFieldKey.PAYMENT_DETAILS, "Lunch already paid")
        c.update(GroupFieldKey.PAYMENT_TOTAL, "10")
        c.dispatch(GroupAction.EDIT_PAYMENT_SHARE, "friend")
        c.update(GroupFieldKey.PAYMENT_SHARE, "9")
        c.update(GroupFieldKey.PAYMENT_RECEIVED, "2")
        c.dispatch(GroupAction.SAVE_PAYMENT_SHARE)
        c.dispatch(GroupAction.SAVE_PAYMENT_ROOM)
        assertNull(device.sent); assertTrue(c.error.contains("add up"))
        c.dispatch(GroupAction.EDIT_PAYMENT_SHARE, "friend")
        c.update(GroupFieldKey.PAYMENT_SHARE, "10")
        c.dispatch(GroupAction.SAVE_PAYMENT_SHARE)
        c.dispatch(GroupAction.SAVE_PAYMENT_ROOM)
        val sent = requireNotNull(device.sent)
        assertEquals(CommandKind.CREATE_PAYMENT_ROOM, sent.kind)
        assertEquals(account, sent.account); assertEquals("Mohre", sent.text)
        assertEquals("identity-token", sent.identityToken)
        assertEquals(1000L, sent.paymentRoom!!.shares.sumOf { it.amount })
        assertEquals(200L, sent.paymentRoom!!.shares.single { it.userId == "friend" }.received)
        assertEquals(0L, sent.paymentRoom!!.shares.single { it.userId == "owner" }.received)
        assertEquals("Owner", c.library.displayName.ifBlank { c.library.home!!.profile.name })
    }
    @Test fun guidedMenuKeepsArabicSizesExtrasAndRequiredSelections() {
        val c = GroupController(Device())
        c.dispatch(GroupAction.NEW_RESTAURANT)
        c.update(GroupFieldKey.RESTAURANT_NAME, "Kitchen")
        c.dispatch(GroupAction.MENU_OPEN)
        c.dispatch(GroupAction.MENU_EDIT, "group|")
        c.update(GroupFieldKey.MENU_ENTITY_NAME, "Extras"); c.update(GroupFieldKey.MENU_ENTITY_AR, "إضافات")
        c.update(GroupFieldKey.MENU_ENTITY_MIN, "1")
        c.dispatch(GroupAction.MENU_SAVE)
        val groupId = c.editingRestaurant!!.restaurant.menu.optionGroups.single().id
        c.dispatch(GroupAction.MENU_EDIT, "group|$groupId")
        c.dispatch(GroupAction.MENU_EDIT, "option||$groupId")
        c.update(GroupFieldKey.MENU_ENTITY_NAME, "Cheese"); c.update(GroupFieldKey.MENU_ENTITY_AR, "جبنة")
        c.update(GroupFieldKey.MENU_ENTITY_PRICE, "2")
        c.dispatch(GroupAction.MENU_SAVE)
        c.dispatch(GroupAction.BACK)
        c.dispatch(GroupAction.MENU_EDIT, "item|")
        c.update(GroupFieldKey.MENU_ENTITY_NAME, "Meal"); c.update(GroupFieldKey.MENU_ENTITY_AR, "وجبة")
        c.update(GroupFieldKey.MENU_ENTITY_PRICE, "10")
        c.dispatch(GroupAction.MENU_TOGGLE_GROUP, groupId)
        c.dispatch(GroupAction.MENU_SAVE)
        val itemId = c.editingRestaurant!!.restaurant.menu.items.single().id
        c.dispatch(GroupAction.MENU_EDIT, "item|$itemId")
        c.dispatch(GroupAction.MENU_EDIT, "variant||$itemId")
        c.update(GroupFieldKey.MENU_ENTITY_NAME, "Large"); c.update(GroupFieldKey.MENU_ENTITY_AR, "كبير")
        c.update(GroupFieldKey.MENU_ENTITY_PRICE, "15")
        c.dispatch(GroupAction.MENU_SAVE); c.dispatch(GroupAction.BACK); c.dispatch(GroupAction.BACK)
        c.dispatch(GroupAction.SAVE_RESTAURANT)
        assertEquals("", c.error)
        val restaurant = c.library.restaurants.single { it.restaurant.name == "Kitchen" }.restaurant
        MenuValidation.validate(restaurant)
        assertEquals("كبير", restaurant.menu.items.single().variants.single().nameAr)
        assertEquals(1500L, restaurant.menu.items.single().variants.single().priceMinor)
        assertEquals(groupId, restaurant.menu.items.single().optionGroupIds.single())
        assertEquals(1, restaurant.menu.optionGroups.single().minSelections)
        assertEquals(200L, restaurant.menu.optionGroups.single().options.single().priceDeltaMinor)
    }
    @Test fun paymentNotificationOpensPaymentFormAndCannotConfirmAnOldTransfer() {
        val device = Device(); val c = GroupController(device)
        val restaurant = Restaurant("r", "Kitchen", openOrdering = true)
        val room = Room("room", "123456", "payer", "Lunch", restaurant, phase = RoomPhase.FULFILLED,
            members = listOf(Member("payer", "Payer", approved = true), Member("me", "Me", approved = true)),
            payerId = "payer", account = account, restaurantPaid = true, carts = listOf(MemberCart("me", lines = listOf(CartLine("line", "", 1, description = "Lunch", unitPrice = 1000)))))
        c.session = StoredSession(hub, room.id, "token", "me", room.name)
        c.reply = RoomReply(room = room, memberId = "me", receipts = Billing.receipts(room))
        c.page = GroupPage.PAYMENT; c.online = true
        assertTrue(c.state.fields.any { it.key == GroupFieldKey.AMOUNT })
        assertTrue(c.state.buttons.any { it.action == GroupAction.DECLARE_TRANSFER })
        assertTrue(c.state.cards.any { it.id == "account" })
        assertNull(device.sent)
    }
}
