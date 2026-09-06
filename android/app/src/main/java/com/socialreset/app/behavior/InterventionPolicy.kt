package com.socialreset.app.behavior

import com.socialreset.app.core.model.InterventionLevel

/**
 * Maps a habit score (and optionally a classification) onto an intervention level.
 *
 * This is the boundary the AI does not cross: a classifier can nudge the level, but the
 * level is chosen here, and SOCIAL_RESET still requires a signed peer grant before
 * anything opens.
 */
class InterventionPolicy(
    private val scorer: BehaviorScorer = BehaviorScorer(),
) {

    data class Choice(
        val level: InterventionLevel,
        val score: Int,
        val rationale: String,
    )

    fun choose(features: BehaviorFeatures, classification: ClassificationResult?): Choice {
        val baseScore = scorer.score(features)

        // A declared intention lowers friction temporarily; it never disables the policy.
        val adjusted = when {
            features.intentionActive -> (baseScore - 3).coerceAtLeast(0)
            classification?.classification == Classification.HABITUAL &&
                classification.confidence >= 0.7 -> (baseScore + 1).coerceAtMost(10)
            classification?.classification == Classification.INTENTIONAL &&
                classification.confidence >= 0.7 -> (baseScore - 1).coerceAtLeast(0)
            else -> baseScore
        }

        val level = when {
            adjusted <= 2 -> InterventionLevel.LIGHT
            adjusted <= 5 -> InterventionLevel.FRICTION
            else -> InterventionLevel.SOCIAL_RESET
        }

        val rationale = buildString {
            append("score=").append(adjusted)
            if (adjusted != baseScore) append(" (base ").append(baseScore).append(")")
            classification?.let { append(" ai=").append(it.classification).append('/').append(it.source) }
            if (features.intentionActive) append(" intention=active")
        }
        return Choice(level, adjusted, rationale)
    }
}
