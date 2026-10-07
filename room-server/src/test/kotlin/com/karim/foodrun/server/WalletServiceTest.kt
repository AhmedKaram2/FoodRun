package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class WalletServiceTest {
    private class Provider : IdentityProvider {
        private val profiles = mutableMapOf<String, FoodProfile>()
        private fun identity(uid: String) = CloudIdentity(uid, "id-$uid", "refresh-$uid", uid, "$uid@example.test", true)
        override fun signIn(email: String, password: String, register: Boolean) = identity(email.substringBefore('@'))
        override fun exchange(idToken: String) = identity(idToken.removePrefix("id-"))
        override fun refresh(refreshToken: String) = identity(refreshToken.removePrefix("refresh-"))
        override fun profile(identity: CloudIdentity) = profiles[identity.userId]
        override fun saveProfile(identity: CloudIdentity, profile: FoodProfile) { profiles[identity.userId] = profile }
        override fun resetPassword(email: String) = Unit
        override fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String) = Unit
    }
    private class Setup(store: DurableStore? = null) : AutoCloseable {
        val f = RoomFixture(Provider(), durableFactory = store?.let { { it } }, emailEnabled = true)
        val account = f.account
        val a = user("Alice"); val b = user("Bob"); val holder = user("Holder"); val second = user("Second"); val recipient = user("Recipient"); val stranger = user("Stranger")
        fun user(name: String): RoomReply = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY,
            identity = IdentityRequest(IdentityAction.REGISTER, email = "$name@example.test", password = "fixture-password", profile = FoodProfile(name = name, phone = "+971501234567", payment = account.copy(holder = name)))))
        fun command(user: RoomReply, kind: CommandKind) = RoomCommand(commandId = f.id(), kind = kind, identityToken = user.identityToken, walletDetails = true)
        fun send(user: RoomReply, kind: CommandKind, fields: (RoomCommand) -> RoomCommand = { it }) = f.execute(fields(command(user, kind)))
        fun home(user: RoomReply) = send(user, CommandKind.HOME).home!!
        fun topUp(customer: RoomReply = a, target: RoomReply = holder, amount: Long = 10000, currency: String = "AED"): WalletTopUp = send(customer, CommandKind.WALLET_TOP_UP) {
            it.copy(userId = target.home!!.profile.userId, amount = amount, currency = currency, accountId = account.id, text = "Bank transfer")
        }.home!!.wallet!!.topUps.first()
        fun credit(customer: RoomReply = a, target: RoomReply = holder, amount: Long = 10000) {
            val value = topUp(customer, target, amount)
            send(target, CommandKind.WALLET_REVIEW_TOP_UP) { it.copy(transferId = value.id, flag = true) }
        }
        fun bill(customers: List<Pair<RoomReply, Long>> = listOf(a to 1500L, b to 2500L)): RoomReply = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.CREATE_PAYMENT_ROOM,
            identityToken = recipient.identityToken, text = "Lunch receipt", name = "Kitchen", amount = customers.sumOf { it.second }, account = account.copy(holder = "Recipient"),
            paymentRoom = PaymentRoomRequest(PaymentRoomDetails("Lunch"), listOf(PaymentShare("Recipient", "No food", 0)) + customers.map { PaymentShare(it.first.home!!.profile.userId, "Food", it.second) })))
        fun payCommand(user: RoomReply, roomId: String): RoomCommand {
            val room = f.db.room(roomId)!!; val member = home(user).rooms.single { it.roomId == roomId }
            val receipt = Billing.receipts(room).single { it.memberId == member.memberId }
            return command(user, CommandKind.PAY_WITH_WALLET).copy(roomId = roomId, token = member.token, expectedRevision = room.revision, expectedOrderNumber = room.orderNumber, amount = receipt.balance)
        }
        fun pay(user: RoomReply, roomId: String) = f.execute(payCommand(user, roomId))
        fun declare(amount: Long, user: RoomReply = holder) = send(user, CommandKind.WALLET_DECLARE_BATCH) { it.copy(userId = "Recipient", accountId = account.id, amount = amount, text = "Full group transfer") }.home!!.wallet!!.batches.first()
        override fun close() = f.close()
    }

    @Test fun topUpRequiresHolderReceiptAndDeduplicatesAcrossRestart() = Setup().use { s ->
        val value = s.topUp()
        assertTrue(s.home(s.a).wallet!!.balances.isEmpty())
        assertFalse(s.f.service.execute(s.command(s.a, CommandKind.WALLET_REVIEW_TOP_UP).copy(transferId = value.id, flag = true)).ok)
        assertFalse(s.f.service.execute(s.command(s.stranger, CommandKind.WALLET_REVIEW_TOP_UP).copy(transferId = value.id, flag = true)).ok)
        val confirm = s.command(s.holder, CommandKind.WALLET_REVIEW_TOP_UP).copy(transferId = value.id, flag = true)
        s.f.execute(confirm); s.f.restart(); s.f.execute(confirm)
        assertEquals(10000L, s.home(s.a).wallet!!.balances.single().available)
        assertEquals("Holder", s.home(s.a).wallet!!.balances.single().holderName)
        assertFalse(s.f.service.execute(confirm.copy(flag = false)).ok)
        assertFalse(s.f.service.execute(confirm.copy(commandId = s.f.id())).ok)
        assertTrue(s.home(s.stranger).wallet!!.topUps.isEmpty())
        assertTrue(s.f.db.records("email-job:").isEmpty())
        val inbox = s.f.service.notificationRequest(NotificationRequest(s.holder.identityToken), false)
        assertTrue(inbox.notifications.any { it.kind == "wallet_top_up" && it.body.contains("100.00") })
    }
    @Test fun rejectedTopUpsNeverCreditAndWrongCurrencyOrAccountCannotBeDeclared() = Setup().use { s ->
        val value = s.topUp()
        s.send(s.holder, CommandKind.WALLET_REVIEW_TOP_UP) { it.copy(transferId = value.id, flag = false) }
        assertTrue(s.home(s.a).wallet!!.balances.isEmpty())
        assertFalse(s.f.service.execute(s.command(s.a, CommandKind.WALLET_TOP_UP).copy(userId = "Holder", accountId = s.account.id, amount = 10000, currency = "USD")).ok)
        assertFalse(s.f.service.execute(s.command(s.a, CommandKind.WALLET_TOP_UP).copy(userId = "Alice", accountId = s.account.id, amount = 10000)).ok)
        assertFalse(s.f.service.execute(s.command(s.a, CommandKind.WALLET_TOP_UP).copy(userId = "Holder", accountId = "forged", amount = 10000)).ok)
        assertFalse(s.f.service.execute(s.command(s.a, CommandKind.WALLET_TOP_UP).copy(userId = "Holder", accountId = s.account.id, amount = -1)).ok)
    }
    @Test fun fullGroupTransferSettlesAllCustomersOnlyAfterRecipientConfirmation() = Setup().use { s ->
        s.credit(s.a); s.credit(s.b)
        val bill = s.bill(); val id = bill.room!!.id
        s.pay(s.a, id); s.pay(s.b, id)
        val room = s.f.db.room(id)!!
        assertTrue(Billing.receipts(room).all { it.balance == 0L })
        assertEquals(8500L, s.home(s.a).wallet!!.balances.single().available)
        assertEquals(7500L, s.home(s.b).wallet!!.balances.single().available)
        assertEquals(0L, WalletSettlement.received(room, Billing.receipts(room)))
        assertEquals(4000L, WalletSettlement.pending(room))
        val inbox = s.f.service.notificationRequest(NotificationRequest(s.recipient.identityToken), false)
        assertTrue(inbox.notifications.any { it.kind == "wallet_payment_assigned" && it.body.contains("Holder") && it.body.contains("Alice") })
        assertFailsWith<IllegalArgumentException> { RoomRules.requireArchive(room) }
        val batch = s.declare(4000)
        assertEquals(2, batch.paymentIds.size)
        assertTrue(s.f.db.room(id)!!.walletPayments.all { it.status == WalletPaymentStatus.SENT })
        assertEquals(0L, WalletSettlement.received(s.f.db.room(id)!!, Billing.receipts(s.f.db.room(id)!!)))
        assertFalse(s.f.service.execute(s.command(s.holder, CommandKind.WALLET_REVIEW_BATCH).copy(transferId = batch.id, flag = true)).ok)
        val confirm = s.command(s.recipient, CommandKind.WALLET_REVIEW_BATCH).copy(transferId = batch.id, flag = true)
        s.f.execute(confirm); s.f.execute(confirm)
        val settled = s.f.db.room(id)!!
        assertTrue(settled.walletPayments.all { it.status == WalletPaymentStatus.SETTLED })
        assertEquals(4000L, WalletSettlement.received(settled, Billing.receipts(settled)))
        assertEquals(0L, WalletSettlement.pending(settled))
        RoomRules.requireArchive(settled)
        assertEquals(8500L, s.home(s.a).wallet!!.balances.single().available)
        assertTrue(s.f.db.records("email-job:").isEmpty())
    }
    @Test fun rejectedBatchKeepsHolderLiableAndAllowsAReplacementWithoutDoubleDebit() = Setup().use { s ->
        s.credit(); val id = s.bill(listOf(s.a to 1500L)).room!!.id; s.pay(s.a, id)
        val first = s.declare(1500)
        s.send(s.recipient, CommandKind.WALLET_REVIEW_BATCH) { it.copy(transferId = first.id, flag = false) }
        assertEquals(WalletPaymentStatus.OWING, s.f.db.room(id)!!.walletPayments.single().status)
        assertFalse(s.f.service.execute(s.command(s.holder, CommandKind.WALLET_DECLARE_BATCH).copy(userId = "Recipient", accountId = s.account.id, amount = 1501)).ok)
        val second = s.declare(1500)
        s.send(s.recipient, CommandKind.WALLET_REVIEW_BATCH) { it.copy(transferId = second.id, flag = true) }
        assertEquals(8500L, s.home(s.a).wallet!!.balances.single().available)
        assertFalse(s.f.service.execute(s.command(s.recipient, CommandKind.WALLET_REVIEW_BATCH).copy(transferId = first.id, flag = true)).ok)
    }
    @Test fun debitUsesMultipleHoldersAtomicallyAndCashAlreadyHeldByRecipientSettlesImmediately() = Setup().use { s ->
        s.credit(amount = 1000); s.credit(target = s.second, amount = 700); s.credit(target = s.recipient, amount = 300)
        val id = s.bill(listOf(s.a to 1800L)).room!!.id
        val payment = s.payCommand(s.a, id)
        s.f.execute(payment); s.f.execute(payment)
        val values = s.f.db.room(id)!!.walletPayments
        assertEquals(1800L, values.sumOf { it.amount })
        assertEquals(300L, values.single { it.holderId == "Recipient" }.amount)
        assertEquals(WalletPaymentStatus.SETTLED, values.single { it.holderId == "Recipient" }.status)
        assertEquals(200L, s.home(s.a).wallet!!.balances.sumOf { it.available })
        assertFalse(s.f.service.execute(payment.copy(commandId = s.f.id())).ok)
    }
    @Test fun insufficientFundsStaleOrdersAndForgedMembershipDoNotDebit() = Setup().use { s ->
        s.credit(amount = 1000); val id = s.bill(listOf(s.a to 1500L)).room!!.id
        val payment = s.payCommand(s.a, id)
        assertFalse(s.f.service.execute(payment).ok)
        assertFalse(s.f.service.execute(payment.copy(identityToken = s.b.identityToken, commandId = s.f.id())).ok)
        s.credit(amount = 1000)
        assertFalse(s.f.service.execute(payment.copy(expectedRevision = payment.expectedRevision - 1, commandId = s.f.id())).ok)
        assertFalse(s.f.service.execute(payment.copy(expectedOrderNumber = 5, commandId = s.f.id())).ok)
        assertFalse(s.f.service.execute(payment.copy(amount = 1499, commandId = s.f.id())).ok)
        assertEquals(2000L, s.home(s.a).wallet!!.balances.single().available)
        assertTrue(s.f.db.room(id)!!.walletPayments.isEmpty())
    }
    @Test fun autoArchiveRetainsCashLiabilityAndPrivateProjectionsExcludeUnrelatedWallets() = Setup().use { s ->
        s.credit(s.a); s.credit(s.b); val bill = s.bill(); val id = bill.room!!.id
        s.pay(s.a, id); s.pay(s.b, id)
        s.f.now += RoomExpiry.LIFETIME_MS; s.f.service.tick()
        val room = s.f.db.room(id)!!
        assertEquals(RoomPhase.ARCHIVED, room.phase)
        assertTrue(RoomExpiry.paymentsPending(room))
        val alice = s.home(s.a).rooms.single { it.roomId == id }
        assertTrue(s.f.service.snapshot(id, alice.token).room!!.walletPayments.all { it.customerId == "Alice" })
        val batch = s.declare(4000)
        s.send(s.recipient, CommandKind.WALLET_REVIEW_BATCH) { it.copy(transferId = batch.id, flag = true) }
        assertFalse(RoomExpiry.paymentsPending(s.f.db.room(id)!!))
    }
    @Test fun searchAndReceivingMethodsRequireIdentityAndLegacyRepliesOmitNewSchema() = Setup().use { s ->
        val search = s.send(s.a, CommandKind.WALLET_PEOPLE) { it.copy(text = "hold") }
        assertEquals(listOf("Holder"), search.walletPeople!!.map { it.name })
        assertFalse(s.f.service.execute(s.command(s.a, CommandKind.WALLET_RECIPIENT).copy(identityToken = "", userId = "Holder")).ok)
        val reply = s.send(s.a, CommandKind.WALLET_RECIPIENT) { it.copy(userId = "Holder") }
        assertEquals(s.account.identifier, reply.walletRecipient!!.accounts.single().identifier)
        assertNull(reply.forClient(false).walletRecipient)
        assertNull(s.send(s.a, CommandKind.HOME).forClient(false).home!!.wallet)
        s.credit(); val bill = s.bill(listOf(s.a to 1000L)); val paid = s.pay(s.a, bill.room!!.id)
        assertTrue(paid.forClient(false).room!!.walletPayments.isEmpty())
        assertNotNull(paid.forClient(true, walletDetails = true).home!!.wallet)
    }
    @Test fun simultaneousPaymentsCannotSpendTheSameBalanceTwice() = Setup().use { s ->
        s.credit(amount = 1000)
        val first = s.bill(listOf(s.a to 700L)).room!!.id
        val second = s.bill(listOf(s.a to 700L)).room!!.id
        val commands = listOf(s.payCommand(s.a, first), s.payCommand(s.a, second))
        val latch = java.util.concurrent.CountDownLatch(1)
        val results = java.util.Collections.synchronizedList(mutableListOf<RoomReply>())
        val threads = commands.map { command -> Thread { latch.await(); results += s.f.service.execute(command) }.also { it.start() } }
        latch.countDown(); threads.forEach { it.join() }
        assertEquals(1, results.count { it.ok })
        assertEquals(300L, s.home(s.a).wallet!!.balances.single().available)
        assertEquals(700L, WalletService.payments(s.f.db).sumOf { it.amount })
    }
    @Test fun peopleSearchRequiresTypedQueryAndMatchesPrivateVerifiedEmail() = Setup().use { s ->
        for (query in listOf("", "   ")) assertTrue(s.send(s.a, CommandKind.WALLET_PEOPLE) { it.copy(text = query) }.walletPeople!!.isEmpty())
        val byEmail = s.send(s.a, CommandKind.WALLET_PEOPLE) { it.copy(text = "  HOLDER@EXAMPLE.TEST  ") }
        assertEquals(listOf(FoodPerson("Holder", "Holder")), byEmail.walletPeople)
        assertFalse(orderJson.encodeToString(byEmail).contains("@example.test"))
        assertTrue(s.send(s.a, CommandKind.WALLET_PEOPLE) { it.copy(text = "Alice@example.test") }.walletPeople!!.isEmpty())
        val holder = s.home(s.holder).profile
        s.f.db.putRecord("profile:Holder", orderJson.encodeToString(holder.copy(discoverable = false)))
        assertTrue(s.send(s.a, CommandKind.WALLET_PEOPLE) { it.copy(text = "Holder@example.test") }.walletPeople!!.isEmpty())
        EmailContacts.capture(s.f.db, CloudIdentity("Second", "", email = "Second@example.test", emailVerified = false), 1)
        assertNull(EmailContacts.address(s.f.db, "Second"))
        assertEquals("Second", s.send(s.a, CommandKind.WALLET_PEOPLE) { it.copy(text = "Second@example.test") }.walletPeople!!.single().name)
        assertEquals("Second", s.send(s.a, CommandKind.WALLET_PEOPLE) { it.copy(text = "second") }.walletPeople!!.single().name)
    }
    @Test fun oneTransferCanCoverDifferentRoomsAndRecipientHeldFundsNeedNoSecondPayment() = Setup().use { s ->
        s.credit(s.a); s.credit(s.b)
        val first = s.bill(listOf(s.a to 1000L)).room!!.id
        val second = s.bill(listOf(s.b to 2000L)).room!!.id
        s.pay(s.a, first); s.pay(s.b, second)
        val batch = s.declare(3000)
        s.send(s.recipient, CommandKind.WALLET_REVIEW_BATCH) { it.copy(transferId = batch.id, flag = true) }
        listOf(first, second).forEach { RoomRules.requireArchive(s.f.db.room(it)!!) }
        s.credit(s.a, s.recipient, 500)
        val third = s.bill(listOf(s.a to 500L)).room!!.id
        s.pay(s.a, third)
        RoomRules.requireArchive(s.f.db.room(third)!!)
        assertEquals("Recipient", s.f.db.room(third)!!.walletPayments.single().holderId)
    }
    @Test fun ambiguousDurableDebitRestoresOneAllocationAndBalanceAfterLosingLocalCache() {
        val store = DurableStorageTest.MemoryStore()
        Setup(store).use { s ->
            s.credit(); val id = s.bill(listOf(s.a to 1500L)).room!!.id
            val payment = s.payCommand(s.a, id)
            store.failAfter = true
            assertFailsWith<StorageUnavailable> { s.f.service.execute(payment) }
            store.failAfter = false; s.f.restart(clearCache = true)
            s.f.execute(payment)
            assertEquals(1, s.f.db.room(id)!!.walletPayments.size)
            assertEquals(8500L, s.home(s.a).wallet!!.balances.single().available)
            assertNull(s.f.db.pendingCloud())
        }
    }
}
