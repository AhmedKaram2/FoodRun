package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlin.test.*

class GroupSettlementPresentationTest {
    private val account = ReceivingAccount("account", "Karim", "Test Bank", "AE070331234567890123456")
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
        var copied = ""
        var opened = ""
        override fun read(key: String) = ""
        override fun write(key: String, value: String) = true
        override fun now() = 0L
        override fun uuid() = "test-command-123456789"
        override fun request(hub: HubPairing, body: String, callback: GroupReplyCallback) = error("Presentation must not send requests")
        override fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback) = object : GroupSubscription { override fun cancel() = Unit }
        override fun share(text: String, fileName: String) = Unit
        override fun openLink(url: String) { opened = url }
        override fun copyToClipboard(text: String) { copied = text }
        override fun importMenu(callback: GroupReplyCallback) = Unit
        override fun scanPairing(callback: GroupReplyCallback) = Unit
        override fun discover(callback: GroupReplyCallback) = Unit
    }

    /** Match the hub's privacy projection: only the payer sees every receipt. */
    private fun controller(room: Room, member: String = "payer", device: Device = Device()): GroupController {
        val isPayer = room.payerId == member
        val canViewPrices = isPayer || room.ownerId == member && room.phase in listOf(RoomPhase.COLLECTING, RoomPhase.REVIEW, RoomPhase.PLACED, RoomPhase.FULFILLED)
        val isOrderer = room.orderingMembers.any { it.id == member }
        val visibleRoom = room.copy(
            carts = room.carts.map { if (canViewPrices || it.memberId == member) it else it.copy(lines = emptyList()) },
            account = room.account.takeIf { isOrderer },
            transfers = room.transfers.filter { isOrderer && (isPayer || it.memberId == member) },
        )
        return GroupController(device).apply {
            session = StoredSession(HubPairing("https://localhost:8443", "a".repeat(64)), room.id, "token", member, room.name)
            reply = RoomReply(room = visibleRoom, memberId = member, receipts = if (isOrderer) Billing.receipts(room).filter { isPayer || it.memberId == member } else emptyList())
            page = GroupPage.ROOM
        }
    }

    private fun GroupFlowContent.action(action: GroupAction) = (buttons + cards.flatMap { it.buttons }).single { it.action == action }

    @Test fun copyAndWhatsAppLanguageDoNotChangeTheAppLanguage() {
        val translated = restaurant.copy(nameAr = "المطعم", menu = restaurant.menu.copy(items = restaurant.menu.items.map { it.copy(nameAr = "فول") }))
        val device = Device()
        val c = controller(room().copy(restaurant = translated), device = device)
        assertEquals("en", c.state.fields.single { it.key == GroupFieldKey.ORDER_COPY_LANGUAGE }.value)
        c.update(GroupFieldKey.ORDER_COPY_LANGUAGE, "ar")
        c.dispatch(GroupAction.SHARE_RESTAURANT_ORDER)
        assertTrue(device.copied.startsWith("المطعم"))
        assertTrue(device.copied.contains("٢ فول"))
        assertEquals("en", c.library.language)
        c.dispatch(GroupAction.SHARE_ORDER_WHATSAPP)
        assertTrue(java.net.URLDecoder.decode(device.opened, "UTF-8").contains("٢ فول"))
        c.library = c.library.copy(language = "ar")
        c.update(GroupFieldKey.ORDER_COPY_LANGUAGE, "en")
        c.dispatch(GroupAction.SHARE_RESTAURANT_ORDER)
        assertTrue(device.copied.startsWith("Kitchen"))
        assertTrue(device.copied.contains("2 Meal"))
        assertEquals("ar", c.library.language)
    }

    @Test fun roomOwnerCanOpenPricesForEveryMemberWithoutReceivingPayerActions() {
        for (phase in listOf(RoomPhase.COLLECTING, RoomPhase.REVIEW, RoomPhase.PLACED, RoomPhase.FULFILLED)) {
            val owner = controller(room().copy(ownerId = "member", phase = phase), "member")
            if (phase in listOf(RoomPhase.PLACED, RoomPhase.FULFILLED)) {
                assertTrue(owner.state.buttons.any { it.action == GroupAction.OPEN_ORDER_PRICES })
                assertFalse(owner.state.buttons.any { it.action == GroupAction.PAY_RESTAURANT })
                owner.dispatch(GroupAction.OPEN_ORDER_PRICES)
                assertEquals(GroupPage.PRICES, owner.state.page)
            }
            val edits = owner.state.cards.flatMap { it.buttons }.filter { it.action == GroupAction.OPEN_PRICE_ITEM }
            assertEquals(setOf("payer:line-payer", "member:line-member"), edits.map { it.value }.toSet())
            owner.dispatch(GroupAction.OPEN_PRICE_ITEM, "payer:line-payer")
            assertEquals(GroupPage.PRICE_ITEM, owner.state.page)
            val ordinary = controller(room().copy(phase = phase), "member").state
            assertFalse(ordinary.cards.flatMap { it.buttons }.any { it.action == GroupAction.OPEN_PRICE_ITEM })
            assertFalse(ordinary.buttons.any { it.action == GroupAction.OPEN_ORDER_PRICES })
        }
    }

    @Test fun membersSeeWhoIsSendingWithoutASecondConfirmation() {
        val content = GroupSettlementPresentation(controller(room(), "member")).review()
        assertFalse(content.buttons.any { it.action == GroupAction.CONFIRM_QUOTE })
        assertTrue(content.cards.single { it.id == "review-next-step" }.detail.contains("Karim"))
        assertEquals("Ready for the restaurant", content.cards.single { it.id == "review-next-step" }.title)
    }

    @Test fun payerCanPlaceAfterEveryoneSubmitsWithoutQuoteConfirmations() {
        val r = room().copy(carts = listOf(room().carts.first(), MemberCart("member", submitted = true)))
        val ready = GroupSettlementPresentation(controller(r)).review()
        val arrival = ready.fields.single { it.key == GroupFieldKey.REFERENCE }
        assertEquals("", arrival.value)
        assertTrue(arrival.label.contains("optional"))
        assertTrue(ready.action(GroupAction.PLACE).enabled)
        assertEquals("Order sent", ready.action(GroupAction.PLACE).title)
        assertTrue(ready.buttons.single { it.primary }.action == GroupAction.PLACE)
        assertFalse(ready.buttons.any { it.action == GroupAction.CONFIRM_QUOTE })
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

    @Test fun billAdjustmentAppliesDirectlyWithoutRepeatMemberApproval() {
        val r = confirmed(room()).copy(phase = RoomPhase.PLACED, billRevision = 2, adjustment = -200, adjustmentApprovals = listOf("payer"))
        val payer = GroupSettlementPresentation(controller(r)).settlement()
        assertTrue(payer.fields.any { it.key == GroupFieldKey.BILL_ADJUSTMENT })
        assertTrue(payer.buttons.single { it.action == GroupAction.PAY_RESTAURANT }.enabled)
        assertTrue(payer.cards.single { it.id == "bill-adjustment" }.detail.contains("No new approval"))
        val member = GroupSettlementPresentation(controller(r, "member")).settlement()
        assertFalse(member.buttons.any { it.action == GroupAction.APPROVE_ADJUSTMENT })
        val approved = GroupSettlementPresentation(controller(r.copy(adjustmentApprovals = listOf("payer", "member")))).settlement()
        assertTrue(approved.buttons.single { it.action == GroupAction.PAY_RESTAURANT }.enabled)
        val unchangedDraft = controller(r).apply { update(GroupFieldKey.BILL_ADJUSTMENT, "-2") }
        assertFalse(GroupSettlementPresentation(unchangedDraft).settlement().action(GroupAction.ADJUST_BILL).enabled,
            "The saved adjustment must not trigger another update unchanged.")
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
        member.library = member.library.copy(home = HomePayload(FoodProfile(name = "Test member")), sessions = listOf(member.session!!), snapshots = mapOf(placed.id to member.reply!!))
        member.page = GroupPage.HOME
        val payable = member.state.cards.single { it.id.startsWith("dashboard:wallet:") }
        assertEquals("Karim", payable.title)
        assertEquals("AED 10.00", payable.badge)
        assertTrue(payable.buttons.any { it.action == GroupAction.RESUME })
        assertTrue(payable.detail.contains("AE070331234567890123456"))
        assertFalse(payable.buttons.any { it.action == GroupAction.WALLET_PAY })
        assertTrue(member.state.cards.single { it.id == "dashboard:wallet-direction:false" }.badge.contains("10.00"))
        val payer = controller(placed)
        payer.library = payer.library.copy(home = HomePayload(FoodProfile(name = "Test member")), sessions = listOf(payer.session!!), snapshots = mapOf(placed.id to payer.reply!!))
        payer.page = GroupPage.HOME
        val receivable = payer.state.cards.single { it.id.startsWith("dashboard:wallet:") }
        assertEquals("Hassan", receivable.title)
        assertEquals("AED 10.00", receivable.badge)
        assertTrue(receivable.buttons.any { it.action == GroupAction.WALLET_CONFIRM })
        assertTrue(receivable.buttons.any { it.action == GroupAction.RESUME })
        assertTrue(payer.state.cards.single { it.id == "dashboard:wallet-direction:true" }.badge.contains("10.00"))
    }

    @Test fun homeAndProfileShareBalancesAndProfileGroupsWalletAndHistoryAfterDetails() {
        val placed = room().copy(phase = RoomPhase.PLACED, account = account)
        val c = controller(placed, "member")
        val receipt = c.reply!!.receipts.single { it.memberId == "member" }
        val snapshot = c.reply!!.copy(history = listOf(PastOrder(8, "Earlier kitchen", 100, listOf(receipt.copy(paid = receipt.total, balance = 0)))))
        c.library = c.library.copy(home = HomePayload(FoodProfile(name = "Member")), sessions = listOf(c.session!!), snapshots = mapOf(placed.id to snapshot))
        c.page = GroupPage.HOME
        val home = c.state.cards.single { it.id == "dashboard:wallet-summary" }
        c.page = GroupPage.PROFILE
        val profile = c.state
        assertEquals(home.detail, profile.sections.single { it.title == "Wallet" }.cards.single { it.id == "profile-dashboard:wallet-summary" }.detail)
        assertTrue(profile.sections.single { it.title == "Payment history" }.cards.any { it.id.endsWith(":8") && it.detail.contains("Paid") })
        assertTrue(profile.topCards.isEmpty())
        assertTrue(profile.mainFields.any { it.key == GroupFieldKey.NAME })
        assertEquals(2, profile.sections.flatMap { it.cards }.count { it.id.startsWith("profile-dashboard:payment-history:") })
    }

    @Test fun backgroundHistoryMergeKeepsOlderPaymentsAndHonorsDeletion() {
        val current = controller(room()).reply!!
        val old = PastOrder(1, "Old kitchen", 10, current.receipts)
        val cached = current.copy(history = listOf(old), historyNextOffset = -1)
        assertEquals(listOf(old), mergePaymentHistory(cached, current).history)
        assertTrue(mergePaymentHistory(cached, current.copy(deletedHistoryNumbers = setOf(1))).history.isEmpty())
    }

}
