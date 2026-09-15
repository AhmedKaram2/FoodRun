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
            host.dispatch(GroupAction.APPROVE, c.me()); bus.drain(); bus.sync(); assertEquals("", host.state.error)
        }
        private fun review(host: GroupController, member: GroupController, bus: Bus) {
            host.update(GroupFieldKey.ELIGIBLE, "true"); host.dispatch(GroupAction.READY); bus.drain()
            member.dispatch(GroupAction.READY); bus.drain(); bus.sync()
            host.dispatch(GroupAction.PREPARE_SPIN); bus.drain(); repeat(4) { bus.sync() }
            bus.time += 10000; bus.server.tick(); bus.sync()
            host.dispatch(GroupAction.ACCEPT_DUTY); bus.drain(); bus.sync()
            host.dispatch(GroupAction.OPEN_ACCOUNT); host.update(GroupFieldKey.ACCOUNT_HOLDER, "Karim")
            host.update(GroupFieldKey.ACCOUNT_BANK, "Example Bank"); host.update(GroupFieldKey.ACCOUNT_IDENTIFIER, "123456789012")
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
    @Test fun threeClientsCompleteMealPersistReceiptAndReuseRoomDaysLater() = Bus().use { bus ->
        val (host, _) = bus.phone(); val (second, secondPhone) = bus.phone(); val (third, _) = bus.phone()
        create(host, bus); join(second, "Karam", host, bus); join(third, "Hassan", host, bus)
        val people = listOf(host, second, third)
        people.forEach { it.update(GroupFieldKey.ELIGIBLE, "true"); it.dispatch(GroupAction.READY); bus.drain(); bus.sync() }
        host.dispatch(GroupAction.PREPARE_SPIN); bus.drain(); repeat(4) { bus.sync() }
        assertEquals(RoomPhase.SPINNING, host.room().phase)
        assertEquals(1, people.map { it.room().spin!!.id }.distinct().size)
        bus.time += 10000; bus.server.tick(); bus.sync()
        val payer = people.single { it.me() == host.room().spin!!.winnerId }
        payer.dispatch(GroupAction.ACCEPT_DUTY); bus.drain(); bus.sync()
        payer.dispatch(GroupAction.OPEN_ACCOUNT); payer.update(GroupFieldKey.ACCOUNT_HOLDER, payer.room().members.single { it.id == payer.me() }.name)
        payer.update(GroupFieldKey.ACCOUNT_BANK, "Example Bank"); payer.update(GroupFieldKey.ACCOUNT_IDENTIFIER, "123456789012")
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
        c.dispatch(GroupAction.IMPORT_MENU); assertTrue(c.library.restaurants.isEmpty())
        c.dispatch(GroupAction.CONFIRM_IMPORT); assertEquals(1, c.library.restaurants.size)
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
        c.update(GroupFieldKey.ELIGIBLE, "true"); c.dispatch(GroupAction.READY)
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
        c.update(GroupFieldKey.ELIGIBLE, "true"); c.dispatch(GroupAction.READY); bus.drain()
        val pending = requireNotNull(c.library.pending)
        c.dispatch(GroupAction.RETRY); bus.drain(); bus.sync()
        assertNull(c.library.pending)
        assertEquals(1, bus.db.room(c.room().id)!!.audit.count { it.id == pending.commandId })
    }
    @Test fun rejectedSpinAcknowledgementRetriesAfterOtherMemberReconnects(): Unit = Bus().use { bus ->
        val (host, _) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Karam", host, bus)
        host.update(GroupFieldKey.ELIGIBLE, "true"); host.dispatch(GroupAction.READY); bus.drain()
        member.dispatch(GroupAction.READY); bus.drain(); bus.sync()
        member.background()
        host.dispatch(GroupAction.PREPARE_SPIN); bus.drain(); bus.sync()
        assertEquals(listOf(host.me()), host.room().preparedIds)
        host.background(); bus.time += 20000
        member.foreground(); bus.sync()
        assertEquals(RoomPhase.PREPARING_SPIN, member.room().phase)
        assertNull(member.library.pending)
        host.foreground(); repeat(4) { bus.sync() }
        assertEquals(RoomPhase.SPINNING, host.room().phase)
        assertEquals(host.room().spin, member.room().spin)
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
    @Test fun reviewShowsAmountAndRecipientBeforeItsConfirmationAction(): Unit = Bus().use { bus ->
        val (host, _) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Karam", host, bus); review(host, member, bus)
        val receipt = member.state.cards.single { card -> card.buttons.any { it.action == GroupAction.CONFIRM_QUOTE } }
        assertTrue(receipt.detail.contains("AED 40.00")); assertTrue(receipt.detail.contains("123456789012")); assertTrue(receipt.detail.contains("Karim"))
        assertTrue(member.state.buttons.none { it.action == GroupAction.CONFIRM_QUOTE })
        val total = host.state.cards.single { card -> card.buttons.any { it.action == GroupAction.PLACE } }
        assertTrue(total.detail.contains("AED 80.00"))
        assertTrue(host.state.buttons.none { it.action == GroupAction.PLACE })
    }
    @Test fun aRefundRecipientCanRejectAFalseClaimFromTheReceiptsScreen(): Unit = Bus().use { bus ->
        val (host, _) = bus.phone(); val (member, _) = bus.phone()
        create(host, bus); join(member, "Karam", host, bus); placeAndPay(host, member, bus)
        member.update(GroupFieldKey.AMOUNT, "40"); member.update(GroupFieldKey.REFERENCE, "Bank transfer"); member.dispatch(GroupAction.DECLARE_TRANSFER); bus.drain(); bus.sync()
        host.dispatch(GroupAction.CONFIRM_TRANSFER, host.room().transfers.last().id); bus.drain(); bus.sync()
        host.update(GroupFieldKey.REASON, "Restaurant discount"); host.update(GroupFieldKey.AMOUNT, "-10"); host.dispatch(GroupAction.ADJUST_BILL); bus.drain(); bus.sync()
        member.dispatch(GroupAction.APPROVE_ADJUSTMENT); bus.drain(); bus.sync()
        host.update(GroupFieldKey.AMOUNT, "70"); host.dispatch(GroupAction.PAY_RESTAURANT); bus.drain(); bus.sync()
        host.dispatch(GroupAction.OPEN_RECEIPTS)
        assertTrue(host.state.fields.any { it.key == GroupFieldKey.AMOUNT }); assertTrue(host.state.fields.any { it.key == GroupFieldKey.REFERENCE })
        host.update(GroupFieldKey.AMOUNT, "5"); host.update(GroupFieldKey.REFERENCE, "Refund claim"); host.dispatch(GroupAction.DECLARE_REFUND, member.me()); bus.drain(); bus.sync()
        member.dispatch(GroupAction.OPEN_RECEIPTS)
        val refund = member.state.cards.single { it.buttons.any { button -> button.title == "Reject refund claim" } }
        assertTrue(member.state.fields.any { it.key == GroupFieldKey.REASON })
        member.update(GroupFieldKey.REASON, "Not received"); member.dispatch(GroupAction.REJECT_TRANSFER, refund.buttons.last().value); bus.drain(); bus.sync()
        assertEquals(TransferStatus.REJECTED, host.room().transfers.last().status)
        assertEquals(-500, member.reply!!.receipts.single().balance)
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
    @Test fun partialCurrencyInputNeverCrashesRestaurantRenderingAndContactEditKeepsWhatsApp(): Unit = Bus().use { bus ->
        val (c, _) = bus.phone(); c.dispatch(GroupAction.OPEN_LIBRARY); c.dispatch(GroupAction.IMPORT_MENU); c.dispatch(GroupAction.CONFIRM_IMPORT)
        val export = c.library.restaurants.single()
        c.saveRestaurant(export.copy(restaurant = export.restaurant.copy(contact = RestaurantContact(whatsappE164 = "+971500000000"))))
        c.dispatch(GroupAction.EDIT_RESTAURANT, "kitchen")
        for (currency in listOf("", "A", "AE", "aed", "INVALID")) { c.update(GroupFieldKey.CURRENCY, currency); assertEquals(GroupPage.RESTAURANT, c.state.page) }
        c.update(GroupFieldKey.CURRENCY, "AED"); c.dispatch(GroupAction.SAVE_RESTAURANT)
        assertEquals("+971500000000", c.library.restaurants.single().restaurant.contact.whatsappE164)
    }

}
