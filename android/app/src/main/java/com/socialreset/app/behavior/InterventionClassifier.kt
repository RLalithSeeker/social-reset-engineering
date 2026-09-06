package com.socialreset.app.behavior

/**
 * Optional AI layer. Output is constrained to an enum plus a confidence — free text never
 * reaches unlock logic, and a classification is only ever an input to the deterministic
 * policy.
 */
interface InterventionClassifier {
    suspend fun classify(features: BehaviorFeatures): ClassificationResult
}

data class ClassificationResult(
    val classification: Classification,
    val confidence: Double,
    val source: String,
)

enum class Classification { INTENTIONAL, AMBIGUOUS, HABITUAL }

/** Mandatory baseline. Always available, no device requirements, no latency. */
class RuleBasedClassifier(private val scorer: BehaviorScorer = BehaviorScorer()) : InterventionClassifier {
    override suspend fun classify(features: BehaviorFeatures): ClassificationResult {
        val score = scorer.score(features)
        val classification = when {
            score <= 2 -> Classification.INTENTIONAL
            score <= 5 -> Classification.AMBIGUOUS
            else -> Classification.HABITUAL
        }
        return ClassificationResult(classification, confidence = 1.0, source = "rules")
    }
}
