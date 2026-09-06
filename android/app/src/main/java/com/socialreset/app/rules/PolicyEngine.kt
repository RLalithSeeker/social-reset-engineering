package com.socialreset.app.rules

import com.socialreset.app.core.time.TimeSource

/**
 * The deterministic gate. Everything about whether an app is blocked right now is decided
 * here, from local state only — no network, no AI. The behaviour engine may later choose
 * *how hard* to intervene, but never *whether* the block applies.
 */
class PolicyEngine(
    private val timeSource: TimeSource,
    private val scheduleMatcher: ScheduleMatcher = ScheduleMatcher(),
) {

    sealed interface Decision {
        /** Not configured, not scheduled, or covered by a live unlock window. */
        data class Allow(val reason: String) : Decision

        /** Blocked; the behaviour engine picks the intervention level. */
        data class Intervene(val app: BlockedApp) : Decision
    }

    fun evaluate(
        packageName: String,
        rules: List<BlockedApp>,
        activeUnlockPackage: String?,
    ): Decision {
        val app = rules.firstOrNull { it.packageName == packageName && it.enabled }
            ?: return Decision.Allow("not configured")

        if (!scheduleMatcher.isAnyActive(app.schedules, timeSource.wallClockSeconds())) {
            return Decision.Allow("outside schedule")
        }

        // The unlock window is checked last so that an unlock for a different app can never
        // leak into this one.
        if (activeUnlockPackage == packageName) {
            return Decision.Allow("unlock window active")
        }

        return Decision.Intervene(app)
    }
}
