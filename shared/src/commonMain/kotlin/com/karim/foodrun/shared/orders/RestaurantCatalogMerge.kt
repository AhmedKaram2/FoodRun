package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.BuiltInRestaurants
import com.karim.foodrun.orders.Restaurant
import com.karim.foodrun.orders.RestaurantExport

internal data class ManagedRestaurantCatalog(val restaurants: List<RestaurantExport>, val managedIds: Set<String>)

internal fun restoreRestaurantMetadata(
    values: List<RestaurantExport>,
    bundledValues: List<RestaurantExport> = BuiltInRestaurants.all,
): List<RestaurantExport> {
    val bundledById = bundledValues.associateBy { it.restaurant.id }
    return values.map { export ->
        val value = export.restaurant
        val fallback = bundledById[value.id]?.restaurant
        val enriched = if (fallback == null) value else value.copy(
            nameAr = value.nameAr.ifBlank { fallback.nameAr },
            emirate = value.emirate.ifBlank { fallback.emirate },
            emirateAr = value.emirateAr.ifBlank { fallback.emirateAr },
            area = value.area.ifBlank { fallback.area },
            areaAr = value.areaAr.ifBlank { fallback.areaAr },
            cuisine = value.cuisine.ifBlank { fallback.cuisine },
            cuisineAr = value.cuisineAr.ifBlank { fallback.cuisineAr },
            mealTypes = value.mealTypes.ifEmpty { fallback.mealTypes },
            googleRating = value.googleRating ?: fallback.googleRating,
            googleRatingCount = value.googleRatingCount ?: fallback.googleRatingCount,
            googleRatingVerifiedOn = value.googleRatingVerifiedOn.ifBlank { fallback.googleRatingVerifiedOn },
        )
        export.copy(restaurant = enriched)
    }
}

internal fun mergeManagedRestaurantCatalog(
    current: List<RestaurantExport>,
    previousManagedIds: Set<String>,
    serverValues: List<Restaurant>,
    bundledValues: List<RestaurantExport> = BuiltInRestaurants.all,
    deletedRestaurantIds: Set<String> = emptySet(),
): ManagedRestaurantCatalog {
    val server = restoreRestaurantMetadata(serverValues.map { RestaurantExport(exportId = it.id, restaurant = it) }, bundledValues)
    val serverIds = server.map { it.restaurant.id }.toSet()
    val managed = (server + bundledValues.filterNot { it.restaurant.id in serverIds }).filterNot { it.restaurant.id in deletedRestaurantIds }
    val managedIds = previousManagedIds + managed.map { it.restaurant.id } + deletedRestaurantIds
    return ManagedRestaurantCatalog(current.filterNot { it.restaurant.id in managedIds } + managed, managedIds)
}
