package com.karim.foodrun.server

import com.karim.foodrun.orders.BuiltInRestaurants
import com.karim.foodrun.orders.orderJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class WebCatalogSyncTest {
    @Test fun webFallbackCatalogMatchesSharedCatalog() {
        val catalogFile = File("../webApp/src/foodrun/builtInRestaurants.json")
        val expected = orderJson.encodeToString(BuiltInRestaurants.all.map { it.restaurant })
        if (System.getenv("FOODRUN_UPDATE_WEB_CATALOG") == "true") catalogFile.writeText(expected)
        assertEquals(expected, catalogFile.readText())
    }
}
