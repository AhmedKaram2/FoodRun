package com.karim.foodrun.shared

import kotlin.math.floor

internal class PickupDateFormatter(private val timeZone: FoodRunTimeZone) {
    fun format(timeMillis: Double): String {
        val localSeconds = floor(timeMillis / 1000.0).toLong() + timeZone.offsetSecondsAt(timeMillis)
        val days = floor(localSeconds / SECONDS_PER_DAY.toDouble()).toLong()
        val secondsInDay = localSeconds - days * SECONDS_PER_DAY
        // Gregorian civil date conversion, using March as the first month for leap-day handling.
        val shiftedDays = days + 719468
        val era = (if (shiftedDays >= 0) shiftedDays else shiftedDays - 146096) / 146097
        val dayOfEra = shiftedDays - era * 146097
        val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
        val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        val marchMonth = (5 * dayOfYear + 2) / 153
        val day = dayOfYear - (153 * marchMonth + 2) / 5 + 1
        val month = marchMonth + if (marchMonth < 10) 3 else -9
        return FoodRunText.formatHistoryDate(month.toInt(), day.toInt(), (secondsInDay / 3600).toInt(), (secondsInDay % 3600 / 60).toInt())
    }

    private companion object { const val SECONDS_PER_DAY = 86400L }
}
