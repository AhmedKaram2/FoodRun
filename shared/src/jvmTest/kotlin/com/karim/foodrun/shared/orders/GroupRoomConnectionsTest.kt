package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import java.util.UUID
import kotlin.test.*

class GroupRoomConnectionsTest {
    private val hub = HubPairing("https://example.test")
    private fun room(id: String, phase: RoomPhase, number: Long = 1) = Room(id, "123456", "me", id, Restaurant("r", "Kitchen", menu = Menu()),
        phase = phase, members = listOf(Member("me", "Owner", approved = true)), orderNumber = number)
    private fun summary(room: Room) = AccountRoom(room.id, room.name, "me", "token-${room.id}", room.phase, room.orderNumber)
    private class Device : GroupPlatform {
        val watches = linkedMapOf<Int, Pair<RoomCommand, GroupReplyCallback>>()
        val requests = mutableListOf<RoomCommand>()
        val rooms = mutableMapOf<String, Room>()
        var sequence = 0
        override fun read(key: String) = ""
        override fun write(key: String, value: String) = true
        override fun now() = 1_800_000_000_000L
        override fun uuid() = UUID.randomUUID().toString()
        override fun request(hub: HubPairing, body: String, callback: GroupReplyCallback) {
            val command = orderJson.decodeFromString<RoomCommand>(body); requests += command
            if (command.kind == CommandKind.SNAPSHOT) callback.complete(orderJson.encodeToString(RoomReply(room = rooms.getValue(command.roomId), memberId = "me")), "")
        }
        override fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback): GroupSubscription {
            val id = ++sequence; watches[id] = orderJson.decodeFromString<RoomCommand>(body) to callback
            return object : GroupSubscription { override fun cancel() { watches.remove(id) } }
        }
        override fun share(text: String, fileName: String) = Unit
        override fun openLink(url: String) = Unit
        override fun importMenu(callback: GroupReplyCallback) = Unit
        override fun scanPairing(callback: GroupReplyCallback) = Unit
        override fun discover(callback: GroupReplyCallback) = Unit
        fun liveRooms() = watches.values.filter { it.first.kind == CommandKind.SNAPSHOT }.map { it.first.roomId }.toSet()
        fun home(value: HomePayload) = watches.values.first { it.first.kind == CommandKind.HOME }.second.complete(orderJson.encodeToString(RoomReply(home = value)), "")
    }
    @Test fun backgroundConnectionsFollowRoomLifecycleWithoutReconnectingOtherRooms() {
        val device = Device(); val c = GroupController(device)
        val a = room("a", RoomPhase.COLLECTING); val b = room("b", RoomPhase.FULFILLED); val old = room("old", RoomPhase.ARCHIVED)
        device.rooms.putAll(listOf(a, b, old).associateBy { it.id })
        var home = HomePayload(FoodProfile(name = "Owner", phone = "+971501234567"), rooms = listOf(a, b, old).map(::summary))
        c.library = c.library.copy(identityHub = hub, identityToken = "identity", home = home,
            sessions = home.rooms.map { StoredSession(hub, it.roomId, it.token, it.memberId, it.roomName) })
        c.foreground()
        assertEquals(setOf("a", "b"), device.liveRooms())
        assertEquals(listOf("old"), device.requests.map { it.roomId })
        val bConnection = device.watches.entries.single { it.value.first.roomId == "b" }.key
        device.home(home); assertEquals(1, device.requests.size)
        device.rooms["a"] = a.copy(phase = RoomPhase.ARCHIVED)
        home = home.copy(rooms = home.rooms.map { if (it.roomId == "a") summary(device.rooms.getValue("a")) else it })
        device.home(home)
        assertEquals(setOf("b"), device.liveRooms()); assertTrue(bConnection in device.watches)
        assertEquals(RoomPhase.ARCHIVED, c.library.snapshots["a"]!!.room!!.phase)
        device.rooms["old"] = old.copy(phase = RoomPhase.LOBBY, orderNumber = 2)
        device.home(home.copy(rooms = home.rooms.map { if (it.roomId == "old") summary(device.rooms.getValue("old")) else it }))
        assertEquals(setOf("b", "old"), device.liveRooms()); assertTrue(bConnection in device.watches)
        c.close(); assertTrue(device.watches.isEmpty())
    }
    @Test fun openingCompletedRoomUsesHttpAndNewOrderRestartsOnlyOneSocket() {
        val device = Device(); val c = GroupController(device)
        val old = room("old", RoomPhase.CANCELLED); device.rooms[old.id] = old
        val member = summary(old); val saved = StoredSession(hub, old.id, member.token, member.memberId, old.name)
        val home = HomePayload(FoodProfile(name = "Owner"), rooms = listOf(member))
        c.library = c.library.copy(identityHub = hub, identityToken = "identity", home = home, sessions = listOf(saved))
        c.foreground(); c.resume(saved)
        assertTrue(device.liveRooms().isEmpty()); assertEquals(RoomPhase.CANCELLED, c.reply!!.room!!.phase)
        val next = old.copy(phase = RoomPhase.LOBBY, orderNumber = 2); device.rooms[old.id] = next
        device.home(home.copy(rooms = listOf(summary(next))))
        assertEquals(setOf("old"), device.liveRooms())
        assertEquals(1, device.watches.values.count { it.first.kind == CommandKind.SNAPSHOT })
        c.close()
    }
}
