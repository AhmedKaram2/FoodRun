package com.karim.foodrun.orders

import kotlin.test.*

class BuiltInRestaurantsTest {
    @Test fun photographedMenusAreValidAndKeepTheRequestedPriceRules() {
        BuiltInRestaurants.all.forEach { MenuValidation.validate(it.restaurant) }
        assertEquals(3, BuiltInRestaurants.all.size)
        assertEquals(400, BuiltInRestaurants.sultan.restaurant.menu.items.single { it.name == "Falafel" }.basePriceMinor)
        assertEquals(300, BuiltInRestaurants.alMahla.restaurant.menu.items.single { it.name == "Falafel" }.basePriceMinor)
        assertEquals(listOf("Sandwiches"), BuiltInRestaurants.alMahla.restaurant.menu.categories.map { it.name })
        assertEquals(29, BuiltInRestaurants.alMahla.restaurant.menu.items.size)
        assertTrue(BuiltInRestaurants.all.all { it.restaurant.nameAr.isNotBlank() })
        assertTrue(BuiltInRestaurants.all.flatMap { it.restaurant.menu.categories }.all { it.nameAr.isNotBlank() })
        assertTrue(BuiltInRestaurants.all.flatMap { it.restaurant.menu.items }.all { it.nameAr.any { character -> character in '\u0600'..'\u06ff' } })
        assertTrue(BuiltInRestaurants.all.flatMap { it.restaurant.menu.items }.flatMap { it.variants }.all { it.nameAr.isNotBlank() })
    }
}
