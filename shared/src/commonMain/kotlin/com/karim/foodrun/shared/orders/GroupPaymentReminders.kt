package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

internal fun GroupController.paymentReminderButtons(room: Room, actorId: String, receipt: Receipt, wallet: Boolean = false): List<GroupButton> {
    if (!PaymentReminderRules.eligible(room, actorId, receipt)) return emptyList()
    val queued = paymentReminderTimes[PaymentReminderRules.key(room, receipt.memberId)]?.let {
        platform.now() - it < PaymentReminderRules.COOLDOWN_MS
    } == true
    val title = if (library.language == "ar") {
        if (queued) "تذكير الإيميل في انتظار الإرسال" else "ابعت تذكير بالدفع بالإيميل"
    } else if (queued) "Email reminder queued" else "Send payment reminder"
    return listOf(GroupButton(title, if (wallet) GroupAction.WALLET_REMIND_PAYMENT else GroupAction.REMIND_PAYMENT,
        if (wallet) "${room.id}|${receipt.memberId}" else receipt.memberId, enabled = !busy && !queued && (wallet || online)))
}
