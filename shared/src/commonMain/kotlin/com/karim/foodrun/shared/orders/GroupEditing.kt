package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

internal fun GroupController.restaurantEditor(export: RestaurantExport?, forRoom: Boolean = false) {
    formDrafts.beginRestaurant(draft)
    editingRoomOrder = if(forRoom) room().let { it.id to it.orderNumber } else null
    editingRestaurant = export ?: RestaurantExport(exportId = platform.uuid(), restaurant = Restaurant(platform.uuid(), "", menu = Menu(categories = listOf(MenuCategory("main", "Menu")))))
    val r = editingRestaurant!!.restaurant
    draft[GroupFieldKey.RESTAURANT_NAME] = r.name; draft[GroupFieldKey.BRANCH] = r.branchName
    draft[GroupFieldKey.CURRENCY] = r.currency; draft[GroupFieldKey.PHONE] = r.contact.phoneE164 ?: ""
    draft[GroupFieldKey.ADDRESS] = r.contact.address ?: ""
    draft[GroupFieldKey.TAX_RATE] = Money.format((r.pricing.taxRateBasisPoints ?: 0).toLong(), "AED").substringAfter(' ')
    draft[GroupFieldKey.MINIMUM_ORDER] = Money.format(r.pricing.minimumOrderMinor, r.currency).substringAfter(' ')
    draft[GroupFieldKey.RESTAURANT_NAME_AR] = r.nameAr
    draft[GroupFieldKey.BRANCH_AR] = r.branchNameAr
    draft[GroupFieldKey.EMIRATE] = r.emirate
    draft[GroupFieldKey.EMIRATE_AR] = r.emirateAr
    draft[GroupFieldKey.AREA] = r.area
    draft[GroupFieldKey.AREA_AR] = r.areaAr
    draft[GroupFieldKey.CUISINE] = r.cuisine
    draft[GroupFieldKey.CUISINE_AR] = r.cuisineAr
    draft[GroupFieldKey.RESTAURANT_NOTES] = r.notes
    draft[GroupFieldKey.RESTAURANT_WHATSAPP] = r.contact.whatsappE164.orEmpty()
    draft[GroupFieldKey.OPEN_ORDERING] = r.openOrdering.toString()
    draft[GroupFieldKey.MEAL_BREAKFAST] = (MealType.BREAKFAST in r.mealTypes).toString()
    draft[GroupFieldKey.MEAL_LUNCH] = (MealType.LUNCH in r.mealTypes).toString()
    draft[GroupFieldKey.MEAL_DINNER] = (MealType.DINNER in r.mealTypes).toString()
    seedFees(r); page = GroupPage.RESTAURANT
}
internal fun GroupController.seedFees(r: Restaurant) {
    fun amount(v: Long) = Money.format(v, r.currency).substringAfter(' ')
    draft[GroupFieldKey.DELIVERY_FEE] = amount(r.pricing.defaultDeliveryFeeMinor)
    draft[GroupFieldKey.SERVICE_FEE] = amount(r.pricing.defaultServiceFeeMinor)
    draft[GroupFieldKey.DISCOUNT] = "0"
}
internal fun GroupController.fees(currency: String): FeePolicy {
    val automatic = if(page == GroupPage.SETUP) flag(GroupFieldKey.DELIVERY)
        else reply?.room?.deliveryMode == true && flag(GroupFieldKey.AUTOMATIC_DELIVERY)
    return FeePolicy(
        if(automatic) 0 else Money.parse(text(GroupFieldKey.DELIVERY_FEE).ifBlank { "0" }, currency),
        Money.parse(text(GroupFieldKey.SERVICE_FEE).ifBlank { "0" }, currency),
        Money.parse(text(GroupFieldKey.DISCOUNT).ifBlank { "0" }, currency),
        if(automatic) false else flag(GroupFieldKey.PROPORTIONAL), automaticDelivery = automatic,
    )
}
internal fun GroupController.addMenuItem() {
    val export = requireNotNull(editingRestaurant)
    val name = text(GroupFieldKey.MENU_ITEM_NAME).trim(); MenuValidation.label(name)
    val currency = "AED"
    val item = MenuItem(platform.uuid(), export.restaurant.menu.categories.first().id, name, basePriceMinor = Money.parse(text(GroupFieldKey.MENU_ITEM_PRICE), currency))
    editingRestaurant = export.copy(restaurant = export.restaurant.copy(menu = export.restaurant.menu.copy(items = export.restaurant.menu.items + item)))
    draft[GroupFieldKey.MENU_ITEM_NAME] = ""; draft[GroupFieldKey.MENU_ITEM_PRICE] = ""
}
internal fun GroupController.saveEditor() {
    val export = requireNotNull(editingRestaurant); val currency = "AED"
    editingRoomOrder?.let { require(it == room().let { r -> r.id to r.orderNumber }) { "A different order is open. Return to it before editing its restaurant." } }
    val taxRate = if(export.restaurant.pricing.taxTreatment == TaxTreatment.ADDED) Money.parse(text(GroupFieldKey.TAX_RATE), "AED").also {
        require(it <= 10000) { "Tax rate must be between 0 and 100 percent." }
    }.toInt() else export.restaurant.pricing.taxRateBasisPoints
    val r = export.restaurant.copy(nameAr = text(GroupFieldKey.RESTAURANT_NAME_AR).trim(), branchNameAr = text(GroupFieldKey.BRANCH_AR).trim(), emirate = text(GroupFieldKey.EMIRATE).trim(), emirateAr = text(GroupFieldKey.EMIRATE_AR).trim(), area = text(GroupFieldKey.AREA).trim(), areaAr = text(GroupFieldKey.AREA_AR).trim(), cuisine = text(GroupFieldKey.CUISINE).trim(), cuisineAr = text(GroupFieldKey.CUISINE_AR).trim(), notes = text(GroupFieldKey.RESTAURANT_NOTES).trim(),
        openOrdering = flag(GroupFieldKey.OPEN_ORDERING), mealTypes = listOfNotNull(MealType.BREAKFAST.takeIf { flag(GroupFieldKey.MEAL_BREAKFAST) }, MealType.LUNCH.takeIf { flag(GroupFieldKey.MEAL_LUNCH) }, MealType.DINNER.takeIf { flag(GroupFieldKey.MEAL_DINNER) }), name = text(GroupFieldKey.RESTAURANT_NAME).trim(), branchName = text(GroupFieldKey.BRANCH).trim(), currency = currency,
        contact = export.restaurant.contact.copy(whatsappE164 = text(GroupFieldKey.RESTAURANT_WHATSAPP).trim().takeIf { it.isNotEmpty() }?.let(UaePhone::normalize), phoneE164 = text(GroupFieldKey.PHONE).trim().takeIf { it.isNotEmpty() }?.let(UaePhone::normalize), address = text(GroupFieldKey.ADDRESS).trim().ifEmpty { null }),
        pricing = export.restaurant.pricing.copy(defaultDeliveryFeeMinor = fees(currency).delivery, defaultServiceFeeMinor = fees(currency).service,
            taxRateBasisPoints = taxRate, minimumOrderMinor = Money.parse(text(GroupFieldKey.MINIMUM_ORDER).ifBlank { "0" }, currency)))
    MenuValidation.validate(r)
    if(administration.editingRestaurant) { administration.saveRestaurant(r); return }
    saveRestaurant(export.copy(restaurant = r, revision = export.revision + 1))
    if(editingRoomOrder != null) command(CommandKind.UPDATE_RESTAURANT, restaurant = r, text = text(GroupFieldKey.REASON).ifBlank { "Restaurant details updated" })
    else { formDrafts.finishRestaurant(draft); page = GroupPage.LIBRARY }
}
internal fun GroupController.saveRestaurant(export: RestaurantExport) {
    MenuValidation.validate(export.restaurant)
    require(library.restaurants.size < 100 || library.restaurants.any { it.restaurant.id == export.restaurant.id }) { "Restaurant library limit reached." }
    replaceLibrary(library.copy(restaurants = library.restaurants.filterNot { it.restaurant.id == export.restaurant.id } + export))
    if (selectedRestaurant?.restaurant?.id == export.restaurant.id) selectedRestaurant = export
}
internal fun GroupController.addCartItem() {
    val item = requireNotNull(selectedItem); val cart = myCart()
    val quantity = text(GroupFieldKey.QUANTITY).toIntOrNull() ?: error("Enter a whole-number quantity.")
    val line = CartLine(editingCartLineId ?: platform.uuid(), item.id, quantity, variant, options, text(GroupFieldKey.NOTE))
    val lines = if(editingCartLineId == null) cart.lines + line else cart.lines.map { if(it.id == editingCartLineId) line else it }
    val next = cart.copy(lines = lines); Billing.lines(room().restaurant, next)
    command(CommandKind.CART, cart = next, revision = cart.revision)
}
internal fun GroupController.seedAccount(a: ReceivingAccount) {
    draft.remove(GroupFieldKey.ACCOUNT_IBAN_DRAFT); draft.remove(GroupFieldKey.ACCOUNT_AANI_DRAFT)
    draft[GroupFieldKey.AANI] = (a.method == PaymentMethod.AANI).toString(); draft[GroupFieldKey.ACCOUNT_HOLDER] = a.holder; draft[GroupFieldKey.ACCOUNT_BANK] = a.bank; draft[GroupFieldKey.ACCOUNT_IDENTIFIER] = a.identifier
}
internal fun GroupController.accountMatchesDraft(a: ReceivingAccount): Boolean =
    selectedAccount?.id == a.id && a.holder == text(GroupFieldKey.ACCOUNT_HOLDER).trim() &&
        a.bank == text(GroupFieldKey.ACCOUNT_BANK).trim() && a.identifier == text(GroupFieldKey.ACCOUNT_IDENTIFIER).trim() &&
        a.currency == (reply?.room?.restaurant?.currency ?: "AED")

internal fun GroupController.saveAccount() {
    val a = ReceivingAccount(selectedAccount?.id ?: platform.uuid(), text(GroupFieldKey.ACCOUNT_HOLDER).trim(), if(flag(GroupFieldKey.AANI)) "Aani" else text(GroupFieldKey.ACCOUNT_BANK).trim(), text(GroupFieldKey.ACCOUNT_IDENTIFIER).trim(), "AED", method = if(flag(GroupFieldKey.AANI)) PaymentMethod.AANI else PaymentMethod.BANK).normalized()
    a.validate(); require(library.accounts.size < 20 || library.accounts.any { it.id == a.id }) { "Saved-account limit reached." }
    replaceLibrary(library.copy(accounts = library.accounts.filterNot { it.id == a.id } + a)); selectedAccount = a
}
internal fun GroupController.dispatchRoom(action: GroupAction, value: String) {
    val r = room(); val reason = text(GroupFieldKey.REASON)
    when(action) {
        GroupAction.PARTICIPATE -> command(CommandKind.PARTICIPATE, flag = value == "true")
        GroupAction.READY -> {
            require(r.members.single { it.id == me() }.participating) { "Join this order before marking yourself ready." }
            val member = r.members.single { it.id == me() }
            val declinedThisOrder = r.pastSpins.any { it.winnerId == me() }
            command(CommandKind.READY, flag = true, eligible = member.eligible || !declinedThisOrder)
        }
        GroupAction.APPROVE -> command(CommandKind.APPROVE, memberId = value, flag = flag(GroupFieldKey.ELIGIBLE))
        GroupAction.APPROVE_LATE_JOIN -> command(CommandKind.APPROVE_LATE_JOIN, memberId = value)
        GroupAction.REMOVE -> command(CommandKind.REMOVE, memberId = if (value.startsWith("invite:")) "" else value, name = value.removePrefix("invite:"), text = reason)
        GroupAction.VOTE_RESTAURANT -> command(CommandKind.VOTE_RESTAURANT, text = value)
        GroupAction.FINALIZE_RESTAURANT -> command(CommandKind.FINALIZE_RESTAURANT, text = value)
        GroupAction.SELECT_PAYER -> command(CommandKind.SELECT_PAYER, memberId = text(GroupFieldKey.PAYER_CHOICE).takeIf { choice -> room().orderingMembers.any { it.id == choice } } ?: room().orderingMembers.firstOrNull()?.id.orEmpty())
        GroupAction.PREPARE_SPIN -> command(CommandKind.PREPARE_SPIN)
        GroupAction.ABORT_SPIN -> command(CommandKind.ABORT_PREPARE, text = reason)
        GroupAction.ACCEPT_DUTY -> command(CommandKind.ACCEPT_DUTY)
        GroupAction.DECLINE_DUTY -> command(CommandKind.DECLINE_DUTY, text = reason)
        GroupAction.SUBMIT_CART -> command(CommandKind.SUBMIT_CART, revision = myCart().revision)
        GroupAction.REVIEW -> command(CommandKind.REVIEW)
        GroupAction.CONFIRM_QUOTE -> command(CommandKind.CONFIRM_QUOTE, revision = r.quoteRevision)
        GroupAction.REOPEN -> command(CommandKind.REOPEN, text = reason)
        GroupAction.SET_FEES -> command(CommandKind.SET_FEES, fees = fees(r.restaurant.currency), text = reason)
        GroupAction.PLACE -> command(CommandKind.PLACE, text = text(GroupFieldKey.REFERENCE))
        GroupAction.PAY_RESTAURANT -> command(CommandKind.PAY_RESTAURANT, amount = Money.parse(text(GroupFieldKey.AMOUNT), r.restaurant.currency))
        GroupAction.FULFILL -> command(CommandKind.FULFILL)
        GroupAction.DECLARE_TRANSFER -> command(CommandKind.DECLARE_TRANSFER, amount = Money.parse(text(GroupFieldKey.AMOUNT), r.restaurant.currency), text = text(GroupFieldKey.REFERENCE))
        GroupAction.CONFIRM_TRANSFER -> command(CommandKind.CONFIRM_TRANSFER, transferId = value)
        GroupAction.REJECT_TRANSFER -> command(CommandKind.REJECT_TRANSFER, transferId = value, text = reason)
        GroupAction.DECLARE_REFUND -> command(CommandKind.DECLARE_REFUND, memberId = value, amount = Money.parse(text(GroupFieldKey.AMOUNT), r.restaurant.currency), text = text(GroupFieldKey.REFERENCE))
        GroupAction.CONFIRM_REFUND -> command(CommandKind.CONFIRM_REFUND, transferId = value)
        GroupAction.ADJUST_BILL -> { val amount = text(GroupFieldKey.BILL_ADJUSTMENT).trim(); command(CommandKind.ADJUST_BILL, amount = Money.parse(amount.removePrefix("-"), r.restaurant.currency) * if(amount.startsWith('-')) -1 else 1, text = reason) }
        GroupAction.APPROVE_ADJUSTMENT -> command(CommandKind.APPROVE_ADJUSTMENT, revision = r.billRevision)
        GroupAction.HANDOVER -> command(CommandKind.HANDOVER, memberId = value, text = reason)
        GroupAction.ARCHIVE -> command(CommandKind.ARCHIVE, text = reason)
        GroupAction.CANCEL -> command(CommandKind.CANCEL, text = reason)
        else -> error("Action unavailable.")
    }
}

internal fun GroupController.quickAddMenuItem(itemId: String) {
    val item = room().restaurant.menu.items.single { it.id == itemId }
    require(item.available && item.variants.isEmpty() && item.optionGroupIds.isEmpty()) { "Choose this item's size and extras first." }
    val cart = myCart()
    val existing = cart.lines.firstOrNull { it.itemId == itemId && it.variantId == null && it.optionIds.isEmpty() && it.notes.isEmpty() && it.description.isEmpty() }
    if (existing != null) { changeCartQuantity(existing.id, 1); return }
    val next = cart.copy(lines = cart.lines + CartLine(id = platform.uuid(), itemId = itemId, quantity = 1))
    Billing.lines(room().restaurant, next)
    command(CommandKind.CART, cart = next, revision = cart.revision)
}

internal fun GroupController.changeCartQuantity(lineId: String, delta: Int) {
    val cart = myCart()
    val line = cart.lines.single { it.id == lineId }
    val quantity = line.quantity + delta
    require(quantity in 0..99) { "Choose a quantity between 1 and 99." }
    val lines = if (quantity == 0) cart.lines.filterNot { it.id == lineId }
        else cart.lines.map { if (it.id == lineId) it.copy(quantity = quantity) else it }
    val next = cart.copy(lines = lines)
    Billing.lines(room().restaurant, next)
    command(CommandKind.CART, cart = next, revision = cart.revision)
}
