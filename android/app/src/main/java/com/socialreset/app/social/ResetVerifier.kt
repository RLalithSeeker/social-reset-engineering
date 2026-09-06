package com.socialreset.app.social

import com.socialreset.app.core.model.ResetType

/**
 * Decides whether a reset session produced real evidence of a human connection.
 *
 * The security posture is `uncertain -> no unlock`: a false positive destroys the point of
 * the product, a false negative is merely annoying.
 */
interface ResetVerifier {
    val resetType: ResetType

    /** True when this verifier can run at all on this device/build. */
    fun isAvailable(): Boolean

    suspend fun verify(session: ResetSession, evidence: VerificationEvidence): VerificationResult
}

/** Everything a verifier is allowed to look at. Deliberately small — no call audio, ever. */
data class VerificationEvidence(
    /** A signed RESET_ACCEPTED from the peer, already checked by EventGuard. */
    val peerAcceptedEventId: String? = null,
    val peerAcceptedAt: Long? = null,
    /** Telecom lifecycle evidence, only populated by the experimental call path. */
    val callConnectedSeconds: Long? = null,
    val callEvidenceTrusted: Boolean = false,
)

sealed interface VerificationResult {
    data class Verified(val resetType: ResetType, val verifiedAt: Long) : VerificationResult

    /** Not enough evidence. Never treated as a soft yes. */
    data class Unverified(val reason: String) : VerificationResult
}
