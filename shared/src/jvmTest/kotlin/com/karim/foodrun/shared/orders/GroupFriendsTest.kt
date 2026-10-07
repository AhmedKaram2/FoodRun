package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlin.test.*

class GroupFriendsTest {
    private class Device : GroupPlatform {
        var time = 1000L; var count = 0
        val requests = mutableListOf<Triple<HubPairing, RoomCommand, GroupReplyCallback>>()
        override fun read(key: String) = ""
        override fun write(key: String, value: String) = true
        override fun now() = time
        override fun uuid() = "friends-command-${++count}-123456789"
        override fun request(hub: HubPairing, body: String, callback: GroupReplyCallback) { requests += Triple(hub, orderJson.decodeFromString(body), callback) }
        override fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback): GroupSubscription = object : GroupSubscription { override fun cancel() = Unit }
        override fun share(text: String, fileName: String) = Unit
        override fun openLink(url: String) = Unit
        override fun importMenu(callback: GroupReplyCallback) = Unit
        override fun scanPairing(callback: GroupReplyCallback) = Unit
        override fun discover(callback: GroupReplyCallback) = Unit
    }
    private val favourite = FriendGroup("friends", "Office", members = listOf(FriendContact("bob@example.test", "bob", "Bob")))
    private fun controller(device: Device) = GroupController(device).apply {
        library = library.copy(home = HomePayload(FoodProfile("me", "Me"), friendGroups = listOf(favourite, favourite.copy(id = "other", favourite = false))),
            identityHub = HubPairing("https://account.example.test"), selectedHub = HubPairing("https://lan.example.test"), identityToken = "account-session")
    }
    @Test fun emailLookupUsesTheAccountHubAndSavesMembersOnTheFriendsPage() {
        val device = Device(); val c = controller(device)
        c.dispatch(GroupAction.FRIENDS_ACTION, "new")
        c.update(GroupFieldKey.FRIEND_GROUP_NAME, "New group"); c.update(GroupFieldKey.FRIEND_EMAIL, "NEW@example.test")
        c.dispatch(GroupAction.FRIENDS_ACTION, "add")
        val read = device.requests.last()
        assertEquals("https://account.example.test", read.first.url)
        assertEquals("new@example.test", read.second.text)
        assertTrue(read.second.friendsDetails)
        read.third.complete(orderJson.encodeToString(RoomReply(friendContact = FriendContact("new@example.test"))), "")
        assertEquals(GroupPage.FRIENDS, c.page)
        assertTrue(c.state.cards.any { it.id == "friend-email:new@example.test" })
        c.dispatch(GroupAction.FRIENDS_ACTION, "save")
        val save = device.requests.last()
        assertEquals(CommandKind.SAVE_FRIEND_GROUP, save.second.kind)
        assertEquals("new@example.test", save.second.friendGroup!!.members.single().email)
        save.third.complete(orderJson.encodeToString(RoomReply(home = c.library.home)), "")
        assertEquals(GroupPage.FRIENDS, c.page)
    }
    @Test fun favouriteSelectorAndCountdownUseServerTimeAndCreationIncludesTimer() {
        val device = Device(); val c = controller(device)
        val restaurant = Restaurant("restaurant", "Kitchen", contact = RestaurantContact("+971501234567"))
        c.selectedRestaurant = RestaurantExport(exportId = "restaurant", restaurant = restaurant)
        c.library = c.library.copy(selectedHub = c.library.identityHub)
        c.page = GroupPage.SETUP
        c.update(GroupFieldKey.NAME, "Me"); c.update(GroupFieldKey.ROOM_NAME, "Lunch")
        c.update(GroupFieldKey.FRIEND_GROUP_CHOICE, "friends"); c.update(GroupFieldKey.JOIN_TIMER, "true"); c.update(GroupFieldKey.JOIN_TIMER_MINUTES, "5")
        assertEquals(listOf("", "friends"), c.state.fields.single { it.key == GroupFieldKey.FRIEND_GROUP_CHOICE }.choices.map { it.value })
        c.dispatch(GroupAction.CREATE_ROOM)
        val command = device.requests.last().second
        assertEquals("friends", command.friendGroupId); assertEquals(5, command.joinTimerMinutes)
        val room = Room("room", "123456", "me", "Lunch", restaurant, members = listOf(Member("me", "Me", approved = true)), joinDeadlineAt = 6000)
        c.session = StoredSession(c.library.identityHub!!, "room", "token", "me", "Lunch")
        c.reply = RoomReply(room = room, serverTime = 1000); c.page = GroupPage.ROOM
        assertTrue(c.state.hasJoinTimer)
        assertTrue(c.state.cards.single { it.id == "join-timer" }.detail.contains("0:05"))
        device.time = 6000; c.tickAccessBlock()
        assertFalse(c.state.hasJoinTimer)
        assertTrue(c.state.cards.single { it.id == "join-timer" }.detail.contains("New joins are closed"))
    }
}
