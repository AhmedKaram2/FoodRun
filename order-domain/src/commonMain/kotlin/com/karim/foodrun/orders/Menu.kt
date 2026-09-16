package com.karim.foodrun.orders

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

val orderJson = Json { encodeDefaults = true; ignoreUnknownKeys = false }

@Serializable data class RestaurantExport(
    val schema: String = "foodrun.restaurant", val schemaVersion: Int = 1,
    val exportId: String, val revision: Long = 1, val restaurant: Restaurant,
)
@Serializable data class Restaurant(
    val id: String, val name: String, val branchName: String = "", val currency: String = "AED",
    val contact: RestaurantContact = RestaurantContact(), val pricing: RestaurantPricing = RestaurantPricing(),
    val notes: String = "", val menu: Menu = Menu(),
)
@Serializable data class RestaurantContact(val phoneE164: String? = null, val whatsappE164: String? = null, val address: String? = null)
@Serializable enum class TaxTreatment {
    @SerialName("included") INCLUDED, @SerialName("added") ADDED, @SerialName("unspecified") UNSPECIFIED,
}
@Serializable data class RestaurantPricing(
    val taxTreatment: TaxTreatment = TaxTreatment.INCLUDED, val taxRateBasisPoints: Int? = null,
    val defaultDeliveryFeeMinor: Long = 0, val defaultServiceFeeMinor: Long = 0, val minimumOrderMinor: Long = 0,
)
@Serializable data class Menu(val categories: List<MenuCategory> = emptyList(), val optionGroups: List<OptionGroup> = emptyList(), val items: List<MenuItem> = emptyList())
@Serializable data class MenuCategory(val id: String, val name: String, val sortOrder: Int = 0)
@Serializable data class MenuItem(
    val id: String, val categoryId: String, val name: String, val description: String = "",
    val basePriceMinor: Long, val available: Boolean = true, val variants: List<MenuVariant> = emptyList(),
    val optionGroupIds: List<String> = emptyList(),
)
@Serializable data class MenuVariant(val id: String, val name: String, val priceMinor: Long)
@Serializable data class MenuOption(val id: String, val name: String, val priceDeltaMinor: Long)
@Serializable data class OptionGroup(val id: String, val name: String, val minSelections: Int = 0, val maxSelections: Int = 1, val options: List<MenuOption>)

object MenuValidation {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_MONEY = 100_000_000L
    fun import(text: String): RestaurantExport {
        require(text.length <= MAX_BYTES && text.encodeToByteArray().size <= MAX_BYTES) { "Menu file exceeds 2 MB." }
        JsonInputValidation.validate(text)
        val export = try { orderJson.decodeFromString<RestaurantExport>(text) }
        catch (_: SerializationException) {
            throw IllegalArgumentException("Menu JSON does not match the Food Run format. Check its schema and field names.")
        }
        require(export.schema == "foodrun.restaurant" && export.schemaVersion == 1) { "Unsupported menu format. Version 1 is required." }
        label(export.exportId)
        require(export.revision > 0) { "Missing export identity or revision." }
        validate(export.restaurant)
        return export
    }
    fun validate(r: Restaurant) {
        label(r.id); label(r.name); require(r.branchName.length <= 160 && r.notes.length <= 4000)
        require(r.currency in Money.currencies) { "Supported currencies: ${Money.currencies.joinToString()}." }
        require(r.pricing.taxRateBasisPoints == null || r.pricing.taxRateBasisPoints in 0..10000) { "Invalid tax rate." }
        if (r.pricing.taxTreatment == TaxTreatment.ADDED) require(r.pricing.taxRateBasisPoints != null) { "Enter the restaurant's tax rate." }
        listOf(r.pricing.defaultDeliveryFeeMinor, r.pricing.defaultServiceFeeMinor, r.pricing.minimumOrderMinor).forEach(::price)
        listOfNotNull(r.contact.phoneE164, r.contact.whatsappE164).forEach {
            require(it.matches(Regex("\\+?[0-9 ()-]{5,24}")) && it.count { digit -> digit in '0'..'9' } >= 5) { "Enter a restaurant phone number with at least 5 digits." }
        }
        require((r.contact.address?.length ?: 0) <= 1000)
        val m = r.menu
        require(m.items.size in 1..500 && m.categories.size in 1..100 && m.optionGroups.size <= 100) { "Menu needs 1–500 items and 1–100 categories." }
        unique(m.categories.map { it.id }); unique(m.items.map { it.id }); unique(m.optionGroups.map { it.id })
        m.categories.forEach { label(it.name) }
        unique(m.optionGroups.flatMap { it.options }.map { it.id })
        m.optionGroups.forEach { g ->
            label(g.name); require(g.options.size in 1..30 && g.minSelections in 0..g.maxSelections && g.maxSelections <= g.options.size) { "Invalid option limits for ${g.name}." }
            unique(g.options.map { it.id }); g.options.forEach { label(it.name); price(it.priceDeltaMinor) }
        }
        m.items.forEach { i ->
            label(i.name); require(i.description.length <= 2000); price(i.basePriceMinor)
            require(m.categories.any { it.id == i.categoryId }) { "Unknown category for ${i.name}." }
            require(i.variants.size <= 30); unique(i.variants.map { it.id }); i.variants.forEach { label(it.name); price(it.priceMinor) }
            unique(i.optionGroupIds); require(i.optionGroupIds.all { id -> m.optionGroups.any { it.id == id } }) { "Unknown option group for ${i.name}." }
        }
    }
    fun label(value: String) { require(value.isNotBlank() && value.length <= 160 && value.none { it.code < 32 }) { "Enter a valid name (up to 160 characters)." } }
    fun price(value: Long) { require(value in 0..MAX_MONEY) { "Amount is outside the supported range." } }
    private fun unique(ids: List<String>) { ids.forEach(::label); require(ids.distinct().size == ids.size) { "Duplicate identifiers in menu." } }
}

object Money {
    val currencies = listOf("AED", "USD", "EUR", "GBP", "SAR", "EGP", "KWD", "BHD", "OMR", "JPY")
    fun precision(currency: String): Int {
        require(currency in currencies) { "Unsupported currency." }
        return when (currency) { "JPY" -> 0; "KWD", "BHD", "OMR" -> 3; else -> 2 }
    }
    fun factor(currency: String): Long = when (precision(currency)) { 0 -> 1; 3 -> 1000; else -> 100 }
    fun format(value: Long, currency: String): String {
        require(value != Long.MIN_VALUE) { "Amount is outside the supported range." }
        val amount = if (value < 0) -value else value; val f = factor(currency)
        return "$currency ${if (value < 0) "-" else ""}${amount / f}${if (f == 1L) "" else "." + (amount % f).toString().padStart(precision(currency), '0')}"
    }
    fun parse(value: String, currency: String): Long {
        require(value.matches(Regex("[0-9]{1,9}(\\.[0-9]{1,3})?"))) { "Enter a positive amount using digits and a decimal point." }
        val parts = value.split('.'); val digits = precision(currency)
        require(parts.size == 1 || parts[1].length <= digits) { "Too many decimal places for $currency." }
        return (parts[0].toLong() * factor(currency) + (parts.getOrNull(1)?.padEnd(digits, '0')?.toLongOrNull() ?: 0)).also(MenuValidation::price)
    }
}
