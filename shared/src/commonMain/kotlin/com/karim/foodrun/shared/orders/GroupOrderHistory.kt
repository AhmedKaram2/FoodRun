package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

internal data class PreviousOrderChoice(
    val value: String,
    val roomId: String,
    val order: PastOrder,
    val receipt: Receipt,
    val restaurantId: String,
    val repeatCount: Int,
)

internal fun GroupController.previousOrderChoices(restaurant: Restaurant? = null, includeEmpty: Boolean = false): List<PreviousOrderChoice> {
    val all = library.sessions.flatMap { saved ->
        library.snapshots[saved.roomId]?.history.orEmpty().mapNotNull { past ->
            val receipt = past.receipts.firstOrNull { it.memberId == saved.memberId } ?: return@mapNotNull null
            if (receipt.lines.isEmpty() && !includeEmpty) return@mapNotNull null
            val restaurantId = past.restaurantId.ifBlank {
                library.restaurants.firstOrNull { it.restaurant.name.equals(past.restaurantName, ignoreCase = true) }?.restaurant?.id.orEmpty()
            }
            if (restaurant != null && restaurantId != restaurant.id && !past.restaurantName.equals(restaurant.name, ignoreCase = true)) return@mapNotNull null
            PreviousOrderChoice("past|${saved.roomId}|${past.number}", saved.roomId, past, receipt, restaurantId, 1)
        }
    }
    return all.groupBy { choice ->
        choice.receipt.orderSelectionKey(choice.restaurantId.ifBlank { "name:${choice.order.restaurantName.lowercase()}" })
    }.values.map { matches ->
        matches.maxBy { it.order.completedAt }.copy(repeatCount = matches.size)
    }.sortedByDescending { it.order.completedAt }
}

internal fun GroupController.currentRoomPreviousOrderChoices(includeEmpty: Boolean = false): List<PreviousOrderChoice> {
    val currentSession = requireNotNull(session)
    val currentRoom = room()
    val choices = reply?.history.orEmpty().mapNotNull { past ->
        val receipt = past.receipts.firstOrNull { it.memberId == currentSession.memberId } ?: return@mapNotNull null
        if (receipt.lines.isEmpty() && !includeEmpty) return@mapNotNull null
        val restaurantId = past.restaurantId.ifBlank {
            currentRoom.restaurant.takeIf { it.name.equals(past.restaurantName, ignoreCase = true) }?.id.orEmpty()
        }
        PreviousOrderChoice("past|${currentRoom.id}|${past.number}", currentRoom.id, past, receipt, restaurantId, 1)
    }
    return choices.groupBy { choice ->
        choice.receipt.orderSelectionKey(choice.restaurantId.ifBlank { "name:${choice.order.restaurantName.lowercase()}" })
    }.values.map { matches ->
        matches.maxBy { it.order.completedAt }.copy(repeatCount = matches.size)
    }.sortedByDescending { it.order.completedAt }
}

private fun GroupController.findPreviousOrder(value: String): PreviousOrderChoice? {
    val parts = value.split('|')
    if (parts.size != 3 || parts[0] != "past") return null
    val saved = library.sessions.firstOrNull { it.roomId == parts[1] } ?: return null
    val snapshot = if (session?.roomId == saved.roomId) reply else library.snapshots[saved.roomId]
    val past = snapshot?.history?.firstOrNull { it.number == parts[2].toLongOrNull() } ?: return null
    val receipt = past.receipts.firstOrNull { it.memberId == saved.memberId && it.lines.isNotEmpty() } ?: return null
    val restaurantId = past.restaurantId.ifBlank {
        library.restaurants.firstOrNull { it.restaurant.name.equals(past.restaurantName, true) }?.restaurant?.id.orEmpty()
    }
    return PreviousOrderChoice(value, saved.roomId, past, receipt, restaurantId, 1)
}

internal fun GroupController.favoriteOrder(value: String) {
    val profile = requireNotNull(library.home?.profile) { "Sign in to save favorite orders." }
    val choice = findPreviousOrder(value) ?: error("This previous order is no longer available.")
    val restaurantId = choice.restaurantId.ifBlank {
        reply?.room?.restaurant?.takeIf { it.name.equals(choice.order.restaurantName, ignoreCase = true) }?.id.orEmpty()
    }
    require(restaurantId.isNotBlank()) { "Open a room for this restaurant before saving this older receipt as a favorite." }
    val lines = choice.receipt.lines.map { line ->
        FavoriteOrderLine(
            itemId = line.itemId,
            quantity = line.quantity,
            variantId = line.variantId,
            optionIds = line.optionIds,
            notes = line.notes,
            description = line.description.takeIf { line.itemId.isBlank() }.orEmpty(),
            label = line.description,
        )
    }
    val summary = lines.take(2).joinToString(" + ") { "${it.quantity} × ${it.label}" } + if (lines.size > 2) " + ${lines.size - 2} more" else ""
    val favorite = FavoriteOrder(
        id = platform.uuid(),
        restaurantId = restaurantId,
        restaurantName = choice.order.restaurantName,
        title = summary.take(160),
        lines = lines,
        savedAt = platform.now(),
    )
    require(profile.favoriteOrders.none { it.selectionKey() == favorite.selectionKey() }) { "This order is already in your favorites." }
    require(profile.favoriteOrders.size < 30) { "Your 30 favorites are full. Remove one before saving another." }
    saveProfileUpdate(profile.copy(favoriteOrders = listOf(favorite) + profile.favoriteOrders))
}

internal fun GroupController.removeFavoriteOrder(id: String) {
    val profile = requireNotNull(library.home?.profile) { "Sign in to edit favorite orders." }
    require(profile.favoriteOrders.any { it.id == id }) { "Favorite order was not found." }
    saveProfileUpdate(profile.copy(favoriteOrders = profile.favoriteOrders.filterNot { it.id == id }))
}

internal fun GroupController.reuseOrder(value: String) {
    val currentRoom = room()
    val source = when {
        value.startsWith("favorite|") -> {
            val favorite = library.home?.profile?.favoriteOrders?.singleOrNull { it.id == value.substringAfter('|') }
                ?: error("Favorite order was not found.")
            require(favorite.restaurantId == currentRoom.restaurant.id) { "This favorite belongs to ${favorite.restaurantName}." }
            favorite.lines
        }
        value.startsWith("past|") -> {
            val choice = findPreviousOrder(value)
                ?: error("Previous order was not found for this restaurant.")
            require(choice.restaurantId == currentRoom.restaurant.id || (choice.restaurantId.isBlank() && choice.order.restaurantName.equals(currentRoom.restaurant.name, true))) { "This previous order belongs to another restaurant." }
            choice.receipt.lines.map { line -> FavoriteOrderLine(
                itemId = line.itemId,
                quantity = line.quantity,
                variantId = line.variantId,
                optionIds = line.optionIds,
                notes = line.notes,
                description = line.description.takeIf { line.itemId.isBlank() }.orEmpty(),
                label = line.description,
            ) }
        }
        else -> error("Saved order selection is invalid.")
    }
    val reusable = source.mapNotNull { saved ->
        if (saved.itemId.isBlank()) {
            if (!currentRoom.restaurant.openOrdering) null else CartLine(platform.uuid(), "", saved.quantity, notes = saved.notes, description = saved.description)
        } else {
            val item = currentRoom.restaurant.menu.items.firstOrNull { it.id == saved.itemId && it.available } ?: return@mapNotNull null
            val validVariant = (item.variants.isEmpty() && saved.variantId == null) || item.variants.any { it.id == saved.variantId }
            val options = currentRoom.restaurant.menu.optionGroups.filter { it.id in item.optionGroupIds }.flatMap { it.options }
            if (!validVariant || saved.optionIds.any { id -> options.none { it.id == id } }) null
            else CartLine(platform.uuid(), saved.itemId, saved.quantity, saved.variantId, saved.optionIds, saved.notes)
        }
    }
    require(reusable.isNotEmpty()) { "This saved order no longer has items available on the current menu." }
    val merged = myCart().lines.toMutableList()
    reusable.forEach { incoming ->
        val index = merged.indexOfFirst { existing ->
            existing.itemId == incoming.itemId && existing.variantId == incoming.variantId &&
                existing.optionIds.sorted() == incoming.optionIds.sorted() && existing.notes == incoming.notes &&
                existing.description == incoming.description
        }
        if (index < 0) merged += incoming
        else {
            val quantity = merged[index].quantity + incoming.quantity
            require(quantity <= 99) { "A reused item would exceed the maximum quantity of 99." }
            merged[index] = merged[index].copy(quantity = quantity, unitPrice = null)
        }
    }
    val cart = myCart()
    Billing.lines(currentRoom.restaurant, cart.copy(lines = merged))
    command(CommandKind.CART, cart = cart.copy(lines = merged), revision = cart.revision)
}

/** Home and room subscriptions retain the same downloaded history and honor admin deletions. */
internal fun mergePaymentHistory(cached: RoomReply?, next: RoomReply): RoomReply {
    if(cached?.room?.id != next.room?.id || cached?.memberId != next.memberId) return next
    val history = (next.history + cached.history).filterNot { it.number in next.deletedHistoryNumbers }.distinctBy { it.number }.sortedByDescending { it.number }
    val offset = if(next.deletedHistoryNumbers.isEmpty() && cached.room?.orderNumber == next.room?.orderNumber && cached.history.size > next.history.size) cached.historyNextOffset else next.historyNextOffset
    return next.copy(history = history, historyNextOffset = offset)
}
