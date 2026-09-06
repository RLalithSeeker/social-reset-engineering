package com.socialreset.app.enforcement

import com.socialreset.app.behavior.BehaviorEvent
import com.socialreset.app.behavior.BehaviorRepository
import com.socialreset.app.behavior.FeatureExtractor
import com.socialreset.app.behavior.InterventionClassifier
import com.socialreset.app.behavior.InterventionPolicy
import com.socialreset.app.core.model.InterventionLevel
import com.socialreset.app.core.time.TimeSource
import com.socialreset.app.rules.PolicyEngine
import com.socialreset.app.rules.RuleRepository

class EnforcementController(
    private val rules: RuleRepository,
    private val unlockWindows: UnlockWindowManager,
    private val behaviorRepository: BehaviorRepository,
    private val classifier: InterventionClassifier,
    private val interventionPolicy: InterventionPolicy,
    private val timeSource: TimeSource,
    private val featureExtractor: FeatureExtractor = FeatureExtractor(),
    private val policyEngine: PolicyEngine = PolicyEngine(timeSource),
) {
    sealed interface Decision {
        data class Allow(val reason: String) : Decision
        data class ShowIntervention(
            val packageName: String,
            val appLabel: String,
            val policyId: String,
            val unlockSeconds: Long,
            val level: InterventionLevel,
            val score: Int,
            val rationale: String,
        ) : Decision
    }

    suspend fun onForegroundPackage(packageName: String): Decision {
        val activeUnlockPackage = unlockWindows.activePackage()
        return when (val policyDecision = policyEngine.evaluate(packageName, rules.snapshot(), activeUnlockPackage)) {
            is PolicyEngine.Decision.Allow -> Decision.Allow(policyDecision.reason)
            is PolicyEngine.Decision.Intervene -> {
                val now = timeSource.wallClockSeconds()
                behaviorRepository.record(
                    BehaviorEvent(
                        packageName = packageName,
                        kind = BehaviorEvent.Kind.FOREGROUND,
                        timestampSeconds = now,
                    )
                )
                val recent = behaviorRepository.recentFor(packageName, now - HISTORY_SECONDS)
                val features = featureExtractor.extract(
                    events = recent,
                    packageName = packageName,
                    nowSeconds = now,
                    scheduledBlockActive = true,
                    currentSessionSeconds = 0,
                    intentionActive = false,
                )
                val classification = classifier.classify(features)
                val choice = interventionPolicy.choose(features, classification)
                Decision.ShowIntervention(
                    packageName = packageName,
                    appLabel = policyDecision.app.label,
                    policyId = policyDecision.app.policyId,
                    unlockSeconds = policyDecision.app.unlockSeconds,
                    level = choice.level,
                    score = choice.score,
                    rationale = choice.rationale,
                )
            }
        }
    }

    private companion object {
        const val HISTORY_SECONDS = 24 * 60 * 60L
    }
}
