package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class AccountMergeRepairTest {
    private class Identity : IdentityProvider {
        override fun signIn(email: String, password: String, register: Boolean) = CloudIdentity(email.substringBefore('@'), "token")
        override fun exchange(idToken: String) = CloudIdentity(idToken, "token")
        override fun refresh(refreshToken: String) = error("Unused")
        override fun profile(identity: CloudIdentity) = FoodProfile(identity.userId, identity.userId)
        override fun saveProfile(identity: CloudIdentity, profile: FoodProfile) = Unit
        override fun resetPassword(email: String) = Unit
        override fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String) = Unit
    }
    private val plan = AccountMergePlan("new-login", "old-login", "New name", "Old name", "Unified name")
    private fun login(f: RoomFixture, uid: String) = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY,
        identity = IdentityRequest(IdentityAction.FIREBASE_SIGN_IN, firebaseToken = uid)))
    private fun save(f: RoomFixture, token: String, name: String) = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY,
        identityToken = token, identity = IdentityRequest(IdentityAction.SAVE_PROFILE, profile = FoodProfile(name = name))))
    private fun join(f: RoomFixture, token: String, name: String) = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.JOIN,
        identityToken = token, code = f.owner.room!!.code, name = name))
    private fun home(f: RoomFixture, token: String) = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.HOME, identityToken = token)).home!!

    @Test fun mergedLoginsShareWalletOrdersAndRenamesWithoutRecreatingAnAccount() = RoomFixture(Identity()).use { f ->
        val old = login(f, plan.targetUserId); save(f, old.identityToken, plan.expectedTargetName)
        val member = join(f, old.identityToken, plan.expectedTargetName)
        f.cart(member, 3)
        val newer = login(f, plan.sourceUserId); save(f, newer.identityToken, plan.expectedSourceName)
        val duplicate = join(f, newer.identityToken, plan.expectedSourceName)
        f.send(f.owner, CommandKind.REMOVE) { it.copy(memberId = duplicate.memberId, text = "Duplicate membership") }
        val topUp = WalletTopUp("top-up", plan.sourceUserId, "New name", "fixture-owner", "Owner", 5000, "AED", f.account, "", WalletStatus.CONFIRMED, f.now)
        f.db.putRecord("wallet:top-up:${topUp.id}", orderJson.encodeToString(topUp))
        val before = f.db.room(f.owner.room!!.id)!!
        val result = AccountMergeRepair.apply(f.db, plan, f.now)
        assertEquals(1, result.walletRecords)
        assertEquals(before.carts, f.db.room(before.id)!!.carts)
        assertEquals(Billing.receipts(before).map { it.total }, Billing.receipts(f.db.room(before.id)!!).map { it.total })
        assertNull(f.db.record("profile:${plan.sourceUserId}"))
        assertEquals(plan.targetUserId, AccountAliases.resolve(f.db, plan.sourceUserId))
        val oldHome = home(f, old.identityToken)
        assertEquals(oldHome, home(f, newer.identityToken))
        assertEquals(member.memberId, oldHome.rooms.single().memberId)
        assertEquals(5000, oldHome.wallet!!.balances.single().available)
        assertEquals(plan.targetUserId, oldHome.wallet!!.balances.single().customerId)
        assertEquals(plan.name, oldHome.profile.name)
        assertEquals(member.memberId, join(f, newer.identityToken, plan.name).memberId)
        save(f, newer.identityToken, "Changed once more")
        assertEquals("Changed once more", home(f, old.identityToken).profile.name)
        val signedAgain = login(f, plan.sourceUserId)
        assertEquals(plan.targetUserId, signedAgain.home!!.profile.userId)
        assertEquals("Changed once more", signedAgain.home!!.profile.name)
        assertEquals("Changed once more", login(f, plan.targetUserId).home!!.profile.name)
        assertNull(f.db.record("profile:${plan.sourceUserId}"))
        f.restart()
        assertEquals(home(f, old.identityToken), home(f, newer.identityToken))
        assertEquals(result, AccountMergeRepair.apply(f.db, plan, f.now + 1))
        assertEquals(1, WalletService.topUps(f.db).size)
    }

    @Test fun invalidRepairRollsBackEveryRecordAndCannotClaimAnotherPersonsOrders() = RoomFixture(Identity()).use { f ->
        val old = login(f, plan.targetUserId); save(f, old.identityToken, plan.expectedTargetName)
        val newer = login(f, plan.sourceUserId); save(f, newer.identityToken, plan.expectedSourceName)
        f.cart(join(f, old.identityToken, plan.expectedTargetName), 1)
        f.cart(join(f, newer.identityToken, plan.expectedSourceName), 2)
        val rooms = f.db.allRooms(); val records = f.db.records("")
        assertFailsWith<IllegalArgumentException> { AccountMergeRepair.apply(f.db, plan, f.now) }
        assertEquals(rooms, f.db.allRooms())
        assertEquals(records, f.db.records(""))
        assertFailsWith<IllegalArgumentException> { AccountMergeRepair.apply(f.db, plan.copy(expectedSourceName = "Wrong person"), f.now) }
        assertNull(f.db.record("account-alias:${plan.sourceUserId}"))
    }

    @Test fun failedDurableCommitCannotPartiallyMoveWalletMoney() {
        val store = DurableStorageTest.MemoryStore()
        RoomFixture(Identity(), { store }).use { f ->
            val old = login(f, plan.targetUserId); save(f, old.identityToken, plan.expectedTargetName)
            val newer = login(f, plan.sourceUserId); save(f, newer.identityToken, plan.expectedSourceName)
            val before = store.rows!!.toMap()
            store.failBefore = true
            assertFailsWith<StorageUnavailable> { AccountMergeRepair.apply(f.db, plan, f.now) }
            assertEquals(before, store.rows!!.toMap())
            store.failBefore = false; f.restart(clearCache = true)
            assertNull(f.db.record("account-alias:${plan.sourceUserId}"))
            AccountMergeRepair.apply(f.db, plan, f.now)
            f.restart(clearCache = true)
            assertEquals(home(f, old.identityToken), home(f, newer.identityToken))
        }
    }
}
