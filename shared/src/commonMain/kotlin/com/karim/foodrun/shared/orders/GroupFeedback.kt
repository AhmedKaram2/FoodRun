package com.karim.foodrun.shared.orders

data class GroupFeedback(val id: Long, val message: String, val isError: Boolean, val durationMillis: Long, val expiresAt: Long)

internal class GroupFeedbacks(private val clock: () -> Long) {
    private var sequence = 0L
    private var message: GroupFeedback? = null
    val current: GroupFeedback? get() = message?.takeIf { clock() < it.expiresAt }
    fun show(text: String, isError: Boolean) {
        val duration = if (isError) 7_000L else 4_000L
        message = GroupFeedback(++sequence, text, isError, duration, clock() + duration)
    }
    fun clearError() { if (message?.isError == true) message = null }
    fun dismiss(id: Long) { if (message?.id == id) message = null }
    fun clear() { message = null }
}
