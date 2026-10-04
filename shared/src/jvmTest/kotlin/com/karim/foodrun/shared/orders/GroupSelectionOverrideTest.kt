package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import java.util.UUID
import kotlin.test.*

class GroupSelectionOverrideTest {
    private val hub = HubPairing("https://example.test")
    private class Device : GroupPlatform {
        var admin = true
        var code = "SELECTION_OVERRIDE_UNLOCKED"
        var request: RoomCommand? = null
        val saved = mutableListOf<String>()
        override fun read(key: String) = ""
        override fun write(key: String, value: String): Boolean { saved += value; return true }
        override fun now() = 1000L
        override fun uuid() = UUID.randomUUID().toString()
        override fun adminRequest(hub: HubPairing, body: String, callback: GroupReplyCallback) {
            callback.complete(orderJson.encodeToString(NativeAdminReply(ok = admin)), "")
        }
        override fun request(hub: HubPairing, body: String, callback: GroupReplyCallback) {
            request = orderJson.decodeFromString(body)
            callback.complete(orderJson.encodeToString(RoomReply(code = code)), "")
        }
        override fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback) = object : GroupSubscription { override fun cancel() = Unit }
        override fun share(text: String, fileName: String) = Unit
        override fun openLink(url: String) = Unit
        override fun importMenu(callback: GroupReplyCallback) = Unit
        override fun scanPairing(callback: GroupReplyCallback) = Unit
        override fun discover(callback: GroupReplyCallback) = Unit
    }
    private fun controller(device: Device): GroupController {
        val c = GroupController(device)
        val room = Room("room", "123456", "owner", "Lunch", Restaurant("r", "Kitchen", openOrdering = true),
            members = listOf(Member("owner", "Owner", approved = true, eligible = true),
                Member("ahmed", "Ahmed", approved = true, eligible = true), Member("ineligible", "Skipping", approved = true, eligible = false)))
        c.session = StoredSession(hub, room.id, "room-token", "ahmed", room.name)
        c.library = c.library.copy(identityToken = "identity-token", identityHub = hub)
        c.reply = RoomReply(room = room, memberId = "ahmed")
        c.page = GroupPage.ROOM
        c.administration.checkAccess()
        return c
    }
    @Test fun optionIsHiddenUntilLongPressAndSuccessfulServerUnlockWithoutSavingThePasscode() {
        val device = Device(); val c = controller(device)
        assertTrue(c.state.canOverrideSelection)
        assertTrue(c.state.buttons.none { it.action == GroupAction.OPEN_SELECTION_OVERRIDE })
        assertTrue(c.state.fields.none { it.key == GroupFieldKey.SELECTION_WINNER })
        c.dispatch(GroupAction.OPEN_SELECTION_OVERRIDE)
        assertTrue(c.state.fields.single().secret)
        assertEquals(GroupFieldKey.SELECTION_PASSCODE, c.state.fields.single().key)
        c.update(GroupFieldKey.SELECTION_PASSCODE, "5457")
        c.dispatch(GroupAction.UNLOCK_SELECTION_OVERRIDE)
        assertEquals("5457", device.request!!.text)
        assertEquals("identity-token", device.request!!.identityToken)
        assertNull(c.library.pending)
        assertTrue(device.saved.none { "5457" in it })
        assertEquals("", c.text(GroupFieldKey.SELECTION_PASSCODE))
        assertEquals(listOf("", "owner", "ahmed"), c.state.fields.single().choices.map { it.value })
        device.code = "SELECTION_OVERRIDE_SAVED"
        c.update(GroupFieldKey.SELECTION_WINNER, "owner")
        c.dispatch(GroupAction.SAVE_SELECTION_OVERRIDE)
        assertEquals(CommandKind.SET_SELECTION_OVERRIDE, device.request!!.kind)
        assertEquals("owner", device.request!!.memberId)
        assertNull(c.library.pending)
        c.dispatch(GroupAction.BACK)
        assertEquals(GroupPage.ROOM, c.state.page)
        c.dispatch(GroupAction.OPEN_SELECTION_OVERRIDE)
        assertEquals(GroupFieldKey.SELECTION_PASSCODE, c.state.fields.single().key)
    }
    @Test fun otherIdentityAndSpinningRoomsCannotExposeTheOption() {
        val device = Device().apply { admin = false }; val c = controller(device)
        assertFalse(c.state.canOverrideSelection)
        c.dispatch(GroupAction.OPEN_SELECTION_OVERRIDE)
        assertEquals(GroupPage.ROOM, c.state.page)
        assertNull(device.request)
        device.admin = true; c.library = c.library.copy(identityToken = "admin-token"); c.administration.checkAccess()
        assertTrue(c.state.canOverrideSelection)
        c.library = c.library.copy(identityToken = "different-token")
        assertFalse(c.state.canOverrideSelection)
        c.administration.checkAccess()
        c.reply = c.reply!!.copy(room = c.room().copy(phase = RoomPhase.SPINNING))
        assertFalse(c.state.canOverrideSelection)
    }
}
