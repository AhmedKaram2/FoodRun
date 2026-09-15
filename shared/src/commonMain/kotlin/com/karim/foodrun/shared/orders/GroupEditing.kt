package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

internal fun GroupController.restaurantEditor(export: RestaurantExport?) {
    formDrafts.beginRestaurant(draft)
    editingRestaurant = export ?: RestaurantExport(exportId = platform.uuid(), restaurant = Restaurant(platform.uuid(), "", menu = Menu(categories = listOf(MenuCategory("main", "Menu")))))
    val r = editingRestaurant!!.restaurant
    draft[GroupFieldKey.RESTAURANT_NAME] = r.name; draft[GroupFieldKey.BRANCH] = r.branchName
    draft[GroupFieldKey.CURRENCY] = r.currency; draft[GroupFieldKey.PHONE] = r.contact.phoneE164 ?: ""
    draft[GroupFieldKey.ADDRESS] = r.contact.address ?: ""; seedFees(r); page = GroupPage.RESTAURANT
}
internal fun GroupController.seedFees(r: Restaurant) {
    fun amount(v: Long) = Money.format(v, r.currency).substringAfter(' ')
    draft[GroupFieldKey.DELIVERY_FEE] = amount(r.pricing.defaultDeliveryFeeMinor)
    draft[GroupFieldKey.SERVICE_FEE] = amount(r.pricing.defaultServiceFeeMinor)
    draft[GroupFieldKey.DISCOUNT] = "0"
}
internal fun GroupController.fees(currency: String): FeePolicy = FeePolicy(
    Money.parse(text(GroupFieldKey.DELIVERY_FEE).ifBlank { "0" }, currency), Money.parse(text(GroupFieldKey.SERVICE_FEE).ifBlank { "0" }, currency), Money.parse(text(GroupFieldKey.DISCOUNT).ifBlank { "0" }, currency), flag(GroupFieldKey.PROPORTIONAL),
)
internal fun GroupController.addMenuItem() {
    val export = requireNotNull(editingRestaurant)
    val name = text(GroupFieldKey.MENU_ITEM_NAME).trim(); MenuValidation.label(name)
    val currency = text(GroupFieldKey.CURRENCY).uppercase()
    val item = MenuItem(platform.uuid(), export.restaurant.menu.categories.first().id, name, basePriceMinor = Money.parse(text(GroupFieldKey.MENU_ITEM_PRICE), currency))
    editingRestaurant = export.copy(restaurant = export.restaurant.copy(menu = export.restaurant.menu.copy(items = export.restaurant.menu.items + item)))
    draft[GroupFieldKey.MENU_ITEM_NAME] = ""; draft[GroupFieldKey.MENU_ITEM_PRICE] = ""
}
internal fun GroupController.saveEditor() {
    val export = requireNotNull(editingRestaurant); val currency = text(GroupFieldKey.CURRENCY).trim().uppercase()
    val r = export.restaurant.copy(name = text(GroupFieldKey.RESTAURANT_NAME).trim(), branchName = text(GroupFieldKey.BRANCH).trim(), currency = currency,
        contact = export.restaurant.contact.copy(phoneE164 = text(GroupFieldKey.PHONE).trim().ifEmpty { null }, address = text(GroupFieldKey.ADDRESS).trim().ifEmpty { null }),
        pricing = export.restaurant.pricing.copy(defaultDeliveryFeeMinor = fees(currency).delivery, defaultServiceFeeMinor = fees(currency).service))
    MenuValidation.validate(r); saveRestaurant(export.copy(restaurant = r, revision = export.revision + 1))
    formDrafts.finishRestaurant(draft); page = GroupPage.LIBRARY
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
    val line = CartLine(platform.uuid(), item.id, quantity, variant, options, text(GroupFieldKey.NOTE))
    val next = cart.copy(lines = cart.lines + line); Billing.lines(room().restaurant, next)
    command(CommandKind.CART, cart = next, revision = cart.revision); page = GroupPage.ROOM
}
internal fun GroupController.seedAccount(a: ReceivingAccount) {
    draft[GroupFieldKey.ACCOUNT_HOLDER] = a.holder; draft[GroupFieldKey.ACCOUNT_BANK] = a.bank; draft[GroupFieldKey.ACCOUNT_IDENTIFIER] = a.identifier
}
internal fun GroupController.saveAccount() {
    val a = ReceivingAccount(selectedAccount?.id ?: platform.uuid(), text(GroupFieldKey.ACCOUNT_HOLDER).trim(), text(GroupFieldKey.ACCOUNT_BANK).trim(), text(GroupFieldKey.ACCOUNT_IDENTIFIER).trim(), reply?.room?.restaurant?.currency ?: "AED")
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
        GroupAction.ADJUST_BILL -> { val amount = text(GroupFieldKey.AMOUNT); command(CommandKind.ADJUST_BILL, amount = Money.parse(amount.removePrefix("-"), r.restaurant.currency) * if(amount.startsWith('-')) -1 else 1, text = reason) }
        GroupAction.APPROVE_ADJUSTMENT -> command(CommandKind.APPROVE_ADJUSTMENT, revision = r.billRevision)
        GroupAction.HANDOVER -> command(CommandKind.HANDOVER, memberId = value, text = reason)
        GroupAction.ARCHIVE -> command(CommandKind.ARCHIVE, text = reason)
        GroupAction.CANCEL -> command(CommandKind.CANCEL, text = reason)
        else -> error("Action unavailable.")
    }
}
