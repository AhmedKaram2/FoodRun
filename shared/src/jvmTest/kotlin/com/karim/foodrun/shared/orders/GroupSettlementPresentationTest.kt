package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlin.test.*

class GroupSettlementPresentationTest {
    private val account = ReceivingAccount("account", "Karim", "Test Bank", "1234567890")
    private val restaurant = Restaurant("kitchen", "Kitchen", contact = RestaurantContact("+971500000000"),
        menu = Menu(categories = listOf(MenuCategory("main", "Main")), items = listOf(MenuItem("meal", "main", "Meal", basePriceMinor = 1000))))
    private fun room() = Room("room", "123456", "payer", "Lunch", restaurant, phase = RoomPhase.REVIEW,
        members = listOf(Member("payer", "Karim", approved = true), Member("member", "Hassan", approved = true)),
        carts = listOf(MemberCart("payer", lines = listOf(CartLine("line-payer", "meal", 1)), submitted = true), MemberCart("member", lines = listOf(CartLine("line-member", "meal", 1)), submitted = true)),
        payerId = "payer", account = account)
    private fun confirmed(room: Room) = room.copy(carts = room.carts.map { it.copy(confirmedQuote = room.quoteRevision) })
    private fun transfer(amount: Long = 1000, status: TransferStatus = TransferStatus.DECLARED, refund: Boolean = false) =
        Transfer("transfer", "member", amount, "Test payment", account, refund = refund, status = status)

    private class Device : GroupPlatform {
        override fun read(key: String) = ""
        override fun write(key: String, value: String) = true
        override fun now() = 0L
        override fun uuid() = "test-command-123456789"
        override fun request(hub: HubPairing, body: String, callback: GroupReplyCallback) = error("Presentation must not send requests")
        override fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback) = object : GroupSubscription { override fun cancel() = Unit }
        override fun share(text: String, fileName: String) = Unit
        override fun openLink(url: String) = Unit
        override fun importMenu(callback: GroupReplyCallback) = Unit
        override fun scanPairing(callback: GroupReplyCallback) = Unit
        override fun discover(callback: GroupReplyCallback) = Unit
    }

    /** Match the hub's privacy projection: only the payer sees every receipt. */
    private fun controller(room: Room, member: String = "payer"): GroupController {
        val isPayer = room.payerId == member
        val isOrderer = room.orderingMembers.any { it.id == member }
        val visibleRoom = room.copy(
            carts = room.carts.map { if (isPayer || it.memberId == member) it else it.copy(lines = emptyList()) },
            account = room.account.takeIf { isOrderer },
            transfers = room.transfers.filter { isOrderer && (isPayer || it.memberId == member) },
        )
        return GroupController(Device()).apply {
            session = StoredSession(HubPairing("https://localhost:8443", "a".repeat(64)), room.id, "token", member, room.name)
            reply = RoomReply(room = visibleRoom, memberId = member, receipts = if (isOrderer) Billing.receipts(room).filter { isPayer || it.memberId == member } else emptyList())
            page = GroupPage.ROOM
        }
    }

    private fun GroupFlowContent.action(action: GroupAction) = (buttons + cards.flatMap { it.buttons }).single { it.action == action }

    @Test fun ownConfirmationIsEnabledOnceAndThenBecomesWaitingFeedback() {
        val first = GroupSettlementPresentation(controller(room(), "member")).review()
        assertTrue(GroupSettlementPresentation.quoteConfirmationAction(room(), "member").enabled)
        assertFalse(first.buttons.any { it.action == GroupAction.CONFIRM_QUOTE }, "Confirmation stays attached to the receipt and recipient details.")
        val confirmedOwn = room().copy(carts = room().carts.map { if (it.memberId == "member") it.copy(confirmedQuote = 1) else it })
        val waiting = GroupSettlementPresentation(controller(confirmedOwn, "member")).review()
        assertFalse(waiting.buttons.any { it.action == GroupAction.CONFIRM_QUOTE })
        assertFalse(GroupSettlementPresentation.quoteConfirmationAction(confirmedOwn, "member").enabled)
        assertTrue(waiting.cards.single { it.id == "review-next-step" }.detail.contains("Karim"))
        assertEquals("Your total is confirmed", waiting.cards.single { it.id == "review-next-step" }.title)
    }

    @Test fun payerCannotPlaceUntilEveryOrderingMemberIncludingNoFoodConfirms() {
        val r = room().copy(carts = listOf(room().carts.first().copy(confirmedQuote = 1), MemberCart("member", submitted = true)))
        val pending = GroupSettlementPresentation(controller(r)).review()
        assertFalse(pending.action(GroupAction.PLACE).enabled)
        assertTrue(pending.cards.single { it.id == "review-next-step" }.detail.contains("Hassan"))
        val ready = GroupSettlementPresentation(controller(confirmed(r))).review()
        assertTrue(ready.action(GroupAction.PLACE).enabled)
    }

    @Test fun placementExplainsMissingContactAndMinimumBeforeTheAction() {
        val ready = confirmed(room())
        val noContact = GroupSettlementPresentation(controller(ready.copy(restaurant = restaurant.copy(contact = RestaurantContact())))).review()
        assertFalse(noContact.action(GroupAction.PLACE).enabled)
        assertTrue(noContact.cards.single { it.id == "placement-blocked" }.detail.contains("keeps everyone's food"))
        assertEquals(GroupAction.EDIT_ROOM_RESTAURANT, noContact.cards.single { it.id == "placement-blocked" }.buttons.single().action)
        val belowMinimum = GroupSettlementPresentation(controller(ready.copy(restaurant = restaurant.copy(pricing = restaurant.pricing.copy(minimumOrderMinor = 3000))))).review()
        assertFalse(belowMinimum.action(GroupAction.PLACE).enabled)
        assertTrue(belowMinimum.cards.single { it.id == "placement-blocked" }.detail.contains("AED 30.00"))
    }

    @Test fun confirmedRestaurantPaymentAdvancesToFoodArrivalInsteadOfRepeatingPayment() {
        val r = confirmed(room()).copy(phase = RoomPhase.PLACED)
        val before = GroupSettlementPresentation(controller(r)).settlement()
        assertTrue(before.buttons.any { it.action == GroupAction.PAY_RESTAURANT })
        val paid = GroupSettlementPresentation(controller(r.copy(restaurantPaid = true))).settlement()
        assertFalse(paid.buttons.any { it.action == GroupAction.PAY_RESTAURANT })
        assertEquals(GroupAction.FULFILL, paid.buttons.single { it.primary }.action)
        assertFalse(paid.fields.any { it.key == GroupFieldKey.AMOUNT }, "A payer with no refund must not see a payment amount field after paying.")
    }

    @Test fun pendingTransferWaitsForPayerAndCannotBeDeclaredAgain() {
        val r = confirmed(room()).copy(phase = RoomPhase.PLACED, restaurantPaid = true, transfers = listOf(transfer()))
        val member = GroupSettlementPresentation(controller(r, "member")).settlement()
        assertFalse(member.buttons.any { it.action == GroupAction.DECLARE_TRANSFER })
        assertFalse(member.fields.any { it.key == GroupFieldKey.AMOUNT })
        assertTrue(member.cards.single { it.id == "my-payment-status" }.detail.contains("Do not send the same payment again"))
    }

    @Test fun confirmedPartialPaymentOffersOnlyTheRemainingAmountAndRejectedClaimsCanRetry() {
        val r = confirmed(room()).copy(phase = RoomPhase.PLACED, restaurantPaid = true)
        val partial = GroupSettlementPresentation(controller(r.copy(transfers = listOf(transfer(400, TransferStatus.CONFIRMED))), "member")).settlement()
        assertTrue(partial.buttons.any { it.action == GroupAction.DECLARE_TRANSFER })
        assertTrue(partial.fields.single { it.key == GroupFieldKey.AMOUNT }.label.contains("AED 6.00"))
        val rejected = GroupSettlementPresentation(controller(r.copy(transfers = listOf(transfer(status = TransferStatus.REJECTED))), "member")).settlement()
        assertTrue(rejected.buttons.any { it.action == GroupAction.DECLARE_TRANSFER })
        assertTrue(rejected.fields.single { it.key == GroupFieldKey.AMOUNT }.label.contains("AED 10.00"))
    }

    @Test fun fullySettledAndNoFoodMembersDoNotGetPaymentForms() {
        val paid = confirmed(room()).copy(phase = RoomPhase.FULFILLED, restaurantPaid = true, transfers = listOf(transfer(status = TransferStatus.CONFIRMED)))
        for (r in listOf(paid, paid.copy(carts = paid.carts.map { if (it.memberId == "member") it.copy(lines = emptyList()) else it }, transfers = emptyList()))) {
            val member = GroupSettlementPresentation(controller(r, "member")).settlement()
            assertFalse(member.buttons.any { it.action == GroupAction.DECLARE_TRANSFER })
            assertTrue(member.cards.single { it.id == "my-payment-status" }.detail.contains("No further payment"))
            assertTrue(member.fields.isEmpty())
        }
    }

    @Test fun unpaidRestaurantAndSpectatorsDoNotGetMemberPaymentActions() {
        val r = confirmed(room()).copy(phase = RoomPhase.PLACED)
        val member = GroupSettlementPresentation(controller(r, "member")).settlement()
        assertFalse(member.buttons.any { it.action == GroupAction.DECLARE_TRANSFER })
        assertTrue(member.cards.single { it.id == "my-payment-status" }.detail.contains("confirm the restaurant payment"))
        val guest = GroupSettlementPresentation(controller(r.copy(members = r.members + Member("guest", "Guest", approved = true, guest = true)), "guest")).settlement()
        assertTrue(guest.fields.isEmpty())
        assertTrue(guest.buttons.isEmpty())
    }

    @Test fun billAdjustmentUsesSeparateInputAndWaitsForAllApprovalsBeforePayment() {
        val r = confirmed(room()).copy(phase = RoomPhase.PLACED, billRevision = 2, adjustment = -200, adjustmentApprovals = listOf("payer"))
        val payer = GroupSettlementPresentation(controller(r)).settlement()
        assertTrue(payer.fields.any { it.key == GroupFieldKey.BILL_ADJUSTMENT })
        assertFalse(payer.buttons.single { it.action == GroupAction.PAY_RESTAURANT }.enabled)
        assertTrue(payer.cards.single { it.id == "bill-adjustment" }.detail.contains("Hassan"))
        val member = GroupSettlementPresentation(controller(r, "member")).settlement()
        assertEquals(GroupAction.APPROVE_ADJUSTMENT, member.buttons.single { it.primary }.action)
        val approved = GroupSettlementPresentation(controller(r.copy(adjustmentApprovals = listOf("payer", "member")))).settlement()
        assertTrue(approved.buttons.single { it.action == GroupAction.PAY_RESTAURANT }.enabled)
        val unchangedDraft = controller(r).apply { update(GroupFieldKey.BILL_ADJUSTMENT, "-2") }
        assertFalse(GroupSettlementPresentation(unchangedDraft).settlement().action(GroupAction.ADJUST_BILL).enabled,
            "The saved adjustment must not trigger another approval cycle unchanged.")
        unchangedDraft.update(GroupFieldKey.BILL_ADJUSTMENT, "0")
        assertTrue(GroupSettlementPresentation(unchangedDraft).settlement().action(GroupAction.ADJUST_BILL).enabled)
    }

    @Test fun refundWaitStatesBlockDuplicateDeclarationsUntilClaimResolved() {
        val r = confirmed(room()).copy(phase = RoomPhase.FULFILLED, restaurantPaid = true, billRevision = 2,
            adjustment = -200, adjustmentApprovals = listOf("payer", "member"), transfers = listOf(transfer(status = TransferStatus.CONFIRMED)))
        val receipt = Billing.receipts(r).single { it.memberId == "member" }
        assertEquals(-100L, receipt.balance)
        assertTrue(GroupSettlementPresentation.refundAvailable(r, receipt))
        assertEquals("Refund due AED 1.00", GroupSettlementPresentation.receiptBalanceText(receipt, r.payerId))
        val member = GroupSettlementPresentation(controller(r, "member")).settlement()
        assertFalse(member.buttons.any { it.action == GroupAction.DECLARE_TRANSFER })
        assertTrue(member.cards.single { it.id == "my-payment-status" }.detail.contains("refund of AED 1.00"))
        val pending = r.copy(transfers = r.transfers + transfer(100, refund = true).copy(id = "refund"))
        assertFalse(GroupSettlementPresentation.refundAvailable(pending, receipt))
        assertFalse(GroupSettlementPresentation.refundAvailable(r.copy(restaurantPaid = false), receipt))
        assertFalse(GroupSettlementPresentation.refundAvailable(r.copy(phase = RoomPhase.ARCHIVED), receipt))
    }

    @Test fun payerOrganizerCanCompleteOnlyAfterArrivalPaymentAndSettlement() {
        val fulfilled = confirmed(room()).copy(phase = RoomPhase.FULFILLED, restaurantPaid = true)
        val unpaidMember = GroupSettlementPresentation(controller(fulfilled)).settlement()
        assertFalse(unpaidMember.buttons.single { it.action == GroupAction.ARCHIVE }.enabled)
        val settled = fulfilled.copy(transfers = listOf(transfer(status = TransferStatus.CONFIRMED)))
        val ready = GroupSettlementPresentation(controller(settled)).settlement()
        assertTrue(ready.buttons.single { it.action == GroupAction.ARCHIVE }.enabled)
        assertEquals(GroupAction.ARCHIVE, ready.buttons.single { it.primary }.action)
        val unpaidRestaurant = GroupSettlementPresentation(controller(settled.copy(restaurantPaid = false))).settlement()
        assertFalse(unpaidRestaurant.buttons.single { it.action == GroupAction.ARCHIVE }.enabled)
    }

    @Test fun nonpayerOrganizerDoesNotInferOtherMembersSettlementFromItsPrivateReceipt() {
        val r = confirmed(room()).copy(ownerId = "member", phase = RoomPhase.FULFILLED, restaurantPaid = true,
            members = room().members + Member("another", "Fakhr", approved = true),
            carts = room().carts + MemberCart("another", lines = listOf(CartLine("line-third", "meal", 1)), submitted = true),
            transfers = listOf(transfer(status = TransferStatus.CONFIRMED)))
        val c = controller(r, "member")
        assertEquals(listOf("member"), c.reply!!.receipts.map { it.memberId })
        val presentation = GroupSettlementPresentation(c).settlement()
        assertTrue(presentation.cards.single { it.id == "complete-order-guidance" }.detail.contains("hub checks"))
        c.reply = c.reply!!.copy(progress = OrderProgress(canArchive = false, archiveBlocker = "Waiting for the remaining reimbursement."))
        val blocked = GroupSettlementPresentation(c).settlement()
        assertFalse(blocked.action(GroupAction.ARCHIVE).enabled)
        assertEquals("Waiting for the remaining reimbursement.", blocked.cards.single { it.id == "complete-order-guidance" }.detail)
        c.reply = c.reply!!.copy(progress = OrderProgress(canArchive = true))
        val complete = GroupSettlementPresentation(c).settlement()
        assertTrue(complete.action(GroupAction.ARCHIVE).enabled)
        assertTrue(complete.cards.single { it.id == "complete-order-guidance" }.detail.contains("Every reimbursement and refund is settled"))
    }
    @Test fun profileWalletSeparatesPeopleAndPendingApprovalInBothDirections() {
        val placed = room().copy(phase = RoomPhase.PLACED, account = account.copy(holder = "Bank holder"), transfers = listOf(transfer()))
        val member = controller(placed, "member")
        member.library = member.library.copy(sessions = listOf(member.session!!), snapshots = mapOf(placed.id to member.reply!!))
        member.page = GroupPage.HOME
        val payable = member.state.cards.single { it.id.startsWith("dashboard:wallet:") }
        assertEquals("Karim", payable.title)
        assertEquals("AED 10.00", payable.badge)
        assertEquals("View pending payment", payable.buttons.single().title)
        assertTrue(member.state.cards.single { it.id == "dashboard:wallet-direction:false" }.badge.contains("10.00"))
        val payer = controller(placed)
        payer.library = payer.library.copy(sessions = listOf(payer.session!!), snapshots = mapOf(placed.id to payer.reply!!))
        payer.page = GroupPage.HOME
        val receivable = payer.state.cards.single { it.id.startsWith("dashboard:wallet:") }
        assertEquals("Hassan", receivable.title)
        assertEquals("AED 10.00", receivable.badge)
        assertEquals("Review and approve receipt", receivable.buttons.single().title)
        assertTrue(payer.state.cards.single { it.id == "dashboard:wallet-direction:true" }.badge.contains("10.00"))
    }

}
