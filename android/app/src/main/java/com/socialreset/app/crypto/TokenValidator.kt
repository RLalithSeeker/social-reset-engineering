package com.socialreset.app.crypto

import com.socialreset.app.core.model.Protocol
import com.socialreset.app.core.result.Outcome
import com.socialreset.app.core.result.RejectionReason
import com.socialreset.app.core.time.TimeSource
import com.socialreset.app.social.ResetState
import com.socialreset.app.social.TrustedPeer
import com.socialreset.app.social.UnlockGrant

/**
 * The nine checks from the crypto protocol, in one place, with no early "looks fine"
 * path. Every rejection names its reason so the diagnostics screen can show why an
 * unlock did not happen — a silent failure here looks identical to a bug.
 *
 * Ordering is cheapest-first, but correctness does not depend on it: all checks run
 * against local state only.
 */
class TokenValidator(
    private val timeSource: TimeSource,
    private val maxGrantSeconds: Long = Protocol.MAX_GRANT_SECONDS,
    private val clockSkewSeconds: Long = Protocol.CLOCK_SKEW_SECONDS,
) {

    /** Local facts the grant is checked against. Assembled by the caller, never remote. */
    data class Context(
        val localDeviceId: String,
        val peer: TrustedPeer?,
        val sessionExists: Boolean,
        val sessionState: ResetState?,
        val expectedPackage: String?,
        val expectedPolicyId: String?,
        val grantAlreadyConsumed: Boolean,
    )

    fun validate(
        grant: UnlockGrant,
        grantSignature: String,
        senderDeviceId: String,
        context: Context,
    ): Outcome<UnlockGrant> {
        if (grant.version != Protocol.VERSION) {
            return Outcome.Rejected(RejectionReason.UNSUPPORTED_VERSION, "v=${grant.version}")
        }

        // 1. signer is a currently trusted peer, and is the device that sent the event
        val peer = context.peer
            ?: return Outcome.Rejected(RejectionReason.UNKNOWN_PEER)
        if (peer.deviceId != senderDeviceId) {
            return Outcome.Rejected(RejectionReason.UNKNOWN_PEER, "sender is not the session peer")
        }
        if (!peer.isActive) {
            return Outcome.Rejected(RejectionReason.PEER_REVOKED)
        }

        // 2. the grant targets this device
        if (grant.targetDeviceId != context.localDeviceId) {
            return Outcome.Rejected(RejectionReason.WRONG_TARGET)
        }

        // 3. the session is one we started
        if (!context.sessionExists) {
            return Outcome.Rejected(RejectionReason.UNKNOWN_SESSION)
        }

        // 4. package and rule match the block that is actually active
        if (context.expectedPackage != null && grant.packageName != context.expectedPackage) {
            return Outcome.Rejected(RejectionReason.PACKAGE_MISMATCH)
        }
        if (context.expectedPolicyId != null && grant.policyId != context.expectedPolicyId) {
            return Outcome.Rejected(RejectionReason.POLICY_MISMATCH)
        }

        // 5/6. timestamps: not stale, not future-dated, not longer than the local maximum
        val now = timeSource.wallClockSeconds()
        if (grant.issuedAt > now + clockSkewSeconds) {
            return Outcome.Rejected(RejectionReason.FUTURE_TIMESTAMP)
        }
        if (grant.expiresAt <= now - clockSkewSeconds) {
            return Outcome.Rejected(RejectionReason.EXPIRED)
        }
        if (grant.durationSeconds <= 0 || grant.durationSeconds > maxGrantSeconds) {
            return Outcome.Rejected(RejectionReason.GRANT_TOO_LONG, "${grant.durationSeconds}s")
        }

        // 7. one grant, one unlock
        if (context.grantAlreadyConsumed) {
            return Outcome.Rejected(RejectionReason.REPLAYED)
        }

        // 8. signature over the canonical payload, using the peer's pinned key
        if (!SignatureVerifier.verify(peer.publicKey, grant.canonicalPayload(), grantSignature)) {
            return Outcome.Rejected(RejectionReason.BAD_SIGNATURE)
        }

        // 9. the session actually reached a state where a grant is meaningful
        if (context.sessionState != ResetState.CONNECTION_VERIFIED &&
            context.sessionState != ResetState.GRANT_ISSUED
        ) {
            return Outcome.Rejected(RejectionReason.BAD_STATE, context.sessionState?.name ?: "none")
        }

        return Outcome.Ok(grant)
    }
}
