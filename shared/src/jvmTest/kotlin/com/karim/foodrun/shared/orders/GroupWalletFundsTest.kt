package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlin.test.*

class GroupWalletFundsTest {
    private class Device : GroupPlatform {
        var response = RoomReply()
        var sent: RoomCommand? = null
        var hub: HubPairing? = null
        var deferred = false
        var callback: GroupReplyCallback? = null
        private var sequence = 0
        override fun read(key: String) = ""
        override fun write(key: String, value: String) = true
        override fun now() = 10000L
        override fun uuid() = "wallet-command-${++sequence}"
        override fun request(hub: HubPairing, body: String, callback: GroupReplyCallback) { this.hub = hub; sent = orderJson.decodeFromString(body); this.callback = callback; if (!deferred) callback.complete(orderJson.encodeToString(response), "") }
        override fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback) = object : GroupSubscription { override fun cancel() = Unit }
        override fun share(text: String, fileName: String) = Unit
        override fun openLink(url: String) = Unit
        override fun importMenu(callback: GroupReplyCallback) = Unit
        override fun scanPairing(callback: GroupReplyCallback) = Unit
        override fun discover(callback: GroupReplyCallback) = Unit
    }
    private val hub = HubPairing("https://account.example.test")
    private val account = ReceivingAccount("bank", "Holder", "Test Bank", "AE070331234567890123456")
    private val home = HomePayload(FoodProfile("me", "Me", "+971501234567"), wallet = WalletSnapshot())
    private fun controller(device: Device) = GroupController(device).also { it.library = it.library.copy(identityToken = "identity-token", identityHub = hub,
        selectedHub = HubPairing("https://different-room.example.test"), home = home); it.page = GroupPage.PROFILE }

    @Test fun topUpUsesAccountHubWithoutRequiringRoomAndReceivesCurrentWallet() {
        val device = Device(); val c = controller(device)
        device.response = RoomReply(walletPeople = listOf(FoodPerson("holder", "Holder")))
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION, "charge")
        assertEquals(GroupPage.WALLET_TOP_UP, c.page); assertNull(device.sent)
        assertFalse(c.state.cards.any { it.id.startsWith("wallet-person:") })
        c.update(GroupFieldKey.WALLET_SEARCH, "Holder@example.test")
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION, "search")
        assertEquals(hub, device.hub); assertEquals("Holder@example.test", device.sent!!.text)
        assertTrue(c.state.cards.any { it.title == "Holder" })
        device.response = RoomReply(walletRecipient = WalletRecipient(FoodPerson("holder", "Holder"), listOf(account)))
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION, "person|holder")
        c.update(GroupFieldKey.WALLET_AMOUNT, "100")
        val pending = WalletTopUp("top-up", "me", "Me", "holder", "Holder", 10000, "AED", account, "", createdAt = 10000)
        device.response = RoomReply(home = home.copy(wallet = WalletSnapshot(topUps = listOf(pending))))
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION, "top-up")
        assertEquals(CommandKind.WALLET_TOP_UP, device.sent!!.kind)
        assertEquals(10000L, device.sent!!.amount); assertEquals("bank", device.sent!!.accountId)
        assertEquals("identity-token", device.sent!!.identityToken)
        assertEquals(GroupPage.PROFILE, c.page); assertNull(c.library.pending)
        assertTrue(c.library.home!!.wallet!!.balances.isEmpty())
        assertEquals("top-up", c.library.home!!.wallet!!.topUps.single().id)
    }
    @Test fun holderGroupAndRecipientConfirmationHaveCorrectAmountAndActionsInBothLanguages() {
        val payment = WalletPayment("a", "alice", "Alice", "me", "Me", "payer", "Payer", "room", "Lunch", 1, "alice", 1500, "AED", createdAt = 10000)
        val second = payment.copy(id = "b", customerId = "bob", customerName = "Bob", amount = 2500)
        val device = Device(); val c = controller(device)
        c.library = c.library.copy(home = home.copy(wallet = WalletSnapshot(payments = listOf(payment, second))))
        val group = c.walletFunds.cards("profile-dashboard:").single { it.id.contains("wallet-group:") }
        assertTrue(group.detail.contains("AED 40.00") && group.detail.contains("Alice") && group.detail.contains("Bob"))
        device.response = RoomReply(walletRecipient = WalletRecipient(FoodPerson("payer", "Payer"), listOf(account)))
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION, "batch|a")
        device.response = RoomReply(home = c.library.home)
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION, "send-batch")
        assertEquals(CommandKind.WALLET_DECLARE_BATCH, device.sent!!.kind); assertEquals(4000L, device.sent!!.amount)
        val batch = WalletBatch("batch", "holder", "Holder", "me", "Me", 4000, "AED", listOf("a", "b"), account, "", createdAt = 10000)
        c.library = c.library.copy(home = home.copy(wallet = WalletSnapshot(batches = listOf(batch))))
        listOf("en", "ar").forEach { language ->
            c.library = c.library.copy(language = language)
            val buttons = c.walletFunds.cards("profile-dashboard:").single { it.id.contains("wallet-batch:") }.buttons
            assertEquals(listOf("batch-yes|batch", "batch-no|batch"), buttons.map { it.value })
        }
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION, "batch-yes|batch")
        assertEquals(CommandKind.WALLET_REVIEW_BATCH, device.sent!!.kind); assertTrue(device.sent!!.flag)
    }
    @Test fun clearingSearchHidesPeopleAndIgnoresEarlierReply() {
        val device = Device(); val c = controller(device)
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION, "charge")
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION, "search")
        assertNull(device.sent)
        c.update(GroupFieldKey.WALLET_SEARCH, "hold")
        device.response = RoomReply(walletPeople = listOf(FoodPerson("holder", "Holder")))
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION, "search")
        val previous = device.sent!!
        c.update(GroupFieldKey.WALLET_SEARCH, "  ")
        c.walletFunds.accept(device.response, previous)
        assertFalse(c.state.cards.any { it.id.startsWith("wallet-person:") })
        assertFalse(c.state.buttons.single { it.value == "search" }.enabled)
    }
    @Test fun topUpShortcutPrefillsOnlyMissingAmountInPaymentCurrency() {
        val device = Device(); val c = controller(device)
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION, "charge|JOD|1250")
        assertEquals("JOD", c.text(GroupFieldKey.WALLET_CURRENCY))
        assertEquals("1.250", c.text(GroupFieldKey.WALLET_AMOUNT))
        assertNull(device.sent)
    }
    @Test fun searchFailureDoesNotSaveAMutationAndLateSearchDoesNotReopenTheForm() {
        val device = Device(); val c = controller(device)
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION, "charge")
        c.update(GroupFieldKey.WALLET_SEARCH, "holder")
        device.deferred = true
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION, "search")
        device.callback!!.complete("", "Connection timed out.")
        assertNull(c.library.pending); assertFalse(c.busy)
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION, "search")
        c.dispatch(GroupAction.BACK)
        device.callback!!.complete(orderJson.encodeToString(RoomReply(walletPeople = listOf(FoodPerson("holder", "Holder")))), "")
        assertEquals(GroupPage.PROFILE, c.page); assertNull(c.library.pending)
        assertFalse(c.state.cards.any { it.id.startsWith("wallet-person:") })
    }
    @Test fun fullBalancePaymentIsDisabledUntilCreditIsConfirmedAndUsesRoomToken() {
        val device = Device(); val c = controller(device)
        val room = Room("room", "123456", "payer", "Lunch", Restaurant("r", "R"), phase = RoomPhase.FULFILLED, payerId = "payer", restaurantPaid = true,
            members = listOf(Member("me", "Me", approved = true)))
        val receipt = Receipt("me", "Me", emptyList(), 1500, 0, 0, 0, 0, 1500, 0, 1500, 1, "AED")
        c.library = c.library.copy(sessions = listOf(StoredSession(hub, "room", "room-token", "me", "Lunch")), snapshots = mapOf("room" to RoomReply(room = room, memberId = "me", receipts = listOf(receipt))))
        assertFalse(c.walletFunds.payButton(room, "me", receipt)!!.enabled)
        c.library = c.library.copy(home = home.copy(wallet = WalletSnapshot(balances = listOf(WalletBalance("me", "Me", "holder", "Holder", "AED", 2000)))))
        assertTrue(c.walletFunds.payButton(room, "me", receipt)!!.enabled)
        device.response = RoomReply(ok = false, error = "Captured")
        c.dispatch(GroupAction.PAY_WITH_WALLET, "room")
        assertEquals(CommandKind.PAY_WITH_WALLET, device.sent!!.kind); assertEquals(1500L, device.sent!!.amount)
        assertEquals("room-token", device.sent!!.token); assertEquals("identity-token", device.sent!!.identityToken)
    }
}
