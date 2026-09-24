package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import java.nio.file.Files
import kotlin.test.*

class CatalogMigrationsTest {
    @Test fun savedSultanMenuGetsOnlyTheThreeRequestedPricesOnce() {
        val directory = Files.createTempDirectory("foodrun-menu-migration").toFile()
        try {
            RoomDatabase(directory).use { db ->
                val prices = mapOf("builtin-sultan-item-1-0" to 300L, "builtin-sultan-item-1-9" to 300L, "builtin-sultan-item-1-11" to 400L)
                val original = BuiltInRestaurants.all.map { it.restaurant }.map { restaurant ->
                    if (restaurant.id != "builtin-sultan") restaurant else restaurant.copy(notes = "Keep saved menu details", menu = restaurant.menu.copy(
                        items = restaurant.menu.items.map { item -> if (item.id in prices) item.copy(basePriceMinor = prices.getValue(item.id) + 100) else item },
                    ))
                }
                db.putRecord(AdminService.RESTAURANTS, orderJson.encodeToString(original))
                val admin = AdminService(db, RoomService(db))
                val updated = admin.catalog()
                original.zip(updated).forEach { (before, after) ->
                    if (before.id != "builtin-sultan") assertEquals(before, after)
                    else {
                        assertEquals(before.copy(menu = after.menu), after)
                        before.menu.items.zip(after.menu.items).forEach { (old, item) ->
                            assertEquals(prices[old.id]?.let { old.copy(basePriceMinor = it) } ?: old, item)
                        }
                    }
                }
                val sultan = updated.single { it.id == "builtin-sultan" }
                val edited = sultan.copy(menu = sultan.menu.copy(items = sultan.menu.items.map { if (it.id in prices) it.copy(basePriceMinor = 600) else it }))
                admin.mutateRestaurant(AdminRestaurantMutation(restaurant = edited))
                assertEquals(edited, AdminService(db, RoomService(db)).catalog().single { it.id == sultan.id })
            }
        } finally { directory.deleteRecursively() }
    }

    @Test fun deletedRestaurantIsNotRestored() {
        val directory = Files.createTempDirectory("foodrun-menu-deleted").toFile()
        try {
            RoomDatabase(directory).use { db ->
                db.putRecord(AdminService.RESTAURANTS, "[]")
                db.putRecord(AdminService.DELETED_RESTAURANTS, "[\"builtin-sultan\"]")
                val payload = AdminService(db, RoomService(db)).catalogPayload()
                assertTrue(payload.restaurants.isEmpty())
                assertEquals(setOf("builtin-sultan"), payload.deletedRestaurantIds)
            }
        } finally { directory.deleteRecursively() }
    }
}
