package com.socialreset.app.social

import com.socialreset.app.core.result.Outcome
import com.socialreset.app.core.result.RejectionReason

/**
 * Transition table for a reset session.
 *
 * Written as data rather than as scattered `if` statements so that the single rule that
 * matters — nothing reaches UNLOCK_ACTIVE without passing through CONNECTION_VERIFIED and
 * GRANT_ISSUED — is checkable by reading one map, and testable directly.
 */
object ResetStateMachine {

    private val ALLOWED: Map<ResetState, Set<ResetState>> = mapOf(
        ResetState.IDLE to setOf(ResetState.REQUEST_CREATED),
        ResetState.REQUEST_CREATED to setOf(ResetState.WAITING_FOR_PEER, ResetState.CANCELLED, ResetState.FAILED),
        ResetState.WAITING_FOR_PEER to setOf(ResetState.PEER_ACCEPTED, ResetState.FAILED, ResetState.CANCELLED),
        ResetState.PEER_ACCEPTED to setOf(ResetState.CONNECTION_ACTIVE, ResetState.FAILED, ResetState.CANCELLED),
        ResetState.CONNECTION_ACTIVE to setOf(
            ResetState.CONNECTION_VERIFIED,
            ResetState.FAILED,
            ResetState.CANCELLED,
        ),
        ResetState.CONNECTION_VERIFIED to setOf(ResetState.GRANT_ISSUED, ResetState.FAILED),
        ResetState.GRANT_ISSUED to setOf(ResetState.UNLOCK_ACTIVE, ResetState.FAILED),
        ResetState.UNLOCK_ACTIVE to setOf(ResetState.EXPIRED),
        ResetState.EXPIRED to setOf(ResetState.LOCKED),
        ResetState.LOCKED to emptySet(),
        ResetState.FAILED to setOf(ResetState.IDLE),
        ResetState.CANCELLED to setOf(ResetState.IDLE),
    )

    fun canTransition(from: ResetState, to: ResetState): Boolean =
        ALLOWED[from]?.contains(to) == true

    /**
     * Idempotent transition: re-applying the transition that produced the current state is
     * a no-op success, so a duplicated relay event cannot advance the session twice or
     * knock it into FAILED.
     */
    fun transition(session: ResetSession, to: ResetState): Outcome<ResetSession> = when {
        session.state == to -> Outcome.Ok(session)
        canTransition(session.state, to) -> Outcome.Ok(session.copy(state = to))
        else -> Outcome.Rejected(RejectionReason.BAD_STATE, "${session.state} -> $to")
    }
}
