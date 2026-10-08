package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import com.karim.foodrun.shared.*
import kotlinx.serialization.json.*
import kotlin.test.*

class GroupReactBridgeTest {
    private class Phone : GroupPlatform {
        val saved = mutableMapOf<String,String>()
        val requests = mutableListOf<Pair<RoomCommand,GroupReplyCallback>>()
        var time = 1000L
        var pushCalls = 0
        var googleCallback: GroupReplyCallback? = null
        var failRequests = false
        override fun read(key: String) = saved[key].orEmpty()
        override fun write(key: String,value: String): Boolean { saved[key] = value; return true }
        override fun now() = time
        override fun uuid() = "bridge-fixture-${requests.size}"
        override fun request(hub: HubPairing,body: String,callback: GroupReplyCallback) {
            check(!failRequests) { "Could not start connection" }
            requests += orderJson.decodeFromString<RoomCommand>(body) to callback
        }
        override fun googleSignIn(callback: GroupReplyCallback) { googleCallback = callback }
        override fun watch(hub: HubPairing,body: String,callback: GroupReplyCallback) = object : GroupSubscription { override fun cancel() = Unit }
        override fun pushToken(prompt: Boolean,callback: GroupReplyCallback) { pushCalls++ }
        override fun share(text: String,fileName: String) = Unit
        override fun openLink(url: String) = Unit
        override fun importMenu(callback: GroupReplyCallback) = Unit
        override fun scanPairing(callback: GroupReplyCallback) = Unit
        override fun discover(callback: GroupReplyCallback) = Unit
    }
    private class Storage : FoodRunStorage {
        var value: String? = null
        override fun read() = value
        override fun write(value: String) { this.value = value }
    }
    private val hub = HubPairing("https://api.example.test")
    private fun owner(phone: Phone): GroupController = GroupController(phone).also {
        it.library = it.library.copy(identityToken = "private-owner-token",identityHub = hub,selectedHub = hub,home = HomePayload(FoodProfile("owner","Owner")))
    }
    @Test fun viewSnapshotContainsCompleteDefaultsAndRedactsCredentials() {
        val phone = Phone(); val c = GroupController(phone)
        c.library = c.library.copy(selectedHub = hub)
        c.dispatch(GroupAction.OPEN_PROFILE); c.update(GroupFieldKey.PASSWORD,"private-password")
        val bridge = GroupReactBridge(c,FoodRunController(Storage(),UtcFoodRunTimeZone()))
        val body = bridge.snapshotJson(); val data = orderJson.parseToJsonElement(body).jsonObject
        assertFalse(body.contains("private-password"))
        val fields = data.getValue("mainFields").jsonArray + data.getValue("extraFields").jsonArray
        assertEquals("",fields.single { it.jsonObject["key"]?.jsonPrimitive?.content == "PASSWORD" }.jsonObject.getValue("value").jsonPrimitive.content)
        assertTrue(data.getValue("state").jsonObject.containsKey("busy"))
        assertTrue(data.getValue("inlineButtons") is JsonArray)
        assertEquals("private-password",c.text(GroupFieldKey.PASSWORD))
        c.library = c.library.copy(identityToken = "private-identity-token",identityHub = hub)
        assertFalse(bridge.snapshotJson().contains("private-identity-token"))
    }
    @Test fun returningHomePreservesExistingRoomMembershipAndUsesSameAccount() {
        val phone = Phone(); val c = owner(phone)
        val saved = StoredSession(hub,"room","private-room-token","member","Lunch")
        c.library = c.library.copy(sessions = listOf(saved)); c.session = saved
        c.page = GroupPage.PROFILE
        c.dispatch(GroupAction.OPEN_HOME)
        assertEquals(GroupPage.HOME,c.page); assertEquals(listOf(saved),c.library.sessions)
        assertEquals("owner",c.library.home!!.profile.userId)
        assertFalse(GroupReactBridge(c,FoodRunController(Storage(),UtcFoodRunTimeZone())).snapshotJson().contains("private-room-token"))
    }
    @Test fun sharedQuickWheelKeepsRosterAndHistoryInTheExistingNativeStorage() {
        val phone = Phone();val storage = Storage();val wheel = FoodRunController(storage,UtcFoodRunTimeZone())
        val bridge = GroupReactBridge(GroupController(phone),wheel)
        bridge.quickAction("crew","");bridge.quickAction("add","");bridge.quickAction("name","New friend");bridge.quickAction("save","")
        assertTrue(FoodRunController(storage,UtcFoodRunTimeZone()).state.people.any { it.name == "New friend" })
        bridge.quickAction("dismiss","");bridge.quickAction("spin","0");bridge.quickAction("finish","")
        assertEquals(1,FoodRunController(storage,UtcFoodRunTimeZone()).state.historyItems.size)
    }
    @Test fun supportDoesNotOverwriteOwnerStorageOrReassignDevicePushAndExpires() {
        val phone = Phone();val c = owner(phone);c.persist();val original = phone.saved.toMap()
        c.startSupport(RoomReply(identityToken = "temporary-target-token",home = HomePayload(FoodProfile("target","Target"))),hub)
        c.dispatch(GroupAction.SET_LANGUAGE,"ar");c.foreground();c.persist()
        assertTrue(c.supportActive);assertEquals("target",c.library.home!!.profile.userId)
        assertEquals(original["group-library-v1"],phone.saved["group-library-v1"]);assertEquals(0,phone.pushCalls)
        phone.time += 30 * 60_000;c.tickAccessBlock()
        assertFalse(c.supportActive);assertEquals("private-owner-token",c.library.identityToken)
        assertEquals("owner",c.library.home!!.profile.userId);assertEquals(original["group-library-v1"],phone.saved["group-library-v1"])
        assertTrue(phone.requests.any { it.first.identityToken == "temporary-target-token" && it.first.identity?.action == IdentityAction.SIGN_OUT })
    }
    @Test fun lateTargetProfileResponseCannotReplaceOwnerAfterReturning() {
        val phone = Phone();val c = owner(phone);c.persist()
        c.startSupport(RoomReply(identityToken = "temporary-target-token",home = HomePayload(FoodProfile("target","Target"))),hub)
        c.dispatch(GroupAction.OPEN_PROFILE);c.update(GroupFieldKey.NAME,"Changed target");c.dispatch(GroupAction.SAVE_PROFILE)
        val pending = phone.requests.last { it.first.identity?.action == IdentityAction.SAVE_PROFILE }
        c.dispatch(GroupAction.END_SUPPORT)
        pending.second.complete(orderJson.encodeToString(RoomReply(home = HomePayload(FoodProfile("target","Changed target")))),"")
        assertEquals("owner",c.library.home!!.profile.userId);assertEquals("private-owner-token",c.library.identityToken)
        assertFalse(c.busy);assertFalse(c.supportActive)
    }
    @Test fun googleSignInFromFreshHomeSelectsInternetAndAsyncFailureCannotCrash() {
        val phone = Phone(); val c = GroupController(phone)
        c.dispatch(GroupAction.GOOGLE_SIGN_IN)
        assertEquals(FOOD_RUN_INTERNET_API,c.library.selectedHub!!.url)
        phone.googleCallback!!.complete("fixture-firebase-token","")
        assertEquals(IdentityAction.FIREBASE_SIGN_IN,phone.requests.last().first.identity!!.action)
        assertTrue(c.busy)
        val failedPhone = Phone();val failed = GroupController(failedPhone)
        failed.dispatch(GroupAction.GOOGLE_SIGN_IN);failedPhone.failRequests = true
        failedPhone.googleCallback!!.complete("fixture-firebase-token","")
        assertFalse(failed.busy);assertTrue(failed.error.contains("Could not start"))
    }
    @Test fun roomsTabShowsExistingRoomsWithoutCreatingMemberships() {
        val phone = Phone();val c = owner(phone)
        c.library = c.library.copy(sessions = listOf(StoredSession(hub,"room","room-token","member","Lunch")))
        c.dispatch(GroupAction.OPEN_ROOMS)
        assertEquals(GroupPage.ROOMS,c.state.page)
        assertTrue(c.state.cards.any { it.id == "session:room" })
        assertTrue(phone.requests.isEmpty())
        c.dispatch(GroupAction.OPEN_HOME);assertEquals(GroupPage.HOME,c.state.page)
    }
    @Test fun lostAcknowledgementRecoversReadOnlyWithoutReplayingOrLosingDrafts() {
        val phone = Phone();val c = owner(phone)
        val saved = RoomCommand(commandId = "pending-request-123456",kind = CommandKind.SET_NOTIFICATION_PREFERENCES,identityToken = "private-owner-token")
        c.library = c.library.copy(pending = saved,pendingHub = hub)
        c.page = GroupPage.PROFILE;c.update(GroupFieldKey.NAME,"Unsubmitted edit")
        c.tickAccessBlock()
        assertEquals(listOf(CommandKind.COMMAND_STATUS),phone.requests.map { it.first.kind })
        assertEquals(saved.commandId,phone.requests.single().first.text)
        phone.requests.single().second.complete(orderJson.encodeToString(RoomReply(code = "COMMAND_UNKNOWN")),"")
        assertEquals(saved,c.library.pending)
        phone.time += 5000;c.tickAccessBlock()
        phone.requests.last().second.complete(orderJson.encodeToString(RoomReply(code = "COMMAND_FOUND",home = HomePayload(FoodProfile("owner","Owner")))),"")
        assertNull(c.library.pending);assertNull(c.library.pendingHub)
        assertEquals("Unsubmitted edit",c.text(GroupFieldKey.NAME))
        assertEquals(GroupPage.PROFILE,c.page)
        assertTrue(phone.requests.all { it.first.kind == CommandKind.COMMAND_STATUS })
    }
    @Test fun delayedRecoveryCannotClearADifferentAccountsSavedRequest() {
        val phone = Phone();val c = owner(phone)
        val saved = RoomCommand(commandId = "pending-request-123456",kind = CommandKind.SET_NOTIFICATION_PREFERENCES)
        c.library = c.library.copy(pending = saved,pendingHub = hub);c.tickAccessBlock()
        c.library = c.library.copy(identityToken = "different-account-token",home = HomePayload(FoodProfile("other","Other")))
        phone.requests.single().second.complete(orderJson.encodeToString(RoomReply(code = "COMMAND_FOUND",home = HomePayload(FoodProfile("owner","Owner")))),"")
        assertEquals(saved,c.library.pending);assertEquals("other",c.library.home!!.profile.userId)
    }
}
