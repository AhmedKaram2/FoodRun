package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.Money
import com.karim.foodrun.orders.RestaurantExport
import com.karim.foodrun.orders.Room

/** Keeps restaurant defaults separate from the order form that opened the editor. */
internal class GroupDraftMemory {
    private var restaurantReturnFees: Map<GroupFieldKey, String?>? = null
    private val feeKeys = listOf(
        GroupFieldKey.DELIVERY_FEE, GroupFieldKey.SERVICE_FEE,
        GroupFieldKey.DISCOUNT, GroupFieldKey.PROPORTIONAL, GroupFieldKey.AUTOMATIC_DELIVERY,
    )

    fun beginRestaurant(draft: Map<GroupFieldKey, String>) {
        if (restaurantReturnFees == null) restaurantReturnFees = feeKeys.associateWith { draft[it] }
    }

    fun finishRestaurant(draft: MutableMap<GroupFieldKey, String>) {
        restaurantReturnFees?.forEach { (key, value) ->
            if (value == null) draft.remove(key) else draft[key] = value
        }
        restaurantReturnFees = null
    }

    fun acceptRoomFees(room: Room, page: GroupPage, libraryReturnPage: GroupPage, draft: MutableMap<GroupFieldKey, String>) {
        val fees = mapOf(
            GroupFieldKey.DELIVERY_FEE to Money.format(room.fees.delivery, room.restaurant.currency).substringAfter(' '),
            GroupFieldKey.SERVICE_FEE to Money.format(room.fees.service, room.restaurant.currency).substringAfter(' '),
            GroupFieldKey.DISCOUNT to Money.format(room.fees.discount, room.restaurant.currency).substringAfter(' '),
            GroupFieldKey.PROPORTIONAL to room.fees.proportionalDelivery.toString(),
            GroupFieldKey.AUTOMATIC_DELIVERY to room.fees.automaticDelivery.toString(),
        )
        when {
            page == GroupPage.SETUP -> Unit
            page in listOf(GroupPage.RESTAURANT, GroupPage.MENU_EDITOR, GroupPage.MENU_ENTITY, GroupPage.LIBRARY) && libraryReturnPage == GroupPage.SETUP -> Unit
            page in listOf(GroupPage.RESTAURANT, GroupPage.MENU_EDITOR, GroupPage.MENU_ENTITY) -> restaurantReturnFees = fees
            else -> draft.putAll(fees)
        }
    }
}

/** Local edits become visible only when the encrypted library has accepted the write. */
internal fun GroupController.replaceLibrary(next: GroupLibrary) {
    val previous = library
    library = next
    try { persist() } catch (failure: Exception) {
        library = previous
        throw failure
    }
}

internal fun GroupController.deleteRestaurant(id: String) {
    replaceLibrary(library.copy(restaurants = library.restaurants.filterNot { it.restaurant.id == id }))
    if (selectedRestaurant?.restaurant?.id == id) selectedRestaurant = null
}

internal fun GroupController.deleteAccount(id: String) {
    replaceLibrary(library.copy(accounts = library.accounts.filterNot { it.id == id }))
    if (selectedAccount?.id == id) {
        selectedAccount = null
        listOf(GroupFieldKey.ACCOUNT_HOLDER, GroupFieldKey.ACCOUNT_BANK, GroupFieldKey.ACCOUNT_IDENTIFIER, GroupFieldKey.ACCOUNT_IBAN_DRAFT, GroupFieldKey.ACCOUNT_AANI_DRAFT).forEach(draft::remove)
    }
}

internal fun GroupController.newAccount() {
    selectedAccount = null
    listOf(GroupFieldKey.ACCOUNT_HOLDER, GroupFieldKey.ACCOUNT_BANK, GroupFieldKey.ACCOUNT_IDENTIFIER, GroupFieldKey.ACCOUNT_IBAN_DRAFT, GroupFieldKey.ACCOUNT_AANI_DRAFT).forEach(draft::remove)
    page = GroupPage.ACCOUNT
}

internal fun GroupController.prepareNextOrder() {
    val previous = room()
    nextOrder = true
    choosingPollRestaurants = false
    pollRestaurantIds.clear()
    draft[GroupFieldKey.RESTAURANT_POLL] = "false"
    selectedRestaurant = RestaurantExport(exportId = previous.restaurant.id, restaurant = previous.restaurant)
    seedFees(previous.restaurant)
    draft[GroupFieldKey.DELIVERY] = previous.deliveryMode.toString()
    draft[GroupFieldKey.DESTINATION] = previous.destination
    draft[GroupFieldKey.EXPECTED_NAMES] = previous.expectedNames.joinToString(", ")
    draft[GroupFieldKey.PROPORTIONAL] = previous.fees.proportionalDelivery.toString()
    page = GroupPage.SETUP
}

internal fun GroupController.acceptFinancialDrafts(previous: Room?, room: Room, changedOrder: Boolean) {
    fun amount(value: Long) = Money.format(value, room.restaurant.currency).substringAfter(' ')
    val changedBill = previous?.billRevision != room.billRevision
    if (changedOrder || changedBill) draft[GroupFieldKey.BILL_ADJUSTMENT] = amount(room.adjustment)
    val changedPhase = previous?.phase != room.phase
    if (changedOrder || changedPhase) draft.remove(GroupFieldKey.REFERENCE)
    val payer = room.payerId == me()
    val relevantTransfersChanged = if (payer) previous?.transfers != room.transfers
        else previous?.transfers?.filter { it.memberId == me() } != room.transfers.filter { it.memberId == me() }
    val restaurantPaymentChanged = previous == null || previous.restaurantPaid != room.restaurantPaid
    if (changedOrder || changedPhase || changedBill || restaurantPaymentChanged || relevantTransfersChanged) {
        val visibleReceipts = requireNotNull(reply).receipts
        val due = when {
            payer && !room.restaurantPaid -> visibleReceipts.sumOf { it.total }
            payer -> visibleReceipts.firstOrNull { GroupSettlementPresentation.refundAvailable(room, it) }?.balance?.let { -it }
            else -> visibleReceipts.firstOrNull { it.memberId == me() }?.balance?.coerceAtLeast(0)
        }
        draft[GroupFieldKey.AMOUNT] = due?.let(::amount).orEmpty()
    }
}
