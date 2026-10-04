package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

internal class GroupSelectionOverride(private val c: GroupController) {
    private var unlocked = false
    private var epoch = 0
    private var order: Pair<String, Long>? = null
    private var message = ""
    val available: Boolean get() = c.administration.verifiedAccess && c.library.identityToken.isNotEmpty() &&
        c.sameHub(c.library.identityHub, c.session?.hub) && c.reply?.room?.let { it.phase == RoomPhase.LOBBY && it.paymentRoom == null } == true
    private fun tr(en: String, ar: String) = if (c.library.language == "ar") ar else en
    fun clear() {
        epoch++; unlocked = false; order = null; message = ""
        c.draft.remove(GroupFieldKey.SELECTION_PASSCODE); c.draft.remove(GroupFieldKey.SELECTION_WINNER)
    }
    fun close() { clear(); c.page = GroupPage.ROOM }
    fun open() {
        require(available && c.page == GroupPage.ROOM) { "This account cannot change the wheel selection." }
        require(c.library.pending == null) { "Retry the saved request before making another change." }
        clear(); order = c.room().let { it.id to it.orderNumber }; c.page = GroupPage.SELECTION_OVERRIDE
    }
    fun unlock() = request(CommandKind.UNLOCK_SELECTION_OVERRIDE, text = c.text(GroupFieldKey.SELECTION_PASSCODE))
    fun save() {
        require(unlocked) { "Enter the passcode first." }
        request(CommandKind.SET_SELECTION_OVERRIDE, memberId = c.text(GroupFieldKey.SELECTION_WINNER))
    }
    private fun request(kind: CommandKind, text: String = "", memberId: String = "") {
        val room = c.room(); val session = requireNotNull(c.session)
        require(available && c.page == GroupPage.SELECTION_OVERRIDE && order == (room.id to room.orderNumber)) { "The order changed. Open this option again." }
        require(c.library.pending == null) { "Retry the saved request before making another change." }
        val identity = c.library.identityToken; val generation = epoch
        val command = RoomCommand(commandId = c.platform.uuid(), kind = kind, roomId = room.id, token = session.token,
            identityToken = identity, expectedRevision = room.revision, expectedOrderNumber = room.orderNumber,
            text = text, memberId = memberId, selectionDetails = true, visualSelectionDetails = true, liveRoomDetails = true)
        c.draft.remove(GroupFieldKey.SELECTION_PASSCODE)
        c.busy = true; message = ""; c.publish()
        // Do not save the passcode or private selection into the durable pending-command queue.
        try { c.platform.request(session.hub, orderJson.encodeToString(command), object : GroupReplyCallback {
            override fun complete(body: String, error: String) {
                c.busy = false
                if (generation != epoch || identity != c.library.identityToken || c.session != session) { c.publish(); return }
                try {
                    require(error.isEmpty()) { error }
                    val reply = orderJson.decodeFromString<RoomReply>(body)
                    require(reply.ok) { reply.error }
                    if (kind == CommandKind.UNLOCK_SELECTION_OVERRIDE) {
                        require(reply.code == "SELECTION_OVERRIDE_UNLOCKED"); unlocked = true
                    } else {
                        require(reply.code == "SELECTION_OVERRIDE_SAVED")
                        message = if (memberId.isEmpty()) tr("Random selection restored.", "تمت استعادة الاختيار العشوائي.")
                        else tr("Saved for the next spin. The wheel will select this person once.", "تم الحفظ للدورة القادمة. ستختار العجلة هذا الشخص مرة واحدة.")
                    }
                } catch (failure: Exception) { c.error = failure.message.orEmpty() }
                c.publish()
            }
        }) } catch (failure: Exception) { c.busy = false; c.error = failure.message.orEmpty(); c.publish() }
    }
    fun content(): GroupFlowContent {
        val candidates = c.reply?.room?.orderingMembers.orEmpty().filter { it.eligible }
        val fields = if (!unlocked) listOf(GroupField(GroupFieldKey.SELECTION_PASSCODE, tr("Passcode", "رمز الدخول"), c.text(GroupFieldKey.SELECTION_PASSCODE), secret = true))
        else listOf(GroupField(GroupFieldKey.SELECTION_WINNER, tr("Person for the next spin", "الشخص للدورة القادمة"), c.text(GroupFieldKey.SELECTION_WINNER),
            choices = listOf(GroupChoice("", tr("Random selection", "اختيار عشوائي"))) + candidates.map { GroupChoice(it.id, it.name) }))
        return GroupFlowContent(fields = fields, cards = if (message.isEmpty()) emptyList() else listOf(GroupCard("selection-status", message)),
            buttons = listOf(GroupButton(if (unlocked) tr("Save", "حفظ") else tr("Unlock", "فتح"),
                if (unlocked) GroupAction.SAVE_SELECTION_OVERRIDE else GroupAction.UNLOCK_SELECTION_OVERRIDE, primary = true,
                enabled = available && if (unlocked) c.text(GroupFieldKey.SELECTION_WINNER).let { it.isEmpty() || candidates.any { candidate -> candidate.id == it } }
                else c.text(GroupFieldKey.SELECTION_PASSCODE).length == 4)))
    }
}
