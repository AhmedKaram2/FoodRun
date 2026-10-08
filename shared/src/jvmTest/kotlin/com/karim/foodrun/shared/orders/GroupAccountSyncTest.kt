package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlin.test.*

class GroupAccountSyncTest {
    private class Watch(val command: RoomCommand, val callback: GroupReplyCallback) : GroupSubscription {
        var cancelled = false
        override fun cancel() { cancelled = true }
        fun reply(reply: RoomReply) = callback.complete(orderJson.encodeToString(reply), "")
    }
    private class Device : GroupPlatform {
        val watches = mutableListOf<Watch>()
        override fun read(key: String) = ""
        override fun write(key: String, value: String) = true
        override fun now() = 1000L
        override fun uuid() = "sync-command-123456789"
        override fun request(hub: HubPairing, body: String, callback: GroupReplyCallback) = Unit
        override fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback) = Watch(orderJson.decodeFromString(body), callback).also(watches::add)
        override fun share(text: String, fileName: String) = Unit
        override fun openLink(url: String) = Unit
        override fun importMenu(callback: GroupReplyCallback) = Unit
        override fun scanPairing(callback: GroupReplyCallback) = Unit
        override fun discover(callback: GroupReplyCallback) = Unit
    }

    @Test fun delayedHomeCannotReplaceANewerWalletSnapshot() {
        val device = Device(); val c = GroupController(device)
        val hub = HubPairing("https://api.example.test")
        c.library = c.library.copy(identityToken = "identity-token", identityHub = hub, selectedHub = hub)
        c.foreground()
        val homeWatch = device.watches.last { it.command.kind == CommandKind.HOME }
        val fresh = HomePayload(FoodProfile("me", "Current name"), wallet = WalletSnapshot(balances = listOf(WalletBalance("me", "Me", "holder", "Holder", "AED", 3900))))
        homeWatch.reply(RoomReply(home = fresh, serverTime = 2000))
        homeWatch.reply(RoomReply(home = fresh.copy(profile = fresh.profile.copy(name = "Old name"), wallet = WalletSnapshot()), serverTime = 1000))
        assertEquals("Current name", c.library.home!!.profile.name)
        assertEquals(3900L, c.library.home!!.wallet!!.balances.single().available)
        homeWatch.reply(RoomReply(home = fresh.copy(profile = fresh.profile.copy(name = "Newest name")), serverTime = 3000))
        assertEquals("Newest name", c.library.home!!.profile.name)
    }

    @Test fun homeRefreshReplacesTheOpenRoomsMemberAndTokenAndIgnoresOldCallbacks() {
        val device = Device(); val c = GroupController(device)
        val hub = HubPairing("https://api.example.test")
        val old = StoredSession(hub, "room", "old-token", "old-member", "Lunch")
        val room = Room("room", "123456", "payer", "Lunch", Restaurant("r", "R"),
            members = listOf(Member("old-member", "Old name", approved = true)))
        c.library = c.library.copy(identityToken = "identity-token", identityHub = hub, selectedHub = hub,
            sessions = listOf(old), snapshots = mapOf(room.id to RoomReply(room = room, memberId = old.memberId)))
        c.resume(old); c.foreground()
        val oldWatch = device.watches.last { it.command.kind == CommandKind.SNAPSHOT }
        val homeWatch = device.watches.last { it.command.kind == CommandKind.HOME }
        val home = HomePayload(FoodProfile("canonical", "New name"), rooms = listOf(AccountRoom(room.id, room.name, "canonical-member", "new-token", RoomPhase.LOBBY)),
            wallet = WalletSnapshot(balances = listOf(WalletBalance("canonical", "New name", "holder", "Holder", "AED", 5000))))
        homeWatch.reply(RoomReply(home = home))
        assertEquals("new-token", c.session!!.token)
        assertEquals("canonical-member", c.session!!.memberId)
        assertNull(c.reply)
        assertTrue(oldWatch.cancelled)
        assertNull(c.library.snapshots[room.id])
        val refreshed = room.copy(revision = 3, members = listOf(Member("canonical-member", "New name", approved = true)))
        device.watches.last { it.command.kind == CommandKind.SNAPSHOT }.reply(RoomReply(room = refreshed, memberId = "canonical-member"))
        oldWatch.reply(RoomReply(room = room.copy(revision = 100), memberId = old.memberId))
        assertEquals("New name", c.reply!!.room!!.members.single().name)
        assertEquals(5000L, c.library.home!!.wallet!!.balances.single().available)
        assertEquals(3L, c.reply!!.room!!.revision)
    }

    @Test fun tokenRenewalKeepsTheSameMemberButReconnectsTheOpenRoom() {
        val device = Device(); val c = GroupController(device); val hub = HubPairing("https://api.example.test")
        val saved = StoredSession(hub, "room", "expired-token", "member", "Lunch")
        c.library = c.library.copy(identityToken = "identity-token", identityHub = hub, selectedHub = hub, sessions = listOf(saved))
        c.resume(saved); c.foreground()
        device.watches.last { it.command.kind == CommandKind.HOME }.reply(RoomReply(home = HomePayload(FoodProfile("user", "User"),
            rooms = listOf(AccountRoom("room", "Lunch", "member", "renewed-token", RoomPhase.LOBBY)))))
        assertEquals("renewed-token", c.session!!.token)
        assertEquals("renewed-token", device.watches.last { it.command.kind == CommandKind.SNAPSHOT }.command.token)
        assertEquals("member", c.session!!.memberId)
    }
}
