package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class SelectionOverrideTest {
    private class Provider : IdentityProvider {
        var verified = true
        var adminEmail = AdminService.ADMIN_EMAIL
        override fun signIn(email: String, password: String, register: Boolean) = identity(email.substringBefore('@'))
        override fun exchange(idToken: String) = identity(idToken.removePrefix("id-"))
        override fun refresh(refreshToken: String) = identity(refreshToken.removePrefix("refresh-"))
        override fun profile(identity: CloudIdentity): FoodProfile? = null
        override fun saveProfile(identity: CloudIdentity, profile: FoodProfile) = Unit
        override fun resetPassword(email: String) = Unit
        override fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String) = Unit
        private fun identity(uid: String) = CloudIdentity(uid, "id-$uid", "refresh-$uid", uid,
            email = if (uid == "1ahmedkaram1") adminEmail else "$uid@example.test", emailVerified = verified)
    }
    private fun login(f: RoomFixture, email: String = AdminService.ADMIN_EMAIL) = f.execute(RoomCommand(
        commandId = f.id(), kind = CommandKind.IDENTITY, identity = IdentityRequest(IdentityAction.SIGN_IN, email, "test-password")))
    private fun joinAhmed(f: RoomFixture, identity: RoomReply, name: String = "Ahmed Karam") = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.JOIN,
        code = f.owner.room!!.code, name = name, identityToken = identity.identityToken))
    private fun request(f: RoomFixture, actor: RoomReply, identity: RoomReply, kind: CommandKind, memberId: String = "", passcode: String = "") =
        f.command(actor, kind).copy(identityToken = identity.identityToken, memberId = memberId, text = passcode)
    private fun unlock(f: RoomFixture, ahmed: RoomReply, identity: RoomReply) = f.execute(request(f, ahmed, identity, CommandKind.UNLOCK_SELECTION_OVERRIDE, passcode = "5457"))
    private fun set(f: RoomFixture, ahmed: RoomReply, identity: RoomReply, memberId: String) = f.execute(request(f, ahmed, identity, CommandKind.SET_SELECTION_OVERRIDE, memberId))

    @Test fun verifiedAhmedCanChooseTheSameAnimatedWinnerForEveryoneOnce() = RoomFixture(Provider()).use { f ->
        val identity = login(f); val ahmed = joinAhmed(f, identity); val target = f.join("Target")
        val before = f.state().room!!
        unlock(f, ahmed, identity); set(f, ahmed, identity, target.memberId)
        assertEquals(before, f.state().room)
        assertTrue(f.state(target).room!!.audit.none { "OVERRIDE" in it.action })
        val spinning = f.send(f.owner, CommandKind.PREPARE_SPIN).room!!
        assertEquals(RoomPhase.SPINNING, spinning.phase)
        assertEquals(target.memberId, spinning.spin!!.winnerId)
        assertTrue(spinning.spin!!.duration > 0)
        val spin = spinning.spin!!
        assertEquals(0.0, spin.rotation(spin.startAt))
        assertTrue(spin.rotation(spin.startAt + spin.duration / 2) > 360)
        assertEquals(0.0, (spin.sliceCenter(spin.memberIds.indexOf(target.memberId)) + spin.rotation(spin.endAt)) % 360, 0.000001)
        assertEquals(spin.rotation(spin.endAt), spin.rotation(spin.endAt + 5000))
        assertEquals(spinning.spin, f.state(target).room!!.spin)
        assertEquals(spinning.spin, f.state(ahmed).room!!.spin)
        assertNull(f.db.record("selection:override:${spinning.id}:${spinning.orderNumber}"))
        f.now = spinning.spin!!.endAt; f.service.tick()
        assertEquals(RoomPhase.ACCEPTING, f.state(target).room!!.phase)
        f.send(target, CommandKind.DECLINE_DUTY) { it.copy(text = "Cannot order today") }
        val next = f.send(f.owner, CommandKind.PREPARE_SPIN).room!!.spin!!
        assertEquals(f.owner.memberId, next.winnerId)
    }
    @Test fun passcodeAloneAndSpoofedDisplayNameDoNotGrantAccess() = RoomFixture(Provider()).use { f ->
        val ordinary = login(f, "ordinary@example.test"); val spoofed = joinAhmed(f, ordinary)
        val reply = f.service.execute(request(f, spoofed, ordinary, CommandKind.UNLOCK_SELECTION_OVERRIDE, passcode = "5457"))
        assertFalse(reply.ok)
        val identity = login(f); val ahmed = joinAhmed(f, identity, "Verified Ahmed")
        assertFalse(f.service.execute(request(f, ahmed, identity, CommandKind.SET_SELECTION_OVERRIDE, f.owner.memberId)).ok)
        assertFalse(f.service.execute(request(f, ahmed, identity, CommandKind.UNLOCK_SELECTION_OVERRIDE, passcode = "0000")).ok)
        assertFalse(f.service.execute(request(f, f.owner, identity, CommandKind.UNLOCK_SELECTION_OVERRIDE, passcode = "5457")).ok)
        assertFalse(f.service.execute(request(f, ahmed, identity, CommandKind.UNLOCK_SELECTION_OVERRIDE, passcode = "5457").copy(identityToken = "")).ok)
    }
    @Test fun unverifiedAndRevokedAhmedIdentityCannotUnlockOrSave() {
        val provider = Provider()
        RoomFixture(provider).use { f ->
            val identity = login(f); val ahmed = joinAhmed(f, identity)
            provider.verified = false
            assertFalse(f.service.execute(request(f, ahmed, identity, CommandKind.UNLOCK_SELECTION_OVERRIDE, passcode = "5457")).ok)
            provider.verified = true; unlock(f, ahmed, identity)
            provider.adminEmail = "another@example.test"
            assertFalse(f.service.execute(request(f, ahmed, identity, CommandKind.SET_SELECTION_OVERRIDE, f.owner.memberId)).ok)
        }
    }
    @Test fun grantsExpireAndSelectionRequiresCurrentOrderRevisionAndEligibility() = RoomFixture(Provider()).use { f ->
        val identity = login(f); val ahmed = joinAhmed(f, identity); val target = f.join("Target")
        unlock(f, ahmed, identity)
        assertFalse(f.service.execute(request(f, ahmed, identity, CommandKind.SET_SELECTION_OVERRIDE, "missing")).ok)
        assertFalse(f.service.execute(request(f, ahmed, identity, CommandKind.SET_SELECTION_OVERRIDE, target.memberId).copy(expectedOrderNumber = 2)).ok)
        assertFalse(f.service.execute(request(f, ahmed, identity, CommandKind.SET_SELECTION_OVERRIDE, target.memberId).copy(expectedRevision = -1)).ok)
        f.send(target, CommandKind.READY) { it.copy(flag = true, eligible = false) }
        assertFalse(f.service.execute(request(f, ahmed, identity, CommandKind.SET_SELECTION_OVERRIDE, target.memberId)).ok)
        f.now += 10 * 60_000
        assertFalse(f.service.execute(request(f, ahmed, identity, CommandKind.SET_SELECTION_OVERRIDE, f.owner.memberId)).ok)
    }
    @Test fun selectionSurvivesRestartCanBeClearedAndDoesNotApplyAfterTargetLeaves() = RoomFixture(Provider()).use { f ->
        val identity = login(f); val ahmed = joinAhmed(f, identity); val target = f.join("Target")
        unlock(f, ahmed, identity); set(f, ahmed, identity, target.memberId)
        f.restart()
        assertEquals(target.memberId, f.db.record("selection:override:${f.owner.room!!.id}:1"))
        set(f, ahmed, identity, "")
        assertNull(f.db.record("selection:override:${f.owner.room!!.id}:1"))
        set(f, ahmed, identity, target.memberId)
        f.send(f.owner, CommandKind.REMOVE) { it.copy(memberId = target.memberId, text = "No longer joining lunch") }
        assertEquals(f.owner.memberId, f.send(f.owner, CommandKind.PREPARE_SPIN).room!!.spin!!.winnerId)
        assertFalse(f.service.execute(request(f, ahmed, identity, CommandKind.SET_SELECTION_OVERRIDE, f.owner.memberId)).ok)
    }
}
