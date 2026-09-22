package com.karim.foodrun.orders

import kotlin.test.*

class BuiltInRestaurantsTest {
    @Test fun photographedMenusAreValidAndKeepTheRequestedPriceRules() {
        BuiltInRestaurants.all.forEach { MenuValidation.validate(it.restaurant) }
        assertEquals(43, BuiltInRestaurants.all.size)
        assertTrue(SharjahRestaurantCatalog.all.all { (it.restaurant.googleRating ?: 0.0) >= 4.0 })
        assertEquals(10, DubaiRestaurantCatalog.egyptian.size)
        assertEquals(10, DubaiRestaurantCatalog.arabicShawarma.size)
        assertEquals(10, DubaiRestaurantCatalog.other.size)
        assertTrue(DubaiRestaurantCatalog.all.all { it.restaurant.emirate == "Dubai" })
        assertTrue(DubaiRestaurantCatalog.all.all { (it.restaurant.googleRating ?: 0.0) in 4.0..5.0 })
        assertEquals(8, SharjahRestaurantCatalog.shawerman.restaurant.menu.items.size)
        assertEquals(9, SharjahRestaurantCatalog.laffah.restaurant.menu.items.size)
        assertEquals(300, BuiltInRestaurants.sultan.restaurant.menu.items.single { it.name == "Falafel" }.basePriceMinor)
        assertEquals(300, BuiltInRestaurants.sultan.restaurant.menu.items.single { it.name == "Beans With Corn Oil" }.basePriceMinor)
        assertEquals(300, BuiltInRestaurants.alMahla.restaurant.menu.items.single { it.name == "Falafel" }.basePriceMinor)
        assertEquals(listOf("Sandwiches"), BuiltInRestaurants.alMahla.restaurant.menu.categories.map { it.name })
        assertEquals(29, BuiltInRestaurants.alMahla.restaurant.menu.items.size)
        assertEquals(126, BuiltInRestaurants.baitAlWaleema.restaurant.menu.items.size)
        assertEquals("+97165223309", BuiltInRestaurants.baitAlWaleema.restaurant.contact.phoneE164)
        assertEquals(17, BuiltInRestaurants.baitAlWaleema.restaurant.menu.items.count {
            it.categoryId == "builtin-bait-al-waleema-category-10"
        })
        assertEquals(1_499, BuiltInRestaurants.baitAlWaleema.restaurant.menu.items.single {
            it.name == "Charcoal Hawawshi Sandwich"
        }.basePriceMinor)
        assertEquals(listOf(3_599L, 6_599L, 12_099L), BuiltInRestaurants.baitAlWaleema.restaurant.menu.items.single {
            it.name == "Egyptian Kofta"
        }.variants.map { it.priceMinor })
        assertTrue(BuiltInRestaurants.all.all { it.restaurant.nameAr.isNotBlank() })
        assertTrue(BuiltInRestaurants.all.flatMap { it.restaurant.menu.categories }.all { it.nameAr.isNotBlank() })
        assertTrue(BuiltInRestaurants.all.flatMap { it.restaurant.menu.items }.all { it.nameAr.any { character -> character in '\u0600'..'\u06ff' } })
        assertTrue(BuiltInRestaurants.all.flatMap { it.restaurant.menu.items }.flatMap { it.variants }.all { it.nameAr.isNotBlank() })
    }
}
