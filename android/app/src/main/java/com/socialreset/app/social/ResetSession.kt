package com.socialreset.app.social

import com.socialreset.app.core.model.ResetType

/** The states of one social reset attempt. Transitions are enforced by [ResetStateMachine]. */
enum class ResetState {
    IDLE,
    REQUEST_CREATED,
    WAITING_FOR_PEER,
    PEER_ACCEPTED,
    CONNECTION_ACTIVE,
    CONNECTION_VERIFIED,
    GRANT_ISSUED,
    UNLOCK_ACTIVE,
    EXPIRED,
    LOCKED,
    FAILED,
    CANCELLED,
    ;

    val isTerminal: Boolean get() = this == LOCKED || this == FAILED || this == CANCELLED
}

data class ResetSession(
    val sessionId: String,
    val requesterDeviceId: String,
    val peerDeviceId: String,
    val blockedPackage: String,
    val policyId: String,
    val resetType: ResetType,
    val state: ResetState,
    val createdAt: Long,
    val expiresAt: Long,
    /** Highest sequence number accepted from the peer on this session. */
    val lastAcceptedSequence: Int = -1,
    /** Our own next outbound sequence number. */
    val nextOutboundSequence: Int = 0,
    val grantId: String? = null,
    val failureReason: String? = null,
)
