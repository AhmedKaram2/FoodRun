package com.karim.foodrun.orders

import kotlinx.serialization.Serializable

@Serializable data class MealSpending(val meal: String, val total: Long)
@Serializable data class SpendingPeriod(
    val period: String, val startDate: String, val endDate: String, val currency: String,
    val paid: Long, val refunds: Long, val total: Long, val meals: List<MealSpending>,
)
@Serializable data class SpendingSummary(val asOf: Long, val timeZone: String, val periods: List<SpendingPeriod>)
