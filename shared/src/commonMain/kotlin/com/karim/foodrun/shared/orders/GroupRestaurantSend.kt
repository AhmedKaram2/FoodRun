package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlinx.serialization.Serializable

@Serializable data class GroupRestaurantSendPrompt(val blocker: String = "", val canConfirm: Boolean = false)

/** Confirm exactly the order that was copied or shared; a changed revision needs a new copy. */
internal class GroupRestaurantSend(private val c: GroupController) {
    private var target: Triple<String, Long, Long>? = null
    private fun tr(en: String, ar: String) = if(c.library.language == "ar") ar else en
    fun open() {
        val room = c.room()
        if(c.me() in listOf(room.ownerId,room.payerId) && room.phase in listOf(RoomPhase.COLLECTING, RoomPhase.REVIEW))
            target = Triple(room.id, room.orderNumber, room.revision)
    }
    fun close() { target = null }
    val prompt: GroupRestaurantSendPrompt? get() {
        val selected = target ?: return null
        val room = c.reply?.room
        if(room == null || room.id != selected.first || room.orderNumber != selected.second || c.me() !in listOf(room.ownerId,room.payerId) ||
            room.phase !in listOf(RoomPhase.COLLECTING, RoomPhase.REVIEW)) { close(); return null }
        val blocker = if(room.revision != selected.third) tr("The order changed. Copy or share the latest list before confirming.", "تغيّر الطلب. انسخ القائمة الحالية أو شاركها قبل التأكيد.")
            else c.reply?.progress?.let { if(it.canReview) "" else it.reviewBlocker.ifBlank { tr("Wait for the order to be ready.","انتظر اكتمال الطلب.") } } ?: runCatching { RoomRules.requirePlaceable(room) }.exceptionOrNull()?.message.orEmpty()
        return GroupRestaurantSendPrompt(blocker, blocker.isEmpty())
    }
    fun confirm() {
        val selected = requireNotNull(target) { "Copy or share the restaurant order first." }
        val current = requireNotNull(prompt)
        require(current.canConfirm) { current.blocker }
        c.command(CommandKind.PLACE, revision = selected.third, text = c.text(GroupFieldKey.REFERENCE).trim())
    }
}
