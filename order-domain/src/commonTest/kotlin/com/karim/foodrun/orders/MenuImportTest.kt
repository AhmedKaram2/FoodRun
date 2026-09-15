package com.karim.foodrun.orders

import kotlin.test.*

class MenuImportTest {
    private val restaurant = Restaurant("r", "Kitchen", menu = Menu(categories = listOf(MenuCategory("c", "Main")), items = listOf(MenuItem("i", "c", "Lunch", basePriceMinor = 1200))))
    private val export = RestaurantExport(exportId = "export", restaurant = restaurant)
    @Test fun exportAndImportPreserveMenuData() {
        assertEquals(export, MenuValidation.import(orderJson.encodeToString(export)))
    }
    @Test fun invalidSchemaOrUnknownKeysAreRejected() {
        assertFailsWith<IllegalArgumentException> { MenuValidation.import(orderJson.encodeToString(export.copy(schemaVersion = 2))) }
        assertFailsWith<IllegalArgumentException> { MenuValidation.import(orderJson.encodeToString(export).dropLast(1) + ",\"secret\":1}") }
    }
    @Test fun duplicateKeysIncludingEscapedKeysCannotOverridePrices() {
        val valid = orderJson.encodeToString(export)
        assertFailsWith<IllegalArgumentException> { MenuValidation.import(valid.replace("\"basePriceMinor\":1200", "\"basePriceMinor\":1200,\"basePriceMinor\":1")) }
        assertFailsWith<IllegalArgumentException> { JsonInputValidation.validate("{\"name\":1,\"na\\u006de\":2}") }
    }
    @Test fun independentObjectsCanUseTheSamePropertyNames() {
        JsonInputValidation.validate("{\"a\":[{\"name\":\"A\"},{\"name\":\"B\"}],\"text\":\"{\\\"name\\\":4}\"}")
    }
    @Test fun largeDeepAndIncompleteInputsAreRejectedBeforeDecode() {
        assertFailsWith<IllegalArgumentException> { JsonInputValidation.validate("[".repeat(25) + "0" + "]".repeat(25)) }
        assertFailsWith<IllegalArgumentException> { MenuValidation.import(" ".repeat(MenuValidation.MAX_BYTES + 1)) }
        assertFailsWith<IllegalArgumentException> { JsonInputValidation.validate("{\"x\":\"unterminated") }
        assertFailsWith<IllegalArgumentException> { JsonInputValidation.validate("}") }
    }
    @Test fun menuIdentifiersReferencesAndOptionIdsAreUnambiguous() {
        assertFailsWith<IllegalArgumentException> { MenuValidation.validate(restaurant.copy(menu = restaurant.menu.copy(items = restaurant.menu.items + restaurant.menu.items))) }
        assertFailsWith<IllegalArgumentException> { MenuValidation.validate(restaurant.copy(menu = restaurant.menu.copy(items = restaurant.menu.items.map { it.copy(categoryId = "missing") }))) }
        val groups = listOf(OptionGroup("g1", "One", options = listOf(MenuOption("same", "A", 0))), OptionGroup("g2", "Two", options = listOf(MenuOption("same", "B", 0))))
        assertFailsWith<IllegalArgumentException> { MenuValidation.validate(restaurant.copy(menu = restaurant.menu.copy(optionGroups = groups))) }
    }
    @Test fun invalidTaxPhoneMoneyAndOptionLimitsAreRejected() {
        assertFailsWith<IllegalArgumentException> { MenuValidation.validate(restaurant.copy(pricing = RestaurantPricing(taxTreatment = TaxTreatment.ADDED))) }
        assertFailsWith<IllegalArgumentException> { MenuValidation.validate(restaurant.copy(contact = RestaurantContact("https://not-a-phone"))) }
        assertFailsWith<IllegalArgumentException> { MenuValidation.validate(restaurant.copy(menu = restaurant.menu.copy(items = restaurant.menu.items.map { it.copy(basePriceMinor = -1) }))) }
        assertFailsWith<IllegalArgumentException> { MenuValidation.validate(restaurant.copy(menu = restaurant.menu.copy(optionGroups = listOf(OptionGroup("g", "Extras", minSelections = 2, options = listOf(MenuOption("o", "Extra", 0))))))) }
    }
}
