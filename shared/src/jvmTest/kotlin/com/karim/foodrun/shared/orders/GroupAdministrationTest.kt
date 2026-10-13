package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlin.test.*

class GroupAdministrationTest {
    private val hub = HubPairing("https://admin.example.test")
    private val payment = ReceivingAccount("bank", "Other", "Test bank", "GB82WEST12345698765432", currency = "GBP", version = 4)
    private val profile = FoodProfile("other", "Other", "+971500000001", payment = payment)
    private val room = AdminRoomView("room", "123456", "Lunch", "COLLECTING", 1, "Kitchen", 2, "Other", 1000, 500, 500, "GBP", 1000,
        wallets = listOf(AdminWalletView("other", "Other", 1000, 500, 500)), revision = 8)
    private fun dashboard() = AdminDashboard(
        users = listOf(
            AdminUserView("admin", "Admin", "", true, false, "en", profile = FoodProfile("admin", "Admin")),
            AdminUserView("other", "Other", profile.phone, true, false, "en", profile = profile,
                roomBlocks = mapOf("room" to AccessBlock(reason = "Room access paused"))),
        ),
        rooms = listOf(room, room.copy(id = "aed-room", currency = "AED", totalMinor = 300)),
        archivedOrders = listOf(room.copy(phase = "ARCHIVED", canDelete = true)),
        restaurants = listOf(Restaurant("restaurant", "Kitchen", openOrdering = true)), settings = AdminSettings(),
    )
    private class Phone(var dashboard: AdminDashboard) : GroupPlatform {
        val requests = mutableListOf<NativeAdminRequest>()
        var admin = true
        private var sequence = 0
        override fun read(key: String) = ""
        override fun write(key: String, value: String) = true
        override fun now() = 1000L
        override fun uuid() = "admin-fixture-${++sequence}"
        override fun adminRequest(hub: HubPairing, body: String, callback: GroupReplyCallback) {
            requests += orderJson.decodeFromString<NativeAdminRequest>(body)
            callback.complete(orderJson.encodeToString(NativeAdminReply(ok = admin, dashboard = dashboard)), "")
        }
        override fun request(hub: HubPairing, body: String, callback: GroupReplyCallback) = Unit
        override fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback) = object : GroupSubscription { override fun cancel() = Unit }
        override fun share(text: String, fileName: String) = Unit
        override fun openLink(url: String) = Unit
        override fun importMenu(callback: GroupReplyCallback) = Unit
        override fun scanPairing(callback: GroupReplyCallback) = Unit
        override fun discover(callback: GroupReplyCallback) = Unit
    }
    private fun controller(phone: Phone) = GroupController(phone).also { c ->
        c.library = c.library.copy(identityToken = "fixture-session", identityHub = hub, selectedHub = hub, home = HomePayload(FoodProfile("admin", "Admin")))
        c.administration.checkAccess()
        c.dispatch(GroupAction.OPEN_ADMIN)
    }

    @Test fun allSectionsHaveAnExplicitSelectedTabAndCurrencyTotalsStaySeparate() {
        val c = controller(Phone(dashboard()))
        val tabs = c.state.cards.single { it.id == "admin-tabs" }.buttons
        assertEquals(9, tabs.size)
        assertEquals("overview", tabs.single { it.primary }.value)
        assertFalse(tabs.single { it.primary }.enabled)
        assertEquals("GBP 10.00", c.state.cards.single { it.id == "admin-metric:value:GBP" }.detail)
        assertEquals("AED 3.00", c.state.cards.single { it.id == "admin-metric:value:AED" }.detail)
        c.dispatch(GroupAction.ADMIN_TAB, "wallets")
        assertEquals("wallets", c.state.cards.single { it.id == "admin-tabs" }.buttons.single { it.primary }.value)
        assertTrue(c.state.cards.single { it.id == "admin-room:0" }.detail.contains("Paid GBP 5.00"))
    }

    @Test fun ownAdminAccountCannotBeBlockedAndScopedUnblockKeepsTheSelectedRoom() {
        val phone = Phone(dashboard()); val c = controller(phone)
        c.dispatch(GroupAction.ADMIN_TAB, "users")
        assertEquals(listOf(GroupAction.ADMIN_USER), c.state.cards.single { it.id == "admin-user:admin" }.buttons.map { it.action })
        val unblock = c.state.cards.single { it.id == "admin-user:other" }.buttons.single { it.value == "unblock|other|room" }
        c.dispatch(unblock.action, unblock.value)
        assertEquals("room", c.state.fields.single { it.key == GroupFieldKey.ADMIN_SCOPE }.value)
        c.dispatch(GroupAction.ADMIN_CONFIRM)
        val mutation = orderJson.decodeFromString<AdminUserMutation>(phone.requests.last().payload)
        assertEquals("unblock", mutation.action); assertEquals("other", mutation.userId); assertEquals("room", mutation.scopeRoomId)
    }

    @Test fun editingUserDetailsPreservesPaymentCurrencyAndVersion() {
        val phone = Phone(dashboard()); val c = controller(phone)
        c.dispatch(GroupAction.ADMIN_USER, "other")
        c.update(GroupFieldKey.ADMIN_NAME, "Updated")
        c.dispatch(GroupAction.ADMIN_SAVE_USER)
        val saved = orderJson.decodeFromString<AdminUserMutation>(phone.requests.last().payload).profile!!
        assertEquals("Updated", saved.name); assertEquals("GBP", saved.payment!!.currency); assertEquals(4L, saved.payment!!.version)
        assertEquals(profile.payment!!.identifier, saved.payment!!.identifier)
    }

    @Test fun roomDeletionRequiresTheCodeAndRetainsRevisionBeforeSending() {
        val phone = Phone(dashboard()); val c = controller(phone)
        c.dispatch(GroupAction.ADMIN_ACTION, "delete|room")
        assertFalse(c.state.primaryAction!!.enabled)
        c.update(GroupFieldKey.ADMIN_CONFIRMATION, "123456")
        assertTrue(c.state.primaryAction!!.enabled)
        c.dispatch(GroupAction.ADMIN_CONFIRM)
        val mutation = orderJson.decodeFromString<AdminRoomMutation>(phone.requests.last().payload)
        assertEquals("room", mutation.roomId); assertEquals("delete", mutation.action)
        assertEquals(8L, mutation.expectedRevision); assertEquals("123456", mutation.confirmation)
    }

    @Test fun oldAccessCannotShowAdminControlsAfterTheIdentityChanges() {
        val c = controller(Phone(dashboard()))
        assertTrue(c.administration.verifiedAccess)
        c.library = c.library.copy(identityToken = "another-account")
        assertFalse(c.administration.verifiedAccess)
        assertTrue(GroupReactBridge(c).snapshotJson().contains("\"adminAvailable\":false"))
    }
}
