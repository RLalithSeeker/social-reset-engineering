package com.socialreset.app.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleMatcherTest {
    private val utc = ZoneId.of("UTC")
    private val matcher = ScheduleMatcher { utc }

    @Test
    fun emptyScheduleMeansAlwaysActive() {
        assertTrue(matcher.isAnyActive(emptyList(), epoch("2026-09-06T12:00:00")))
    }

    @Test
    fun midnightWrapCarriesIntoNextDay() {
        val fridayNight = ScheduleRule(
            id = "late",
            label = "Late block",
            daysOfWeek = setOf(5),
            startMinuteOfDay = 22 * 60,
            endMinuteOfDay = 2 * 60,
        )

        assertTrue(matcher.isActive(fridayNight, epoch("2026-09-04T23:30:00")))
        assertTrue(matcher.isActive(fridayNight, epoch("2026-09-05T01:30:00")))
        assertFalse(matcher.isActive(fridayNight, epoch("2026-09-05T03:00:00")))
    }

    private fun epoch(value: String): Long =
        LocalDateTime.parse(value).atZone(utc).toEpochSecond()
}
