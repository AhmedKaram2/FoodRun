package com.karim.foodrun.orders

import kotlinx.serialization.Serializable

@Serializable data class NotificationPreferences(val pushEnabled: Boolean = true, val emailEnabled: Boolean = true)
