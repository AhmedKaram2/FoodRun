package com.karim.foodrun.server

import com.karim.foodrun.orders.RoomReply

/** Preserve the wire schema accepted by each released generation of native clients. */
internal fun RoomReply.forClient(selectionDetails: Boolean, visualSelectionDetails: Boolean = false, liveRoomDetails: Boolean = false, multiplePaymentDetails: Boolean = false): RoomReply = copy(
    home = home?.let { it.copy(profile = if (multiplePaymentDetails) it.profile else it.profile.copy(paymentAccounts = emptyList()),
        rooms = if (liveRoomDetails) it.rooms else it.rooms.map { member -> member.copy(phase = null, orderNumber = 0) }) },
    room = room?.let { value -> value.copy(
        accounts = if (multiplePaymentDetails) value.accounts else emptyList(),
        selectionStyle = if(visualSelectionDetails) value.selectionStyle else "wheel",
        lastChosenMemberId = value.lastChosenMemberId.takeIf { selectionDetails },
        lastChosenName = if(selectionDetails) value.lastChosenName else "",
        spin = value.spin?.let { if(selectionDetails) it else it.copy(weights = emptyList()) },
        pastSpins = value.pastSpins.map { if(selectionDetails) it else it.copy(weights = emptyList()) },
    ) },
)
