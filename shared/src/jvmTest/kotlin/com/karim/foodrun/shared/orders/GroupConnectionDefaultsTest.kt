package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlin.test.*

class GroupConnectionDefaultsTest {
    private class Phone : GroupPlatform {
        val saved = mutableMapOf<String, String>()
        val sent = mutableListOf<Pair<HubPairing, RoomCommand>>()
        override fun read(key: String) = saved[key].orEmpty()
        override fun write(key: String, value: String): Boolean { saved[key] = value; return true }
        override fun now() = 1_800_000_000_000L
        override fun uuid() = "command-${sent.size}"
        override fun request(hub: HubPairing, body: String, callback: GroupReplyCallback) {
            sent += hub to orderJson.decodeFromString<RoomCommand>(body)
            callback.complete("", "Network unavailable")
        }
        override fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback) = object : GroupSubscription { override fun cancel() {} }
        override fun share(text: String, fileName: String) {}
        override fun openLink(url: String) {}
        override fun importMenu(callback: GroupReplyCallback) {}
        override fun scanPairing(callback: GroupReplyCallback) {}
        override fun discover(callback: GroupReplyCallback) {}
    }

    @Test fun creationAndJoiningOpenInternetSetupDirectly() {
        for (action in listOf(GroupAction.CREATE, GroupAction.JOIN)) {
            val phone = Phone(); val c = GroupController(phone)
            c.accountEditing = true // A previously opened profile must not redirect room setup.
            c.library = c.library.copy(selectedHub = HubPairing("https://localhost:8443", "a".repeat(64)))
            c.dispatch(action)
            assertEquals(GroupPage.SETUP, c.state.page)
            assertEquals(HubPairing(FOOD_RUN_INTERNET_API, ""), c.library.selectedHub)
            assertEquals(action == GroupAction.JOIN, c.joinMode)
            assertTrue(phone.sent.isEmpty(), "Opening a draft must not submit a room command")
            if(action == GroupAction.CREATE) assertEquals("names", c.state.fields.single { it.key == GroupFieldKey.SELECTION_STYLE }.value)
        }
    }

    @Test fun failureOffersExplicitConnectionOptionsAndKeepsTheDraft() {
        val phone = Phone(); val c = GroupController(phone)
        c.dispatch(GroupAction.CREATE); c.update(GroupFieldKey.ROOM_NAME, "Breakfast")
        c.error = "Network unavailable"
        assertTrue(c.state.buttons.any { it.action == GroupAction.OPEN_CONNECTION_OPTIONS })
        c.dispatch(GroupAction.OPEN_CONNECTION_OPTIONS)
        assertEquals(GroupPage.CONNECT, c.state.page)
        assertEquals("Breakfast", c.text(GroupFieldKey.ROOM_NAME))
        c.dispatch(GroupAction.BACK)
        assertEquals(GroupPage.SETUP, c.state.page)
        c.dispatch(GroupAction.OPEN_CONNECTION_OPTIONS)
        c.update(GroupFieldKey.HUB_URL, "https://localhost:8443")
        c.dispatch(GroupAction.CONNECT)
        assertEquals(GroupPage.SETUP, c.state.page)
        assertEquals("Breakfast", c.text(GroupFieldKey.ROOM_NAME))
        assertTrue(phone.sent.isEmpty())
    }

    @Test fun anUnconfirmedCreationStaysOnItsOriginalHubAndIsNeverRerouted() {
        val phone = Phone(); val c = GroupController(phone)
        c.dispatch(GroupAction.CREATE); c.update(GroupFieldKey.NAME, "Ahmed")
        c.dispatch(GroupAction.USE_OPEN_ORDER); c.dispatch(GroupAction.CREATE_ROOM)
        assertEquals(1, phone.sent.size)
        assertEquals("names", phone.sent.single().second.selectionStyle)
        val pending = assertNotNull(c.library.pending)
        assertTrue(c.state.buttons.none { it.action == GroupAction.OPEN_CONNECTION_OPTIONS })
        c.dispatch(GroupAction.OPEN_CONNECTION_OPTIONS)
        assertEquals(GroupPage.SETUP, c.state.page)
        assertEquals(pending, c.library.pending)
        assertEquals(FOOD_RUN_INTERNET_API, c.library.pendingHub?.url)
        assertEquals(1, phone.sent.size)
    }
}
