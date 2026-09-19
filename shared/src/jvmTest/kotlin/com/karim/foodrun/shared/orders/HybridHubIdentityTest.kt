package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.HubPairing
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HybridHubIdentityTest {
    private class Device : GroupPlatform {
        override fun read(key: String) = ""
        override fun write(key: String, value: String) = true
        override fun now() = 0L
        override fun uuid() = "00000000-0000-0000-0000-000000000001"
        override fun request(hub: HubPairing, body: String, callback: GroupReplyCallback) = Unit
        override fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback) = object : GroupSubscription { override fun cancel() = Unit }
        override fun share(text: String, fileName: String) = Unit
        override fun openLink(url: String) = Unit
        override fun importMenu(callback: GroupReplyCallback) = Unit
        override fun scanPairing(callback: GroupReplyCallback) = Unit
        override fun discover(callback: GroupReplyCallback) = Unit
    }

    @Test fun publicApisUseTheirUrlWhileLanHubsCanFollowTheirPinnedCertificate() {
        val controller = GroupController(Device())
        assertTrue(controller.sameHub(HubPairing("https://api.foodrun.example"), HubPairing("https://API.foodrun.example/")))
        assertFalse(controller.sameHub(HubPairing("https://api.foodrun.example"), HubPairing("https://another.example")))
        assertTrue(controller.sameHub(HubPairing("https://192.168.1.20:8443", "abc"), HubPairing("https://192.168.1.40:8443", "abc")))
        assertFalse(controller.sameHub(HubPairing("https://192.168.1.20:8443", "abc"), HubPairing("https://192.168.1.20:8443", "def")))
    }
}
