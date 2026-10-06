package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlin.test.*

class GroupWalletAnnouncementTest {
    private class Device : GroupPlatform {
        val saved = mutableMapOf<String, String>()
        var writable = true
        override fun read(key: String): String { require(key.matches(Regex("[a-zA-Z0-9_-]{1,80}"))); return saved[key].orEmpty() }
        override fun write(key: String, value: String): Boolean { require(key.matches(Regex("[a-zA-Z0-9_-]{1,80}"))); if (!writable) return false; saved[key] = value; return true }
        override fun now() = 1_800_000_000_000L
        override fun uuid() = "test"
        override fun request(hub: HubPairing, body: String, callback: GroupReplyCallback) = Unit
        override fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback) = object : GroupSubscription { override fun cancel() = Unit }
        override fun share(text: String, fileName: String) = Unit
        override fun openLink(url: String) = Unit
        override fun importMenu(callback: GroupReplyCallback) = Unit
        override fun scanPairing(callback: GroupReplyCallback) = Unit
        override fun discover(callback: GroupReplyCallback) = Unit
    }
    private fun GroupController.account(id: String) { library = library.copy(home = HomePayload(FoodProfile(userId = id)), selectedHub = HubPairing("https://example.test")) }
    @Test fun signedInHomeShowsAnnouncementUntilDismissedAndRemembersEachAccount() {
        val device = Device(); val c = GroupController(device)
        assertNull(c.walletAnnouncement.card())
        c.account("alice"); assertNotNull(c.state.cards.find { it.id == "wallet-announcement" })
        c.dispatch(GroupAction.DISMISS_WALLET_ANNOUNCEMENT); assertNull(c.walletAnnouncement.card())
        c.account("bob"); assertNotNull(c.walletAnnouncement.card())
        val restored = GroupController(device); restored.account("alice"); assertNull(restored.walletAnnouncement.card())
        restored.account("bob"); assertNotNull(restored.walletAnnouncement.card())
    }
    @Test fun openingWalletDismissesAnnouncementAndOpensProfile() {
        val c = GroupController(Device()); c.account("alice")
        c.dispatch(GroupAction.OPEN_WALLET_ANNOUNCEMENT)
        assertEquals(GroupPage.PROFILE, c.page); assertNull(c.walletAnnouncement.card())
    }
    @Test fun failedPreferenceWriteKeepsAnnouncementAvailableToRetry() {
        val device = Device(); val c = GroupController(device); c.account("alice"); device.writable = false
        c.dispatch(GroupAction.DISMISS_WALLET_ANNOUNCEMENT)
        assertNotNull(c.walletAnnouncement.card()); assertTrue(c.state.error.contains("preference"))
        device.writable = true; c.dispatch(GroupAction.DISMISS_WALLET_ANNOUNCEMENT); assertNull(c.walletAnnouncement.card())
    }
}
