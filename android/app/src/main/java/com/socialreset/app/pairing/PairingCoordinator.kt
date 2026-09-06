package com.socialreset.app.pairing

import com.socialreset.app.core.model.Protocol
import com.socialreset.app.crypto.DeviceIdentityManager
import com.socialreset.app.crypto.FingerprintFormatter
import com.socialreset.app.crypto.KeyAgreementService
import com.socialreset.app.data.repository.PeerRepository
import com.socialreset.app.network.ApiClient
import com.socialreset.app.social.PeerStatus
import com.socialreset.app.social.TrustedPeer
import java.time.Instant

class PairingCoordinator(
    private val identity: DeviceIdentityManager,
    private val keyAgreement: KeyAgreementService,
    private val apiClient: ApiClient,
    private val peerRepository: PeerRepository,
    private val now: () -> Long = { Instant.now().epochSecond },
) {
    data class CreatedPairing(
        val sessionId: String,
        val code: String,
        val expiresAtEpochSeconds: Long,
    )

    data class PendingPeer(
        val sessionId: String,
        val peerDeviceId: String,
        val peerDisplayName: String,
        val peerSigningPublicKey: String,
        val fingerprint: String,
    )

    private var accessToken: String? = null
    private var createdEphemeral: KeyAgreementService.Ephemeral? = null
    private var joinedEphemeral: KeyAgreementService.Ephemeral? = null
    private var joinedCode: String? = null
    private var pendingPeer: PendingPeer? = null

    val localDeviceId: String get() = identity.deviceId
    val ownFingerprint: String get() = identity.ownFingerprint()
    val isRegistered: Boolean get() = accessToken != null
    val currentAccessToken: String? get() = accessToken

    suspend fun register(displayName: String): String {
        identity.displayName = displayName
        val registration = apiClient.registerDevice(
            deviceId = identity.deviceId,
            displayName = displayName,
            signingPublicKey = identity.publicKey(),
        )
        accessToken = registration.accessToken
        return registration.deviceId
    }

    suspend fun createPairing(): CreatedPairing {
        val token = requireToken()
        val ephemeral = keyAgreement.newEphemeral()
        createdEphemeral = ephemeral
        val session = apiClient.createPairingSession(
            bearerToken = token,
            creatorEphemeralPublicKey = ephemeral.publicKeyB64,
        )
        return CreatedPairing(
            sessionId = session.sessionId,
            code = session.code,
            expiresAtEpochSeconds = session.expiresAtEpochSeconds,
        )
    }

    suspend fun joinPairing(sessionId: String, code: String): PendingPeer {
        val token = requireToken()
        val ephemeral = keyAgreement.newEphemeral()
        joinedEphemeral = ephemeral
        joinedCode = code
        val response = apiClient.joinPairingSession(
            bearerToken = token,
            pairingSessionId = sessionId.trim(),
            code = code.trim(),
            deviceId = identity.deviceId,
            signingPublicKey = identity.publicKey(),
            ephemeralPublicKey = ephemeral.publicKeyB64,
        )
        return buildPendingPeer(
            sessionId = response.sessionId,
            code = code,
            ephemeral = ephemeral,
            response = response,
        ).also { pendingPeer = it }
    }

    suspend fun completeCreatedPairing(sessionId: String, code: String): PendingPeer {
        val token = requireToken()
        val ephemeral = createdEphemeral ?: error("Create a pairing on this device first.")
        val response = apiClient.completePairingSession(token, sessionId.trim())
        return buildPendingPeer(
            sessionId = response.sessionId,
            code = code.trim(),
            ephemeral = ephemeral,
            response = response,
        ).also { pendingPeer = it }
    }

    suspend fun confirmPendingPeer(): TrustedPeer {
        val pending = pendingPeer ?: error("No pending peer to confirm.")
        val peer = TrustedPeer(
            deviceId = pending.peerDeviceId,
            displayName = pending.peerDisplayName,
            publicKey = pending.peerSigningPublicKey,
            fingerprint = pending.fingerprint,
            createdAt = now(),
            status = PeerStatus.ACTIVE,
            protocolVersion = Protocol.VERSION,
        )
        peerRepository.upsertPinned(peer)
        return peer
    }

    private fun buildPendingPeer(
        sessionId: String,
        code: String,
        ephemeral: KeyAgreementService.Ephemeral,
        response: ApiClient.PairingStatus,
    ): PendingPeer {
        val peerDeviceId = response.peerDeviceId ?: error("Relay did not return peer device id.")
        val peerSigningKey = response.peerSigningPublicKey ?: error("Relay did not return peer signing key.")
        val peerEphemeralKey = response.peerEphemeralPublicKey ?: error("Relay did not return peer ephemeral key.")
        val sessionKey = ephemeral.deriveSessionKey(
            peerPublicKeyB64 = peerEphemeralKey,
            pairingCode = code,
            pairingSessionId = sessionId,
            localDeviceId = identity.deviceId,
            peerDeviceId = peerDeviceId,
        ) ?: error("Could not derive pairing session key.")
        return PendingPeer(
            sessionId = sessionId,
            peerDeviceId = peerDeviceId,
            peerDisplayName = response.peerDisplayName?.ifBlank { peerDeviceId } ?: peerDeviceId,
            peerSigningPublicKey = peerSigningKey,
            fingerprint = FingerprintFormatter.forTranscript(
                localPublicKeyB64 = identity.publicKey(),
                peerPublicKeyB64 = peerSigningKey,
                sessionKey = sessionKey,
            ),
        )
    }

    private fun requireToken(): String =
        accessToken ?: error("Register this device with the relay first.")
}
