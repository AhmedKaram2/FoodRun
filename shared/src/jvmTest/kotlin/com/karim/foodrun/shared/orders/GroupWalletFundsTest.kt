package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlin.test.*

class GroupWalletFundsTest {
    @Test fun sudaneseHomeLocalizesRoomStatusAndInvitationsWithoutRewritingUserNames() {
        val c=controller(Device());c.page=GroupPage.HOME
        val room=Room("room","123456","me","الفطور",Restaurant("r","Kitchen"),phase=RoomPhase.PLACED,payerId="me",members=listOf(Member("me","أمل {number}",approved=true)))
        c.library=c.library.copy(language="ar-SD",sessions=listOf(StoredSession(hub,"room","token","me","الفطور")),snapshots=mapOf("room" to RoomReply(room=room)),home=home.copy(invitations=listOf(FoodInvitation("invite","me","other","غرفة {name}","نور {number}",22))))
        val card=c.state.cards.single { it.id=="session:room" }
        assertTrue(card.detail.contains("طلب رقم 1"));assertTrue(card.detail.contains("أمل {number} الزول البيطلب"));assertFalse(card.detail.contains("is selected"))
        val invitation=c.state.cards.single { it.id=="invitation:invite" }
        assertEquals("ادخل غرفة غرفة {name}",invitation.title);assertEquals("نور {number} عزمك للطلب رقم 22",invitation.detail)
    }
    @Test fun homeOffersPaymentAndRestaurantCreationAndShowsOwnSpendingInEachCurrency() {
        val c=controller(Device());c.page=GroupPage.HOME
        val periods=listOf("week","month").flatMap { period -> listOf("AED","USD").map { currency -> SpendingPeriod(period,"2026-10-01","2026-10-31",currency,7500,0,7500,emptyList()) } }
        c.library=c.library.copy(home=home.copy(spending=SpendingSummary(10000,"Asia/Dubai",periods)))
        assertTrue(c.state.buttons.any { it.action==GroupAction.CREATE_PAYMENT_ROOM });assertTrue(c.state.buttons.any { it.action==GroupAction.NEW_RESTAURANT })
        val week=c.state.cards.single { it.id=="home-spending:week" }
        assertEquals("AED 75.00\nUSD 75.00",week.badge)
        assertEquals("spending",week.buttons.single().value)
        c.dispatch(GroupAction.OPEN_WALLET,"spending")
        assertEquals(GroupPage.WALLET,c.page);assertEquals("spending",c.state.walletSection)
        c.dispatch(GroupAction.OPEN_WALLET)
        assertEquals("",c.state.walletSection)
    }
    @Test fun walletTrackingKeepsCompletedActivityAndSeparatesOutstandingCustodyFromIncomingMoney() {
        val c=controller(Device());c.page=GroupPage.WALLET
        val incoming=WalletPayment("incoming","alice","Alice","holder","Holder","me","Me","room","Lunch",1,"alice",1500,"AED",createdAt=10000)
        val sent=incoming.copy(id="sent",customerId="me",recipientId="payer",status=WalletPaymentStatus.SENT)
        val settled=sent.copy(id="settled",status=WalletPaymentStatus.SETTLED)
        val credit=WalletTopUp("credit","me","Me","holder","Holder",2000,"AED",account,"",status=WalletStatus.CONFIRMED,createdAt=10000)
        c.library=c.library.copy(home=home.copy(wallet=WalletSnapshot(payments=listOf(incoming,sent,settled),topUps=listOf(credit))))
        val cards=c.walletFunds.cards("")
        assertEquals("receive",cards.single { it.id=="wallet-payment:incoming" }.paymentGroup)
        assertEquals("waiting",cards.single { it.id=="wallet-payment:sent" }.paymentGroup)
        assertEquals("history",cards.single { it.id=="wallet-payment:settled" }.paymentGroup)
        assertEquals("history",cards.single { it.id=="wallet-top-up:credit" }.paymentGroup)
        assertFalse(cards.filter { it.paymentGroup=="history" }.flatMap { it.buttons }.any { it.value.contains("-yes|") })
    }
    @Test fun roomShowsWalletObligationsAndSettlesOnlyItsOwnOrderIncludingPersonalShare() {
        val device=Device();val c=controller(device)
        val payment=WalletPayment("due","alice","Alice","me","Me","payer","Payer","room","Lunch",1,"alice",1500,"AED",createdAt=10000)
        val room=Room("room","123456","payer","Lunch",Restaurant("r","Restaurant"),phase=RoomPhase.FULFILLED,payerId="payer",restaurantPaid=true,account=account,members=listOf(Member("me","Me",approved=true),Member("payer","Payer",approved=true)),walletPayments=listOf(payment))
        val receipt=Receipt("me","Me",emptyList(),2000,0,0,0,0,2000,0,2000,1,"AED")
        c.library=c.library.copy(home=home.copy(wallet=WalletSnapshot(payments=listOf(payment,payment.copy(id="elsewhere",roomId="another",amount=500)))),sessions=listOf(StoredSession(hub,"room","room-token","me","Lunch")),snapshots=mapOf("room" to RoomReply(room=room,receipts=listOf(receipt))))
        c.page=GroupPage.ROOM
        val cards=c.walletFunds.roomCards(room)
        val total=cards.first();assertEquals("wallet-custody-total",total.id);assertEquals("AED 35.00",total.badge)
        assertTrue(cards.any { it.detail.contains("Alice") })
        device.response=RoomReply(walletRecipient=WalletRecipient(FoodPerson("payer","Payer"),listOf(account)))
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION,"settle-room|room")
        assertEquals(GroupPage.WALLET_BATCH,c.page)
        assertEquals("AED 35.00",c.state.cards.first { it.id=="wallet-group-total" }.detail)
        assertEquals("AED 20.00",c.state.cards.first { it.id=="wallet-group-own" }.detail)
        device.response=RoomReply(ok=false,error="Captured")
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION,"send-batch")
        assertEquals(CommandKind.WALLET_DECLARE_BATCH,device.sent!!.kind);assertTrue(device.sent!!.flag)
        assertEquals(3500,device.sent!!.amount);assertEquals("room",device.sent!!.roomId);assertEquals("room-token",device.sent!!.token)
        assertEquals(hub,device.hub)
    }
    @Test fun walletTabPutsOtherPeoplesPaymentsAndApprovalsBeforeBalances() {
        val device=Device();val c=controller(device);c.page=GroupPage.WALLET
        val payment=WalletPayment("due","alice","Alice","me","Me","payer","Payer","room","Lunch",1,"alice",1500,"AED",createdAt=10000)
        val topUp=WalletTopUp("credit","alice","Alice","me","Me",2000,"AED",account,"",createdAt=10000)
        val batch=WalletBatch("incoming","holder","Holder","me","Me",4000,"AED",listOf("other"),account,"",createdAt=10000)
        c.library=c.library.copy(home=home.copy(wallet=WalletSnapshot(payments=listOf(payment),topUps=listOf(topUp),batches=listOf(batch),balances=listOf(WalletBalance("me","Me","holder","Holder","AED",5000)))))
        val state=c.state
        assertEquals(listOf("Required payments","Wallet balance","Payment history"),state.sections.map { it.title })
        val required=state.sections.first().cards
        assertTrue(required.first().buttons.any { it.action==GroupAction.WALLET_FUNDS_ACTION })
        assertTrue(required.single { it.id.endsWith("wallet-group:due") }.detail.contains("Alice"))
        assertEquals("batch|due",required.single { it.id.endsWith("wallet-group:due") }.buttons.single().value)
        assertTrue(required.single { it.id.endsWith("wallet-top-up:credit") }.buttons.any { it.value=="top-up-yes|credit" })
        assertTrue(required.single { it.id.endsWith("wallet-batch:incoming") }.buttons.any { it.value=="batch-yes|incoming" })
        device.response=RoomReply(home=c.library.home)
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION,"top-up-yes|credit")
        assertEquals(CommandKind.WALLET_REVIEW_TOP_UP,device.sent!!.kind);assertTrue(device.sent!!.flag)
        assertEquals(hub,device.hub);assertEquals(GroupPage.WALLET,c.page)
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION,"batch-yes|incoming")
        assertEquals(CommandKind.WALLET_REVIEW_BATCH,device.sent!!.kind);assertTrue(device.sent!!.flag)
        assertEquals(GroupPage.WALLET,c.page)
    }
    @Test fun sendingOnBehalfOfWalletOwnersReturnsToWalletWithoutNotes() {
        val device=Device();val c=controller(device);c.page=GroupPage.WALLET
        val payment=WalletPayment("due","alice","Alice","me","Me","payer","Payer","room","Lunch",1,"alice",1500,"AED",createdAt=10000)
        c.library=c.library.copy(home=home.copy(wallet=WalletSnapshot(payments=listOf(payment))))
        device.response=RoomReply(walletRecipient=WalletRecipient(FoodPerson("payer","Payer"),listOf(account)))
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION,"batch|due")
        assertEquals(GroupPage.WALLET_BATCH,c.page)
        assertFalse(c.state.fields.any { it.key==GroupFieldKey.WALLET_NOTE })
        assertTrue(c.state.cards.any { it.title=="Alice" })
        device.response=RoomReply(home=c.library.home)
        c.dispatch(GroupAction.WALLET_FUNDS_ACTION,"send-batch")
        assertEquals(CommandKind.WALLET_DECLARE_BATCH,device.sent!!.kind)
        assertEquals(1500,device.sent!!.amount);assertEquals("payer",device.sent!!.userId);assertEquals("",device.sent!!.text)
        assertEquals(GroupPage.WALLET,c.page)
    }
    @Test fun walletSenderCannotApproveSomeoneElsesTopUpOrBatch() {
        val c=controller(Device());c.page=GroupPage.WALLET
        val topUp=WalletTopUp("credit","me","Me","holder","Holder",2000,"AED",account,"",createdAt=10000)
        val batch=WalletBatch("outgoing","me","Me","payer","Payer",4000,"AED",listOf("other"),account,"",createdAt=10000)
        c.library=c.library.copy(home=home.copy(wallet=WalletSnapshot(topUps=listOf(topUp),batches=listOf(batch))))
        val buttons=c.state.sections.first().cards.flatMap { it.buttons }
        assertFalse(buttons.any { it.value.startsWith("top-up-yes|") || it.value.startsWith("top-up-no|") || it.value.startsWith("batch-yes|") || it.value.startsWith("batch-no|") })
    }
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

    @Test fun walletHistoryUsesPrivateAccountHubAndLateReplyCannotReopenDismissedPopup() {
        val device = Device(); val c = controller(device); val balance = WalletBalance("me","Me","holder","Holder","AED",500)
        c.library = c.library.copy(home = home.copy(wallet = WalletSnapshot(balances = listOf(balance))))
        device.deferred = true; val key = orderJson.encodeToString(WalletKey("me","holder","AED"))
        c.dispatch(GroupAction.OPEN_WALLET_HISTORY,key)
        assertEquals(CommandKind.WALLET_HISTORY,device.sent!!.kind); assertEquals(hub,device.hub); assertNull(c.library.pending); assertFalse(c.busy)
        val callback = device.callback!!
        c.dispatch(GroupAction.DISMISS_WALLET_HISTORY)
        callback.complete(orderJson.encodeToString(RoomReply(walletHistory = WalletHistory(balance,emptyList()))),"")
        assertNull(c.state.walletHistoryPrompt)
        c.dispatch(GroupAction.OPEN_WALLET_HISTORY,key)
        device.callback!!.complete(orderJson.encodeToString(RoomReply(walletHistory = WalletHistory(balance,emptyList()))),"")
        assertTrue(c.state.walletHistoryPrompt!!.balance.contains("5.00"))
        c.page = GroupPage.HOME; assertNull(c.state.walletHistoryPrompt); c.page = GroupPage.PROFILE; assertNull(c.state.walletHistoryPrompt)
    }
    @Test fun notificationPreferencesSaveOnTheAccountHubWithoutChangingProfileIdentity() {
        val device = Device(); val c = controller(device)
        c.dispatch(GroupAction.OPEN_NOTIFICATION_PREFERENCES)
        c.update(GroupFieldKey.PUSH_NOTIFICATIONS,"false"); c.update(GroupFieldKey.EMAIL_NOTIFICATIONS,"false")
        device.response = RoomReply(home = home.copy(notificationPreferences = NotificationPreferences(false,false)))
        c.dispatch(GroupAction.SAVE_NOTIFICATION_PREFERENCES)
        assertEquals(CommandKind.SET_NOTIFICATION_PREFERENCES,device.sent!!.kind); assertEquals(hub,device.hub)
        assertEquals(NotificationPreferences(false,false),device.sent!!.notificationPreferences)
        assertEquals("me",c.library.home!!.profile.userId); assertEquals(GroupPage.PROFILE,c.page)
    }
}
