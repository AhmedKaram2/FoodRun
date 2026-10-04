package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

internal class GroupPaymentReminders(private val c: GroupController) {
    private val states = mutableMapOf<String, String>()
    private val commands = mutableMapOf<String, RoomCommand>()
    private val checking = mutableSetOf<String>()
    private var recipientCommand: RoomCommand? = null
    private var returnPage = GroupPage.ROOM
    val hasPending: Boolean get() = states.values.any { it == "pending" }
    val prompt: GroupReminderEmailPrompt? get() = recipientCommand?.let { command ->
        val room = c.library.snapshots[command.roomId]?.room ?: c.reply?.room
        GroupReminderEmailPrompt(room?.members?.firstOrNull { it.id == command.memberId }?.name.orEmpty(), c.text(GroupFieldKey.REMINDER_EMAIL), GroupText.localized(c.error, c.library.language == "ar"))
    }
    fun state(key: String) = states[key]
    fun clear() { states.clear(); commands.clear(); checking.clear(); c.paymentReminderTimes.clear(); dismiss() }
    fun dismiss() { recipientCommand = null; c.draft.remove(GroupFieldKey.REMINDER_EMAIL) }
    fun ask(command: RoomCommand, page: GroupPage) { recipientCommand = command; returnPage = page; c.draft.remove(GroupFieldKey.REMINDER_EMAIL) }
    fun sendToEmail() {
        val command = requireNotNull(recipientCommand)
        val address = c.text(GroupFieldKey.REMINDER_EMAIL).trim()
        require(address.length in 3..254 && address.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) { "Enter a valid email address." }
        c.send(command.copy(commandId = c.platform.uuid(), text = address), returnPage = returnPage)
    }
    fun accept(command: RoomCommand, code: String) {
        val key = "${command.roomId}:${command.expectedOrderNumber}:${command.memberId}"
        val state = when (code) { "REMINDER_SENT" -> "sent"; "REMINDER_QUEUED", "REMINDER_PENDING" -> "pending"; else -> "failed" }
        states[key] = state
        if (state == "failed") { c.paymentReminderTimes.remove(key); c.error = "Email could not be sent. Please try again." }
        else c.paymentReminderTimes.getOrPut(key) { c.platform.now() }
        if (state == "sent") c.success("Email reminder sent")
        if (state == "pending") commands[key] = command.copy(text = "") else commands.remove(key)
        if (state != "failed" && recipientCommand?.let { it.roomId == command.roomId && it.memberId == command.memberId } == true) dismiss()
    }
    fun tick() {
        commands.toMap().forEach { (key, command) ->
            if (!checking.add(key)) return@forEach
            val session = c.library.sessions.firstOrNull { it.roomId == command.roomId }
            if (session == null) { commands.remove(key); states.remove(key); checking.remove(key); return@forEach }
            val identity = c.library.identityToken
            val status = command.copy(commandId = c.platform.uuid(), kind = CommandKind.PAYMENT_REMINDER_STATUS, text = "")
            c.platform.request(session.hub, orderJson.encodeToString(status), object : GroupReplyCallback {
                override fun complete(body: String, error: String) {
                    checking.remove(key)
                    if (identity != c.library.identityToken || commands[key] != command) return
                    if (error.isEmpty()) runCatching {
                        val reply = orderJson.decodeFromString<RoomReply>(body)
                        if (reply.ok) accept(command, reply.code)
                        else { states[key] = "failed"; commands.remove(key); c.paymentReminderTimes.remove(key) }
                    }
                    c.publish()
                }
            })
        }
    }
}

internal fun GroupController.paymentReminderButtons(room: Room, actorId: String, receipt: Receipt, wallet: Boolean = false): List<GroupButton> {
    if (!PaymentReminderRules.eligible(room, actorId, receipt)) return emptyList()
    val key = PaymentReminderRules.key(room, receipt.memberId)
    val status = paymentReminders.state(key)
    val queued = paymentReminderTimes[key]?.let {
        platform.now() - it < PaymentReminderRules.COOLDOWN_MS
    } == true
    val title = if (library.language == "ar") {
        if (queued && status == "sent") "تم إرسال تذكير الإيميل" else if (queued) "جاري إرسال تذكير الإيميل…" else "ابعت تذكير بالدفع بالإيميل"
    } else if (queued && status == "sent") "Email reminder sent" else if (queued) "Sending email reminder…" else "Send payment reminder"
    return listOf(GroupButton(title, if (wallet) GroupAction.WALLET_REMIND_PAYMENT else GroupAction.REMIND_PAYMENT,
        if (wallet) "${room.id}|${receipt.memberId}" else receipt.memberId, enabled = !busy && !queued && (wallet || online)))
}

internal fun GroupController.recordPaymentButtons(room: Room, actorId: String, receipt: Receipt, wallet: Boolean = false): List<GroupButton> {
    if (!PaymentReminderRules.eligible(room, actorId, receipt)) return emptyList()
    return listOf(GroupButton(if (library.language == "ar") "تسجيل دفعة مستلمة" else "Record payment received",
        if (wallet) GroupAction.WALLET_RECORD_PAYMENT else GroupAction.RECORD_PAYMENT,
        if (wallet) "${room.id}|${receipt.memberId}" else receipt.memberId, enabled = !busy && (wallet || online)))
}
