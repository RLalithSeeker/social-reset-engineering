package com.socialreset.app.social

import com.socialreset.app.core.model.ResetType
import com.socialreset.app.platform.TelecomAdapter

/**
 * Experimental, disabled by default.
 *
 * Android does not give an ordinary app a guaranteed "call was answered and stayed
 * connected for N seconds" callback. CallScreeningService exists for screening and
 * identification, not for post-call proof, so launching the dialer proves nothing and is
 * never accepted as evidence on its own.
 *
 * If the device cannot supply telecom lifecycle evidence that [TelecomAdapter] marks as
 * trusted, this returns Unverified and the flow falls back to FriendAcceptedVerifier.
 */
class CallAttemptVerifier(
    private val telecom: TelecomAdapter,
    private val minimumConnectedSeconds: Long = 30,
    private val enabled: Boolean = false,
) : ResetVerifier {

    override val resetType = ResetType.CALL_ATTEMPT

    override fun isAvailable(): Boolean = enabled && telecom.hasTrustedLifecycleEvidence()

    override suspend fun verify(
        session: ResetSession,
        evidence: VerificationEvidence,
    ): VerificationResult {
        if (!isAvailable()) {
            return VerificationResult.Unverified("call verification unavailable on this device")
        }
        if (!evidence.callEvidenceTrusted) {
            return VerificationResult.Unverified("telecom evidence not trusted")
        }
        val connected = evidence.callConnectedSeconds
            ?: return VerificationResult.Unverified("no connected duration")
        if (connected < minimumConnectedSeconds) {
            return VerificationResult.Unverified("connected ${connected}s < ${minimumConnectedSeconds}s")
        }
        return VerificationResult.Verified(resetType, session.createdAt + connected)
    }
}
