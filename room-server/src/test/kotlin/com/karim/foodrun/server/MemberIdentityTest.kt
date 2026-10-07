package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class MemberIdentityTest {
    private class Identity : IdentityProvider {
        override fun signIn(email: String, password: String, register: Boolean) = CloudIdentity(email.substringBefore('@'), "token")
        override fun exchange(idToken: String) = CloudIdentity(idToken, "token")
        override fun refresh(refreshToken: String) = error("Unused")
        override fun profile(identity: CloudIdentity): FoodProfile? = null
        override fun saveProfile(identity: CloudIdentity, profile: FoodProfile) = Unit
        override fun resetPassword(email: String) = Unit
        override fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String) = Unit
    }

    private fun signIn(f: RoomFixture, uid: String = "member") = f.execute(RoomCommand(
        commandId = f.id(), kind = CommandKind.IDENTITY,
        identity = IdentityRequest(IdentityAction.FIREBASE_SIGN_IN, firebaseToken = uid),
    ))
    private fun join(f: RoomFixture, token: String, name: String) = f.execute(RoomCommand(
        commandId = f.id(), kind = CommandKind.JOIN, identityToken = token, code = f.owner.room!!.code, name = name,
    ))
    private fun rename(f: RoomFixture, token: String, name: String) = f.execute(RoomCommand(
        commandId = f.id(), kind = CommandKind.IDENTITY, identityToken = token,
        identity = IdentityRequest(IdentityAction.SAVE_PROFILE, profile = FoodProfile(name = name)),
    ))

    @Test fun renamedMemberRejoinsWithTheSameCartAndIdentityAcrossSignInAndRestart() = RoomFixture(Identity()).use { f ->
        val account = signIn(f)
        val first = join(f, account.identityToken, "Old name")
        f.cart(first, 2)
        val before = f.db.room(f.owner.room!!.id)!!
        val saved = rename(f, account.identityToken, "New name")
        assertEquals(first.memberId, saved.home!!.rooms.single().memberId)
        f.restart()
        val signedAgain = signIn(f)
        val rejoined = join(f, signedAgain.identityToken, "New name")
        val after = f.db.room(before.id)!!
        assertEquals(first.memberId, rejoined.memberId)
        assertEquals(first.token, rejoined.token)
        assertEquals(2, after.members.size)
        assertEquals("New name", after.members.single { it.id == first.memberId }.name)
        assertEquals(before.members.single { it.id == first.memberId }.copy(name = "New name"), after.members.single { it.id == first.memberId })
        assertEquals(before.carts, after.carts)
        assertEquals(before.ownerId, after.ownerId)
        assertEquals(before.quoteRevision, after.quoteRevision)
        assertEquals(Billing.receipts(before).map { it.total }, Billing.receipts(after).map { it.total })
    }

    @Test fun missingMembershipIndexDoesNotTurnARenameIntoAnotherMember() = RoomFixture(Identity()).use { f ->
        val account = signIn(f)
        val first = join(f, account.identityToken, "Old name")
        f.cart(first, 3)
        val roomId = first.room!!.id
        f.db.deleteRecord("membership:member:$roomId")
        rename(f, account.identityToken, "New name")
        val rejoined = join(f, account.identityToken, "New name")
        assertEquals(first.memberId, rejoined.memberId)
        assertEquals(2, f.db.room(roomId)!!.members.size)
        assertEquals("New name", f.db.room(roomId)!!.members.single { it.id == first.memberId }.name)
        assertEquals(3, f.state(rejoined).room!!.carts.single().lines.single().quantity)
    }

    @Test fun accountBasedMemberIdRecoversWhenBothIndexesAndRoomSessionsAreMissing() = RoomFixture(Identity()).use { f ->
        val account = signIn(f)
        val first = join(f, account.identityToken, "Old name")
        f.cart(first, 2)
        val roomId = first.room!!.id
        f.db.deleteRecord("membership:member:$roomId")
        f.db.deleteRecord("member-user:$roomId:${first.memberId}")
        while (f.db.deleteSessionBatch() > 0) Unit
        f.restart()
        val saved = rename(f, account.identityToken, "New name")
        assertEquals(first.memberId, saved.home!!.rooms.single().memberId)
        val rejoined = join(f, account.identityToken, "New name")
        assertEquals(first.memberId, rejoined.memberId)
        assertNotEquals(first.token, rejoined.token)
        assertEquals(2, f.db.room(roomId)!!.members.size)
        assertEquals(2, f.state(rejoined).room!!.carts.single().lines.single().quantity)
    }

    @Test fun renamedOrganizerKeepsOwnershipAndOutstandingPayments() = RoomFixture(Identity()).use { f ->
        f.placed(); f.pay()
        val account = signIn(f, "fixture-owner")
        val before = f.db.room(f.owner.room!!.id)!!
        rename(f, account.identityToken, "New organizer")
        val rejoined = join(f, account.identityToken, "New organizer")
        val after = f.db.room(before.id)!!
        assertEquals(f.owner.memberId, rejoined.memberId)
        assertEquals(before.ownerId, after.ownerId)
        assertEquals(before.payerId, after.payerId)
        assertEquals("New organizer", after.lastChosenName)
        assertEquals(before.carts, after.carts)
        assertEquals(before.transfers, after.transfers)
        assertEquals(before.restaurantPaid, after.restaurantPaid)
        assertEquals(Billing.receipts(before).map { it.balance }, Billing.receipts(after).map { it.balance })
    }

    @Test fun renamingPendingMemberDoesNotBypassApprovalOrChangeSelection() = RoomFixture(Identity()).use { f ->
        f.send(f.owner, CommandKind.PREPARE_SPIN)
        val account = signIn(f)
        val first = join(f, account.identityToken, "Waiting")
        val before = f.db.room(first.room!!.id)!!
        f.db.deleteRecord("membership:member:${before.id}")
        rename(f, account.identityToken, "Still waiting")
        val rejoined = join(f, account.identityToken, "Still waiting")
        val after = f.db.room(before.id)!!
        assertEquals(first.memberId, rejoined.memberId)
        assertFalse(after.members.single { it.id == first.memberId }.approved)
        assertEquals(before.spin, after.spin)
        assertEquals(before.preparedIds, after.preparedIds)
        assertEquals(before.quoteRevision, after.quoteRevision)
        assertEquals(2, after.members.size)
    }

    @Test fun anotherAccountCannotClaimMembershipByUsingTheSameName() = RoomFixture(Identity()).use { f ->
        val account = signIn(f)
        val first = join(f, account.identityToken, "Member")
        val other = signIn(f, "other")
        val rejected = f.service.execute(RoomCommand(commandId = f.id(), kind = CommandKind.JOIN,
            identityToken = other.identityToken, code = f.owner.room!!.code, name = "Member"))
        assertFalse(rejected.ok)
        rename(f, account.identityToken, "Renamed")
        val second = join(f, other.identityToken, "Member")
        assertNotEquals(first.memberId, second.memberId)
        assertEquals(first.memberId, join(f, account.identityToken, "Member").memberId)
        assertEquals("Renamed", f.db.room(first.room!!.id)!!.members.single { it.id == first.memberId }.name)
    }
}
