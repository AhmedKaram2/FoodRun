package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class ModerationServiceTest {
    private class Identity : IdentityProvider {
        val profiles = mutableMapOf<String, FoodProfile>()
        override fun signIn(email: String, password: String, register: Boolean) = CloudIdentity(email.substringBefore('@'), "token", email = email)
        override fun exchange(idToken: String) = CloudIdentity(idToken, "token")
        override fun refresh(refreshToken: String) = error("Unused")
        override fun profile(identity: CloudIdentity) = profiles[identity.userId]
        override fun saveProfile(identity: CloudIdentity, profile: FoodProfile) { profiles[identity.userId] = profile }
        override fun resetPassword(email: String) = Unit
        override fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String) = Unit
    }
    private fun token(f: RoomFixture, email: String) = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY,
        identity = IdentityRequest(IdentityAction.SIGN_IN, email = email, password = "fixture-password"))).identityToken

    @Test fun temporaryBlockAppliesToExistingRoomTokensAndExpiresWithoutAdminAction() = RoomFixture(Identity()).use { f ->
        val member = f.join("member")
        val identityToken = token(f, "member@example.test")
        val admin = AdminService(f.db, f.service, { f.now })
        admin.mutateUser(AdminUserMutation(userId = "member", action = "block", durationHours = 6, reason = "Repeated disruption"), "admin")
        assertTrue(f.service.execute(RoomCommand(commandId = f.id(), kind = CommandKind.HOME, identityToken = identityToken)).ok)
        assertTrue(token(f, "member@example.test").isNotBlank(), "Blocked users can sign in normally")
        for (command in listOf(
            RoomCommand(commandId = f.id(), kind = CommandKind.SNAPSHOT, roomId = f.owner.room!!.id, token = member.token),
            RoomCommand(commandId = f.id(), kind = CommandKind.PARTICIPATE, roomId = f.owner.room!!.id, token = member.token, flag = false),
        )) {
            val reply = f.service.execute(command)
            assertFalse(reply.ok); assertEquals("ROOM_BLOCKED", reply.code)
            assertEquals(f.now + 6 * 3_600_000L, reply.accessBlock!!.until)
            assertEquals(6, reply.accessBlock!!.durationHours)
        }
        f.restart(); f.now += 6 * 3_600_000L
        assertTrue(f.service.execute(RoomCommand(commandId = f.id(), kind = CommandKind.SNAPSHOT, roomId = f.owner.room!!.id, token = member.token)).ok)
    }

    @Test fun ownerRequestsNeedAdminReviewAndCannotBeDuplicatedOrSelfApplied() = RoomFixture(Identity()).use { f ->
        val member = f.join("member")
        val ownerToken = token(f, "fixture-owner@example.test")
        val command = f.command(f.owner, CommandKind.REQUEST_BLOCK).copy(identityToken = ownerToken,
            memberId = member.memberId, amount = 24, text = "Repeatedly disrupting the group")
        assertTrue(f.service.execute(command).ok)
        val admin = AdminService(f.db, f.service, { f.now })
        assertFalse(admin.dashboard().users.single { it.id == "member" }.disabled)
        assertTrue(f.service.execute(command).ok, "The same request is idempotent")
        assertFalse(f.service.execute(command.copy(commandId = f.id())).ok)
        assertFalse(f.service.execute(command.copy(commandId = f.id(), token = member.token)).ok)
        assertFalse(f.service.execute(command.copy(commandId = f.id(), memberId = f.owner.memberId)).ok)
        val pending = admin.dashboard().blockRequests.single()
        admin.reviewBlockRequest(AdminBlockDecision(pending.id, "approve"), "admin")
        assertFalse(admin.dashboard().users.single { it.id == "member" }.disabled, "Owner requests restrict only their room")
        assertTrue(admin.dashboard().users.single { it.id == "member" }.roomBlocks.containsKey(f.owner.room!!.id))
        assertTrue(token(f, "member@example.test").isNotEmpty())
        val otherRoom = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.CREATE, name = "Member", text = "Other room", restaurant = f.restaurant, identityToken = token(f, "member@example.test")))
        assertNotNull(otherRoom.room)
        assertEquals("approved", admin.dashboard().blockRequests.single().status)
        assertFails { admin.reviewBlockRequest(AdminBlockDecision(pending.id, "approve"), "admin") }
    }

    @Test fun rejectionDoesNotBlockAndAdminCannotBlockThemself() = RoomFixture(Identity()).use { f ->
        val member = f.join("member")
        val command = f.command(f.owner, CommandKind.REQUEST_BLOCK).copy(identityToken = token(f, "fixture-owner@example.test"), memberId = member.memberId, amount = 1, text = "Please review this member")
        f.execute(command)
        val admin = AdminService(f.db, f.service, { f.now })
        admin.reviewBlockRequest(AdminBlockDecision(command.commandId, "reject"), "admin")
        assertTrue(f.state(member).ok)
        assertFalse(admin.dashboard().users.single { it.id == "member" }.disabled)
        assertFails { admin.mutateUser(AdminUserMutation(userId = "fixture-owner", action = "block"), "fixture-owner") }
    }

    @Test fun cleanupPreviewProtectsActiveOrdersAndInvalidatesStaleSelections() = RoomFixture().use { f ->
        val admin = AdminService(f.db, f.service, { f.now })
        val request = AdminCleanupRequest(olderThanDays = 0)
        assertEquals(0, admin.cleanupPreview(request).count)
        f.send(f.owner, CommandKind.CANCEL) { it.copy(text = "Old test room") }
        val preview = admin.cleanupPreview(request)
        assertEquals(1, preview.count)
        assertFails { admin.cleanup(request.copy(previewToken = preview.previewToken, confirmation = "wrong"), "admin") }
        f.send(f.owner, CommandKind.NEXT_ORDER)
        assertFails { admin.cleanup(request.copy(previewToken = preview.previewToken, confirmation = "DELETE"), "admin") }
        assertNotNull(f.db.room(f.owner.room!!.id))
        f.send(f.owner, CommandKind.CANCEL) { it.copy(text = "Finished again") }
        val latest = admin.cleanupPreview(request)
        assertEquals(1, admin.cleanup(request.copy(previewToken = latest.previewToken, confirmation = "DELETE"), "admin").removedCount)
        assertNull(f.db.room(f.owner.room!!.id))
        assertTrue(f.db.allHistory().isEmpty())
        assertNull(f.db.session(RoomService.hash(f.owner.token)))
        assertTrue(admin.dashboard().activity.any { it.action.startsWith("cleanup:") })
    }

    @Test fun cleanupDoesNotRemovePlacedUnsettledRooms() = RoomFixture().use { f ->
        f.placed()
        val admin = AdminService(f.db, f.service, { f.now })
        assertEquals(0, admin.cleanupPreview(AdminCleanupRequest(olderThanDays = 0)).count)
        assertFails { admin.mutateRoom(AdminRoomMutation(f.owner.room!!.id, "delete", f.state().room!!.revision, f.owner.room!!.code), "admin") }
        assertNotNull(f.db.room(f.owner.room!!.id))
    }

    @Test fun createAndRemoveFoodRunUserRetainsSharedIdentityAndPreventsReentry() = RoomFixture(Identity()).use { f ->
        val provider = Identity()
        val admin = AdminService(f.db, f.service, { f.now }, provider)
        admin.mutateUser(AdminUserMutation(action = "create", name = "New member", email = "new@example.test", password = "fixture-password", phone = "+971501234567"), "admin")
        assertEquals("New member", admin.dashboard().users.single { it.id == "new" }.name)
        assertNotNull(provider.profiles["new"])
        admin.mutateUser(AdminUserMutation(userId = "new", action = "remove", confirmation = "new"), "admin")
        assertTrue(admin.dashboard().users.single { it.id == "new" }.removed)
        assertNotNull(provider.profiles["new"], "Shared Firebase account data is preserved")
        val signedIn = f.service.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY,
            identity = IdentityRequest(IdentityAction.SIGN_IN, email = "new@example.test", password = "fixture-password")))
        assertEquals("ACCOUNT_BLOCKED", signedIn.code)
        admin.mutateUser(AdminUserMutation(userId = "new", action = "restore"), "admin")
        assertTrue(token(f, "new@example.test").isNotEmpty())
    }
    @Test fun adminCanRenameAUserAndTheNextSignInSyncsTheCloudProfile() {
        val provider = Identity()
        RoomFixture(provider).use { f ->
            val admin = AdminService(f.db, f.service, { f.now }, provider)
            admin.mutateUser(AdminUserMutation(action = "create", name = "Old name", email = "rename@example.test", password = "fixture-password", phone = "+971501234567"), "admin")
            val firstSignIn = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY,
                identity = IdentityRequest(IdentityAction.SIGN_IN, email = "rename@example.test", password = "fixture-password")))
            val joined = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.JOIN, code = f.owner.room!!.code,
                name = "Old name", identityToken = firstSignIn.identityToken))

            admin.mutateUser(AdminUserMutation(userId = "rename", action = "rename", name = "New name"), "admin")

            assertEquals("New name", admin.dashboard().users.single { it.id == "rename" }.name)
            assertEquals("New name", f.db.room(f.owner.room!!.id)!!.members.single { it.id == joined.memberId }.name)
            val signedIn = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY,
                identity = IdentityRequest(IdentityAction.SIGN_IN, email = "rename@example.test", password = "fixture-password")))
            assertEquals("New name", signedIn.home!!.profile.name)
            assertEquals("New name", provider.profiles.getValue("rename").name)
            assertNull(f.db.record("admin:profile-name:rename"))
        }
    }
    @Test fun historyCleanupPublishesDeletionForConnectedClients() = RoomFixture().use { f ->
        f.send(f.owner, CommandKind.CANCEL) { it.copy(text = "Finished meal") }
        f.send(f.owner, CommandKind.NEXT_ORDER)
        assertEquals(1, f.state().history.size)
        val admin = AdminService(f.db, f.service, { f.now })
        val request = AdminCleanupRequest(scope = "history", olderThanDays = 0)
        val preview = admin.cleanupPreview(request)
        assertEquals(1, admin.cleanup(request.copy(previewToken = preview.previewToken, confirmation = "DELETE"), "admin").removedCount)
        val snapshot = f.state()
        assertTrue(snapshot.history.isEmpty())
        assertEquals(setOf(1L), snapshot.deletedHistoryNumbers)
        assertNotNull(snapshot.room)
    }

}
