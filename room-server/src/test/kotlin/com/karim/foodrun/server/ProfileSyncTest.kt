package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class ProfileSyncTest {
    private class Identity : IdentityProvider {
        val profiles = mutableMapOf<String, FoodProfile>()
        var offline = false
        var failedUserId = ""
        override fun signIn(email: String, password: String, register: Boolean) = CloudIdentity(email.substringBefore('@'), "token", email = email)
        override fun exchange(idToken: String) = CloudIdentity(idToken, "token")
        override fun refresh(refreshToken: String) = error("Unused")
        override fun profile(identity: CloudIdentity) = profiles[identity.userId]
        override fun saveProfile(identity: CloudIdentity, profile: FoodProfile) {
            check(!offline && identity.userId != failedUserId) { "Offline" }; profiles[identity.userId] = profile
        }
        override fun resetPassword(email: String) = Unit
        override fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String) = Unit
    }
    private val userId = "fixture-owner"
    private val aani = ReceivingAccount("aani", "Owner", "Aani", "+971509876543", method = PaymentMethod.AANI)
    private fun signIn(f: RoomFixture) = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY,
        identity = IdentityRequest(IdentityAction.SIGN_IN, email = "$userId@example.test", password = "fixture-password")))
    private fun profile(f: RoomFixture) = orderJson.decodeFromString<FoodProfile>(f.db.record("profile:$userId")!!)
    private fun linkedCopy(f: RoomFixture, id: String, phase: RoomPhase = RoomPhase.PLACED, payerId: String? = f.owner.memberId): Room {
        val copy = f.state().room!!.copy(id = id, code = id.takeLast(6), phase = phase, payerId = payerId)
        f.db.save(copy)
        f.db.putRecord("membership:$userId:$id", orderJson.encodeToString(AccountRoom(id, copy.name, f.owner.memberId, f.owner.token)))
        return copy
    }

    @Test fun aFailingCloudProfileDoesNotBlockOtherUsers() {
        val provider = Identity()
        RoomFixture(provider).use { f ->
            f.join("second-user")
            val first = profile(f).copy(phone = "+971501234567")
            val second = orderJson.decodeFromString<FoodProfile>(f.db.record("profile:second-user")!!).copy(phone = "+971509876543")
            f.db.transaction {
                ProfileUpdates(f.db) { f.now }.save(first)
                ProfileUpdates(f.db) { f.now }.save(second)
            }
            provider.failedUserId = userId
            f.service.syncCloud()
            f.service.syncCloud()
            assertNotNull(f.db.record("profile-sync:$userId"))
            assertNull(f.db.record("profile-sync:second-user"))
            assertEquals(second, provider.profiles["second-user"])
            provider.failedUserId = ""
            f.service.syncCloud()
            assertNull(f.db.record("profile-sync:$userId"))
        }
    }

    @Test fun roomPaymentEditsUpdateProfileAndOtherActivePayerRoomsWithoutChangingPastTransfers() = RoomFixture(Identity()).use { f ->
        val member = f.placed(); f.pay()
        f.send(member, CommandKind.DECLARE_TRANSFER) { it.copy(amount = 100, text = "Already sent") }
        val before = f.state().room!!
        val active = linkedCopy(f, "room222222")
        val archived = linkedCopy(f, "room333333", RoomPhase.ARCHIVED)
        val notPayer = linkedCopy(f, "room444444", payerId = member.memberId)
        val event = f.service.eventVersion(CommandKind.SNAPSHOT, active.id)
        val saved = f.send(f.owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = aani) }.room!!
        assertEquals(aani.identifier, profile(f).payment!!.identifier)
        assertEquals(aani.identifier, saved.account!!.identifier)
        assertEquals(aani.identifier, f.db.room(active.id)!!.account!!.identifier)
        assertEquals(archived, f.db.room(archived.id))
        assertEquals(notPayer.account, f.db.room(notPayer.id)!!.account)
        assertEquals(before.transfers, saved.transfers)
        assertEquals(before.carts, saved.carts)
        assertEquals(before.quoteRevision, saved.quoteRevision)
        assertTrue(f.service.eventVersion(CommandKind.SNAPSHOT, active.id) > event)
        f.send(f.owner, CommandKind.CONFIRM_TRANSFER) { it.copy(transferId = saved.transfers.single().id) }
        assertEquals(TransferStatus.CONFIRMED, f.state().room!!.transfers.single().status)
        f.restart()
        assertEquals(aani.identifier, profile(f).payment!!.identifier)
        assertEquals(aani.identifier, f.db.room(active.id)!!.account!!.identifier)
    }

    @Test fun profilePaymentEditsSyncRoomsAndRetryCloudAfterAnOutage() {
        val provider = Identity()
        RoomFixture(provider).use { f ->
            f.placed()
            val other = linkedCopy(f, "room222222", RoomPhase.COLLECTING)
            val token = signIn(f).identityToken
            provider.offline = true
            val updated = profile(f).copy(name = "Updated owner", phone = "+971501234567", payment = aani)
            val saved = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY, identityToken = token,
                identity = IdentityRequest(IdentityAction.SAVE_PROFILE, profile = updated)))
            assertEquals(aani, saved.home!!.profile.payment)
            assertEquals(aani.identifier, f.state().room!!.account!!.identifier)
            assertEquals(aani.identifier, f.db.room(other.id)!!.account!!.identifier)
            assertEquals("Updated owner", f.state().room!!.members.single { it.id == f.owner.memberId }.name)
            f.service.syncCloud()
            assertNotNull(f.db.record("profile-sync:$userId"))
            provider.offline = false
            f.service.syncCloud()
            assertNull(f.db.record("profile-sync:$userId"))
            assertEquals(updated, provider.profiles[userId])
            assertEquals(updated, signIn(f).home!!.profile)
        }
    }

    @Test fun adminEditsAllProfileFieldsAndCannotOverwriteAnotherUsersIdentityOrFavorites() {
        val provider = Identity()
        RoomFixture(provider).use { f ->
            f.placed()
            val old = profile(f)
            val admin = AdminService(f.db, f.service, { f.now }, provider)
            val edited = old.copy(userId = "wrong-user", name = "New owner", phone = "050 987 6543",
                payment = aani, photo = "https://example.test/photo.png", discoverable = false, language = "ar")
            admin.mutateUser(AdminUserMutation(userId = userId, action = "profile", profile = edited), "admin")
            val saved = admin.dashboard().users.single { it.id == userId }.profile!!
            assertEquals(userId, saved.userId)
            assertEquals("+971509876543", saved.phone)
            assertEquals(edited.photo, saved.photo)
            assertFalse(saved.discoverable)
            assertEquals("ar", saved.language)
            assertEquals(old.favoriteOrders, saved.favoriteOrders)
            assertEquals(aani.identifier, f.state().room!!.account!!.identifier)
            assertEquals(saved, signIn(f).home!!.profile)
            assertEquals(saved, provider.profiles[userId])
            assertNull(f.db.record("profile:wrong-user"))
            val before = f.state().room!!
            assertFailsWith<IllegalArgumentException> {
                admin.mutateUser(AdminUserMutation(userId = userId, action = "profile", profile = edited.copy(phone = "123")), "admin")
            }
            assertEquals(saved, profile(f)); assertEquals(before, f.state().room)
        }
    }

    @Test fun conflictingNamesRejectTheEntireUpdateAndNonPayersCannotEditReceivingDetails() = RoomFixture(Identity()).use { f ->
        val member = f.placed()
        val before = f.state().room!!
        val old = profile(f)
        val otherName = before.members.single { it.id == member.memberId }.name
        val admin = AdminService(f.db, f.service, { f.now })
        assertFailsWith<IllegalArgumentException> {
            admin.mutateUser(AdminUserMutation(userId = userId, action = "profile", profile = old.copy(name = otherName, phone = "+971501234567", payment = aani)), "admin")
        }
        assertEquals(old, profile(f)); assertEquals(before, f.state().room)
        assertFalse(f.service.execute(f.command(member, CommandKind.SHARE_ACCOUNT).copy(account = aani)).ok)
        assertEquals(old, profile(f)); assertEquals(before, f.state().room)
    }
}
