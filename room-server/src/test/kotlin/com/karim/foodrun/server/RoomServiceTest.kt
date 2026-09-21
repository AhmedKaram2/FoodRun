package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import java.util.concurrent.Executors
import kotlin.test.*

class RoomServiceTest {
    @Test fun selectedMemberOrdersPaysAndSettlesIncludingTheirOwnFoodWithoutOwnerControl(): Unit = RoomFixture().use { f ->
        val selected = f.join("Selected person"); f.approve(selected)
        f.send(f.owner, CommandKind.READY) { it.copy(flag = true, eligible = false) }
        val preparation = f.send(f.owner, CommandKind.PREPARE_SPIN).room!!.preparationId
        f.send(f.owner, CommandKind.ACK_SPIN) { it.copy(text = preparation) }
        val spinning = f.send(selected, CommandKind.ACK_SPIN) { it.copy(text = preparation) }.room!!
        assertEquals(selected.memberId, spinning.spin!!.winnerId)
        f.now = spinning.spin!!.endAt; f.service.tick()
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.ACCEPT_DUTY)).ok)
        f.send(selected, CommandKind.ACCEPT_DUTY)
        assertEquals(selected.memberId, f.state().room!!.payerId)
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.SHARE_ACCOUNT).copy(account = f.account)).ok)
        f.send(selected, CommandKind.SHARE_ACCOUNT) { it.copy(account = f.account.copy(holder = "Selected person")) }
        f.cart(f.owner, 2); f.cart(selected, 3)
        f.send(selected, CommandKind.SET_FEES) { it.copy(fees = FeePolicy(delivery = 100), text = "Split delivery equally") }
        val receipts = f.state(selected).receipts
        assertEquals(600, receipts.sumOf { it.total })
        assertEquals(350, receipts.single { it.memberId == selected.memberId }.total)
        assertEquals(250, receipts.single { it.memberId == f.owner.memberId }.total)
        assertEquals(0, receipts.single { it.memberId == selected.memberId }.balance)
        f.send(selected, CommandKind.REVIEW)
        listOf(f.owner, selected).forEach { actor -> f.send(actor, CommandKind.CONFIRM_QUOTE) { it.copy(expectedRevision = f.state(actor).room!!.quoteRevision) } }
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.PLACE).copy(text = "Owner cannot place")).ok)
        f.send(selected, CommandKind.PLACE) { it.copy(text = "Selected person placed order; 30 minutes") }
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.PAY_RESTAURANT).copy(amount = 600)).ok)
        f.send(selected, CommandKind.PAY_RESTAURANT) { it.copy(amount = 600) }
        assertFalse(f.service.execute(f.command(selected, CommandKind.DECLARE_TRANSFER).copy(amount = 350, text = "No self transfer")).ok)
        val transfer = f.send(f.owner, CommandKind.DECLARE_TRANSFER) { it.copy(amount = 250, text = "Paid selected person") }.room!!.transfers.single()
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.CONFIRM_TRANSFER).copy(transferId = transfer.id)).ok)
        f.send(selected, CommandKind.CONFIRM_TRANSFER) { it.copy(transferId = transfer.id) }
        f.send(selected, CommandKind.FULFILL)
        f.send(selected, CommandKind.ARCHIVE)
        assertEquals(RoomPhase.ARCHIVED, f.state().room!!.phase)
        assertTrue(f.state(selected).receipts.all { it.balance == 0L })
    }

    @Test fun adminCannotCancelPlacedOrdersAndHideOutstandingBalances(): Unit = RoomFixture().use { f ->
        val member = f.placed(); f.pay()
        val before = f.state(member).receipts.single().balance
        assertTrue(before > 0)
        assertFailsWith<IllegalArgumentException> { f.service.adminCancel(f.owner.room!!.id) }
        assertEquals(RoomPhase.PLACED, f.state().room!!.phase)
        assertEquals(before, f.state(member).receipts.single().balance)
        f.send(f.owner, CommandKind.FULFILL)
        assertFailsWith<IllegalArgumentException> { f.service.adminCancel(f.owner.room!!.id) }
    }

    @Test fun adminCanCancelAnUnplacedRoom(): Unit = RoomFixture().use { f ->
        f.service.adminCancel(f.owner.room!!.id)
        assertEquals(RoomPhase.CANCELLED, f.state().room!!.phase)
    }
    @Test fun restaurantVotesStayLiveAndTheWinnerUnlocksSandwichOrdering(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member)
        val current = f.db.room(f.owner.room!!.id)!!
        val second = current.restaurant.copy(id = "second-restaurant", name = "Second Sandwich Shop")
        f.db.save(current.copy(
            restaurantOptions = listOf(current.restaurant, second),
            restaurantVotes = listOf(RestaurantVote(f.owner.memberId, current.restaurant.id)),
            restaurantPollOpen = true,
        ))

        f.send(member, CommandKind.VOTE_RESTAURANT) { it.copy(text = second.id) }
        f.send(f.owner, CommandKind.VOTE_RESTAURANT) { it.copy(text = second.id) }
        assertFalse(f.service.execute(f.command(member, CommandKind.CART).copy(expectedRevision = 0, cart = MemberCart(member.memberId))).ok)

        val chosen = f.send(f.owner, CommandKind.FINALIZE_RESTAURANT).room!!
        assertEquals(second.id, chosen.restaurant.id)
        assertFalse(chosen.restaurantPollOpen)
        assertEquals(2, chosen.restaurantVotes.count { it.restaurantId == second.id })
        assertTrue(f.service.execute(f.command(member, CommandKind.CART).copy(expectedRevision = 0, cart = MemberCart(member.memberId))).ok)

        f.restart()
        val restored = f.state(member).room!!
        assertEquals(second.id, restored.restaurant.id)
        assertTrue(restored.members.single { it.id == member.memberId }.ready)
        assertTrue(restored.members.single { it.id == member.memberId }.participating)
    }
    @Test fun completeMealFlowSettlesAndReusesPermanentRoomAndMembership(): Unit = RoomFixture().use { f ->
        val member = f.placed(); f.pay()
        val declared = f.send(member, CommandKind.DECLARE_TRANSFER) { it.copy(amount = 3000, text = "Bank transfer") }
        f.send(f.owner, CommandKind.CONFIRM_TRANSFER) { it.copy(transferId = declared.room!!.transfers.single().id) }
        f.send(f.owner, CommandKind.FULFILL)
        f.send(f.owner, CommandKind.ARCHIVE)
        val oldRoom = f.state().room!!
        val next = f.send(f.owner, CommandKind.NEXT_ORDER).room!!
        assertEquals(oldRoom.id, next.id); assertEquals(oldRoom.code, next.code)
        assertEquals(2, next.orderNumber)
        assertEquals(oldRoom.members.map { it.id }, next.members.map { it.id })
        assertTrue(next.carts.isEmpty()); assertNull(next.account); assertTrue(next.transfers.isEmpty())
        assertFalse(next.members.single { it.id == member.memberId }.participating)
        assertEquals(1, f.state(member).history.size)
        assertEquals(0, f.state(member).history.single().receipts.single().balance)
        f.now += 10L * 365 * 24 * 60 * 60 * 1000
        f.restart()
        assertTrue(f.state(member).ok)
        f.send(member, CommandKind.PARTICIPATE) { it.copy(flag = true) }
        assertTrue(f.state(member).room!!.orderingMembers.any { it.id == member.memberId })
    }
    @Test fun payerOrderingNoFoodCanPlaceAndSettleEveryoneElsesBill(): Unit = RoomFixture().use { f ->
        val member = f.placed(ownerQuantity = 0); f.pay()
        assertEquals(0, f.state().receipts.single { it.memberId == f.owner.memberId }.total)
        assertEquals(3000, f.state(member).receipts.single().balance)
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.DECLARE_TRANSFER).copy(amount = 100, text = "self")).ok)
    }
    @Test fun retriesAreDurableAndNeverDuplicateFinancialActions(): Unit = RoomFixture().use { f ->
        val member = f.placed(); f.pay()
        val command = f.command(member, CommandKind.DECLARE_TRANSFER).copy(amount = 1000, text = "Partial payment")
        val first = f.execute(command)
        f.restart()
        assertEquals(first, f.execute(command))
        assertEquals(1, f.state().room!!.transfers.size)
        assertFalse(f.service.execute(command.copy(amount = 1500)).ok)
    }
    @Test fun concurrentRetriesChooseOneWinnerAndPersistOnlyOneSpin(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member)
        f.send(f.owner, CommandKind.READY) { it.copy(flag = true, eligible = true) }
        f.send(member, CommandKind.READY) { it.copy(flag = true, eligible = true) }
        val prepare = f.command(f.owner, CommandKind.PREPARE_SPIN)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val replies = pool.invokeAll((1..20).map { java.util.concurrent.Callable { f.service.execute(prepare) } }).map { it.get() }
            assertTrue(replies.all { it.ok }); assertEquals(1, replies.map { it.room!!.preparationId }.distinct().size)
            val preparationId = replies.first().room!!.preparationId
            f.send(f.owner, CommandKind.ACK_SPIN) { it.copy(text = preparationId) }
            val ack = f.command(member, CommandKind.ACK_SPIN).copy(text = preparationId)
            val spins = pool.invokeAll((1..20).map { java.util.concurrent.Callable { f.service.execute(ack) } }).map { it.get() }
            assertTrue(spins.all { it.ok }); assertEquals(1, spins.map { it.room!!.spin!!.id }.distinct().size)
            assertEquals(1, spins.map { it.room!!.spin!!.winnerId }.distinct().size)
        } finally { pool.shutdownNow() }
    }
    @Test fun hubRestartFinalizesTheSameSpinWithoutAnyConnectedClients(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member)
        val spin = f.start(member).room!!.spin!!
        val current = f.db.room(f.owner.room!!.id)!!
        f.db.save(current.copy(phase = RoomPhase.SPINNING, payerId = null))
        f.now = spin.endAt + 100000; f.restart(); f.service.tick()
        assertEquals(RoomPhase.ACCEPTING, f.state().room!!.phase)
        assertEquals(spin, f.state().room!!.spin)
        val revision = f.state().room!!.revision
        f.service.tick(); assertEquals(revision, f.state().room!!.revision)
    }
    @Test fun offlineMembersDoNotBlockSelectionAndSeeTheSameWinnerWhenTheyReturn(): Unit = RoomFixture().use { f ->
        val member = f.join()
        f.now += 60_000
        val started = f.send(f.owner, CommandKind.PREPARE_SPIN).room!!
        assertEquals(RoomPhase.SPINNING, started.phase)
        val spin = assertNotNull(started.spin)
        assertEquals(setOf(f.owner.memberId, member.memberId), spin.memberIds.toSet())
        f.now = spin.endAt + 1
        f.restart(); f.service.tick()
        val returned = f.state(member).room!!
        assertEquals(RoomPhase.ACCEPTING, returned.phase)
        assertEquals(spin, returned.spin)
    }
    @Test fun skippedPersistentMemberDoesNotBlockTodaysSpin(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member)
        f.send(member, CommandKind.PARTICIPATE) { it.copy(flag = false) }
        f.send(f.owner, CommandKind.READY) { it.copy(flag = true, eligible = true) }
        val preparation = f.send(f.owner, CommandKind.PREPARE_SPIN).room!!.preparationId
        assertEquals(RoomPhase.SPINNING, f.send(f.owner, CommandKind.ACK_SPIN) { it.copy(text = preparation) }.room!!.phase)
        assertFalse(f.service.execute(f.command(member, CommandKind.PARTICIPATE).copy(flag = true)).ok)
    }
    @Test fun staleRoomAndCartRevisionsNeverOverwriteNewerState(): Unit = RoomFixture().use { f ->
        val member = f.join(); val approve = f.command(f.owner, CommandKind.APPROVE).copy(memberId = member.memberId)
        f.send(f.owner, CommandKind.READY) { it.copy(flag = true) }
        assertFalse(f.service.execute(approve).ok)
        f.approve(member); f.start(member)
        val command = f.command(member, CommandKind.CART).copy(expectedRevision = 0, cart = MemberCart(member.memberId, lines = listOf(CartLine("l", "meal", 1))))
        f.execute(command)
        assertFalse(f.service.execute(command.copy(commandId = f.id(), cart = command.cart!!.copy(lines = emptyList()))).ok)
        assertEquals(1, f.state(member).room!!.carts.single { it.memberId == member.memberId }.lines.size)
    }
    @Test fun independentCartEditsDoNotConflictWithOtherMembersCartRevisions(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member); f.start(member)
        val ownerCommand = f.command(f.owner, CommandKind.CART).copy(expectedRevision = 0, cart = MemberCart(f.owner.memberId))
        val memberCommand = f.command(member, CommandKind.CART).copy(expectedRevision = 0, cart = MemberCart(member.memberId))
        f.execute(ownerCommand); f.execute(memberCommand)
        assertEquals(2, f.state().room!!.carts.size)
    }
    @Test fun personalOrdersCanBeEditedBeforeAndDuringTheSpinAndRemainAfterSelection(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member)
        fun save(actor: RoomReply, revision: Long, quantity: Int) = f.send(actor, CommandKind.CART) {
            it.copy(expectedRevision = revision, cart = MemberCart(actor.memberId, lines = listOf(CartLine("line-${actor.memberId}", "meal", quantity))))
        }

        save(f.owner, 0, 1)
        f.send(f.owner, CommandKind.SUBMIT_CART) { it.copy(expectedRevision = 1) }
        val revised = save(f.owner, 1, 2).room!!.carts.single { it.memberId == f.owner.memberId }
        assertEquals(2, revised.lines.single().quantity)
        assertFalse(revised.submitted, "Editing a saved order must require submission again")
        f.send(f.owner, CommandKind.SUBMIT_CART) { it.copy(expectedRevision = 2) }

        f.send(f.owner, CommandKind.READY) { it.copy(flag = true, eligible = true) }
        f.send(member, CommandKind.READY) { it.copy(flag = true) }
        val preparation = f.send(f.owner, CommandKind.PREPARE_SPIN).room!!.preparationId
        save(member, 0, 1)
        f.send(f.owner, CommandKind.ACK_SPIN) { it.copy(text = preparation) }
        val spinning = f.send(member, CommandKind.ACK_SPIN) { it.copy(text = preparation) }
        assertEquals(RoomPhase.SPINNING, spinning.room!!.phase)
        save(member, 1, 2)

        f.now = spinning.room!!.spin!!.endAt
        f.service.tick()
        assertEquals(RoomPhase.ACCEPTING, f.state().room!!.phase)
        save(member, 2, 3)
        f.send(f.owner, CommandKind.ACCEPT_DUTY)

        val room = f.state().room!!
        assertEquals(RoomPhase.COLLECTING, room.phase)
        assertEquals(2, room.carts.single { it.memberId == f.owner.memberId }.lines.single().quantity)
        assertEquals(3, room.carts.single { it.memberId == member.memberId }.lines.single().quantity)
    }
    @Test fun newViewersGuestsAndInactiveMembersCannotSeePrivateDataOrAct(): Unit = RoomFixture().use { f ->
        val member = f.placed(); val pending = f.join("Pending"); val guest = f.join("Guest", guest = true)
        val raw = f.db.room(f.owner.room!!.id)!!
        f.db.save(raw.copy(members = raw.members.map { if (it.id == guest.memberId) it.copy(approved = true) else it }))
        val pendingState = f.state(pending)
        assertTrue(pendingState.receipts.isEmpty()); assertNull(pendingState.room!!.account)
        assertTrue(pendingState.room!!.carts.all { it.lines.isEmpty() }); assertEquals(f.restaurant.name, pendingState.room!!.restaurant.name)
        assertTrue(pendingState.room!!.members.single { it.id == pending.memberId }.approved)
        val guestState = f.state(guest)
        assertTrue(guestState.receipts.isEmpty()); assertNull(guestState.room!!.account)
        assertTrue(guestState.room!!.carts.all { it.lines.isEmpty() }); assertTrue(guestState.room!!.audit.isEmpty())
        assertFalse(f.service.execute(f.command(pending, CommandKind.CANCEL).copy(text = "malicious")).ok)
        assertFalse(f.service.execute(f.command(guest, CommandKind.DECLARE_TRANSFER).copy(amount = 1, text = "malicious")).ok)
        val memberState = f.state(member)
        assertEquals(1, memberState.receipts.size)
        assertTrue(memberState.room!!.carts.filter { it.memberId != member.memberId }.all { it.lines.isEmpty() })
        assertEquals(2, f.state().receipts.size)
    }
    @Test fun invalidCredentialsCrossRoomTokensAndRemovedSessionsFail(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member)
        assertFalse(f.service.execute(f.command(member, CommandKind.SNAPSHOT).copy(token = "a".repeat(43))).ok)
        assertFalse(f.service.execute(f.command(member, CommandKind.SNAPSHOT).copy(roomId = "other-room")).ok)
        f.send(f.owner, CommandKind.REMOVE) { it.copy(memberId = member.memberId, text = "Removed") }
        assertFalse(f.service.execute(RoomCommand(commandId = f.id(), kind = CommandKind.SNAPSHOT, roomId = f.owner.room!!.id, token = member.token)).ok)
    }
    @Test fun aDifferentOwnerDoesNotGainOtherMembersReceipts(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member); f.start(member)
        f.send(f.owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = f.account) }; f.cart(f.owner, 45); f.cart(member, 30)
        f.send(f.owner, CommandKind.HANDOVER) { it.copy(memberId = member.memberId, text = "Manage the room") }
        assertEquals(member.memberId, f.state(member).room!!.ownerId)
        assertEquals(1, f.state(member).receipts.size)
        assertEquals(2, f.state().receipts.size)
    }
    @Test fun freshPlacementUsesUpdatedTotalsWithoutRequiringAnotherMemberConfirmation(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member); f.start(member)
        f.send(f.owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = f.account) }; f.cart(f.owner, 45); f.cart(member, 30)
        f.send(f.owner, CommandKind.REVIEW)
        val quote = f.state().room!!.quoteRevision
        f.send(member, CommandKind.CONFIRM_QUOTE) { it.copy(expectedRevision = quote) }
        f.send(f.owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = f.account.copy(identifier = "AE770331234567890123457")) }
        assertFalse(f.service.execute(f.command(member, CommandKind.CONFIRM_QUOTE).copy(expectedRevision = quote)).ok)
        assertTrue(f.service.execute(f.command(f.owner, CommandKind.PLACE).copy(text = "Expected delivery in 30 minutes")).ok)
    }
    @Test fun deadlinesRejectEditsAndSubmitUntilOrganizerReopens(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member); f.start(member)
        val raw = f.db.room(f.owner.room!!.id)!!; f.db.save(raw.copy(deadline = f.now - 1))
        assertFalse(f.service.execute(f.command(member, CommandKind.CART).copy(expectedRevision = 0, cart = MemberCart(member.memberId))).ok)
        assertFalse(f.service.execute(f.command(member, CommandKind.SUBMIT_CART).copy(expectedRevision = 0)).ok)
        f.send(f.owner, CommandKind.REOPEN) { it.copy(text = "More time") }
        f.cart(member, 30)
    }
    @Test fun partialTransfersAndRefundsRequireTheCorrectRecipientToConfirm(): Unit = RoomFixture().use { f ->
        val member = f.placed(); f.pay()
        val payment = f.send(member, CommandKind.DECLARE_TRANSFER) { it.copy(amount = 3000, text = "Paid") }.room!!.transfers.single()
        assertFalse(f.service.execute(f.command(member, CommandKind.CONFIRM_TRANSFER).copy(transferId = payment.id)).ok)
        f.send(f.owner, CommandKind.CONFIRM_TRANSFER) { it.copy(transferId = payment.id) }
        f.send(f.owner, CommandKind.ADJUST_BILL) { it.copy(amount = -750, text = "Restaurant discount") }
        f.pay()
        assertEquals(-300, f.state(member).receipts.single().balance)
        val refund = f.send(f.owner, CommandKind.DECLARE_REFUND) { it.copy(memberId = member.memberId, amount = 300, text = "Refunded") }.room!!.transfers.last()
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.CONFIRM_REFUND).copy(transferId = refund.id)).ok)
        f.send(member, CommandKind.CONFIRM_REFUND) { it.copy(transferId = refund.id) }
        assertEquals(0, f.state(member).receipts.single().balance)
    }
    @Test fun unconfirmedTransferDoesNotSettleAndCannotBeDuplicatedOrOverpaid(): Unit = RoomFixture().use { f ->
        val member = f.placed(); f.pay()
        assertFalse(f.service.execute(f.command(member, CommandKind.DECLARE_TRANSFER).copy(amount = 3001, text = "Too much")).ok)
        val transfer = f.send(member, CommandKind.DECLARE_TRANSFER) { it.copy(amount = 1000, text = "Partial") }.room!!.transfers.single()
        assertEquals(3000, f.state(member).receipts.single().balance)
        assertFalse(f.service.execute(f.command(member, CommandKind.DECLARE_TRANSFER).copy(amount = 1000, text = "Again")).ok)
        f.send(f.owner, CommandKind.REJECT_TRANSFER) { it.copy(transferId = transfer.id, text = "Not received") }
        f.send(member, CommandKind.DECLARE_TRANSFER) { it.copy(amount = 1000, text = "Correct reference") }
    }
    @Test fun unfinishedMoneyCannotBeSilentlyArchivedAndPlacedOrdersCannotBeCancelled(): Unit = RoomFixture().use { f ->
        f.placed(); f.pay()
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.CANCEL).copy(text = "Cancel placed order")).ok)
        f.send(f.owner, CommandKind.FULFILL)
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.ARCHIVE)).ok)
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.ARCHIVE).copy(text = "Outstanding reimbursement recorded")).ok)
    }
    @Test fun historicalReceiptsStayPrivateFromPeopleWhoJoinLater(): Unit = RoomFixture().use { f ->
        val member = f.placed(); f.pay(); f.send(f.owner, CommandKind.FULFILL)
        val payment = f.send(member, CommandKind.DECLARE_TRANSFER) { it.copy(amount = 3000, text = "Settled") }.room!!.transfers.single()
        f.send(f.owner, CommandKind.CONFIRM_TRANSFER) { it.copy(transferId = payment.id) }
        f.send(f.owner, CommandKind.ARCHIVE)
        f.send(f.owner, CommandKind.NEXT_ORDER)
        val later = f.join("New member"); f.approve(later)
        assertTrue(f.state(later).history.isEmpty())
        assertEquals(1, f.state(member).history.single().receipts.size)
        assertEquals(2, f.state().history.single().receipts.size)
    }
    @Test fun cancelThenNextOrderRetainsMemberCredentialsAndAppliesNewDeadline(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member)
        f.send(f.owner, CommandKind.CANCEL) { it.copy(text = "Today cancelled") }
        val room = f.send(f.owner, CommandKind.NEXT_ORDER) { it.copy(deadline = f.now + 5000) }.room!!
        assertEquals(f.now + 5000, room.deadline)
        assertEquals(member.memberId, f.state(member).memberId)
    }
    @Test fun invalidApiFieldsAndDuplicateNamesFailWithSafeErrors(): Unit = RoomFixture().use { f ->
        f.join()
        assertFalse(f.service.execute(RoomCommand(commandId = f.id(), kind = CommandKind.JOIN, name = " member ", code = f.owner.room!!.code)).ok)
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.READY).copy(protocolVersion = 99)).ok)
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.READY).copy(text = "x".repeat(501))).ok)
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.READY).copy(commandId = "short")).ok)
    }
    @Test fun secretsAreEncryptedOnDiskAndMissingKeyCannotSilentlyReplaceThem(): Unit = RoomFixture().use { f ->
        f.placed(); f.restart()
        val sqlite = java.io.File(f.directory, "rooms.sqlite").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(sqlite.contains(f.account.identifier)); assertFalse(sqlite.contains(f.owner.token))
        assertEquals(f.account.identifier, f.state().room!!.account!!.identifier)
    }
    @Test fun lateJoinEntersCollectingWithoutPayerOrOrganizerApproval(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.start(member)
        val late = f.join("Late member")
        assertTrue(late.room!!.orderingMembers.any { it.id == late.memberId && it.ready })
        f.cart(late, 2)
        assertEquals(2, f.state(late).room!!.carts.single { it.memberId == late.memberId }.lines.single().quantity)
    }
    @Test fun historyPaginationRetainsOlderOrdersWithoutExpiringMembership(): Unit = RoomFixture().use { f ->
        repeat(12) {
            f.send(f.owner, CommandKind.CANCEL) { it.copy(text = "Skipped today") }
            f.send(f.owner, CommandKind.NEXT_ORDER)
        }
        var page = f.state()
        assertEquals(5, page.history.size)
        val numbers = page.history.map { it.number }.toMutableList()
        while (page.historyNextOffset >= 0) {
            page = f.execute(f.command(f.owner, CommandKind.SNAPSHOT).copy(historyOffset = page.historyNextOffset))
            assertTrue(page.history.size <= 5)
            assertTrue(orderJson.encodeToString(page).toByteArray().size < 4 * 1024 * 1024)
            numbers.addAll(page.history.map { it.number })
        }
        assertEquals((1L..12L).reversed().toList(), numbers)
        assertEquals(13, f.state().room!!.orderNumber)
    }
    @Test fun oversizedProjectedMenusAreRejectedBeforeCreatingAnUnusableRoom(): Unit = RoomFixture().use { f ->
        val base = f.restaurant.menu.items.single()
        val items = (1..500).map { base.copy(id = "item-$it", name = "料理".repeat(50), description = "食".repeat(1500)) }
        val command = RoomCommand(commandId = f.id(), kind = CommandKind.CREATE, name = "Another owner", text = "Huge menu", restaurant = f.restaurant.copy(menu = f.restaurant.menu.copy(items = items)))
        val reply = f.service.execute(command)
        assertFalse(reply.ok)
        assertTrue(reply.error.contains("too large"))
        assertEquals(1, f.db.activeRoomCount())
    }

    @Test fun delayedLegacyAcknowledgementsCannotRerollTheWheel(): Unit = RoomFixture().use { f ->
        val member = f.join()
        val started = f.send(f.owner, CommandKind.PREPARE_SPIN).room!!
        f.now = started.spin!!.endAt + 1; f.service.tick()
        val reply = f.send(member, CommandKind.ACK_SPIN) { it.copy(text = started.preparationId) }
        assertEquals(started.spin, reply.room!!.spin)
        assertEquals(RoomPhase.ACCEPTING, reply.room!!.phase)
    }
    @Test fun thirtyMembersAndTenGuestsCanJoinButOnlyOrderersControlSpinReadiness(): Unit = RoomFixture().use { f ->
        val members = (1..29).map { f.join("Member $it").also(f::approve) }
        (1..10).forEach { f.approve(f.join("Guest $it", guest = true)) }
        for (guest in listOf(false, true)) {
            val extra = f.service.execute(RoomCommand(commandId = f.id(), kind = CommandKind.JOIN,
                code = f.owner.room!!.code, name = "Over capacity", guest = guest))
            assertFalse(extra.ok)
            assertEquals(if (guest) "Guest mode is unavailable. Sign in to join the room." else "Room capacity reached.", extra.error)
        }
        f.send(f.owner, CommandKind.READY) { it.copy(flag = true, eligible = true) }
        members.forEach { member -> f.send(member, CommandKind.READY) { it.copy(flag = true) } }
        val preparation = f.send(f.owner, CommandKind.PREPARE_SPIN).room!!.preparationId
        members.forEach { member -> f.send(member, CommandKind.ACK_SPIN) { it.copy(text = preparation) } }
        val spin = f.send(f.owner, CommandKind.ACK_SPIN) { it.copy(text = preparation) }.room!!.spin!!
        assertEquals(listOf(f.owner.memberId), spin.memberIds)
        assertEquals(40, f.state().room!!.activeMembers.size)
    }
    @Test fun databaseFailureRollsBackStateAndMissingKeyNeverCreatesANewKey(): Unit = RoomFixture().use { f ->
        val original = f.db.room(f.owner.room!!.id)!!
        assertFailsWith<IllegalStateException> {
            f.db.transaction { f.db.save(original.copy(name = "Uncommitted")); error("Simulated failure") }
        }
        assertEquals(original, f.db.room(original.id))
        val key = java.io.File(f.directory, "storage.key")
        val bytes = key.readBytes(); assertTrue(key.delete())
        try {
            assertFailsWith<IllegalArgumentException> { RoomDatabase(f.directory) }
            assertFalse(key.exists())
        } finally { key.writeBytes(bytes) }
    }

    @Test fun lobbyMenuUpdatePreservesAutomaticReadinessAndCanSpin(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member)
        val updated = f.restaurant.copy(menu = f.restaurant.menu.copy(items = f.restaurant.menu.items.map { it.copy(basePriceMinor = 150) }))
        f.send(f.owner, CommandKind.UPDATE_RESTAURANT) { it.copy(restaurant = updated, text = "Updated menu price") }
        assertTrue(f.state().room!!.orderingMembers.all { it.ready })
        assertEquals(RoomPhase.SPINNING, f.send(f.owner, CommandKind.PREPARE_SPIN).room!!.phase)
    }

    @Test fun menuChangesPreserveSubmissionsAndSelectionsWhileRejectingStaleCarts(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member); f.start(member)
        f.send(f.owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = f.account) }; f.cart(f.owner, 45); f.cart(member, 30)
        f.send(f.owner, CommandKind.REVIEW)
        val before = f.state().room!!
        val updated = before.restaurant.copy(menu = before.restaurant.menu.copy(items = before.restaurant.menu.items.map { it.copy(basePriceMinor = 110) }))
        assertFalse(f.service.execute(f.command(member, CommandKind.UPDATE_RESTAURANT).copy(restaurant = updated, text = "Price update")).ok)
        f.send(f.owner, CommandKind.UPDATE_RESTAURANT) { it.copy(restaurant = updated, text = "Restaurant price update") }
        val after = f.state().room!!
        assertEquals(RoomPhase.COLLECTING, after.phase)
        assertEquals(before.quoteRevision + 1, after.quoteRevision)
        assertTrue(after.carts.all { it.submitted && it.confirmedQuote == -1L })
        assertEquals(before.carts.map { it.lines }, after.carts.map { it.lines })
        assertEquals(4950, f.state().receipts.single { it.memberId == f.owner.memberId }.food)
        assertFalse(f.service.execute(f.command(member, CommandKind.CART).copy(expectedRevision = 1, cart = MemberCart(member.memberId))).ok)
    }
    @Test fun removedMenuSelectionsCannotBeSilentlyDeletedAndPlacedMenusAreImmutable(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member); f.start(member); f.cart(member, 30)
        val menu = f.restaurant.copy(menu = f.restaurant.menu.copy(items = listOf(f.restaurant.menu.items.single().copy(id = "replacement"))))
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.UPDATE_RESTAURANT).copy(restaurant = menu, text = "Sold out")).ok)
        assertEquals("meal", f.state(member).room!!.carts.single().lines.single().itemId)
        f.cart(member, 0)
        f.send(f.owner, CommandKind.UPDATE_RESTAURANT) { it.copy(restaurant = menu, text = "Sold-out item removed after member consent") }
        assertEquals("replacement", f.state().room!!.restaurant.menu.items.single().id)
    }
    @Test fun menuUpdatesAfterPlacementOrCurrencyChangesAreRejected(): Unit = RoomFixture().use { f ->
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.UPDATE_RESTAURANT).copy(restaurant = f.restaurant.copy(currency = "USD"), text = "Wrong currency")).ok)
        f.placed()
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.UPDATE_RESTAURANT).copy(restaurant = f.restaurant, text = "Too late")).ok)
    }

    @Test fun restoringAnAdjustmentToZeroAppliesWithoutMemberApproval(): Unit = RoomFixture().use { f ->
        val member = f.placed(); f.pay()
        f.send(f.owner, CommandKind.ADJUST_BILL) { it.copy(amount = -750, text = "Discount") }
        f.pay()
        f.send(f.owner, CommandKind.ADJUST_BILL) { it.copy(amount = 0, text = "Restaurant removed discount") }
        f.pay()
    }

}
