package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class PaymentRoomTest {
    private class Identity : IdentityProvider {
        override fun signIn(email: String, password: String, register: Boolean) = CloudIdentity(email.substringBefore('@'), "id-token")
        override fun exchange(idToken: String) = CloudIdentity(idToken, "id-token")
        override fun refresh(refreshToken: String) = error("Unused")
        override fun profile(identity: CloudIdentity) = FoodProfile(userId = identity.userId, name = identity.userId, phone = "+971501234567")
        override fun saveProfile(identity: CloudIdentity, profile: FoodProfile) = Unit
        override fun resetPassword(email: String) = Unit
        override fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String) = Unit
    }
    private fun signIn(f: RoomFixture, name: String) = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY,
        identity = IdentityRequest(IdentityAction.FIREBASE_SIGN_IN, firebaseToken = name)))
    private fun command(f: RoomFixture, owner: RoomReply, friend: RoomReply) = RoomCommand(commandId = f.id(), kind = CommandKind.CREATE_PAYMENT_ROOM,
        identityToken = owner.identityToken, text = "Lunch already ordered", name = "Local restaurant", amount = 3000, account = f.account,
        paymentRoom = PaymentRoomRequest(PaymentRoomDetails("Sandwiches and drinks", "data:image/png;base64,iVBORw0KGgo="),
            listOf(PaymentShare(owner.home!!.profile.userId, "My food", 1000), PaymentShare(friend.home!!.profile.userId, "Two sandwiches", 2000, 500))))
    private fun send(f: RoomFixture, actor: RoomReply, kind: CommandKind, fields: (RoomCommand) -> RoomCommand = { it }): RoomReply {
        val room = f.service.snapshot(actor.room!!.id, actor.token).room!!
        return f.execute(fields(RoomCommand(commandId = f.id(), kind = kind, roomId = room.id, token = actor.token,
            expectedOrderNumber = room.orderNumber, expectedRevision = room.revision)))
    }
    private fun member(f: RoomFixture, identity: RoomReply, roomId: String): RoomReply {
        val home = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.HOME, identityToken = identity.identityToken)).home!!
        val membership = home.rooms.single { it.roomId == roomId }
        return f.service.snapshot(roomId, membership.token).copy(token = membership.token)
    }

    @Test fun existingBillGoesStraightToWalletWithPrivateSharesPhotoAndPartialPayments() = RoomFixture(Identity()).use { f ->
        val owner = signIn(f, "payer"); val friend = signIn(f, "friend")
        val c = command(f, owner, friend)
        val created = f.execute(c)
        val room = created.room!!
        assertEquals(RoomPhase.FULFILLED, room.phase)
        assertTrue(room.restaurantPaid && room.carts.all { it.submitted })
        assertNull(room.spin)
        assertEquals(3000L, created.receipts.sumOf { it.total })
        assertEquals(0L, created.receipts.single { it.memberId == created.memberId }.balance)
        val view = member(f, friend, room.id)
        assertEquals(1, view.receipts.size)
        assertEquals(1500L, view.receipts.single().balance)
        assertEquals(c.paymentRoom!!.details, view.room!!.paymentRoom)
        assertTrue(view.room!!.carts.single { it.memberId == created.memberId }.lines.isEmpty())
        assertEquals(created.token, f.execute(c).token)
        assertEquals(2, f.db.allRooms().size) // fixture room plus one idempotently-created payment room
        f.restart()
        assertEquals(1500L, member(f, friend, room.id).receipts.single().balance)
        val paid = send(f, created, CommandKind.RECORD_PAYMENT) { it.copy(memberId = view.memberId, amount = 1500, text = "Cash received") }
        assertEquals(0L, paid.receipts.single { it.memberId == view.memberId }.balance)
        val archived = send(f, created, CommandKind.ARCHIVE)
        assertEquals(RoomPhase.ARCHIVED, archived.room!!.phase)
        val next = RoomCommand(commandId = f.id(), kind = CommandKind.NEXT_ORDER, roomId = room.id, token = created.token,
            expectedOrderNumber = 1, expectedRevision = archived.room!!.revision)
        assertFalse(f.service.execute(next).ok)
    }

    @Test fun correctionsKeepPaymentsAndTurnOverpaymentsIntoRefundsWithoutApprovals() = RoomFixture(Identity()).use { f ->
        val owner = signIn(f, "payer"); val friend = signIn(f, "friend")
        val created = f.execute(command(f, owner, friend)); val view = member(f, friend, created.room!!.id)
        val changed = send(f, created, CommandKind.UPDATE_PAYMENT_SHARE) { it.copy(memberId = view.memberId, amount = 200, text = "Corrected share") }
        assertTrue(changed.room!!.restaurantPaid)
        assertEquals(-300L, changed.receipts.single { it.memberId == view.memberId }.balance)
        assertTrue(changed.room!!.carts.all { it.submitted })
        val refund = send(f, created, CommandKind.DECLARE_REFUND) { it.copy(memberId = view.memberId, amount = 300, text = "Refund sent") }.room!!.transfers.last()
        send(f, view, CommandKind.CONFIRM_REFUND) { it.copy(transferId = refund.id) }
        assertEquals(0L, member(f, friend, created.room!!.id).receipts.single().balance)
    }

    @Test fun payerOnlyEditsAndReceivedPaymentsCannotDuplicatePendingClaims() = RoomFixture(Identity()).use { f ->
        val owner = signIn(f, "payer"); val friend = signIn(f, "friend")
        val created = f.execute(command(f, owner, friend)); val view = member(f, friend, created.room!!.id)
        for (kind in listOf(CommandKind.UPDATE_PAYMENT_SHARE, CommandKind.UPDATE_PAYMENT_RECEIPT, CommandKind.RECORD_PAYMENT)) {
            assertFalse(f.service.execute(RoomCommand(commandId = f.id(), kind = kind, roomId = created.room!!.id, token = view.token,
                expectedRevision = created.room!!.revision, expectedOrderNumber = 1, memberId = view.memberId, amount = 100, text = "Change",
                paymentRoom = PaymentRoomRequest(PaymentRoomDetails("Changed"), emptyList()))).ok)
        }
        send(f, view, CommandKind.DECLARE_TRANSFER) { it.copy(amount = 500, text = "Bank transfer") }
        val latest = f.service.snapshot(created.room!!.id, created.token)
        assertFalse(f.service.execute(RoomCommand(commandId = f.id(), kind = CommandKind.RECORD_PAYMENT, roomId = created.room!!.id, token = created.token,
            expectedRevision = latest.room!!.revision, expectedOrderNumber = 1, memberId = view.memberId, amount = 500, text = "Duplicate")).ok)
        val stranger = signIn(f, "stranger")
        assertFalse(f.service.execute(RoomCommand(commandId = f.id(), kind = CommandKind.JOIN, identityToken = stranger.identityToken, name = "Stranger", code = created.room!!.code)).ok)
    }

    @Test fun invalidSharesUsersAndPhotosAreRejectedAtomically() = RoomFixture(Identity()).use { f ->
        val owner = signIn(f, "payer"); val friend = signIn(f, "friend")
        val c = command(f, owner, friend); val request = c.paymentRoom!!
        val invalid = listOf(
            c.copy(amount = 3001), c.copy(identityToken = ""), c.copy(account = null),
            c.copy(paymentRoom = request.copy(shares = request.shares + request.shares.last())),
            c.copy(paymentRoom = request.copy(shares = request.shares.map { it.copy(received = it.amount + 1) })),
            c.copy(paymentRoom = request.copy(shares = request.shares.map { if (it.userId == "friend") it.copy(userId = "missing") else it })),
            c.copy(paymentRoom = request.copy(details = request.details.copy(receiptPhoto = "data:image/svg+xml;base64,PHN2Zz4="))),
            c.copy(paymentRoom = request.copy(details = request.details.copy(receiptPhoto = "data:image/png;base64," + "A".repeat(600001))))
        )
        invalid.forEach { assertFalse(f.service.execute(it.copy(commandId = f.id())).ok) }
        assertEquals(1, f.db.allRooms().size)
        assertTrue(f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.HOME, identityToken = friend.identityToken)).home!!.rooms.isEmpty())
        f.db.putRecord("profile:friend", orderJson.encodeToString(friend.home!!.profile.copy(discoverable = false)))
        assertFalse(f.service.execute(c).ok)
    }

    @Test fun receiptChangesAreVersionedAndDoNotChangeTheLedger() = RoomFixture(Identity()).use { f ->
        val owner = signIn(f, "payer"); val friend = signIn(f, "friend")
        val created = f.execute(command(f, owner, friend))
        val update = RoomCommand(commandId = f.id(), kind = CommandKind.UPDATE_PAYMENT_RECEIPT, roomId = created.room!!.id, token = created.token,
            expectedRevision = created.room!!.revision, expectedOrderNumber = 1, paymentRoom = PaymentRoomRequest(PaymentRoomDetails("Corrected details"), emptyList()))
        val updated = f.execute(update)
        assertEquals(created.receipts, updated.receipts)
        assertEquals("Corrected details", updated.room!!.paymentRoom!!.orderDetails)
        assertFalse(f.service.execute(update.copy(commandId = f.id())).ok)
    }
}
