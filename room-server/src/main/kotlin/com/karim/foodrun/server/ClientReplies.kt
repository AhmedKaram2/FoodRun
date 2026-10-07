package com.karim.foodrun.server

import com.karim.foodrun.orders.RoomReply
import com.karim.foodrun.orders.Receipt

private fun Receipt.withoutHalfDetails(): Receipt = copy(lines = lines.map {
    it.copy(quantity = it.restaurantQuantity ?: it.quantity, halfShare = false, restaurantQuantity = null)
})

/** Preserve the wire schema accepted by each released generation of native clients. */
internal fun RoomReply.forClient(selectionDetails: Boolean, visualSelectionDetails: Boolean = false, liveRoomDetails: Boolean = false, multiplePaymentDetails: Boolean = false, wheelProtectionDetails: Boolean = false, autoArchiveDetails: Boolean = false, walletDetails: Boolean = false, halfItemDetails: Boolean = false): RoomReply = copy(
    receipts = if (halfItemDetails) receipts else receipts.map { it.withoutHalfDetails() },
    history = if (halfItemDetails) history else history.map { it.copy(receipts = it.receipts.map { receipt -> receipt.withoutHalfDetails() }) },
    walletRecipient = walletRecipient.takeIf { walletDetails }, walletPeople = walletPeople.takeIf { walletDetails },
    home = home?.let { it.copy(profile = if (multiplePaymentDetails) it.profile else it.profile.copy(paymentAccounts = emptyList()),
        wallet = it.wallet.takeIf { walletDetails },
        rooms = it.rooms.map { member -> member.copy(phase = member.phase.takeIf { liveRoomDetails }, orderNumber = if (liveRoomDetails) member.orderNumber else 0, paymentsPending = autoArchiveDetails && member.paymentsPending) }) },
    room = room?.let { value -> value.copy(
        accounts = if (multiplePaymentDetails) value.accounts else emptyList(),
        wheelProtections = if (wheelProtectionDetails) value.wheelProtections else emptyList(),
        wheelProtectionAccounts = if (wheelProtectionDetails) value.wheelProtectionAccounts else emptyList(),
        orderCreatedAt = if (autoArchiveDetails) value.orderCreatedAt else 0,
        autoArchivedAt = if (autoArchiveDetails) value.autoArchivedAt else 0,
        autoArchiveFrom = if (autoArchiveDetails) value.autoArchiveFrom else null,
        paymentsPending = autoArchiveDetails && value.paymentsPending,
        walletPayments = if(walletDetails) value.walletPayments else emptyList(),
        halfItemOffers = if (halfItemDetails) value.halfItemOffers else emptyList(),
        selectionStyle = if(visualSelectionDetails) value.selectionStyle else "wheel",
        lastChosenMemberId = value.lastChosenMemberId.takeIf { selectionDetails },
        lastChosenName = if(selectionDetails) value.lastChosenName else "",
        spin = value.spin?.let { if(selectionDetails && (wheelProtectionDetails || it.weights.all { weight -> weight <= 100 })) it else it.copy(weights = emptyList()) },
        pastSpins = value.pastSpins.map { if(selectionDetails && (wheelProtectionDetails || it.weights.all { weight -> weight <= 100 })) it else it.copy(weights = emptyList()) },
    ) },
)
