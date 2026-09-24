package com.karim.foodrun.server

import com.karim.foodrun.orders.RoomReply

/** Older apps still receive the selected winner, using their original equal-slice animation. */
internal fun RoomReply.forClient(selectionDetails: Boolean): RoomReply = if (selectionDetails) this else copy(
    room = room?.let { value -> value.copy(lastChosenMemberId = null, lastChosenName = "",
        spin = value.spin?.copy(weights = emptyList()), pastSpins = value.pastSpins.map { it.copy(weights = emptyList()) }) },
)
