package com.socialreset.app.social

import com.socialreset.app.core.model.ResetType

/**
 * The deterministic path, and the only one enabled by default.
 *
 * Evidence is a signed RESET_ACCEPTED event from the peer that has already passed
 * [EventGuard] — signature, session binding, sequence and nonce were checked there. This
 * verifier's own job is only to confirm that such an event exists for this session and
 * that the session was in a state where accepting means anything.
 */
class FriendAcceptedVerifier : ResetVerifier {

    override val resetType = ResetType.FRIEND_ACCEPTED

    override fun isAvailable(): Boolean = true

    override suspend fun verify(
        session: ResetSession,
        evidence: VerificationEvidence,
    ): VerificationResult {
        val acceptedAt = evidence.peerAcceptedAt
        if (evidence.peerAcceptedEventId == null || acceptedAt == null) {
            return VerificationResult.Unverified("no signed acceptance from peer")
        }
        if (session.state != ResetState.CONNECTION_ACTIVE && session.state != ResetState.PEER_ACCEPTED) {
            return VerificationResult.Unverified("session state ${session.state}")
        }
        if (acceptedAt > session.expiresAt) {
            return VerificationResult.Unverified("acceptance arrived after session expiry")
        }
        return VerificationResult.Verified(resetType, acceptedAt)
    }
}
