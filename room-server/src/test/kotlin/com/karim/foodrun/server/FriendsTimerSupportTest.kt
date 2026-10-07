package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class FriendsTimerSupportTest {
    private class Provider : IdentityProvider {
        override fun signIn(email: String, password: String, register: Boolean) = CloudIdentity(email.substringBefore('@'), "id", "refresh", email.substringBefore('@'), email, true)
        override fun exchange(idToken: String) = CloudIdentity("admin", idToken, email = if(idToken == "admin") AdminService.ADMIN_EMAIL else "other@example.test", emailVerified = idToken != "unverified")
        override fun refresh(refreshToken: String) = error("Not used")
        override fun profile(identity: CloudIdentity): FoodProfile? = null
        override fun saveProfile(identity: CloudIdentity, profile: FoodProfile) = Unit
        override fun resetPassword(email: String) = Unit
        override fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String) = Unit
    }
    private fun login(f: RoomFixture, name: String) = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY,
        identity = IdentityRequest(IdentityAction.REGISTER, email = "$name@example.test", password = "fixture-password", profile = FoodProfile(name = name))))
    private fun command(f: RoomFixture, user: RoomReply, kind: CommandKind) = RoomCommand(commandId = f.id(), kind = kind, identityToken = user.identityToken, friendsDetails = true)
    private fun home(f: RoomFixture, user: RoomReply) = f.execute(command(f, user, CommandKind.HOME)).home!!

    @Test fun groupsArePrivateAndEmailsResolveAccountsWithoutTrustingSubmittedIds() = RoomFixture(Provider(), emailEnabled = true).use { f ->
        val owner = login(f, "Alice"); val bob = login(f, "Bob"); val other = login(f, "Other")
        val group = FriendGroup("group", "Lunch friends", members = listOf(FriendContact(" BOB@example.test ", "spoofed", "Fake"), FriendContact("new@example.test")))
        val save = command(f, owner, CommandKind.SAVE_FRIEND_GROUP).copy(friendGroup = group)
        val reply = f.execute(save)
        assertEquals("Bob", reply.home!!.friendGroups.single().members.first().userId)
        assertTrue(home(f, other).friendGroups.isEmpty())
        assertEquals(1, f.db.records("email-job:").size)
        f.execute(save); f.restart(); f.execute(save)
        assertEquals(1, f.db.records("email-job:").size)
        val email = f.service.nextEmail(100)!!
        assertEquals("new@example.test", email.address)
        assertTrue(email.job.body.contains("Lunch friends"))
        val mime = GmailEmailSender({ "fixture-token" }, "https://intrvioo.com", "https://api.example.test").mime(email)
        val body = String(java.util.Base64.getMimeDecoder().decode(mime.substringAfter("\r\n\r\n")), Charsets.UTF_8)
        assertTrue(body.contains("friend group creator's request"))
        assertFalse(body.contains("payment reminder"))
        f.service.finishEmail(email, EmailResult.SENT)
        val created = f.execute(command(f, owner, CommandKind.CREATE).copy(name = "Alice", text = "Office lunch", restaurant = f.restaurant, friendGroupId = "group", joinTimerMinutes = 5))
        assertEquals(f.now + 300_000, created.room!!.joinDeadlineAt)
        assertEquals("Office lunch", home(f, bob).invitations.single().roomName)
        assertEquals(2, f.db.records("email-job:").size)
        val roomEmail = f.service.nextEmail(100)!!
        assertTrue(roomEmail.job.body.contains(created.room!!.code))
        assertTrue(roomEmail.job.body.contains("https://intrvioo.com/?room="))
        assertFalse(f.service.execute(command(f, other, CommandKind.CREATE).copy(name = "Other", text = "Forged group", restaurant = f.restaurant, friendGroupId = "group")).ok)
        assertFalse(f.service.execute(command(f, owner, CommandKind.FRIEND_LOOKUP).copy(text = "not-an-email")).ok)
        assertTrue(orderJson.encodeToString(reply.forClient(false)).let { "friendGroups" !in it && "friendContact" !in it })
    }
    @Test fun unselectedGroupsSendNoRoomEmailAndRemovedGroupRevokesPendingInvitations() = RoomFixture(Provider(), emailEnabled = true).use { f ->
        val owner = login(f, "Alice")
        f.execute(command(f, owner, CommandKind.SAVE_FRIEND_GROUP).copy(friendGroup = FriendGroup("group", "Friends", members = listOf(FriendContact("new@example.test")))))
        val sent = f.service.nextEmail(100)!!; f.service.finishEmail(sent, EmailResult.SENT)
        f.execute(command(f, owner, CommandKind.CREATE).copy(name = "Alice", text = "Private lunch", restaurant = f.restaurant))
        assertNull(f.service.nextEmail(100))
        f.execute(command(f, owner, CommandKind.SAVE_FRIEND_GROUP).copy(friendGroup = FriendGroup("group", "Friends", members = listOf(FriendContact("another@example.test")))))
        f.execute(command(f, owner, CommandKind.DELETE_FRIEND_GROUP).copy(friendGroupId = "group"))
        assertNull(f.service.nextEmail(100))
        assertTrue(f.db.records("email-job:").isEmpty())
    }
    @Test fun timerSurvivesRestartSpinsOnceAndClosesOnlyNewJoins() = RoomFixture(Provider()).use { f ->
        val owner = login(f, "Alice"); val bob = login(f, "Bob"); val late = login(f, "Late")
        val created = f.execute(command(f, owner, CommandKind.CREATE).copy(name = "Alice", text = "Timed lunch", restaurant = f.restaurant, joinTimerMinutes = 1))
        val joined = f.execute(command(f, bob, CommandKind.JOIN).copy(name = "Bob", code = created.room!!.code))
        f.now = created.room!!.joinDeadlineAt - 1; f.service.tick()
        assertEquals(RoomPhase.LOBBY, f.db.room(created.room!!.id)!!.phase)
        f.restart(); f.now++; f.service.tick()
        val spinning = f.db.room(created.room!!.id)!!
        assertEquals(RoomPhase.SPINNING, spinning.phase)
        assertEquals(setOf(created.memberId, joined.memberId), spinning.spin!!.memberIds.toSet())
        f.service.tick(); assertEquals(spinning.spin!!.id, f.db.room(spinning.id)!!.spin!!.id)
        assertFalse(f.service.execute(command(f, late, CommandKind.JOIN).copy(name = "Late", code = spinning.code)).ok)
        assertEquals(joined.memberId, f.execute(command(f, bob, CommandKind.JOIN).copy(name = "Renamed Bob", code = spinning.code)).memberId)
        f.now = spinning.spin!!.endAt; f.service.tick()
        assertEquals(RoomPhase.ACCEPTING, f.db.room(spinning.id)!!.phase)
        assertTrue(orderJson.encodeToString(f.service.snapshot(spinning.id, created.token).forClient(false)).let { "joinDeadlineAt" !in it && "joinTimerFinishedAt" !in it })
    }
    @Test fun timerFinishesRestaurantPollAndRespectsMemberConsentAndManualSelection() = RoomFixture().use { f ->
        val created = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.CREATE, name = "Owner", text = "Poll", restaurant = f.restaurant,
            restaurants = listOf(f.restaurant, f.restaurant.copy(id = "second", name = "Second")), joinTimerMinutes = 1))
        f.now = created.room!!.joinDeadlineAt; f.service.tick()
        assertFalse(f.db.room(created.room!!.id)!!.restaurantPollOpen)
        assertEquals(RoomPhase.SPINNING, f.db.room(created.room!!.id)!!.phase)
        val manual = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.CREATE, name = "Owner", text = "Manual", restaurant = f.restaurant, joinTimerMinutes = 1))
        val selected = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.SELECT_PAYER, roomId = manual.room!!.id, token = manual.token,
            expectedRevision = manual.room!!.revision, expectedOrderNumber = 1, memberId = manual.memberId))
        f.now = manual.room!!.joinDeadlineAt; f.service.tick()
        assertEquals(RoomPhase.COLLECTING, f.db.room(selected.room!!.id)!!.phase)
        assertNull(f.db.room(selected.room!!.id)!!.spin)
    }
    @Test fun ownerSupportUsesSeparateRevocableRoomTokensAndCannotAccessAdminAsTarget() = RoomFixture(Provider()).use { f ->
        val target = login(f, "Bob")
        val joined = f.execute(command(f, target, CommandKind.JOIN).copy(name = "Bob", code = f.owner.room!!.code))
        val normal = home(f, target).rooms.single().token
        val admin = AdminService(f.db, f.service, { f.now }, Provider())
        assertFails { admin.startSupport("Bearer other", AdminSupportRequest("Bob")) }
        assertFails { admin.startSupport("Bearer unverified", AdminSupportRequest("Bob")) }
        val support = admin.startSupport("Bearer admin", AdminSupportRequest("Bob"))
        assertEquals("Bob", support.home!!.profile.userId)
        val borrowed = support.home!!.rooms.single().token
        assertNotEquals(normal, borrowed)
        assertEquals(normal, home(f, target).rooms.single().token)
        assertFails { f.service.nativeAdminToken(support.identityToken) }
        assertFalse(f.service.notificationRequest(NotificationRequest(support.identityToken), true).pushAvailable)
        assertFails { f.service.notificationRequest(NotificationRequest(support.identityToken, action = "register", token = "fixture-push-token", installationId = "owner-device"), true) }
        val change = RoomCommand(commandId = f.id(), kind = CommandKind.READY, roomId = f.owner.room!!.id, token = borrowed, expectedOrderNumber = 1,
            identityToken = support.identityToken, flag = true, eligible = true)
        f.execute(change)
        assertTrue(f.db.records("admin:audit:").any { orderJson.decodeFromString<AdminAuditEvent>(it.second).let { it.actorId == "admin" && it.action == "support:READY" } })
        f.execute(command(f, support, CommandKind.IDENTITY).copy(identity = IdentityRequest(IdentityAction.SIGN_OUT)))
        assertFalse(f.service.execute(change.copy(commandId = f.id(), identityToken = "")).ok)
        assertEquals(joined.memberId, f.service.snapshot(f.owner.room!!.id, normal).memberId)
        val expiring = admin.startSupport("Bearer admin", AdminSupportRequest("Bob"))
        f.now += 30 * 60_000; f.restart()
        assertFalse(f.service.execute(command(f, expiring, CommandKind.HOME)).ok)
        assertFalse(f.service.execute(change.copy(commandId = f.id(), token = expiring.home!!.rooms.single().token, identityToken = "")).ok)
    }
}
