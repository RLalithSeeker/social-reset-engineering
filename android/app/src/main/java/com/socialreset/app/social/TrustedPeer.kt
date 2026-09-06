package com.socialreset.app.social

/**
 * A paired friend. `publicKey` is pinned at pairing time: a peer whose key changes is a
 * different peer and must pair again, which is what makes revocation meaningful.
 */
data class TrustedPeer(
    val deviceId: String,
    val displayName: String,
    val publicKey: String,
    val fingerprint: String,
    val createdAt: Long,
    val status: PeerStatus,
    val protocolVersion: Int,
) {
    val isActive: Boolean get() = status == PeerStatus.ACTIVE
}

enum class PeerStatus {
    /** Fingerprint confirmed on both screens; may accept sessions and issue grants. */
    ACTIVE,

    /** Key exchanged, fingerprint not yet confirmed. Cannot issue grants. */
    PENDING_CONFIRMATION,

    /** Revoked locally. New sessions and new grants are refused permanently. */
    REVOKED,
}
