package com.socialreset.app.rules

/**
 * One app the user chose to put behind the reset flow.
 *
 * `policyId` is stable and is what an unlock grant binds to, so editing a rule (new
 * policyId) invalidates grants issued against the old one.
 */
data class BlockedApp(
    val packageName: String,
    val label: String,
    val policyId: String,
    val enabled: Boolean = true,
    /** Empty means "always on"; otherwise the rule is active only inside these windows. */
    val schedules: List<ScheduleRule> = emptyList(),
    /** Unlock length this app's grants may request, capped again at validation time. */
    val unlockSeconds: Long = 10 * 60,
)
