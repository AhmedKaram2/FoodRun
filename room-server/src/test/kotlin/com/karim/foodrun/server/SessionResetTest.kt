package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import java.util.Base64
import kotlin.test.*

class SessionResetTest {
    private class Identity : IdentityProvider {
        override fun signIn(email: String, password: String, register: Boolean) = CloudIdentity(email.substringBefore('@'), "fixture", email = email, emailVerified = true)
        override fun exchange(idToken: String) = error("Unused")
        override fun refresh(refreshToken: String) = error("Unused")
        override fun profile(identity: CloudIdentity) = FoodProfile(identity.userId, identity.userId, "+971501234567")
        override fun saveProfile(identity: CloudIdentity, profile: FoodProfile) = Unit
        override fun resetPassword(email: String) = Unit
        override fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String) = Unit
    }
    private fun login(f: RoomFixture) = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY,
        identity = IdentityRequest(IdentityAction.SIGN_IN, email = "fixture-owner@example.test", password = "fixture-password")))

    @Test fun resetRevokesCredentialsPreservesBillsAndRestoresMembershipOnLogin() = RoomFixture(Identity()).use { f ->
        val member = f.placed(); f.pay()
        val account = login(f)
        val before = f.db.allRooms()
        val profiles = f.db.records("profile:")
        val memberships = f.db.records("membership:")
        val reset = assertNotNull(SessionReset.apply(f.db, 100))
        assertTrue(reset.first >= 3); assertEquals(2, reset.second)
        assertEquals(before, f.db.allRooms()); assertEquals(profiles, f.db.records("profile:"))
        assertEquals(memberships, f.db.records("membership:"))
        assertEquals("REAUTH_REQUIRED", f.service.execute(RoomCommand(commandId = f.id(), kind = CommandKind.HOME, identityToken = account.identityToken)).code)
        for (actor in listOf(f.owner, member)) {
            assertNull(f.db.session(RoomService.hash(actor.token)))
            assertFalse(f.service.execute(RoomCommand(commandId = f.id(), kind = CommandKind.SNAPSHOT, roomId = before.single().id, token = actor.token)).ok)
        }
        f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY, identityToken = account.identityToken, identity = IdentityRequest(IdentityAction.SIGN_OUT)))
        val fresh = login(f)
        val restored = fresh.home!!.rooms.single()
        assertNotEquals(f.owner.token, restored.token)
        assertEquals(f.owner.memberId, restored.memberId)
        assertEquals(before.single(), f.db.room(restored.roomId))
        assertEquals("fixture-owner@example.test", EmailContacts.address(f.db, "fixture-owner"))
        assertTrue(f.service.execute(RoomCommand(commandId = f.id(), kind = CommandKind.SNAPSHOT, roomId = restored.roomId, token = restored.token)).ok)
        f.restart()
        assertNull(SessionReset.apply(f.db, 100))
        assertTrue(f.service.execute(RoomCommand(commandId = f.id(), kind = CommandKind.HOME, identityToken = fresh.identityToken)).ok)
    }
    @Test fun largeResetUsesBatchesAndCanRunAgainWithANewerCutoff() = RoomFixture().use { f ->
        repeat(125) { i -> f.db.putRecord("identity:test-$i", "fixture"); f.db.addSession("fixture-$i", f.owner.room!!.id, f.owner.memberId) }
        assertEquals(125 to 126, SessionReset.apply(f.db, 100))
        assertNull(SessionReset.apply(f.db, 99)); assertNull(SessionReset.apply(f.db, 100))
        f.db.putRecord("identity:new", "fixture")
        assertEquals(1 to 0, SessionReset.apply(f.db, 101))
    }
    @Test fun refreshingAnOldFirebaseTokenDoesNotBypassTheLoginCutoff() {
        fun token(payload: String) = "header." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray()) + ".signature"
        assertFailsWith<SignInRequired> { SessionReset.requireFreshLogin(token("""{"auth_time":99,"iat":9999}"""), 100) }
        assertFailsWith<SignInRequired> { SessionReset.requireFreshLogin(token("""{"iat":9999}"""), 100) }
        assertFailsWith<SignInRequired> { SessionReset.requireFreshLogin("malformed", 100) }
        SessionReset.requireFreshLogin(token("""{"auth_time":100}"""), 100)
        SessionReset.requireFreshLogin("legacy-without-cutoff", 0)
    }
}
