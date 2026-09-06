package com.socialreset.app.rules

/**
 * A recurring local-time window, e.g. Mon-Fri 09:00-17:00.
 *
 * Minutes-since-midnight rather than timestamps, because the user means "9am wherever I
 * am", not "9am in the timezone I set this up in". Windows that wrap past midnight are
 * expressed with end < start and handled by [ScheduleMatcher].
 */
data class ScheduleRule(
    val id: String,
    val label: String,
    /** java.time.DayOfWeek values, 1 = Monday .. 7 = Sunday. */
    val daysOfWeek: Set<Int>,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val enabled: Boolean = true,
) {
    val wrapsMidnight: Boolean get() = endMinuteOfDay <= startMinuteOfDay

    init {
        require(startMinuteOfDay in 0..1439) { "startMinuteOfDay out of range" }
        require(endMinuteOfDay in 0..1439) { "endMinuteOfDay out of range" }
    }
}
