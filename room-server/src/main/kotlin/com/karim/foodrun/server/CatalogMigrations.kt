package com.karim.foodrun.server

import com.karim.foodrun.orders.Restaurant
import com.karim.foodrun.orders.orderJson

internal object CatalogMigrations {
    private const val SULTAN_PRICES = "migration:sultan-prices-2026-09-24"

    /** Apply the requested prices once to saved menus, preserving subsequent admin edits. */
    fun apply(db: RoomDatabase) {
        if (db.record(SULTAN_PRICES) != null) return
        db.record(AdminService.RESTAURANTS)?.let { saved ->
            val prices = mapOf("builtin-sultan-item-1-0" to 300L, "builtin-sultan-item-1-9" to 300L, "builtin-sultan-item-1-11" to 400L)
            val restaurants = orderJson.decodeFromString<List<Restaurant>>(saved).map { restaurant ->
                if (restaurant.id != "builtin-sultan") restaurant else restaurant.copy(menu = restaurant.menu.copy(
                    items = restaurant.menu.items.map { item -> prices[item.id]?.let { item.copy(basePriceMinor = it) } ?: item },
                ))
            }
            db.putRecord(AdminService.RESTAURANTS, orderJson.encodeToString(restaurants))
        }
        db.putRecord(SULTAN_PRICES, "complete")
    }
}
