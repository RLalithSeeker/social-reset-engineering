package com.socialreset.app.rules

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * Evaluates schedules against the current local clock.
 *
 * Called on every enforcement event rather than driven by alarms: exact alarms need
 * special access on Android 12+, and a missed alarm would silently disable a block. An
 * alarm may still be used to refresh UI at a boundary, but it is never the source of
 * truth.
 */
class ScheduleMatcher(private val zoneId: () -> ZoneId = { ZoneId.systemDefault() }) {

    fun isActive(rule: ScheduleRule, atEpochSeconds: Long): Boolean {
        if (!rule.enabled) return false
        val local = Instant.ofEpochSecond(atEpochSeconds).atZone(zoneId())
        val minuteOfDay = local.hour * 60 + local.minute
        val today = local.dayOfWeek.value

        return if (!rule.wrapsMidnight) {
            today in rule.daysOfWeek && minuteOfDay >= rule.startMinuteOfDay && minuteOfDay < rule.endMinuteOfDay
        } else {
            // Wrapping window: the tail after midnight belongs to the previous day's rule.
            val yesterday = DayOfWeek.of(today).minus(1).value
            (today in rule.daysOfWeek && minuteOfDay >= rule.startMinuteOfDay) ||
                (yesterday in rule.daysOfWeek && minuteOfDay < rule.endMinuteOfDay)
        }
    }

    /** A rule set with no schedules means always active. */
    fun isAnyActive(rules: List<ScheduleRule>, atEpochSeconds: Long): Boolean =
        rules.isEmpty() || rules.any { isActive(it, atEpochSeconds) }
}
