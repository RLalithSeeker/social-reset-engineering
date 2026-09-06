package com.socialreset.app.social

import com.socialreset.app.core.model.ResetType
import com.socialreset.app.core.result.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetStateMachineTest {
    @Test
    fun cannotJumpFromWaitingForPeerToUnlockActive() {
        val session = session(state = ResetState.WAITING_FOR_PEER)

        val result = ResetStateMachine.transition(session, ResetState.UNLOCK_ACTIVE)

        assertTrue(result is Outcome.Rejected)
    }

    @Test
    fun reapplyingCurrentStateIsIdempotent() {
        val session = session(state = ResetState.GRANT_ISSUED)

        val result = ResetStateMachine.transition(session, ResetState.GRANT_ISSUED)

        assertEquals(session, (result as Outcome.Ok).value)
    }

    private fun session(state: ResetState) = ResetSession(
        sessionId = "session",
        requesterDeviceId = "requester",
        peerDeviceId = "peer",
        blockedPackage = "com.example",
        policyId = "policy",
        resetType = ResetType.FRIEND_ACCEPTED,
        state = state,
        createdAt = 1_770_000_000,
        expiresAt = 1_770_000_600,
    )
}
