package com.karim.foodrun.orders

/** Preserve identifiers and operator prices while splitting the old combined oil entries. */
object SultanMenuUpdate {
    fun apply(restaurant: Restaurant): Restaurant {
        if(restaurant.id != "builtin-sultan") return restaurant
        val defaults = BuiltInRestaurants.sultan.restaurant.menu.items
        val items = restaurant.menu.items.toMutableList()
        listOf("builtin-sultan-item-0-1" to "builtin-sultan-item-0-28", "builtin-sultan-item-1-10" to "builtin-sultan-item-1-37").forEach { (oldId, newId) ->
            val index = items.indexOfFirst { it.id == oldId }
            if(index >= 0) {
                val old = items[index]
                val hot = defaults.single { it.id == oldId }
                // These entries were a single oil choice; unrelated admin edits remain intact.
                items[index] = old.copy(name = hot.name, nameAr = hot.nameAr)
                if(items.none { it.id == newId }) {
                    val olive = defaults.single { it.id == newId }
                    items += old.copy(id = newId, name = olive.name, nameAr = olive.nameAr,
                        variants = old.variants.map { it.copy(id = it.id.replace(oldId, newId)) })
                }
            }
        }
        if(items.none { it.id == "builtin-sultan-item-1-38" }) {
            val base = items.firstOrNull { it.id == "builtin-sultan-item-1-24" }
            val cheese = defaults.single { it.id == "builtin-sultan-item-1-38" }
            if(base != null) items += cheese.copy(basePriceMinor = base.basePriceMinor + 100,
                categoryId = base.categoryId)
        }
        return restaurant.copy(menu = restaurant.menu.copy(items = items))
    }
}
