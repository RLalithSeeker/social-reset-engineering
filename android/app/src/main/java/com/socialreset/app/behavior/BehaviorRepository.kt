package com.socialreset.app.behavior

/**
 * Bounded local history. Retention is a fixed horizon (default 24h) so the store cannot
 * grow without limit and so a habit score reflects today, not last month.
 */
interface BehaviorRepository {
    suspend fun record(event: BehaviorEvent)
    suspend fun recentFor(packageName: String, sinceSeconds: Long): List<BehaviorEvent>
    suspend fun pruneOlderThan(cutoffSeconds: Long)
}

/** Turns raw events into the fixed feature vector the scorer consumes. */
class FeatureExtractor(private val horizonSeconds: Long = 24 * 60 * 60) {

    fun extract(
        events: List<BehaviorEvent>,
        packageName: String,
        nowSeconds: Long,
        scheduledBlockActive: Boolean,
        currentSessionSeconds: Long,
        intentionActive: Boolean,
    ): BehaviorFeatures {
        val relevant = events
            .filter { it.packageName == packageName && nowSeconds - it.timestampSeconds <= horizonSeconds }
            .sortedBy { it.timestampSeconds }
        val opens = relevant.filter { it.kind == BehaviorEvent.Kind.FOREGROUND }
        val unlocks = relevant.filter { it.kind == BehaviorEvent.Kind.UNLOCK_GRANTED }

        val durations = opens.mapNotNull { it.durationSeconds }.sorted()
        val median = if (durations.isEmpty()) 0L else durations[durations.size / 2]

        val lastUnlockAt = unlocks.lastOrNull()?.timestampSeconds
        val reopensAfterUnlock = lastUnlockAt != null && opens.any { it.timestampSeconds > lastUnlockAt }

        var reopensUnder60s = 0
        opens.zipWithNext { previous, next ->
            val gap = next.timestampSeconds - (previous.timestampSeconds + (previous.durationSeconds ?: 0))
            if (gap in 0..59) reopensUnder60s++
        }

        return BehaviorFeatures(
            opens10m = opens.count { nowSeconds - it.timestampSeconds <= 600 },
            opens30m = opens.count { nowSeconds - it.timestampSeconds <= 1800 },
            medianRecentSessionSeconds = median,
            currentSessionSeconds = currentSessionSeconds,
            reopensUnder60s = reopensUnder60s,
            minutesSinceLastSession = opens.lastOrNull()
                ?.let { (nowSeconds - it.timestampSeconds) / 60 }
                ?: NEVER_OPENED_MINUTES,
            recentUnlockCount = unlocks.count { nowSeconds - it.timestampSeconds <= 3600 },
            sameAppReopensAfterUnlock = reopensAfterUnlock,
            scheduledBlockActive = scheduledBlockActive,
            intentionActive = intentionActive,
        )
    }

    private companion object {
        /** Sentinel for "no prior session in the retention horizon". */
        const val NEVER_OPENED_MINUTES = 100_000L
    }
}
