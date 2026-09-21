package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.MealType
import com.karim.foodrun.orders.Restaurant
import com.karim.foodrun.orders.RestaurantExport
import kotlin.test.Test
import kotlin.test.assertEquals

class RestaurantCatalogMergeTest {
    @Test fun administratorDeletionsOverrideBundledFallback() {
        val removed = RestaurantExport(exportId = "removed", restaurant = Restaurant("removed", "Removed", openOrdering = true))
        val added = removed.copy(exportId = "new", restaurant = removed.restaurant.copy(id = "new"))
        val result = mergeManagedRestaurantCatalog(listOf(removed), emptySet(), emptyList(), listOf(removed, added), setOf("removed"))
        assertEquals(listOf("new"), result.restaurants.map { it.restaurant.id })
        assertEquals(setOf("removed", "new"), result.managedIds)
    }

    @Test fun staleServerCopyKeepsSharjahMetadataAndDoesNotRemoveNewBundledRestaurants() {
        fun export(id: String, emirate: String = "", meals: List<MealType> = emptyList()) =
            RestaurantExport(exportId = id, restaurant = Restaurant(id = id, name = id, emirate = emirate, mealTypes = meals, openOrdering = true))
        val sultan = export("builtin-sultan", "Sharjah", listOf(MealType.BREAKFAST))
        val dubai = export("builtin-dubai", "Dubai", listOf(MealType.LUNCH))
        val personal = export("personal")

        val result = mergeManagedRestaurantCatalog(
            current = listOf(sultan, dubai, personal),
            previousManagedIds = setOf("builtin-sultan"),
            serverValues = listOf(sultan.restaurant.copy(emirate = "", mealTypes = emptyList())),
            bundledValues = listOf(sultan, dubai),
        )

        assertEquals(listOf("personal", "builtin-sultan", "builtin-dubai"), result.restaurants.map { it.restaurant.id })
        assertEquals("Sharjah", result.restaurants.single { it.restaurant.id == "builtin-sultan" }.restaurant.emirate)
        assertEquals(listOf(MealType.BREAKFAST), result.restaurants.single { it.restaurant.id == "builtin-sultan" }.restaurant.mealTypes)
    }
}
