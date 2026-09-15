package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import com.karim.foodrun.server.RoomDatabase
import com.karim.foodrun.server.RoomService
import java.nio.file.Files
import java.util.UUID
import kotlin.test.*

class GroupStateConsistencyTest {
    private class Device : GroupPlatform, AutoCloseable {
        val directory = Files.createTempDirectory("foodrun-state-test").toFile()
        val database = RoomDatabase(directory)
        val server = RoomService(database, { 1_800_000_000_000L })
        val saved = mutableMapOf<String, String>()
        val queue = ArrayDeque<() -> Unit>()
        var storageAvailable = true
        var watcher: Pair<RoomCommand, GroupReplyCallback>? = null
        val controller = GroupController(this)

        override fun read(key: String) = saved[key].orEmpty()
        override fun write(key: String, value: String): Boolean {
            if (!storageAvailable) return false
            saved[key] = value
            return true
        }
        override fun now() = 1_800_000_000_000L
        override fun uuid() = UUID.randomUUID().toString()
        override fun request(hub: HubPairing, body: String, callback: GroupReplyCallback) {
            val response = server.execute(orderJson.decodeFromString<RoomCommand>(body))
            queue += { callback.complete(orderJson.encodeToString(response), "") }
        }
        override fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback): GroupSubscription {
            val subscription = orderJson.decodeFromString<RoomCommand>(body) to callback
            watcher = subscription
            return object : GroupSubscription {
                override fun cancel() { if (watcher === subscription) watcher = null }
            }
        }
        override fun share(text: String, fileName: String) = Unit
        override fun openLink(url: String) = Unit
        override fun importMenu(callback: GroupReplyCallback) = Unit
        override fun scanPairing(callback: GroupReplyCallback) = Unit
        override fun discover(callback: GroupReplyCallback) = Unit
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst()() }
        fun sync() { watcher?.let { (request, callback) -> callback.complete(orderJson.encodeToString(server.execute(request)), "") }; drain() }
        override fun close() { controller.close(); database.close(); directory.deleteRecursively() }
    }

    private fun restaurant(id: String = "kitchen", name: String = "Kitchen") = RestaurantExport(
        exportId = id,
        restaurant = Restaurant(id, name, contact = RestaurantContact("+971500000000"), menu = Menu(
            categories = listOf(MenuCategory("main", "Main")),
            items = listOf(MenuItem("burger", "main", "Burger", basePriceMinor = 3500)),
        )),
    )

    private fun import(c: GroupController, export: RestaurantExport) {
        c.update(GroupFieldKey.JSON_MENU, orderJson.encodeToString(export))
        c.dispatch(GroupAction.PREVIEW_IMPORT)
        c.dispatch(GroupAction.CONFIRM_IMPORT)
    }

    private fun setup(c: GroupController) {
        c.dispatch(GroupAction.CREATE)
        c.update(GroupFieldKey.HUB_URL, "https://localhost:8443")
        c.update(GroupFieldKey.FINGERPRINT, "a".repeat(64))
        c.dispatch(GroupAction.CONNECT)
        c.update(GroupFieldKey.NAME, "Karim")
        c.update(GroupFieldKey.ROOM_NAME, "Lunch")
        c.dispatch(GroupAction.OPEN_LIBRARY)
        import(c, restaurant())
        import(c, restaurant("other", "Other Kitchen"))
        c.dispatch(GroupAction.SELECT_RESTAURANT, "kitchen")
        assertEquals(GroupPage.SETUP, c.state.page)
    }

    @Test fun browsingAnotherRestaurantDoesNotChangeTheOrderFeeDraft() = Device().use { device ->
        val c = device.controller
        setup(c)
        c.update(GroupFieldKey.DELIVERY_FEE, "17.50")
        c.update(GroupFieldKey.SERVICE_FEE, "2.25")
        c.update(GroupFieldKey.DISCOUNT, "1.00")
        c.update(GroupFieldKey.PROPORTIONAL, "true")
        c.dispatch(GroupAction.OPEN_LIBRARY)
        c.dispatch(GroupAction.EDIT_RESTAURANT, "other")
        c.dispatch(GroupAction.BACK)
        c.dispatch(GroupAction.BACK)
        assertEquals(GroupPage.SETUP, c.state.page)
        assertEquals("17.50", c.text(GroupFieldKey.DELIVERY_FEE))
        assertEquals("2.25", c.text(GroupFieldKey.SERVICE_FEE))
        assertEquals("1.00", c.text(GroupFieldKey.DISCOUNT))
        assertEquals("true", c.text(GroupFieldKey.PROPORTIONAL))
        c.dispatch(GroupAction.CREATE_ROOM)
        device.drain()
        assertEquals(FeePolicy(1750, 225, 100, true), c.room().fees)
    }

    @Test fun savingTheSelectedRestaurantUpdatesTheMenuUsedForRoomCreation() = Device().use { device ->
        val c = device.controller
        setup(c)
        c.dispatch(GroupAction.OPEN_LIBRARY)
        c.dispatch(GroupAction.EDIT_RESTAURANT, "kitchen")
        c.update(GroupFieldKey.RESTAURANT_NAME, "Updated Kitchen")
        c.update(GroupFieldKey.MENU_ITEM_NAME, "Water")
        c.update(GroupFieldKey.MENU_ITEM_PRICE, "3")
        c.dispatch(GroupAction.ADD_MENU_ITEM)
        c.dispatch(GroupAction.SAVE_RESTAURANT)
        assertEquals("", c.state.error)
        c.dispatch(GroupAction.BACK)
        assertEquals(GroupPage.SETUP, c.state.page)
        assertEquals("Updated Kitchen", c.state.cards.single { it.id == "restaurant" }.title)
        c.dispatch(GroupAction.CREATE_ROOM)
        device.drain()
        assertEquals("Updated Kitchen", c.room().restaurant.name)
        assertEquals(listOf("Burger", "Water"), c.room().restaurant.menu.items.map { it.name })
    }

    @Test fun importingAnUpdatedSelectedMenuUpdatesTheCreationSelection() = Device().use { device ->
        val c = device.controller
        setup(c)
        c.dispatch(GroupAction.OPEN_LIBRARY)
        import(c, restaurant(name = "Latest Kitchen").copy(revision = 2))
        c.dispatch(GroupAction.BACK)
        c.dispatch(GroupAction.CREATE_ROOM)
        device.drain()
        assertEquals("Latest Kitchen", c.room().restaurant.name)
    }

    @Test fun aFailedImportDoesNotAppearSavedOrReplaceTheExistingMenu() = Device().use { device ->
        val c = device.controller
        setup(c)
        val before = c.library.restaurants
        c.dispatch(GroupAction.OPEN_LIBRARY)
        device.storageAvailable = false
        import(c, restaurant(name = "Unsaved Kitchen").copy(revision = 2))
        assertTrue(c.state.error.contains("Could not save"))
        assertEquals(before, c.library.restaurants)
        assertNotNull(c.importPreview, "The import remains available to retry after storage recovers.")
        device.storageAvailable = true
        c.dispatch(GroupAction.CONFIRM_IMPORT)
        assertEquals("", c.state.error)
        assertEquals("Unsaved Kitchen", c.library.restaurants.single { it.restaurant.id == "kitchen" }.restaurant.name)
    }

    @Test fun deletingTheSelectedSavedRestaurantRequiresAReplacementBeforeCreating() = Device().use { device ->
        val c = device.controller
        setup(c)
        c.dispatch(GroupAction.OPEN_LIBRARY)
        c.dispatch(GroupAction.DELETE_RESTAURANT, "kitchen")
        c.dispatch(GroupAction.BACK)
        assertNull(c.selectedRestaurant)
        c.dispatch(GroupAction.CREATE_ROOM)
        device.drain()
        assertTrue(c.state.error.contains("Choose or create a restaurant"))
        assertTrue(device.database.allRooms().isEmpty())
    }

    @Test fun aFailedDeleteKeepsTheSavedRestaurantAndItsSelection() = Device().use { device ->
        val c = device.controller
        setup(c)
        val before = c.library.restaurants
        val selected = c.selectedRestaurant
        c.dispatch(GroupAction.OPEN_LIBRARY)
        device.storageAvailable = false
        c.dispatch(GroupAction.DELETE_RESTAURANT, "kitchen")
        assertTrue(c.state.error.contains("Could not save"))
        assertEquals(before, c.library.restaurants)
        assertEquals(selected, c.selectedRestaurant)
    }

    @Test fun aFailedAccountSaveKeepsThePreviouslySavedAccount() = Device().use { device ->
        val c = device.controller
        c.dispatch(GroupAction.OPEN_ACCOUNT)
        c.update(GroupFieldKey.ACCOUNT_HOLDER, "Karim")
        c.update(GroupFieldKey.ACCOUNT_BANK, "Example Bank")
        c.update(GroupFieldKey.ACCOUNT_IDENTIFIER, "123456789012")
        c.dispatch(GroupAction.SAVE_ACCOUNT)
        val before = c.library.accounts.single()
        c.update(GroupFieldKey.ACCOUNT_IDENTIFIER, "987654321012")
        device.storageAvailable = false
        c.dispatch(GroupAction.SAVE_ACCOUNT)
        assertTrue(c.state.error.contains("Could not save"))
        assertEquals(listOf(before), c.library.accounts)
        assertEquals(before, c.selectedAccount)
        assertEquals("987654321012", c.text(GroupFieldKey.ACCOUNT_IDENTIFIER), "Keep the rejected edit available to retry.")
    }

    @Test fun reopeningTheAccountEditorDoesNotDuplicateThePrefilledSavedAccount() = Device().use { device ->
        val c = device.controller
        c.dispatch(GroupAction.OPEN_ACCOUNT)
        c.update(GroupFieldKey.ACCOUNT_HOLDER, "Karim")
        c.update(GroupFieldKey.ACCOUNT_BANK, "Example Bank")
        c.update(GroupFieldKey.ACCOUNT_IDENTIFIER, "123456789012")
        c.dispatch(GroupAction.SAVE_ACCOUNT)
        val savedAccount = c.library.accounts.single()
        c.dispatch(GroupAction.BACK)
        c.dispatch(GroupAction.OPEN_ACCOUNT)
        assertEquals(savedAccount, c.selectedAccount)
        assertEquals(savedAccount.identifier, c.text(GroupFieldKey.ACCOUNT_IDENTIFIER))
        c.dispatch(GroupAction.SAVE_ACCOUNT)
        assertEquals("", c.state.error)
        assertEquals(listOf(savedAccount), c.library.accounts)
    }

    @Test fun addingAnotherAccountExplicitlyStartsAnEmptyFormAndKeepsTheSavedAccount() = Device().use { device ->
        val c = device.controller
        c.dispatch(GroupAction.OPEN_ACCOUNT)
        c.update(GroupFieldKey.ACCOUNT_HOLDER, "Karim")
        c.update(GroupFieldKey.ACCOUNT_BANK, "Example Bank")
        c.update(GroupFieldKey.ACCOUNT_IDENTIFIER, "123456789012")
        c.dispatch(GroupAction.SAVE_ACCOUNT)
        val firstAccount = c.library.accounts.single()
        c.dispatch(GroupAction.NEW_ACCOUNT)
        assertEquals(GroupPage.ACCOUNT, c.state.page)
        assertNull(c.selectedAccount)
        for (key in listOf(GroupFieldKey.ACCOUNT_HOLDER, GroupFieldKey.ACCOUNT_BANK, GroupFieldKey.ACCOUNT_IDENTIFIER)) assertEquals("", c.text(key))
        assertEquals(listOf(firstAccount), c.library.accounts)
        c.update(GroupFieldKey.ACCOUNT_HOLDER, "Karim")
        c.update(GroupFieldKey.ACCOUNT_BANK, "Other Bank")
        c.update(GroupFieldKey.ACCOUNT_IDENTIFIER, "987654321012")
        c.dispatch(GroupAction.SAVE_ACCOUNT)
        assertEquals("", c.state.error)
        assertEquals(2, c.library.accounts.size)
        assertEquals(firstAccount, c.library.accounts.single { it.id == firstAccount.id })
    }

    @Test fun nextOrderAfterRestartUsesTheSavedDeliveryDetailsAndExpectedPeople() = Device().use { device ->
        val original = device.controller
        setup(original)
        original.update(GroupFieldKey.DELIVERY, "true")
        original.update(GroupFieldKey.DESTINATION, "Office reception, call Karim")
        original.update(GroupFieldKey.EXPECTED_NAMES, "Hassan, Fakhr")
        original.update(GroupFieldKey.PROPORTIONAL, "true")
        original.dispatch(GroupAction.CREATE_ROOM)
        device.drain()
        original.update(GroupFieldKey.REASON, "Start tomorrow")
        original.dispatch(GroupAction.CANCEL)
        device.drain()
        assertEquals(RoomPhase.CANCELLED, original.room().phase)
        val roomId = original.room().id
        original.close()

        val restored = GroupController(device)
        restored.dispatch(GroupAction.RESUME, roomId)
        restored.dispatch(GroupAction.NEXT_ORDER)
        assertEquals(GroupPage.SETUP, restored.state.page)
        assertEquals("true", restored.text(GroupFieldKey.DELIVERY))
        assertEquals("Office reception, call Karim", restored.text(GroupFieldKey.DESTINATION))
        assertEquals("Hassan, Fakhr", restored.text(GroupFieldKey.EXPECTED_NAMES))
        assertEquals("true", restored.text(GroupFieldKey.PROPORTIONAL))
        restored.dispatch(GroupAction.CREATE_ROOM)
        device.drain()
        assertTrue(restored.room().deliveryMode)
        assertEquals(listOf("Hassan", "Fakhr"), restored.room().expectedNames)
        assertEquals("Office reception, call Karim", restored.room().destination)
        restored.close()
    }

    @Test fun aRoomFeeUpdateDoesNotOverwriteTheRestaurantDefaultsBeingEdited() = Device().use { device ->
        val c = device.controller
        setup(c)
        c.update(GroupFieldKey.DELIVERY_FEE, "10.00")
        c.dispatch(GroupAction.CREATE_ROOM)
        device.drain()
        val roomId = c.room().id
        c.dispatch(GroupAction.BACK)
        c.dispatch(GroupAction.OPEN_LIBRARY)
        c.dispatch(GroupAction.EDIT_RESTAURANT, "other")
        c.update(GroupFieldKey.DELIVERY_FEE, "4.50")
        val remote = device.database.room(roomId)!!
        device.database.save(remote.copy(fees = remote.fees.copy(delivery = 1500), revision = remote.revision + 1))
        device.sync()
        assertEquals(GroupPage.RESTAURANT, c.state.page)
        assertEquals("4.50", c.text(GroupFieldKey.DELIVERY_FEE))
        c.dispatch(GroupAction.SAVE_RESTAURANT)
        assertEquals(450L, c.library.restaurants.single { it.restaurant.id == "other" }.restaurant.pricing.defaultDeliveryFeeMinor)
        c.dispatch(GroupAction.BACK)
        c.dispatch(GroupAction.RESUME, roomId)
        assertEquals("15.00", c.text(GroupFieldKey.DELIVERY_FEE))
    }
}
