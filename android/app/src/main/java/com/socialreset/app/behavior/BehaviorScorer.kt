package com.socialreset.app.behavior

/**
 * Heuristic habit score, 0..10. These weights are prototype defaults, not a validated
 * model — they are here so the intervention level stays explainable and reproducible,
 * which an LLM classification is not.
 */
class BehaviorScorer {

    fun score(features: BehaviorFeatures): Int {
        var score = 0
        if (features.opens10m >= 2) score += 2
        if (features.opens10m >= 3) score += 2
        if (features.opens30m >= 5) score += 1
        if (features.reopensUnder60s >= 2) score += 1
        if (features.sameAppReopensAfterUnlock) score += 2
        if (isLongStableSession(features)) score -= 1
        return score.coerceIn(0, 10)
    }

    /** A single long session looks like deliberate use, not a habit loop. */
    private fun isLongStableSession(features: BehaviorFeatures): Boolean =
        features.currentSessionSeconds >= 300 && features.opens10m <= 1
}
