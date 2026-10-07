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
        assertTrue(c.state.cards.any { it.id == "friend-email:email:new@example.test" })
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
        c.update(GroupFieldKey.FRIEND_GROUP_CHOICE, "me:friends"); c.update(GroupFieldKey.JOIN_TIMER, "true"); c.update(GroupFieldKey.JOIN_TIMER_MINUTES, "5")
        assertEquals(listOf("", "me:friends"), c.state.fields.single { it.key == GroupFieldKey.FRIEND_GROUP_CHOICE }.choices.map { it.value })
        c.dispatch(GroupAction.CREATE_ROOM)
        val command = device.requests.last().second
        assertEquals("friends", command.friendGroupId); assertEquals("me", command.friendGroupOwnerId); assertEquals(5, command.joinTimerMinutes)
        val room = Room("room", "123456", "me", "Lunch", restaurant, members = listOf(Member("me", "Me", approved = true)), joinDeadlineAt = 6000)
        c.session = StoredSession(c.library.identityHub!!, "room", "token", "me", "Lunch")
        c.reply = RoomReply(room = room, serverTime = 1000); c.page = GroupPage.ROOM
        assertTrue(c.state.hasJoinTimer)
        assertTrue(c.state.cards.single { it.id == "join-timer" }.detail.contains("0:05"))
        device.time = 6000; c.tickAccessBlock()
        assertFalse(c.state.hasJoinTimer)
        assertTrue(c.state.cards.single { it.id == "join-timer" }.detail.contains("New joins are closed"))
    }
    @Test fun joinedGroupsAppearForRoomCreationAndMatchingIdsKeepTheirOwners() {
        val device = Device(); val c = controller(device)
        c.library = c.library.copy(selectedHub = c.library.identityHub, home = c.library.home!!.copy(joinedFriendGroups = listOf(
            FriendGroupMembership(favourite.copy(favourite = false), "alice", "Alice"), FriendGroupMembership(favourite, "charlie", "Charlie"))))
        c.selectedRestaurant = RestaurantExport(exportId = "restaurant", restaurant = Restaurant("restaurant", "Kitchen", contact = RestaurantContact("+971501234567")))
        c.page = GroupPage.SETUP; c.update(GroupFieldKey.NAME, "Me"); c.update(GroupFieldKey.ROOM_NAME, "Lunch")
        assertEquals(listOf("", "me:friends", "alice:friends", "charlie:friends"), c.state.fields.single { it.key == GroupFieldKey.FRIEND_GROUP_CHOICE }.choices.map { it.value })
        c.update(GroupFieldKey.FRIEND_GROUP_CHOICE, "alice:friends"); c.dispatch(GroupAction.CREATE_ROOM)
        val sent = device.requests.last().second
        assertEquals(CommandKind.CREATE, sent.kind)
        assertEquals("friends", sent.friendGroupId)
        assertEquals("alice", sent.friendGroupOwnerId)
    }
    @Test fun leavingSelectedGroupBeforeCreationRequiresChoosingAgain() {
        val device = Device(); val c = controller(device)
        c.library = c.library.copy(selectedHub = c.library.identityHub, home = c.library.home!!.copy(joinedFriendGroups = listOf(FriendGroupMembership(favourite, "alice", "Alice"))))
        c.selectedRestaurant = RestaurantExport(exportId = "restaurant", restaurant = Restaurant("restaurant", "Kitchen", contact = RestaurantContact("+971501234567")))
        c.page = GroupPage.SETUP; c.update(GroupFieldKey.NAME, "Me"); c.update(GroupFieldKey.ROOM_NAME, "Lunch")
        c.update(GroupFieldKey.FRIEND_GROUP_CHOICE, "alice:friends")
        c.library = c.library.copy(home = c.library.home!!.copy(joinedFriendGroups = emptyList()))
        c.dispatch(GroupAction.CREATE_ROOM)
        assertTrue(device.requests.isEmpty())
    }
    @Test fun membersCanViewAndLeaveWithoutOwnerEditingControls() {
        val device = Device(); val c = controller(device)
        val joined = FriendGroupMembership(favourite.copy(revision = 3), "owner", "Owner")
        c.library = c.library.copy(home = c.library.home!!.copy(joinedFriendGroups = listOf(joined)))
        c.dispatch(GroupAction.FRIENDS_ACTION, "open")
        assertTrue(c.state.cards.any { it.id == "friend-joined:owner:friends" })
        c.dispatch(GroupAction.FRIENDS_ACTION, "view|owner|friends")
        assertTrue(c.state.fields.isEmpty())
        assertTrue(c.state.cards.any { it.title == "Bob" && it.detail == "bob@example.test" })
        assertEquals(listOf("leave|owner|friends", "open"), c.state.buttons.map { it.value })
        c.dispatch(GroupAction.FRIENDS_ACTION, "leave|owner|friends")
        val leave = device.requests.last().second
        assertEquals(CommandKind.LEAVE_FRIEND_GROUP, leave.kind)
        assertEquals("owner", leave.friendGroupOwnerId)
        assertEquals("friends", leave.friendGroupId)
        assertTrue(leave.friendMembershipDetails)
        device.requests.last().third.complete(orderJson.encodeToString(RoomReply(home = c.library.home!!.copy(joinedFriendGroups = emptyList()))), "")
        assertEquals(GroupPage.FRIENDS, c.page)
        assertTrue(c.state.cards.none { it.id.startsWith("friend-joined:") })
    }
    @Test fun ownerEditsKeepTheGroupRevisionAndRevokedMembershipClosesDetails() {
        val device = Device(); val c = controller(device)
        c.library = c.library.copy(home = c.library.home!!.copy(friendGroups = listOf(favourite.copy(revision = 4))))
        c.dispatch(GroupAction.FRIENDS_ACTION, "edit|friends")
        c.update(GroupFieldKey.FRIEND_GROUP_NAME, "Renamed")
        c.dispatch(GroupAction.FRIENDS_ACTION, "save")
        assertEquals(4L, device.requests.last().second.friendGroup!!.revision)
        assertEquals("Renamed", device.requests.last().second.friendGroup!!.name)
        c.library = c.library.copy(home = c.library.home!!.copy(joinedFriendGroups = listOf(FriendGroupMembership(favourite, "owner", "Owner"))))
        c.dispatch(GroupAction.FRIENDS_ACTION, "view|owner|friends")
        c.library = c.library.copy(home = c.library.home!!.copy(joinedFriendGroups = emptyList()))
        assertTrue(c.state.cards.none { it.id == "friend-view" })
        assertTrue(c.state.fields.any { it.key == GroupFieldKey.FRIEND_GROUP_NAME })
    }

    @Test fun nameSearchIsReadOnlyAndCannotReturnIntoAnotherIdentityOrPage() {
        val device = Device(); val c = controller(device)
        c.dispatch(GroupAction.FRIENDS_ACTION,"new"); c.update(GroupFieldKey.FRIEND_EMAIL,"bo")
        val search = device.requests.last(); assertEquals(CommandKind.FRIEND_SEARCH,search.second.kind); assertNull(c.library.pending); assertFalse(c.busy)
        search.third.complete(orderJson.encodeToString(RoomReply(friendContacts = listOf(FriendContact("bob@example.test","bob","Bob")))),"")
        assertTrue(c.state.cards.any { it.id == "friend-result:user:bob" })
        c.dispatch(GroupAction.FRIENDS_ACTION,"pick|user:bob"); c.update(GroupFieldKey.FRIEND_GROUP_NAME,"Friends"); c.dispatch(GroupAction.FRIENDS_ACTION,"save")
        assertEquals("bob",device.requests.last().second.friendGroup!!.members.single().userId)
        device.requests.last().third.complete(orderJson.encodeToString(RoomReply(home = c.library.home)),"")
        c.update(GroupFieldKey.FRIEND_EMAIL,"al"); val late = device.requests.last()
        c.page = GroupPage.HOME
        late.third.complete(orderJson.encodeToString(RoomReply(friendContacts = listOf(FriendContact("alice@example.test","alice","Alice")))),"")
        assertEquals(GroupPage.HOME,c.page); assertFalse(c.state.cards.any { it.id == "friend-result:user:alice" })
    }
}
