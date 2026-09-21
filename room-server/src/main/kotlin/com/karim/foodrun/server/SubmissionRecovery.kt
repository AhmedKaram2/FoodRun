package com.karim.foodrun.server

import com.karim.foodrun.orders.*

/** Repair only explicit submissions invalidated by the old restaurant-details update. */
internal fun recoverTaxResetSubmissions(room: Room, recordedReply: (String) -> RoomReply?): Room {
    if (room.phase !in listOf(RoomPhase.COLLECTING, RoomPhase.REVIEW)) return room
    val carts = room.carts.map { cart ->
        if (cart.submitted) return@map cart
        val index = room.audit.indexOfLast { it.actorId == cart.memberId && it.action in listOf("CART", "SUBMIT_CART") }
        if (index < 0 || room.audit[index].action != "SUBMIT_CART") return@map cart
        val saved = recordedReply(room.audit[index].id)?.room ?: return@map cart
        if (saved.id != room.id || saved.orderNumber != room.orderNumber || saved.restaurant.id != room.restaurant.id ||
            saved.restaurant.currency != room.restaurant.currency) return@map cart
        val submitted = saved.carts.singleOrNull { it.memberId == cart.memberId && it.submitted } ?: return@map cart
        // Payer pricing may change, but customer quantities, extras and notes must be identical.
        if (submitted.lines.map { it.copy(unitPrice = null) } != cart.lines.map { it.copy(unitPrice = null) }) return@map cart
        val updates = room.audit.drop(index + 1).filter { it.action == "UPDATE_RESTAURANT" }
        if (updates.isEmpty()) return@map cart
        if (cart.lines.isNotEmpty()) {
            val restaurants = updates.map { recordedReply(it.id)?.room?.takeIf { old -> old.id == room.id && old.orderNumber == room.orderNumber }?.restaurant }
            if ((restaurants + room.restaurant).any { it == null || it.id != saved.restaurant.id || it.menu != saved.restaurant.menu || it.openOrdering != saved.restaurant.openOrdering }) return@map cart
        }
        cart.copy(submitted = true, revision = cart.revision + 1, confirmedQuote = -1)
    }
    return if (carts == room.carts) room else room.copy(carts = carts, revision = room.revision + 1)
}
