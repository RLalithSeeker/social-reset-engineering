package com.socialreset.app.data.repository

import com.socialreset.app.data.db.dao.TrustedPeerDao
import com.socialreset.app.social.PeerStatus
import com.socialreset.app.social.TrustedPeer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PeerRepository(
    private val dao: TrustedPeerDao,
) {
    fun observeAll(): Flow<List<TrustedPeer>> =
        dao.observeAll().map { peers -> peers.map { it.toDomain() } }

    suspend fun all(): List<TrustedPeer> =
        dao.all().map { it.toDomain() }

    suspend fun byId(deviceId: String): TrustedPeer? =
        dao.byId(deviceId)?.toDomain()

    /**
     * Public keys are pinned at pairing time. Seeing a different key for the same device
     * id is treated as re-pairing after revocation, not as silent key rotation.
     */
    suspend fun upsertPinned(peer: TrustedPeer) {
        val existing = dao.byId(peer.deviceId)
        if (existing != null && existing.publicKey != peer.publicKey) {
            dao.setStatus(peer.deviceId, PeerStatus.REVOKED.name)
            return
        }
        dao.upsert(peer.toEntity())
    }

    suspend fun revoke(deviceId: String) {
        dao.setStatus(deviceId, PeerStatus.REVOKED.name)
    }
}
