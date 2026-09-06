package com.socialreset.app.behavior

/**
 * Local-only usage signal. No message contents, no audio, no remote upload — the feature
 * vector below is everything the engine ever sees.
 */
data class BehaviorEvent(
    val id: Long = 0,
    val packageName: String,
    val kind: Kind,
    val timestampSeconds: Long,
    /** Filled in when a foreground session ends. */
    val durationSeconds: Long? = null,
    val duringUnlockWindow: Boolean = false,
) {
    enum class Kind { FOREGROUND, BACKGROUND, INTERVENTION_SHOWN, UNLOCK_GRANTED, INTENTION_DECLARED }
}

/** The exact inputs the scorer is allowed to use. */
data class BehaviorFeatures(
    val opens10m: Int,
    val opens30m: Int,
    val medianRecentSessionSeconds: Long,
    val currentSessionSeconds: Long,
    val reopensUnder60s: Int,
    val minutesSinceLastSession: Long,
    val recentUnlockCount: Int,
    val sameAppReopensAfterUnlock: Boolean,
    val scheduledBlockActive: Boolean,
    val intentionActive: Boolean = false,
)
