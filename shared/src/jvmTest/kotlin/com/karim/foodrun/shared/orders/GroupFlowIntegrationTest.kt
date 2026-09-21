package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import com.karim.foodrun.server.RoomDatabase
import com.karim.foodrun.server.RoomService
import java.nio.file.Files
import java.util.UUID
import kotlin.test.*

class GroupFlowIntegrationTest {
    private class Bus : AutoCloseable {
        val directory = Files.createTempDirectory("foodrun-client-test").toFile()
        val db = RoomDatabase(directory)
        var time = 1_800_000_000_000L
        val server = RoomService(db, { time })
        val phones = mutableListOf<Phone>()
        val queue = ArrayDeque<() -> Unit>()
        fun phone(saved: MutableMap<String, String> = mutableMapOf()): Pair<GroupController, Phone> {
            val phone = Phone(this, saved); phones += phone
            val controller = GroupController(phone)
            controller.observe(object : GroupObserver {
                override fun changed(state: GroupState) {
                    assertEquals(state.cards.size, state.cards.map { it.id }.distinct().size, "Duplicate card keys on ${state.page}: ${state.cards.map { it.id }}")
                }
            })
            return controller to phone
        }
        fun drain() { var remaining = 1000; while(queue.isNotEmpty()) { check(--remaining > 0); queue.removeFirst()() } }
        fun sync() { phones.toList().forEach { p -> p.watch?.let { (request, callback) -> callback.complete(orderJson.encodeToString(server.execute(request)), "") } }; drain() }
        override fun close() { db.close(); directory.deleteRecursively() }
    }
    private class Phone(val bus: Bus, val saved: MutableMap<String, String>) : GroupPlatform {
        var watch: Pair<RoomCommand, GroupReplyCallback>? = null
        var dropNext = false
        var throwNext = false
        var failureCode: String? = null
        val requestedHubs = mutableListOf<HubPairing>()
        var storageWorks = true
        var writes = 0
        var shared = ""
        override fun read(key: String) = saved[key] ?: ""
        override fun write(key: String, value: String): Boolean { writes++; if(!storageWorks) return false; saved[key] = value; return true }
        override fun now() = bus.time
        override fun uuid() = UUID.randomUUID().toString()
        override fun request(hub: HubPairing, body: String, callback: GroupReplyCallback) {
            if (throwNext) { throwNext = false; error("Transport could not start") }
            requestedHubs += hub
            val reply = bus.server.execute(orderJson.decodeFromString<RoomCommand>(body))
            val failure = failureCode; failureCode = null
            val dropped = dropNext; dropNext = false
            bus.queue += { callback.complete(if(dropped) "" else orderJson.encodeToString(if (failure != null) RoomReply(ok = false, code = failure, error = "Hub interrupted") else reply), if(dropped) "Connection lost" else "") }
        }
        override fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback): GroupSubscription {
            val current = orderJson.decodeFromString<RoomCommand>(body) to callback; watch = current
            return object : GroupSubscription { override fun cancel() { if(watch === current) watch = null } }
        }
        override fun share(text: String, fileName: String) { shared = text }
        override fun openLink(url: String) { shared = url }
        override fun importMenu(callback: GroupReplyCallback) { callback.complete(menu(), "") }
        override fun scanPairing(callback: GroupReplyCallback) { callback.complete("", "Scan cancelled") }
        override fun discover(callback: GroupReplyCallback) { callback.complete("https://localhost:8443", "") }
    }
    companion object {
        private fun menu() = orderJson.encodeToString(RestaurantExport(exportId = "menu-source", restaurant = Restaurant("kitchen", "Test Kitchen", contact = RestaurantContact("+971500000000"), menu = Menu(categories = listOf(MenuCategory("main", "Main")), items = listOf(MenuItem("burger", "main", "Burger", basePriceMinor = 3500), MenuItem("water", "main", "Water", basePriceMinor = 300))))))
        private fun connect(c: GroupController, join: Boolean = false) {
            c.dispatch(if(join) GroupAction.JOIN else GroupAction.CREATE)
            c.update(GroupFieldKey.HUB_URL, "https://localhost:8443"); c.update(GroupFieldKey.FINGERPRINT, "a".repeat(64)); c.dispatch(GroupAction.CONNECT)
            assertEquals(GroupPage.SETUP, c.state.page)
        }
        private fun create(c: GroupController, bus: Bus) {
            connect(c); c.update(GroupFieldKey.NAME, "Karim"); c.update(GroupFieldKey.ROOM_NAME, "The lunch table")
            c.dispatch(GroupAction.OPEN_LIBRARY); c.dispatch(GroupAction.IMPORT_MENU); c.dispatch(GroupAction.CONFIRM_IMPORT); c.dispatch(GroupAction.SELECT_RESTAURANT, "kitchen")
            c.update(GroupFieldKey.DELIVERY_FEE, "10"); c.dispatch(GroupAction.CREATE_ROOM); bus.drain(); bus.sync()
            assertEquals("", c.state.error); assertEquals(GroupPage.ROOM, c.state.page)
        }
        private fun join(c: GroupController, name: String, host: GroupController, bus: Bus) {
            connect(c, true); c.update(GroupFieldKey.NAME, name); c.update(GroupFieldKey.ROOM_CODE, host.room().code); c.dispatch(GroupAction.JOIN_ROOM); bus.drain(); bus.sync()
            assertEquals("", c.state.error)
            assertTrue(c.room().members.single { it.id == c.me() }.approved)
            assertFalse(host.state.cards.flatMap { it.buttons }.any { it.action in listOf(GroupAction.APPROVE, GroupAction.APPROVE_LATE_JOIN) })
        }
        private fun collecting(host: GroupController, member: GroupController, bus: Bus, memberPays: Boolean = false) {
            host.update(GroupFieldKey.ELIGIBLE, "true"); bus.drain(); host.dispatch(GroupAction.READY); bus.drain()
            member.dispatch(GroupAction.READY); bus.drain(); bus.sync()
            // Exercise the supported legacy opt-out while keeping this helper's payer deterministic.
            (if(memberPays) host else member).update(GroupFieldKey.ELIGIBLE, "false"); bus.drain(); bus.sync()
            host.dispatch(GroupAction.PREPARE_SPIN); bus.drain(); repeat(4) { bus.sync() }
            bus.time += 10000; bus.server.tick(); bus.sync()
            (if(memberPays) member else host).dispatch(GroupAction.ACCEPT_DUTY); bus.drain(); bus.sync()
            assertEquals(RoomPhase.COLLECTING, host.room().phase)
        }
        private fun review(host: GroupController, member: GroupController, bus: Bus) {
            collecting(host, member, bus)
            host.dispatch(GroupAction.OPEN_ACCOUNT); host.update(GroupFieldKey.ACCOUNT_HOLDER, "Karim")
            host.update(GroupFieldKey.ACCOUNT_BANK, "Example Bank"); host.update(GroupFieldKey.ACCOUNT_IDENTIFIER, "AE070331234567890123456")
            host.dispatch(GroupAction.SHARE_ACCOUNT); bus.drain(); bus.sync()
            listOf(host, member).forEach { controller ->
                controller.dispatch(GroupAction.OPEN_ITEM, "burger"); controller.dispatch(GroupAction.ADD_CART_ITEM); bus.drain(); bus.sync()
                controller.dispatch(GroupAction.SUBMIT_CART); bus.drain(); bus.sync()
            }
            host.dispatch(GroupAction.REVIEW); bus.drain(); bus.sync()
            assertEquals(RoomPhase.REVIEW, host.room().phase)
        }
        private fun placeAndPay(host: GroupController, member: GroupController, bus: Bus) {
            review(host, member, bus)
            listOf(host, member).forEach { it.dispatch(GroupAction.CONFIRM_QUOTE); bus.drain(); bus.sync() }
            host.update(GroupFieldKey.REFERENCE, "Restaurant confirmed"); host.dispatch(GroupAction.PLACE); bus.drain(); bus.sync()
            host.update(GroupFieldKey.AMOUNT, "80"); host.dispatch(GroupAction.PAY_RESTAURANT); bus.drain(); bus.sync()
            assertTrue(host.room().restaurantPaid)
        }

    }
    @Test fun savedAccountSelectionLeadsToFoodSubmissionAndOrganizerReview() = Bus().use { bus ->
        val (host, _) = bus.phone(); val (payer, phone) = bus.phone()
        create(host, bus); join(payer, "Hassan", host, bus); collecting(host, payer, bus, memberPays = true)
        assertEquals(GroupAction.OPEN_ACCOUNT, payer.state.primaryAction?.action)
        payer.dispatch(GroupAction.OPEN_ACCOUNT)
        payer.update(GroupFieldKey.ACCOUNT_HOLDER, "Hassan")
        payer.update(GroupFieldKey.ACCOUNT_BANK, "Example Bank")
        payer.update(GroupFieldKey.ACCOUNT_IDENTIFIER, "AE070331234567890123456")
        payer.dispatch(GroupAction.SAVE_ACCOUNT)
        val savedAccount = payer.library.accounts.single()
        assertFalse(payer.state.cards.single().buttons.single { it.action == GroupAction.SELECT_ACCOUNT }.enabled)
        payer.dispatch(GroupAction.NEW_ACCOUNT)
        assertTrue(payer.state.cards.single().buttons.single { it.action == GroupAction.SELECT_ACCOUNT }.enabled)
        payer.dispatch(GroupAction.SELECT_ACCOUNT, savedAccount.id)
        assertEquals(savedAccount.identifier, payer.text(GroupFieldKey.ACCOUNT_IDENTIFIER))
        assertNull(payer.room().account, "Selecting locally must not silently share bank details")
        payer.dispatch(GroupAction.SHARE_ACCOUNT)
        assertEquals(GroupPage.ACCOUNT, payer.state.page, "Wait for the hub to acknowledge sharing")
        bus.drain(); bus.sync()
        assertEquals(GroupPage.ROOM, payer.state.page)
        assertEquals(savedAccount.id, host.room().account?.id)
        assertEquals(GroupAction.SUBMIT_CART, payer.state.primaryAction?.action)
        assertTrue(payer.state.utilityButtons.any { it.action == GroupAction.OPEN_ACCOUNT })
        listOf(host, payer).forEach { c ->
            c.dispatch(GroupAction.OPEN_ITEM, "burger"); c.dispatch(GroupAction.ADD_CART_ITEM); bus.drain(); bus.sync()
            c.dispatch(GroupAction.SUBMIT_CART); bus.drain(); bus.sync()
            assertFalse(c.state.buttons.any { it.action == GroupAction.SUBMIT_CART })
        }
        assertNull(host.state.primaryAction, "The selected person handles sending the order")
        assertTrue(payer.state.buttons.single { it.action == GroupAction.PLACE }.enabled)
        payer.dispatch(GroupAction.REVIEW); bus.drain(); bus.sync()
        assertEquals(RoomPhase.REVIEW, payer.room().phase)
        payer.dispatch(GroupAction.CONFIRM_QUOTE); bus.drain(); bus.sync()
        val quote = payer.room().quoteRevision
        payer.dispatch(GroupAction.OPEN_ACCOUNT)
        assertEquals("Continue to order", payer.state.primaryAction?.title)
        payer.dispatch(GroupAction.SHARE_ACCOUNT); bus.drain(); bus.sync()
        assertEquals(quote, payer.room().quoteRevision, "Sharing unchanged details must not reset confirmations")
        assertEquals(quote, payer.myCart().confirmedQuote)
        payer.close()
        val (restored, _) = bus.phone(phone.saved)
        restored.dispatch(GroupAction.RESUME, host.room().id); bus.sync()
        restored.dispatch(GroupAction.OPEN_ACCOUNT)
        assertEquals(savedAccount.id, restored.selectedAccount?.id)
        assertEquals("Continue to order", restored.state.primaryAction?.title)
    }

    @Test fun lostAccountAcknowledgementKeepsTheFormAndMakesRetryThePrimaryAction() = Bus().use { bus ->
        val (host, phone) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Hassan", host, bus); collecting(host, member, bus)
        host.dispatch(GroupAction.OPEN_ACCOUNT)
        host.update(GroupFieldKey.ACCOUNT_HOLDER, "Karim")
        host.update(GroupFieldKey.ACCOUNT_BANK, "Example Bank")
        host.update(GroupFieldKey.ACCOUNT_IDENTIFIER, "AE070331234567890123456")
        phone.dropNext = true
        host.dispatch(GroupAction.SHARE_ACCOUNT); bus.drain()
        assertEquals(GroupPage.ACCOUNT, host.state.page)
        assertEquals(GroupAction.RETRY, host.state.primaryAction?.action)
        assertFalse(host.state.busy)
        val command = host.library.pending!!
        host.dispatch(GroupAction.RETRY); bus.drain(); bus.sync()
        assertEquals(GroupPage.ROOM, host.state.page)
        assertNull(host.library.pending)
        assertEquals(1, bus.db.room(host.room().id)!!.audit.count { it.id == command.commandId })
        assertEquals(1, host.library.accounts.size)
    }

    @Test fun rejectedAccountShareRetainsEditableDetailsAndDoesNotPretendToAdvance() = Bus().use { bus ->
        val (host, _) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Hassan", host, bus); collecting(host, member, bus)
        host.dispatch(GroupAction.OPEN_ACCOUNT)
        host.update(GroupFieldKey.ACCOUNT_HOLDER, "Karim")
        host.update(GroupFieldKey.ACCOUNT_BANK, "Example Bank")
        host.update(GroupFieldKey.ACCOUNT_IDENTIFIER, "AE070331234567890123456")
        member.dispatch(GroupAction.SUBMIT_CART); bus.drain() // Hub advances before the payer receives its snapshot.
        host.dispatch(GroupAction.SHARE_ACCOUNT); bus.drain()
        assertEquals(GroupPage.ACCOUNT, host.state.page)
        assertTrue(host.state.error.isNotEmpty())
        assertNull(host.library.pending)
        assertNull(host.room().account)
        assertEquals("AE070331234567890123456", host.text(GroupFieldKey.ACCOUNT_IDENTIFIER))
        bus.sync()
        host.dispatch(GroupAction.SHARE_ACCOUNT); bus.drain(); bus.sync()
        assertEquals(GroupPage.ROOM, host.state.page)
        assertEquals("", host.state.error)
    }

    @Test fun collectingPrimaryActionAdvancesAndFoodEditsRequireResubmission() = Bus().use { bus ->
        val (host, _) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Hassan", host, bus); review(host, member, bus)
        host.update(GroupFieldKey.REASON, "Change food"); host.dispatch(GroupAction.REOPEN); bus.drain(); bus.sync()
        assertEquals(GroupAction.PLACE, host.state.primaryAction?.action)
        host.dispatch(GroupAction.OPEN_ITEM, "water"); host.dispatch(GroupAction.ADD_CART_ITEM); bus.drain(); bus.sync()
        assertEquals(GroupAction.SUBMIT_CART, host.state.primaryAction?.action)
        assertFalse(host.state.buttons.single { it.action == GroupAction.PLACE }.enabled)
        host.dispatch(GroupAction.SUBMIT_CART); bus.drain(); bus.sync()
        assertEquals(GroupAction.PLACE, host.state.primaryAction?.action)
        assertTrue(host.state.primaryAction!!.enabled)
    }

    @Test fun noFoodOrdersShowAnExplanationInsteadOfAnEnabledReviewAction() = Bus().use { bus ->
        val (host, _) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Hassan", host, bus); collecting(host, member, bus)
        host.dispatch(GroupAction.OPEN_ACCOUNT)
        host.update(GroupFieldKey.ACCOUNT_HOLDER, "Karim")
        host.update(GroupFieldKey.ACCOUNT_BANK, "Example Bank")
        host.update(GroupFieldKey.ACCOUNT_IDENTIFIER, "AE070331234567890123456")
        host.dispatch(GroupAction.SHARE_ACCOUNT); bus.drain(); bus.sync()
        listOf(host, member).forEach { it.dispatch(GroupAction.SUBMIT_CART); bus.drain(); bus.sync() }
        assertFalse(host.state.primaryAction!!.enabled)
        assertTrue(host.state.cards.single { it.id == "order-next-step" }.detail.contains("No food was ordered"))
    }

    @Test fun anOrganizerOrderingNoFoodCanReviewAnotherMembersPrivateCart() = Bus().use { bus ->
        val (host, _) = bus.phone(); val (payer, _) = bus.phone()
        create(host, bus); join(payer, "Hassan", host, bus); collecting(host, payer, bus, memberPays = true)
        payer.dispatch(GroupAction.OPEN_ACCOUNT)
        payer.update(GroupFieldKey.ACCOUNT_HOLDER, "Hassan"); payer.update(GroupFieldKey.ACCOUNT_BANK, "Test Bank")
        payer.update(GroupFieldKey.ACCOUNT_IDENTIFIER, "AE070331234567890123456"); payer.dispatch(GroupAction.SHARE_ACCOUNT); bus.drain(); bus.sync()
        payer.dispatch(GroupAction.OPEN_ITEM, "burger"); payer.dispatch(GroupAction.ADD_CART_ITEM); bus.drain(); bus.sync()
        listOf(host, payer).forEach { it.dispatch(GroupAction.SUBMIT_CART); bus.drain(); bus.sync() }
        assertTrue(host.reply!!.receipts.single().lines.isEmpty(), "Other members' food stays private")
        assertNull(host.state.primaryAction)
        assertTrue(payer.state.primaryAction!!.enabled, "The selected person can send the complete food order")
        payer.update(GroupFieldKey.REFERENCE, "Arrives in 30 minutes")
        payer.dispatch(GroupAction.PLACE); bus.drain(); bus.sync()
        assertEquals(RoomPhase.PLACED, payer.room().phase)
    }

    @Test fun failedCartSaveKeepsTheItemFormUntilRetryConfirmsIt() = Bus().use { bus ->
        val (host, phone) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Hassan", host, bus); collecting(host, member, bus)
        host.dispatch(GroupAction.OPEN_ITEM, "burger"); host.update(GroupFieldKey.NOTE, "No onions")
        phone.dropNext = true
        host.dispatch(GroupAction.ADD_CART_ITEM); bus.drain()
        assertEquals(GroupPage.ITEM, host.page)
        assertEquals("No onions", host.text(GroupFieldKey.NOTE))
        assertEquals(GroupAction.RETRY, host.state.primaryAction?.action)
        host.dispatch(GroupAction.RETRY); bus.drain(); bus.sync()
        assertEquals(GroupPage.ROOM, host.page)
        assertEquals("No onions", host.myCart().lines.single().notes)
    }

    @Test fun aContactRepairFromTheRoomKeepsSubmittedFoodAndConfirmedQuotes() = Bus().use { bus ->
        val (host, _) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Hassan", host, bus); review(host, member, bus)
        listOf(host, member).forEach { it.dispatch(GroupAction.CONFIRM_QUOTE); bus.drain(); bus.sync() }
        val quote = host.room().quoteRevision
        val carts = host.room().carts
        host.dispatch(GroupAction.EDIT_ROOM_RESTAURANT)
        assertEquals(GroupPage.RESTAURANT, host.page)
        assertEquals("Save & update this order", host.state.primaryAction?.title)
        host.update(GroupFieldKey.PHONE, "+971501234567")
        host.dispatch(GroupAction.SAVE_RESTAURANT)
        assertEquals(GroupPage.RESTAURANT, host.page, "Wait for the hub's acknowledgement")
        bus.drain(); bus.sync()
        assertEquals("", host.state.error)
        assertEquals(GroupPage.ROOM, host.page)
        assertEquals(RoomPhase.REVIEW, member.room().phase)
        assertEquals(quote, member.room().quoteRevision)
        assertEquals(carts, host.room().carts)
        assertEquals("+971501234567", member.room().restaurant.contact.phoneE164)
        assertEquals(1000, Money.parse(host.text(GroupFieldKey.DELIVERY_FEE), "AED"), "Restaurant defaults must not replace room fees")
    }

    @Test fun cancellingTheCurrentOrderRestaurantEditorReturnsDirectlyToTheRoom() = Bus().use { bus ->
        val (host, _) = bus.phone(); create(host, bus)
        val original = host.room().restaurant
        host.dispatch(GroupAction.EDIT_ROOM_RESTAURANT)
        host.update(GroupFieldKey.PHONE, "+971501234567")
        host.dispatch(GroupAction.BACK)
        assertEquals(GroupPage.ROOM, host.page)
        assertNull(host.editingRoomOrder)
        assertNull(host.editingRestaurant)
        assertEquals(original, host.room().restaurant)
        assertEquals("10", host.text(GroupFieldKey.DELIVERY_FEE))
    }

    @Test fun restaurantTaxCanBeResolvedInTheRoomAndInvalidRateKeepsTheEditor() = Bus().use { bus ->
        val (host, _) = bus.phone(); create(host, bus)
        host.dispatch(GroupAction.EDIT_ROOM_RESTAURANT)
        host.dispatch(GroupAction.SELECT_TAX_TREATMENT, TaxTreatment.ADDED.name)
        host.update(GroupFieldKey.TAX_RATE, "101")
        host.dispatch(GroupAction.SAVE_RESTAURANT)
        assertEquals(GroupPage.RESTAURANT, host.page)
        assertTrue(host.state.error.contains("100 percent"))
        host.update(GroupFieldKey.TAX_RATE, "5.25")
        host.update(GroupFieldKey.MINIMUM_ORDER, "15.50")
        host.dispatch(GroupAction.SAVE_RESTAURANT); bus.drain(); bus.sync()
        assertEquals("", host.state.error)
        assertEquals(525, host.room().restaurant.pricing.taxRateBasisPoints)
        assertEquals(1550, host.room().restaurant.pricing.minimumOrderMinor)
        assertEquals(TaxTreatment.ADDED, host.room().restaurant.pricing.taxTreatment)
    }

    @Test fun paymentDraftsFollowTheCurrentBillWithoutReusingTheRestaurantReference() = Bus().use { bus ->
        val (host, _) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Hassan", host, bus); review(host, member, bus)
        listOf(host, member).forEach { it.dispatch(GroupAction.CONFIRM_QUOTE); bus.drain(); bus.sync() }
        host.update(GroupFieldKey.REFERENCE, "Restaurant ETA 30 minutes")
        host.dispatch(GroupAction.PLACE); bus.drain(); bus.sync()
        assertEquals("80.00", host.text(GroupFieldKey.AMOUNT))
        assertEquals("", host.text(GroupFieldKey.REFERENCE))
        host.dispatch(GroupAction.PAY_RESTAURANT); bus.drain(); bus.sync()
        assertEquals("40.00", member.text(GroupFieldKey.AMOUNT))
        member.update(GroupFieldKey.AMOUNT, "15"); member.update(GroupFieldKey.REFERENCE, "First part")
        member.dispatch(GroupAction.DECLARE_TRANSFER); bus.drain(); bus.sync()
        assertEquals("", member.text(GroupFieldKey.REFERENCE))
        assertFalse(member.state.buttons.any { it.action == GroupAction.DECLARE_TRANSFER })
        host.dispatch(GroupAction.CONFIRM_TRANSFER, host.room().transfers.last().id); bus.drain(); bus.sync()
        assertEquals("25.00", member.text(GroupFieldKey.AMOUNT))
        host.update(GroupFieldKey.BILL_ADJUSTMENT, "-10"); host.update(GroupFieldKey.REASON, "Discount")
        host.dispatch(GroupAction.ADJUST_BILL); bus.drain(); bus.sync()
        assertEquals("70.00", host.text(GroupFieldKey.AMOUNT))
        assertEquals("-10.00", host.text(GroupFieldKey.BILL_ADJUSTMENT))
    }

    @Test fun aNewOrderClosesAnOldItemDraftBeforeItCanBeSentToThatOrder() = Bus().use { bus ->
        val (host, _) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Hassan", host, bus); collecting(host, member, bus)
        member.dispatch(GroupAction.OPEN_ITEM, "burger"); member.update(GroupFieldKey.NOTE, "Yesterday's draft")
        host.update(GroupFieldKey.REASON, "Cancel meal"); host.dispatch(GroupAction.CANCEL); bus.drain()
        host.dispatch(GroupAction.NEXT_ORDER); host.dispatch(GroupAction.CREATE_ROOM); bus.drain(); bus.sync()
        assertEquals(GroupPage.ROOM, member.page)
        assertNull(member.selectedItem)
        assertTrue(member.state.error.contains("new order"))
        assertEquals(2, member.room().orderNumber)
        assertTrue(member.myCart().lines.isEmpty())
    }

    @Test fun consentChangedAfterReadyIsSavedAndIncludedInEveryClientsSpin() = Bus().use { bus ->
        val (host, _) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Hassan", host, bus)
        listOf(host, member).forEach {
            it.dispatch(GroupAction.READY); bus.drain()
            it.update(GroupFieldKey.ELIGIBLE, "false"); bus.drain(); bus.sync()
        }
        assertTrue(host.room().orderingMembers.none { it.eligible })
        member.update(GroupFieldKey.ELIGIBLE, "true"); bus.drain(); bus.sync()
        listOf(host, member).forEach { controller ->
            val savedMember = controller.room().members.single { it.id == member.me() }
            assertTrue(savedMember.eligible, "Payment consent must reach the hub and every phone")
            assertTrue(savedMember.ready, "Changing consent must preserve readiness")
        }
        host.dispatch(GroupAction.PREPARE_SPIN); bus.drain(); repeat(4) { bus.sync() }
        assertEquals("", host.state.error)
        assertEquals(RoomPhase.SPINNING, host.room().phase)
        assertEquals(listOf(member.me()), host.room().spin!!.memberIds)
        assertEquals(host.room().spin, member.room().spin)
    }
    @Test fun approvedMemberStaysReadyWhilePayerEligibilityChanges() = Bus().use { bus ->
        val (host, _) = bus.phone(); create(host, bus)
        host.update(GroupFieldKey.ELIGIBLE, "false"); bus.drain()
        host.update(GroupFieldKey.ELIGIBLE, "true"); bus.drain(); bus.sync()
        assertTrue(host.room().orderingMembers.single().eligible)
        assertTrue(host.room().orderingMembers.single().ready)
        assertTrue(host.room().orderingMembers.single().eligible)
    }
    @Test fun revokingConsentAfterReadyRemovesMemberFromPayerCandidates() = Bus().use { bus ->
        val (host, _) = bus.phone(); create(host, bus)
        host.update(GroupFieldKey.ELIGIBLE, "true"); bus.drain()
        host.dispatch(GroupAction.READY); bus.drain()
        host.update(GroupFieldKey.ELIGIBLE, "false"); bus.drain(); bus.sync()
        assertFalse(host.room().orderingMembers.single().eligible)
        assertTrue(host.room().orderingMembers.single().ready)
        host.dispatch(GroupAction.PREPARE_SPIN); bus.drain()
        assertEquals(RoomPhase.LOBBY, host.room().phase)
        assertTrue(host.state.error.contains("consent"))
    }
    @Test fun skippedMemberCannotConsentOrBecomeReadyWithoutJoiningThisOrder() = Bus().use { bus ->
        val (host, _) = bus.phone(); create(host, bus)
        host.dispatch(GroupAction.PARTICIPATE, "false"); bus.drain(); bus.sync()
        assertFalse(host.state.fields.any { it.key == GroupFieldKey.ELIGIBLE })
        assertFalse(host.state.buttons.any { it.action == GroupAction.READY })
        host.update(GroupFieldKey.ELIGIBLE, "true"); bus.drain(); bus.sync()
        assertFalse(host.room().members.single().participating)
        assertFalse(host.room().members.single().eligible)
        assertTrue(host.state.error.contains("Join this order"))
    }
    @Test fun lostConsentAcknowledgementKeepsConfirmedValueAndRetriesAcrossRestart() = Bus().use { bus ->
        val (host, phone) = bus.phone(); create(host, bus)
        host.dispatch(GroupAction.READY); bus.drain()
        host.update(GroupFieldKey.ELIGIBLE, "false"); bus.drain()
        val beforeConsent = host.room().revision
        phone.dropNext = true
        host.update(GroupFieldKey.ELIGIBLE, "true"); bus.drain()
        assertNotNull(host.library.pending)
        assertFalse(host.flag(GroupFieldKey.ELIGIBLE), "An unconfirmed switch must not pretend it was saved")
        assertTrue(host.state.error.contains("Retry"))
        val commandId = host.library.pending!!.commandId
        host.close()
        val (restored, _) = bus.phone(phone.saved)
        restored.dispatch(GroupAction.RESUME, host.room().id)
        restored.dispatch(GroupAction.RETRY); bus.drain(); bus.sync()
        assertNull(restored.library.pending)
        assertTrue(restored.flag(GroupFieldKey.ELIGIBLE))
        assertTrue(restored.room().orderingMembers.single().ready)
        assertEquals(beforeConsent + 1, restored.room().revision, "Retry must not apply consent twice: $commandId")
    }
    @Test fun consentStorageFailureDoesNotTurnSwitchOnOrSendRequest() = Bus().use { bus ->
        val (host, phone) = bus.phone(); create(host, bus)
        host.update(GroupFieldKey.ELIGIBLE, "false"); bus.drain()
        val requests = phone.requestedHubs.size
        phone.storageWorks = false
        host.update(GroupFieldKey.ELIGIBLE, "true")
        assertTrue(host.state.error.contains("storage"))
        assertFalse(host.state.busy)
        assertFalse(host.flag(GroupFieldKey.ELIGIBLE))
        assertEquals(requests, phone.requestedHubs.size)
        assertNull(host.library.pending, "An unsaved request must not be described as saved")
    }
    @Test fun transportStartupFailureClearsBusyAndKeepsDurableConsentRetry() = Bus().use { bus ->
        val (host, phone) = bus.phone(); create(host, bus)
        host.update(GroupFieldKey.ELIGIBLE, "false"); bus.drain()
        phone.throwNext = true
        host.update(GroupFieldKey.ELIGIBLE, "true")
        assertFalse(host.state.busy)
        assertFalse(host.flag(GroupFieldKey.ELIGIBLE))
        assertTrue(host.state.error.contains("Retry"))
        assertNotNull(orderJson.decodeFromString<GroupLibrary>(phone.saved.getValue("group-library-v1")).pending)
        host.dispatch(GroupAction.RETRY); bus.drain(); bus.sync()
        assertNull(host.library.pending)
        assertTrue(host.flag(GroupFieldKey.ELIGIBLE))
    }
    @Test fun failedSnapshotSaveIsRetriedOnNextHeartbeatWithoutShowingUnsavedConsent() = Bus().use { bus ->
        val (host, phone) = bus.phone(); create(host, bus)
        host.update(GroupFieldKey.ELIGIBLE, "false"); bus.drain()
        val saved = host.room()
        bus.db.save(saved.copy(members = saved.members.map { it.copy(eligible = true) }, revision = saved.revision + 1))
        phone.storageWorks = false; bus.sync()
        assertFalse(host.flag(GroupFieldKey.ELIGIBLE))
        assertEquals(saved.revision, host.library.snapshots[saved.id]!!.room!!.revision)
        phone.storageWorks = true; bus.sync()
        assertTrue(host.flag(GroupFieldKey.ELIGIBLE))
        val restored = orderJson.decodeFromString<GroupLibrary>(phone.saved.getValue("group-library-v1"))
        assertTrue(restored.snapshots[saved.id]!!.room!!.members.single().eligible)
    }
    @Test fun payerConsentIsHiddenAndAllReadyParticipantsEnterTheWheelByDefault() = Bus().use { bus ->
        val (host, _) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Hassan", host, bus)
        listOf(host, member).forEach {
            assertFalse(it.state.fields.any { field -> field.key == GroupFieldKey.ELIGIBLE })
            assertTrue(it.room().members.single { m -> m.id == it.me() }.eligible)
            assertTrue(it.room().members.single { m -> m.id == it.me() }.ready)
            assertFalse(it.state.buttons.any { button -> button.action == GroupAction.READY })
        }
        host.dispatch(GroupAction.PREPARE_SPIN); bus.drain(); repeat(4) { bus.sync() }
        assertEquals(RoomPhase.SPINNING, host.room().phase)
        assertEquals(setOf(host.me(), member.me()), host.room().spin!!.memberIds.toSet())
    }
    @Test fun readyRestoresLegacyOptOutWithoutRequiringAHiddenSwitch() = Bus().use { bus ->
        val (host, _) = bus.phone(); create(host, bus)
        host.dispatch(GroupAction.READY); bus.drain()
        host.update(GroupFieldKey.ELIGIBLE, "false"); bus.drain(); bus.sync()
        assertEquals(GroupAction.PREPARE_SPIN, host.state.primaryAction?.action)
        assertFalse(host.state.primaryAction!!.enabled)
        host.dispatch(GroupAction.READY); bus.drain(); bus.sync()
        assertTrue(host.room().orderingMembers.single().eligible)
        assertEquals(GroupAction.PREPARE_SPIN, host.state.primaryAction?.action)
    }
    @Test fun declinedPayerCanStayInTheMealWithoutReenteringTheNextSpin() = Bus().use { bus ->
        val (host, _) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Hassan", host, bus)
        val people = listOf(host, member)
        people.forEach { it.dispatch(GroupAction.READY); bus.drain(); bus.sync() }
        host.dispatch(GroupAction.PREPARE_SPIN); bus.drain(); repeat(4) { bus.sync() }
        bus.time += 10000; bus.server.tick(); bus.sync()
        val selected = people.single { it.me() == host.room().spin!!.winnerId }
        selected.update(GroupFieldKey.REASON, "Unable to advance payment today")
        selected.dispatch(GroupAction.DECLINE_DUTY); bus.drain(); bus.sync()
        selected.dispatch(GroupAction.READY); bus.drain(); bus.sync()
        val declined = host.room().members.single { it.id == selected.me() }
        assertTrue(declined.participating)
        assertTrue(declined.ready)
        assertFalse(declined.eligible)
        host.dispatch(GroupAction.PREPARE_SPIN); bus.drain(); repeat(4) { bus.sync() }
        assertEquals(RoomPhase.SPINNING, host.room().phase)
        assertEquals(people.filterNot { it === selected }.map { it.me() }, host.room().spin!!.memberIds)
    }
    @Test fun threeClientsCompleteMealPersistReceiptAndReuseRoomDaysLater() = Bus().use { bus ->
        val (host, _) = bus.phone(); val (second, secondPhone) = bus.phone(); val (third, _) = bus.phone()
        create(host, bus); join(second, "Karam", host, bus); join(third, "Hassan", host, bus)
        val people = listOf(host, second, third)
        assertEquals(GroupAction.PREPARE_SPIN, host.state.primaryAction?.action)
        assertEquals(2, host.state.progressStep)
        people.forEach { assertTrue(it.room().members.single { member -> member.id == it.me() }.ready) }
        assertEquals(GroupAction.PREPARE_SPIN, host.state.primaryAction?.action)
        host.dispatch(GroupAction.PREPARE_SPIN); bus.drain(); repeat(4) { bus.sync() }
        assertEquals(RoomPhase.SPINNING, host.room().phase)
        assertEquals(3, host.state.progressStep)
        assertEquals(1, people.map { it.room().spin!!.id }.distinct().size)
        bus.time += 10000; bus.server.tick(); bus.sync()
        val payer = people.single { it.me() == host.room().spin!!.winnerId }
        payer.dispatch(GroupAction.ACCEPT_DUTY); bus.drain(); bus.sync()
        payer.dispatch(GroupAction.OPEN_ACCOUNT); payer.update(GroupFieldKey.ACCOUNT_HOLDER, payer.room().members.single { it.id == payer.me() }.name)
        payer.update(GroupFieldKey.ACCOUNT_BANK, "Example Bank"); payer.update(GroupFieldKey.ACCOUNT_IDENTIFIER, "AE070331234567890123456")
        payer.dispatch(GroupAction.SHARE_ACCOUNT); bus.drain(); bus.sync()
        people.forEach { it.dispatch(GroupAction.OPEN_ITEM, "burger"); it.dispatch(GroupAction.ADD_CART_ITEM); bus.drain(); bus.sync(); it.dispatch(GroupAction.SUBMIT_CART); bus.drain(); bus.sync(); assertEquals("", it.state.error) }
        host.dispatch(GroupAction.REVIEW); bus.drain(); bus.sync()
        people.forEach { it.dispatch(GroupAction.CONFIRM_QUOTE); bus.drain(); bus.sync() }
        payer.update(GroupFieldKey.REFERENCE, "Confirmed by restaurant · 20 minutes"); payer.dispatch(GroupAction.PLACE); bus.drain(); bus.sync()
        assertEquals(RoomPhase.PLACED, host.room().phase)
        payer.update(GroupFieldKey.AMOUNT, "115.00"); payer.dispatch(GroupAction.PAY_RESTAURANT); bus.drain(); bus.sync()
        assertTrue(host.room().restaurantPaid)
        people.filterNot { it === payer }.forEach { member ->
            val receipt = member.reply!!.receipts.single { it.memberId == member.me() }
            member.update(GroupFieldKey.AMOUNT, Money.format(receipt.balance, receipt.currency).substringAfter(' ')); member.update(GroupFieldKey.REFERENCE, "Test bank transfer")
            member.dispatch(GroupAction.DECLARE_TRANSFER); bus.drain(); bus.sync()
            payer.dispatch(GroupAction.CONFIRM_TRANSFER, payer.room().transfers.last().id); bus.drain(); bus.sync()
        }
        assertTrue(payer.reply!!.receipts.all { it.balance == 0L })
        payer.dispatch(GroupAction.FULFILL); bus.drain(); bus.sync(); host.dispatch(GroupAction.ARCHIVE); bus.drain(); bus.sync()
        assertEquals(RoomPhase.ARCHIVED, host.room().phase)
        val roomId = second.room().id; val memberId = second.me(); val token = second.session!!.token
        second.background(); second.close()
        val (restored, _) = bus.phone(secondPhone.saved)
        restored.dispatch(GroupAction.RESUME, roomId); restored.background(); restored.dispatch(GroupAction.OPEN_RECEIPTS)
        assertTrue(restored.state.cards.any { it.title.contains("Karam") })
        assertFalse(restored.state.online)
        bus.time += 7 * 86400000L
        host.dispatch(GroupAction.NEXT_ORDER); host.dispatch(GroupAction.CREATE_ROOM); bus.drain(); bus.sync()
        assertEquals("", host.state.error); assertEquals(RoomPhase.LOBBY, host.room().phase); assertEquals(2L, host.room().orderNumber)
        restored.foreground(); bus.sync()
        assertEquals(roomId, restored.room().id); assertEquals(memberId, restored.me()); assertEquals(token, restored.session!!.token)
        assertEquals(1, restored.reply!!.history.size)
        assertFalse(restored.room().members.single { it.id == restored.me() }.participating)
        restored.dispatch(GroupAction.PARTICIPATE, "true"); bus.drain(); bus.sync()
        assertTrue(restored.room().members.single { it.id == restored.me() }.participating)
    }
    @Test fun lostCreateAcknowledgementRetriesSameRequestWithoutDuplicateRoom() = Bus().use { bus ->
        val (host, phone) = bus.phone(); phone.dropNext = true
        connect(host); host.update(GroupFieldKey.NAME, "Karim"); host.update(GroupFieldKey.ROOM_NAME, "Persistent table")
        host.dispatch(GroupAction.OPEN_LIBRARY); host.dispatch(GroupAction.IMPORT_MENU); host.dispatch(GroupAction.CONFIRM_IMPORT); host.dispatch(GroupAction.SELECT_RESTAURANT, "kitchen")
        host.dispatch(GroupAction.CREATE_ROOM); bus.drain(); assertNotNull(host.library.pending)
        val commandId = host.library.pending!!.commandId
        host.dispatch(GroupAction.RETRY); bus.drain(); assertNull(host.library.pending)
        assertEquals(1, bus.db.allRooms().size); assertEquals(commandId.length, 36)
    }
    @Test fun malformedStorageCannotBeOverwrittenAndInvalidPairingCannotConnect() = Bus().use { bus ->
        val saved = mutableMapOf("group-library-v1" to "unreadable")
        val (c, _) = bus.phone(saved)
        assertTrue(c.state.error.contains("could not be opened"))
        c.dispatch(GroupAction.CREATE); c.update(GroupFieldKey.HUB_URL, "http://localhost:8443"); c.update(GroupFieldKey.FINGERPRINT, "a".repeat(64)); c.dispatch(GroupAction.CONNECT)
        assertEquals(GroupPage.CONNECT, c.state.page)
        assertEquals("unreadable", saved["group-library-v1"])
        c.update(GroupFieldKey.HUB_URL, "https://localhost:8443"); c.dispatch(GroupAction.CONNECT)
        assertTrue(c.state.error.contains("Saved data")); assertEquals("unreadable", saved["group-library-v1"])
    }
    @Test fun observerDetachesAndMenuImportRequiresExplicitConfirmation() = Bus().use { bus ->
        val (c, _) = bus.phone(); var updates = 0
        val observer = object : GroupObserver { override fun changed(state: GroupState) { updates++ } }
        c.observe(observer); c.removeObserver(observer); c.dispatch(GroupAction.OPEN_LIBRARY)
        assertEquals(1, updates)
        val initialCount = c.library.restaurants.size
        c.dispatch(GroupAction.IMPORT_MENU); assertEquals(initialCount, c.library.restaurants.size)
        c.dispatch(GroupAction.CONFIRM_IMPORT); assertEquals(initialCount + 1, c.library.restaurants.size)
        c.dispatch(GroupAction.BACK); assertEquals(GroupPage.HOME, c.page)
    }
    @Test fun unchangedHeartbeatsDoNotRewriteEncryptedLibraryEverySecond() = Bus().use { bus ->
        val (c, phone) = bus.phone(); create(c, bus)
        val writes = phone.writes
        repeat(30) { bus.time += 1000; bus.sync() }
        assertEquals(writes, phone.writes)
        bus.time += 31000; bus.sync(); assertEquals(writes + 1, phone.writes)
    }
    @Test fun delayedRequestAfterBackgroundDoesNotReconnectUntilForeground() = Bus().use { bus ->
        val (c, phone) = bus.phone(); create(c, bus)
        c.update(GroupFieldKey.ELIGIBLE, "true"); bus.drain(); c.dispatch(GroupAction.READY)
        c.background(); bus.drain()
        assertNull(phone.watch)
        assertNull(orderJson.decodeFromString<GroupLibrary>(phone.saved.getValue("group-library-v1")).pending)
        assertFalse(c.state.online)
        c.foreground(); assertNotNull(phone.watch); Unit
    }
    @Test fun downloadedHistorySurvivesLiveRefreshAndAppRestart() = Bus().use { bus ->
        val (c, phone) = bus.phone(); create(c, bus)
        repeat(12) {
            c.update(GroupFieldKey.REASON, "Test cancelled meal"); c.dispatch(GroupAction.CANCEL); bus.drain(); bus.sync()
            c.dispatch(GroupAction.NEXT_ORDER); c.dispatch(GroupAction.CREATE_ROOM); bus.drain(); bus.sync()
            assertEquals("", c.state.error)
        }
        c.dispatch(GroupAction.OPEN_HISTORY)
        while(c.reply!!.historyNextOffset >= 0) { c.dispatch(GroupAction.LOAD_OLDER_HISTORY); bus.drain() }
        assertEquals(12, c.reply!!.history.size); bus.sync()
        assertEquals(-1, c.reply!!.historyNextOffset)
        c.close(); val (restored, _) = bus.phone(phone.saved)
        restored.dispatch(GroupAction.RESUME, c.room().id); restored.background(); restored.dispatch(GroupAction.OPEN_HISTORY)
        assertEquals(12, restored.reply!!.history.size)
    }
    @Test fun pendingCreateKeepsItsOriginalHubAcrossHubSwitchAndAppRestart(): Unit = Bus().use { bus ->
        val (host, phone) = bus.phone(); phone.dropNext = true
        connect(host); host.update(GroupFieldKey.NAME, "Karim"); host.update(GroupFieldKey.ROOM_NAME, "Persistent table")
        host.dispatch(GroupAction.OPEN_LIBRARY); host.dispatch(GroupAction.IMPORT_MENU); host.dispatch(GroupAction.CONFIRM_IMPORT); host.dispatch(GroupAction.SELECT_RESTAURANT, "kitchen")
        host.dispatch(GroupAction.CREATE_ROOM); bus.drain()
        val originalHub = host.library.pendingHub!!
        host.dispatch(GroupAction.CREATE); host.update(GroupFieldKey.HUB_URL, "https://other-hub:8443"); host.update(GroupFieldKey.FINGERPRINT, "b".repeat(64)); host.dispatch(GroupAction.CONNECT)
        assertNotEquals(originalHub, host.library.selectedHub)
        host.close()
        val (restored, restoredPhone) = bus.phone(phone.saved)
        restored.dispatch(GroupAction.RETRY); bus.drain(); bus.sync()
        assertEquals(originalHub, restoredPhone.requestedHubs.last())
        assertEquals(originalHub, restored.session!!.hub)
        assertNull(restored.library.pending); assertNull(restored.library.pendingHub)
        assertEquals(1, bus.db.allRooms().size)
    }
    @Test fun uncertainServerFailureKeepsCommandForIdempotentRetry(): Unit = Bus().use { bus ->
        val (c, phone) = bus.phone(); create(c, bus)
        phone.failureCode = "HUB_UNAVAILABLE"
        c.dispatch(GroupAction.READY); bus.drain()
        val pending = requireNotNull(c.library.pending)
        c.dispatch(GroupAction.RETRY); bus.drain(); bus.sync()
        assertNull(c.library.pending)
        assertEquals(1, bus.db.room(c.room().id)!!.audit.count { it.id == pending.commandId })
    }
    @Test fun offlineMemberReturnsToThePersistedWheelResult(): Unit = Bus().use { bus ->
        val (host, _) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Karam", host, bus)
        member.background()
        host.dispatch(GroupAction.PREPARE_SPIN); bus.drain(); bus.sync()
        assertEquals(RoomPhase.SPINNING, host.room().phase)
        val spin = host.room().spin!!
        host.background(); bus.time = spin.endAt + 1; bus.server.tick()
        member.foreground(); bus.sync()
        assertEquals(RoomPhase.ACCEPTING, member.room().phase)
        assertEquals(spin, member.room().spin)
        host.foreground(); bus.sync()
        assertEquals(host.room().spin, member.room().spin)
        assertNull(member.library.pending)
    }
    @Test fun absentExpectedPersistentMemberCanBeSkippedWithoutRevokingMembership(): Unit = Bus().use { bus ->
        val (host, _) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Karam", host, bus)
        host.update(GroupFieldKey.REASON, "Tomorrow"); host.dispatch(GroupAction.CANCEL); bus.drain(); bus.sync()
        host.dispatch(GroupAction.NEXT_ORDER); host.update(GroupFieldKey.EXPECTED_NAMES, "Karam"); host.dispatch(GroupAction.CREATE_ROOM); bus.drain(); bus.sync()
        val invite = host.state.cards.single { it.id == "invite:Karam" }
        host.update(GroupFieldKey.REASON, "Not joining today")
        host.dispatch(invite.buttons.single().action, invite.buttons.single().value); bus.drain(); bus.sync()
        assertTrue(host.room().expectedNames.isEmpty())
        assertTrue(host.room().activeMembers.any { it.id == member.me() })
        assertFalse(host.room().members.single { it.id == member.me() }.removed)
    }
    @Test fun orderShowsAmountAndRecipientWithoutASecondConfirmation(): Unit = Bus().use { bus ->
        val (host, _) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Karam", host, bus); review(host, member, bus)
        val account = member.state.cards.single { it.id == "account" }
        assertTrue(account.detail.contains("AE070331234567890123456")); assertTrue(account.title.contains("Karim"))
        assertTrue(member.state.buttons.none { it.action == GroupAction.CONFIRM_QUOTE })
        assertTrue(host.state.cards.single { it.id == "review-total" }.detail.contains("AED 80.00"))
        assertEquals(GroupAction.PLACE, host.state.primaryAction?.action)
        assertTrue(host.state.primaryAction!!.enabled)
    }
    @Test fun aRefundRecipientCanRejectAFalseClaimFromTheReceiptsScreen(): Unit = Bus().use { bus ->
        val (host, _) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Karam", host, bus); placeAndPay(host, member, bus)
        member.update(GroupFieldKey.AMOUNT, "40"); member.update(GroupFieldKey.REFERENCE, "Bank transfer"); member.dispatch(GroupAction.DECLARE_TRANSFER); bus.drain(); bus.sync()
        host.dispatch(GroupAction.CONFIRM_TRANSFER, host.room().transfers.last().id); bus.drain(); bus.sync()
        host.update(GroupFieldKey.REASON, "Restaurant discount"); host.update(GroupFieldKey.BILL_ADJUSTMENT, "-10"); host.dispatch(GroupAction.ADJUST_BILL); bus.drain(); bus.sync()
        member.dispatch(GroupAction.APPROVE_ADJUSTMENT); bus.drain(); bus.sync()
        host.update(GroupFieldKey.AMOUNT, "70"); host.dispatch(GroupAction.PAY_RESTAURANT); bus.drain(); bus.sync()
        assertEquals("5.00", host.text(GroupFieldKey.AMOUNT))
        host.dispatch(GroupAction.OPEN_RECEIPTS)
        assertTrue(host.state.fields.any { it.key == GroupFieldKey.AMOUNT }); assertTrue(host.state.fields.any { it.key == GroupFieldKey.REFERENCE })
        host.update(GroupFieldKey.AMOUNT, "5"); host.update(GroupFieldKey.REFERENCE, "Refund claim"); host.dispatch(GroupAction.DECLARE_REFUND, member.me()); bus.drain(); bus.sync()
        member.dispatch(GroupAction.OPEN_RECEIPTS)
        val refund = member.state.cards.single { it.buttons.any { button -> button.title == "Reject refund claim" } }
        assertTrue(member.state.fields.any { it.key == GroupFieldKey.REASON })
        member.update(GroupFieldKey.REASON, "Not received"); member.dispatch(GroupAction.REJECT_TRANSFER, refund.buttons.last().value); bus.drain(); bus.sync()
        assertEquals(TransferStatus.REJECTED, host.room().transfers.last().status)
        assertEquals(-500, member.reply!!.receipts.single().balance)
        assertEquals("5.00", host.text(GroupFieldKey.AMOUNT))
    }
    @Test fun resumingAndExternalFeeChangesSeedTheAuthoritativeFeeDraft(): Unit = Bus().use { bus ->
        val (host, phone) = bus.phone(); create(host, bus); val roomId = host.room().id
        host.close(); val (restored, _) = bus.phone(phone.saved)
        restored.dispatch(GroupAction.RESUME, roomId)
        assertEquals("10.00", restored.text(GroupFieldKey.DELIVERY_FEE))
        val raw = bus.db.room(roomId)!!
        bus.db.save(raw.copy(fees = raw.fees.copy(delivery = 1500), revision = raw.revision + 1))
        bus.sync(); assertEquals("15.00", restored.text(GroupFieldKey.DELIVERY_FEE))
    }
    @Test fun predefinedMenuQuantityActionsNeedNoTypedNamesAndPreserveOneCartLine(): Unit = Bus().use { bus ->
        val (c, _) = bus.phone(); create(c, bus)
        repeat(2) { c.dispatch(GroupAction.QUICK_ADD_ITEM, "burger"); bus.drain(); bus.sync() }
        assertEquals("", c.state.error)
        val line = c.myCart().lines.single()
        assertEquals("burger", line.itemId)
        assertEquals("", line.description)
        assertEquals(2, line.quantity)
        assertEquals("AED 70.00", c.state.cards.single { it.id == "cart:${line.id}" }.badge)
        c.dispatch(GroupAction.DECREASE_CART_QUANTITY, line.id); bus.drain(); bus.sync()
        assertEquals(1, c.myCart().lines.single().quantity)
        c.dispatch(GroupAction.INCREASE_CART_QUANTITY, line.id); bus.drain(); bus.sync()
        assertEquals(2, c.myCart().lines.single().quantity)
        c.dispatch(GroupAction.DECREASE_CART_QUANTITY, line.id); bus.drain()
        c.dispatch(GroupAction.DECREASE_CART_QUANTITY, line.id); bus.drain(); bus.sync()
        assertTrue(c.myCart().lines.isEmpty())
        c.dispatch(GroupAction.SET_LANGUAGE, "ar")
        assertEquals("الكمية والملاحظات", c.state.cards.single { it.id == "menu:burger" }.buttons.last().title)
    }

    @Test fun configuredItemsCannotBypassSizeOrExtrasWithQuickAdd(): Unit = Bus().use { bus ->
        val (c, _) = bus.phone(); create(c, bus)
        val room = c.room()
        val restaurant = room.restaurant.copy(menu = room.restaurant.menu.copy(items = room.restaurant.menu.items.map {
            if(it.id == "burger") it.copy(variants = listOf(MenuVariant("large", "Large", 4000))) else it
        }))
        bus.db.save(room.copy(restaurant = restaurant)); bus.sync()
        val actions = c.state.cards.single { it.id == "menu:burger" }.buttons
        assertEquals(listOf(GroupAction.OPEN_ITEM), actions.map { it.action })
        c.dispatch(GroupAction.QUICK_ADD_ITEM, "burger"); bus.drain()
        assertTrue(c.state.error.contains("size and extras"))
        assertTrue(c.myCart().lines.isEmpty())
        c.dispatch(GroupAction.OPEN_ITEM, "burger")
        c.update(GroupFieldKey.QUANTITY, "3")
        c.dispatch(GroupAction.ADD_CART_ITEM); bus.drain(); bus.sync()
        assertEquals(3, c.myCart().lines.single().quantity)
        assertEquals("large", c.myCart().lines.single().variantId)
    }

    @Test fun directSelectionUsesTheChosenMemberWithoutWheel(): Unit = Bus().use { bus ->
        val (host, _) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Known orderer", host, bus)
        host.update(GroupFieldKey.PAYER_MODE, "direct")
        host.update(GroupFieldKey.PAYER_CHOICE, member.me())
        assertEquals(GroupAction.SELECT_PAYER, host.state.primaryAction?.action)
        host.dispatch(GroupAction.SELECT_PAYER); bus.drain(); bus.sync()
        assertEquals(RoomPhase.COLLECTING, member.room().phase)
        assertEquals(member.me(), host.room().payerId)
        assertNull(host.room().spin)
        assertEquals(GroupAction.OPEN_ACCOUNT, member.state.primaryAction?.action)
    }

    @Test fun walletPaymentAndConfirmationStayOnTheDashboard(): Unit = Bus().use { bus ->
        val (host, _) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Karam", host, bus); placeAndPay(host, member, bus)
        listOf(host, member).forEach { it.library = it.library.copy(home = HomePayload(FoodProfile(name = "Member"))); it.page = GroupPage.HOME }
        val target = "${host.room().id}|${member.me()}"
        member.dispatch(GroupAction.WALLET_PAY, target); bus.drain(); bus.sync()
        assertEquals(GroupPage.HOME, member.page)
        assertEquals(4000, host.room().transfers.single().amount)
        assertTrue(member.state.cards.single { it.id.startsWith("dashboard:wallet:") }.buttons.none { it.action == GroupAction.WALLET_PAY })
        host.dispatch(GroupAction.WALLET_CONFIRM, target); bus.drain(); bus.sync()
        assertEquals(GroupPage.HOME, host.page)
        assertEquals(0, member.reply!!.receipts.single().balance)
        assertEquals(TransferStatus.CONFIRMED, host.room().transfers.single().status)
    }

    @Test fun signedOutHomeOffersGoogleAndNoGuestRoomActions(): Unit = Bus().use { bus ->
        val (phone, _) = bus.phone()
        assertTrue(phone.state.buttons.any { it.action == GroupAction.GOOGLE_SIGN_IN })
        assertFalse(phone.state.buttons.any { it.action in listOf(GroupAction.CREATE, GroupAction.JOIN, GroupAction.QUICK_SPIN) })
    }

    @Test fun walletExcludesCancelledFoodAndSearchSupportsArabic(): Unit = Bus().use { bus ->
        val (c, _) = bus.phone(); create(c, bus)
        val receipt = Receipt(c.me(), "Karim", emptyList(), 1000, 0, 0, 0, 0, 1000, 0, 1000, 1, "AED")
        val snapshot = c.reply!!.copy(room = c.room().copy(phase = RoomPhase.CANCELLED, payerId = "other"), receipts = listOf(receipt))
        c.library = c.library.copy(home = HomePayload(FoodProfile(name = "Test member")), snapshots = c.library.snapshots + (c.room().id to snapshot))
        c.page = GroupPage.HOME
        assertTrue(c.state.cards.single { it.id == "dashboard:wallet-summary" }.detail.contains("pay AED 0.00"))
        c.page = GroupPage.ROOM
        val restaurant = c.room().restaurant.copy(menu = c.room().restaurant.menu.copy(items = c.room().restaurant.menu.items.map { if(it.id == "burger") it.copy(nameAr = "برجر") else it }))
        c.reply = c.reply!!.copy(room = c.room().copy(restaurant = restaurant))
        c.update(GroupFieldKey.MENU_SEARCH, "برجر")
        assertEquals(listOf("menu:burger"), c.state.cards.filter { it.id.startsWith("menu:") }.map { it.id })
    }

    @Test fun historicalReceiptExportsKeepTheirOwnRestaurantAndRecipient(): Unit = Bus().use { bus ->
        val (c, phone) = bus.phone(); create(c, bus)
        val account = ReceivingAccount("old-account", "Previous payer", "Old Bank", "987654321")
        val receipt = Receipt(c.me(), "Karim", emptyList(), 1000, 0, 0, 0, 0, 1000, 1000, 0, 7, "AED")
        c.reply = c.reply!!.copy(room = c.room().copy(payerId = c.me(), orderNumber = 2), history = listOf(PastOrder(1, "Old restaurant", bus.time - 86400000, listOf(receipt), account)))
        c.dispatch(GroupAction.OPEN_HISTORY)
        val old = c.state.cards.single { it.id == "receipt:past:1:${c.me()}" }
        assertFalse(old.detail.contains("own contribution"))
        c.dispatch(GroupAction.SHARE_RECEIPT, "past:1:${c.me()}")
        assertTrue(phone.shared.contains("order #1")); assertTrue(phone.shared.contains("Old restaurant"))
        assertTrue(phone.shared.contains("Previous payer")); assertTrue(phone.shared.contains("987654321"))
    }
    @Test fun repeatedPreviousOrdersCollapseAndChangedQuantityRemainsReusable(): Unit = Bus().use { bus ->
        val (c, _) = bus.phone(); create(c, bus)
        fun receipt(quantity: Int, amount: Long) = Receipt(
            c.me(), "Karim", listOf(ReceiptLine("Burger", quantity, amount, itemId = "burger")),
            amount, 0, 0, 0, 0, amount, 0, amount, 1, "AED",
        )
        val history = listOf(
            PastOrder(1, "Test Kitchen", bus.time - 3_000, listOf(receipt(1, 3_500)), restaurantId = "kitchen"),
            PastOrder(2, "Test Kitchen", bus.time - 2_000, listOf(receipt(1, 3_700)), restaurantId = "kitchen"),
            PastOrder(3, "Test Kitchen", bus.time - 1_000, listOf(receipt(2, 7_000)), restaurantId = "kitchen"),
        )
        c.reply = c.reply!!.copy(history = history)
        c.library = c.library.copy(snapshots = c.library.snapshots + (c.room().id to c.reply!!))

        val choices = c.previousOrderChoices(c.room().restaurant)
        assertEquals(2, choices.size)
        assertEquals(2, choices.single { it.receipt.lines.single().quantity == 1 }.repeatCount)
        assertEquals(1, choices.single { it.receipt.lines.single().quantity == 2 }.repeatCount)

        c.dispatch(GroupAction.REUSE_ORDER, choices.single { it.receipt.lines.single().quantity == 2 }.value)
        bus.drain(); bus.sync()
        assertEquals(2, c.myCart().lines.single().quantity)
        assertEquals("burger", c.myCart().lines.single().itemId)
    }
    @Test fun pairingSameHubAtNewAddressUpdatesSavedSessionsWithoutRejoining(): Unit = Bus().use { bus ->
        val (c, phone) = bus.phone(); create(c, bus)
        val token = c.session!!.token; val id = c.room().id
        c.dispatch(GroupAction.CREATE); c.update(GroupFieldKey.HUB_URL, "https://192.168.1.99:8443"); c.dispatch(GroupAction.CONNECT)
        assertEquals("https://192.168.1.99:8443", c.library.sessions.single().hub.url)
        c.dispatch(GroupAction.RESUME, id); bus.sync()
        assertEquals(token, c.session!!.token)
        c.dispatch(GroupAction.READY); bus.drain()
        assertEquals("https://192.168.1.99:8443", phone.requestedHubs.last().url)
    }
    @Test fun internetRoomActionSelectsTheDeployedPublicApiWithoutCertificatePinning(): Unit = Bus().use { bus ->
        val (c, _) = bus.phone()
        c.dispatch(GroupAction.CREATE)
        assertTrue(c.state.buttons.any { it.action == GroupAction.USE_INTERNET && it.primary })
        c.dispatch(GroupAction.USE_INTERNET)
        assertEquals(GroupPage.SETUP, c.state.page)
        assertEquals(FOOD_RUN_INTERNET_API, c.library.selectedHub?.url)
        assertEquals("", c.library.selectedHub?.fingerprint)
    }
    @Test fun partialCurrencyInputNeverCrashesRestaurantRenderingAndContactEditKeepsWhatsApp(): Unit = Bus().use { bus ->
        val (c, _) = bus.phone(); c.dispatch(GroupAction.OPEN_LIBRARY); c.dispatch(GroupAction.IMPORT_MENU); c.dispatch(GroupAction.CONFIRM_IMPORT)
        val export = c.library.restaurants.single { it.restaurant.id == "kitchen" }
        c.saveRestaurant(export.copy(restaurant = export.restaurant.copy(contact = RestaurantContact(whatsappE164 = "+971500000000"))))
        c.dispatch(GroupAction.EDIT_RESTAURANT, "kitchen")
        for (currency in listOf("", "A", "AE", "aed", "INVALID")) { c.update(GroupFieldKey.CURRENCY, currency); assertEquals(GroupPage.RESTAURANT, c.state.page) }
        c.update(GroupFieldKey.CURRENCY, "AED"); c.dispatch(GroupAction.SAVE_RESTAURANT)
        assertEquals("+971500000000", c.library.restaurants.single { it.restaurant.id == "kitchen" }.restaurant.contact.whatsappE164)
    }

    @Test fun menuCategoriesSeparateSandwichesFromPartyOrders() = Bus().use { bus ->
        val (c, _) = bus.phone(); create(c, bus)
        val restaurant = BuiltInRestaurants.all.single { it.restaurant.id == "builtin-bait-al-waleema" }.restaurant
        bus.db.save(c.room().copy(restaurant = restaurant)); bus.sync()
        fun items() = c.state.cards.filter { it.id.startsWith("menu:") }
        val category = c.state.fields.single { it.key == GroupFieldKey.MENU_CATEGORY }
        assertEquals("Sandwiches", category.choices.first().label)
        assertEquals(17, items().size)
        val parties = category.choices.single { it.label == "Parties" }
        c.update(GroupFieldKey.MENU_CATEGORY, parties.value)
        assertEquals(5, items().size)
        assertTrue(items().first().badge.contains("225.00"))
        c.update(GroupFieldKey.MENU_SEARCH, "حواوشي")
        assertEquals(0, items().size)
        c.update(GroupFieldKey.MENU_CATEGORY, category.value)
        assertEquals(1, items().size)
        c.update(GroupFieldKey.MENU_SEARCH, "")
        c.update(GroupFieldKey.MENU_CATEGORY, "all")
        assertEquals(126, items().size)
    }

    @Test fun deliveryRoomCanBeCreatedWithoutTypingAnAddress() = Bus().use { bus ->
        val (c, _) = bus.phone()
        connect(c); c.update(GroupFieldKey.NAME, "Karim"); c.update(GroupFieldKey.ROOM_NAME, "Delivery lunch")
        c.dispatch(GroupAction.OPEN_LIBRARY); c.dispatch(GroupAction.IMPORT_MENU); c.dispatch(GroupAction.CONFIRM_IMPORT); c.dispatch(GroupAction.SELECT_RESTAURANT, "kitchen")
        c.update(GroupFieldKey.DELIVERY, "true"); c.dispatch(GroupAction.CREATE_ROOM); bus.drain(); bus.sync()
        assertEquals("", c.state.error)
        assertTrue(c.room().deliveryMode)
        assertTrue(c.room().fees.automaticDelivery)
        assertEquals("The selected orderer will arrange delivery with the restaurant.", c.room().destination)
    }

}
