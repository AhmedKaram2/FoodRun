package com.karim.foodrun.orders

import kotlinx.serialization.Serializable

/** Notification routes never carry credentials or change an order/payment by themselves. */
@Serializable data class FoodNotification(
    val id: String, val roomId: String, val orderNumber: Long, val kind: String,
    val title: String, val body: String, val actions: List<NotificationAction> = emptyList(),
    val transferId: String = "", val createdAt: Long, val read: Boolean = false,
)
@Serializable data class NotificationAction(val id: String, val title: String)
@Serializable data class NotificationRequest(
    val identityToken: String, val action: String = "list", val notificationId: String = "",
    val token: String = "", val platform: String = "", val installationId: String = "", val language: String = "en",
)
@Serializable data class NotificationReply(
    val ok: Boolean = true, val error: String = "", val notifications: List<FoodNotification> = emptyList(),
    val pushAvailable: Boolean = false,
)
