package com.karim.foodrun.server

import com.karim.foodrun.orders.BuiltInRestaurants
import com.karim.foodrun.orders.orderJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class WebCatalogSyncTest {
    @Test fun webPricedFallbackCatalogMatchesSharedPricedMenus() {
        val catalogFile = File("../webApp/src/foodrun/builtInRestaurants.json")
        val expected = listOf(
            BuiltInRestaurants.sultan,
            BuiltInRestaurants.alKalha,
            BuiltInRestaurants.alMahla,
            BuiltInRestaurants.baitAlWaleema,
        ).map { export -> export.restaurant.copy(
            emirate = "", emirateAr = "", area = "", areaAr = "", cuisine = "", cuisineAr = "",
            mealTypes = emptyList(), googleRating = null, googleRatingCount = null, googleRatingVerifiedOn = "",
        ) }
        val actual = orderJson.decodeFromString<List<com.karim.foodrun.orders.Restaurant>>(catalogFile.readText())
        assertEquals(expected, actual)
    }
}
