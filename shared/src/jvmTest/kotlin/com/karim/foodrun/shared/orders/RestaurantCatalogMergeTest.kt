package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.MealType
import com.karim.foodrun.orders.Restaurant
import com.karim.foodrun.orders.RestaurantExport
import kotlin.test.Test
import kotlin.test.assertEquals

class RestaurantCatalogMergeTest {
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
