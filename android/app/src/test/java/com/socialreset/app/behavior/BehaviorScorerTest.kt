package com.socialreset.app.behavior

import org.junit.Assert.assertEquals
import org.junit.Test

class BehaviorScorerTest {
    private val scorer = BehaviorScorer()

    @Test
    fun habitualSignalsIncreaseScoreWithinBounds() {
        val score = scorer.score(
            features(
                opens10m = 4,
                opens30m = 7,
                reopensUnder60s = 3,
                sameAppReopensAfterUnlock = true,
            )
        )

        assertEquals(8, score)
    }

    @Test
    fun longStableSessionReducesLowReopenScore() {
        val score = scorer.score(
            features(
                opens10m = 1,
                currentSessionSeconds = 600,
            )
        )

        assertEquals(0, score)
    }

    private fun features(
        opens10m: Int = 0,
        opens30m: Int = 0,
        currentSessionSeconds: Long = 0,
        reopensUnder60s: Int = 0,
        sameAppReopensAfterUnlock: Boolean = false,
    ) = BehaviorFeatures(
        opens10m = opens10m,
        opens30m = opens30m,
        medianRecentSessionSeconds = 0,
        currentSessionSeconds = currentSessionSeconds,
        reopensUnder60s = reopensUnder60s,
        minutesSinceLastSession = 100_000,
        recentUnlockCount = 0,
        sameAppReopensAfterUnlock = sameAppReopensAfterUnlock,
        scheduledBlockActive = true,
    )
}
