package com.karim.foodrun.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class PickupDateFormatterTest {
    @Test fun formatsEpochLeapDayAndNegativeEpochInCommonCode() {
        val formatter = PickupDateFormatter(UtcFoodRunTimeZone())
        assertEquals("Jan 1, 12:00 AM", formatter.format(0.0))
        assertEquals("Dec 31, 11:59 PM", formatter.format(-1000.0))
        assertEquals("Feb 29, 12:00 AM", formatter.format(1709164800000.0))
        assertEquals("Mar 1, 12:00 AM", formatter.format(1709251200000.0))
    }

    @Test fun formatsLocalDateAcrossMidnightAndFractionalHourZones() {
        val formatter = PickupDateFormatter(object : FoodRunTimeZone {
            override fun offsetSecondsAt(timeMillis: Double) = 5 * 3600 + 45 * 60
        })
        assertEquals("Jan 1, 5:45 AM", formatter.format(0.0))
        assertEquals("Jan 2, 12:15 AM", formatter.format(18.5 * 3600000))
    }

    @Test fun usesEachPickupsHistoricalZoneOffsetForDaylightSaving() {
        val formatter = PickupDateFormatter(object : FoodRunTimeZone {
            override fun offsetSecondsAt(timeMillis: Double) = if (timeMillis < 1710054000000.0) -5 * 3600 else -4 * 3600
        })
        assertEquals("Mar 10, 1:59 AM", formatter.format(1710053940000.0))
        assertEquals("Mar 10, 3:00 AM", formatter.format(1710054000000.0))
    }
}
