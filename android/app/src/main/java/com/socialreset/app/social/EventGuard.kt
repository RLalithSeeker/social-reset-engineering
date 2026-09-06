package com.socialreset.app.social

import com.socialreset.app.core.model.Protocol
import com.socialreset.app.core.result.Outcome
import com.socialreset.app.core.result.RejectionReason
import com.socialreset.app.core.time.TimeSource
import com.socialreset.app.network.RelayEvent

/**
 * Envelope-level checks that run before an event is allowed to touch the state machine:
 * right session, right sender, fresh timestamp, monotonic sequence, unseen nonce, valid
 * signature.
 *
 * The nonce cache is bounded and scoped to a session — replay protection only has to
 * outlive the session itself, and an unbounded cache would be its own bug.
 */
class EventGuard(
    private val timeSource: TimeSource,
    private val clockSkewSeconds: Long = Protocol.CLOCK_SKEW_SECONDS,
    private val maxNoncesPerSession: Int = 256,
) {
    private val seenNonces = LinkedHashMap<String, MutableSet<String>>()

    fun accept(
        event: RelayEvent,
        session: ResetSession,
        peer: TrustedPeer,
        localDeviceId: String,
    ): Outcome<RelayEvent> {
        if (event.protocolVersion != Protocol.VERSION) {
            return Outcome.Rejected(RejectionReason.UNSUPPORTED_VERSION)
        }
        if (event.sessionId != session.sessionId) {
            return Outcome.Rejected(RejectionReason.WRONG_SESSION)
        }
        if (event.senderDeviceId != peer.deviceId || event.senderDeviceId == localDeviceId) {
            return Outcome.Rejected(RejectionReason.UNKNOWN_PEER)
        }
        if (event.recipientDeviceId != localDeviceId) {
            return Outcome.Rejected(RejectionReason.WRONG_TARGET)
        }
        if (!peer.isActive) {
            return Outcome.Rejected(RejectionReason.PEER_REVOKED)
        }

        val now = timeSource.wallClockSeconds()
        if (event.timestamp > now + clockSkewSeconds) {
            return Outcome.Rejected(RejectionReason.FUTURE_TIMESTAMP)
        }
        if (event.timestamp < now - MAX_EVENT_AGE_SECONDS) {
            return Outcome.Rejected(RejectionReason.STALE_TIMESTAMP)
        }

        // Strict ordering: v1 does not need out-of-order delivery, and allowing it would
        // widen the replay surface for no user-visible gain.
        if (event.sequence <= session.lastAcceptedSequence) {
            return Outcome.Rejected(RejectionReason.OUT_OF_ORDER, "seq=${event.sequence}")
        }

        val nonces = seenNonces.getOrPut(session.sessionId) { linkedSetOf() }
        if (!nonces.add(event.nonce)) {
            return Outcome.Rejected(RejectionReason.REPLAYED, "nonce")
        }
        if (nonces.size > maxNoncesPerSession) {
            nonces.iterator().let { if (it.hasNext()) { it.next(); it.remove() } }
        }

        // Signature last: it is the most expensive check and the cheap ones above already
        // discarded anything addressed to the wrong session or device.
        if (!event.verifySignature(peer.publicKey)) {
            nonces.remove(event.nonce)
            return Outcome.Rejected(RejectionReason.BAD_SIGNATURE)
        }

        return Outcome.Ok(event)
    }

    fun forgetSession(sessionId: String) {
        seenNonces.remove(sessionId)
    }

    private companion object {
        /** Events older than this cannot be part of a live session anyway. */
        const val MAX_EVENT_AGE_SECONDS = 15 * 60L
    }
}
