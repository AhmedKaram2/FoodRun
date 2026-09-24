package com.karim.foodrun.orders

object Billing {
    fun lines(restaurant: Restaurant, cart: MemberCart): List<ReceiptLine> {
        require(cart.lines.size <= 100 && cart.lines.map { it.id }.distinct().size == cart.lines.size) { "Invalid cart lines." }
        return cart.lines.map { line ->
            MenuValidation.label(line.id)
            if (line.description.isNotEmpty()) {
                require(restaurant.openOrdering && line.itemId.isEmpty() && line.variantId == null && line.optionIds.isEmpty()) { "Custom items are only available for open orders." }
                MenuValidation.label(line.description)
                require(line.quantity in 1..99 && line.notes.length <= 500) { "Invalid quantity or note." }
                line.unitPrice?.let(MenuValidation::price)
                val amount = (line.unitPrice ?: 0) * line.quantity
                MenuValidation.price(amount)
                return@map ReceiptLine(line.description, line.quantity, amount, line.notes)
            }
            // The server accepts overrides only through the owner or selected payer's PRICE_ITEM command.
            line.unitPrice?.let(MenuValidation::price)
            val item = restaurant.menu.items.singleOrNull { it.id == line.itemId } ?: error("Menu item no longer exists.")
            require(item.available) { "${item.name} is unavailable." }
            require(line.quantity in 1..99 && line.notes.length <= 500) { "Invalid quantity or note." }
            val variant = item.variants.singleOrNull { it.id == line.variantId }
            require((item.variants.isEmpty() && line.variantId == null) || variant != null) { "Select a size for ${item.name}." }
            require(line.optionIds.distinct().size == line.optionIds.size) { "An extra can only be selected once per item." }
            val groups = restaurant.menu.optionGroups.filter { it.id in item.optionGroupIds }
            val options = groups.flatMap { it.options }
            require(line.optionIds.all { id -> options.any { it.id == id } }) { "Invalid extra." }
            groups.forEach { group -> require(line.optionIds.count { id -> group.options.any { it.id == id } } in group.minSelections..group.maxSelections) { "Check ${group.name} selection limits." } }
            val selected = options.filter { it.id in line.optionIds }
            val unit = line.unitPrice ?: ((variant?.priceMinor ?: item.basePriceMinor) + selected.sumOf { it.priceDeltaMinor })
            val amount = unit * line.quantity
            MenuValidation.price(amount)
            val description = (listOf(item.name) + listOfNotNull(variant?.name) + selected.map { it.name }).joinToString(" · ")
            ReceiptLine(description, line.quantity, amount, line.notes, line.itemId, line.variantId, line.optionIds)
        }.also { MenuValidation.price(it.sumOf { line -> line.amount }) }
    }

    /** Largest remainder allocation; stable member IDs settle exact ties. */
    fun allocate(amount: Long, weights: Map<String, Long>): Map<String, Long> {
        require(amount in 0..MenuValidation.MAX_MONEY && weights.size <= 30 && weights.values.all { it in 0..10_000_000_000L }) { "Amount is too large." }
        if (weights.isEmpty()) { require(amount == 0L) { "Cannot divide fees without food orders." }; return emptyMap() }
        val safe = if (weights.values.sum() == 0L) weights.mapValues { 1L } else weights
        val denominator = safe.values.sum()
        require(denominator <= 10_000_000_000L) { "Allocation weights exceed the supported range." }
        val result = safe.mapValues { amount * it.value / denominator }.toMutableMap()
        val order = safe.keys.sortedWith(compareByDescending<String> { amount * safe.getValue(it) % denominator }.thenBy { it })
        val remaining = (amount - result.values.sum()).toInt()
        repeat(remaining) { index -> val id = order[index]; result[id] = result.getValue(id) + 1 }
        return result
    }

    fun receipts(room: Room): List<Receipt> {
        RoomRules.validateFees(room.fees)
        require(room.adjustment in -MenuValidation.MAX_MONEY..MenuValidation.MAX_MONEY) { "Invalid bill adjustment." }
        val carts = room.carts.filter { c -> room.orderingMembers.any { it.id == c.memberId } }
        require(carts.map { it.memberId }.distinct().size == carts.size) { "Duplicate member carts." }
        val lines = carts.associate { it.memberId to lines(room.restaurant, it) }
        val foods = lines.filterValues { it.isNotEmpty() }.mapValues { it.value.sumOf { line -> line.amount } }
        val totalFood = foods.values.sum()
        MenuValidation.price(totalFood)
        val automaticDelivery = room.deliveryMode && room.fees.automaticDelivery
        val deliveryTotal = if (automaticDelivery) maxOf(500L, foods.size * 100L) else room.fees.delivery
        val delivery = if (foods.isEmpty()) emptyMap() else allocate(deliveryTotal, if (room.fees.proportionalDelivery && !automaticDelivery) foods else foods.mapValues { 1L })
        val service = if (foods.isEmpty()) emptyMap() else allocate(room.fees.service, foods.mapValues { 1L })
        // During collection a configured discount can exceed the food entered so far. Final confirmation validates it.
        val discounts = allocate(minOf(room.fees.discount, totalFood), foods)
        val taxBase = foods.mapValues { (id, amount) -> amount - discounts.getValue(id) + delivery.getValue(id) + service.getValue(id) }
        val taxTotal = if (room.restaurant.pricing.taxTreatment == TaxTreatment.ADDED) (taxBase.values.sum() * (room.restaurant.pricing.taxRateBasisPoints ?: 0) + 5000) / 10000 else 0L
        val taxes = allocate(taxTotal, taxBase)
        val beforeAdjustment = taxBase.mapValues { (id, amount) -> amount + taxes.getValue(id) }
        require(room.adjustment >= -beforeAdjustment.values.sum()) { "Adjustment exceeds the bill." }
        val adjustments = allocate(kotlin.math.abs(room.adjustment), beforeAdjustment)
        val receipts = carts.map { cart ->
            val id = cart.memberId
            val feeAdjustment = (adjustments[id] ?: 0) * if (room.adjustment < 0) -1 else 1
            val total = (beforeAdjustment[id] ?: 0) + feeAdjustment
            MenuValidation.price(total)
            val paid = room.transfers.filter { it.memberId == id && it.status == TransferStatus.CONFIRMED }.sumOf { if (it.refund) -it.amount else it.amount }
            Receipt(id, room.members.single { it.id == id }.name, lines.getValue(id), foods[id] ?: 0, delivery[id] ?: 0, (service[id] ?: 0) + feeAdjustment, discounts[id] ?: 0, taxes[id] ?: 0, total, paid, if (id == room.payerId) 0 else total - paid, room.billRevision + room.quoteRevision, room.restaurant.currency)
        }
        MenuValidation.price(receipts.sumOf { it.total })
        return receipts
    }
}
