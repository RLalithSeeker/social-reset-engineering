package com.socialreset.app.behavior

import com.socialreset.app.core.logging.Log
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Optional on-device model adapter.
 *
 * Availability is a device property (API level, AICore presence, inference quota), so it
 * is detected rather than assumed, wrapped in a hard timeout, and falls straight back to
 * the rule-based classifier. Enforcement never waits on inference.
 */
class OnDeviceAiClassifier(
    private val backend: Backend,
    private val fallback: InterventionClassifier = RuleBasedClassifier(),
    private val timeoutMillis: Long = 400,
) : InterventionClassifier {

    /** Whatever generative API the device offers, behind one narrow call. */
    interface Backend {
        fun isAvailable(): Boolean

        /** Must return the structured classification shape, or null. */
        suspend fun classifyStructured(prompt: String): ClassificationResult?
    }

    override suspend fun classify(features: BehaviorFeatures): ClassificationResult {
        if (!backend.isAvailable()) return fallback.classify(features)
        return try {
            withTimeout(timeoutMillis) { backend.classifyStructured(prompt(features)) }
                ?.takeIf { it.confidence in 0.0..1.0 }
                ?: fallback.classify(features)
        } catch (e: TimeoutCancellationException) {
            Log.w("on-device classifier timed out; using rules")
            fallback.classify(features)
        } catch (e: Exception) {
            Log.w("on-device classifier failed; using rules", e)
            fallback.classify(features)
        }
    }

    /** Tiny structured prompt: numeric features only, no app names, no content. */
    private fun prompt(f: BehaviorFeatures): String = buildString {
        append("Classify phone usage as INTENTIONAL, AMBIGUOUS or HABITUAL. ")
        append("Reply only as JSON with fields classification and confidence 0..1. ")
        append("opens_10m=").append(f.opens10m)
        append(" opens_30m=").append(f.opens30m)
        append(" median_session_s=").append(f.medianRecentSessionSeconds)
        append(" current_session_s=").append(f.currentSessionSeconds)
        append(" reopens_under_60s=").append(f.reopensUnder60s)
        append(" recent_unlocks=").append(f.recentUnlockCount)
        append(" reopen_after_unlock=").append(f.sameAppReopensAfterUnlock)
    }
}
