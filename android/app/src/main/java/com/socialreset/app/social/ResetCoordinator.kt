package com.socialreset.app.social

import com.socialreset.app.core.logging.Log
import com.socialreset.app.core.model.EventType
import com.socialreset.app.core.model.ResetType
import com.socialreset.app.core.result.Outcome
import com.socialreset.app.core.result.RejectionReason
import com.socialreset.app.core.time.TimeSource
import com.socialreset.app.crypto.SignatureService
import com.socialreset.app.crypto.TokenValidator
import com.socialreset.app.data.repository.PeerRepository
import com.socialreset.app.data.repository.SessionRepository
import com.socialreset.app.enforcement.UnlockWindowManager
import com.socialreset.app.network.RelayEvent
import com.socialreset.app.network.ResetRelayApi
import com.socialreset.app.network.WebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.WebSocket
import java.util.UUID

class ResetCoordinator(
    private val localDeviceId: () -> String,
    private val signer: SignatureService,
    private val accessTokenProvider: () -> String?,
    private val relayApi: ResetRelayApi,
    private val webSocketClient: WebSocketClient,
    private val peerRepository: PeerRepository,
    private val sessionRepository: SessionRepository,
    private val unlockWindowManager: UnlockWindowManager,
    private val timeSource: TimeSource,
    private val appScope: CoroutineScope,
    private val eventGuard: EventGuard = EventGuard(timeSource),
    private val verifier: ResetVerifier = FriendAcceptedVerifier(),
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val eventIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    data class Status(
        val connected: Boolean,
        val message: String,
    )

    @Volatile
    var status: Status = Status(connected = false, message = "Relay not connected.")
        private set

    @Volatile
    private var relaySocket: WebSocket? = null

    fun connectRelay(): Outcome<WebSocket> {
        val token = accessTokenProvider()
            ?: return Outcome.Rejected(RejectionReason.UNKNOWN_SESSION, "register before websocket connect")
        val socket = webSocketClient.connect(localDeviceId(), token, object : WebSocketClient.Listener {
            override fun onEvent(event: RelayEvent) {
                appScope.launch {
                    when (val result = handleInbound(event)) {
                        is Outcome.Ok -> {
                            status = Status(true, "Accepted ${event.type} for ${result.value.sessionId.take(8)}.")
                        }
                        is Outcome.Rejected -> {
                            status = Status(true, "Dropped ${event.type}: ${result.reason}.")
                        }
                    }
                }
            }

            override fun onConnected() {
                status = Status(true, "Relay connected.")
            }

            override fun onClosed(reason: String) {
                status = Status(false, "Relay closed: $reason")
            }

            override fun onFailure(error: Throwable) {
                status = Status(false, "Relay failed: ${error.message ?: error::class.java.simpleName}")
            }
        })
        relaySocket = socket
        return Outcome.Ok(socket)
    }

    suspend fun requestReset(
        peerDeviceId: String,
        blockedPackage: String,
        policyId: String,
        unlockSeconds: Long,
        resetType: ResetType = ResetType.FRIEND_ACCEPTED,
    ): Outcome<ResetSession> {
        val token = accessTokenProvider()
            ?: return Outcome.Rejected(RejectionReason.UNKNOWN_SESSION, "register before reset")
        val peer = peerRepository.byId(peerDeviceId)
            ?: return Outcome.Rejected(RejectionReason.UNKNOWN_PEER)
        if (!peer.isActive) return Outcome.Rejected(RejectionReason.PEER_REVOKED)

        val now = timeSource.wallClockSeconds()
        val sessionId = sessionIdFactory()
        val remote = relayApi.createResetSession(
            bearerToken = token,
            sessionId = sessionId,
            requesterDeviceId = localDeviceId(),
            peerDeviceId = peer.deviceId,
            blockedPackage = blockedPackage,
            resetType = resetType,
        )
        val requested = ResetSession(
            sessionId = remote.sessionId,
            requesterDeviceId = localDeviceId(),
            peerDeviceId = peer.deviceId,
            blockedPackage = remote.blockedPackage,
            policyId = policyId,
            resetType = resetType,
            state = ResetState.REQUEST_CREATED,
            createdAt = now,
            expiresAt = remote.expiresAtEpochSeconds,
        )
        val waiting = requested.transitionTo(ResetState.WAITING_FOR_PEER)
            ?: return Outcome.Rejected(RejectionReason.BAD_STATE)

        val event = signedEvent(
            type = EventType.RESET_REQUESTED,
            session = waiting,
            recipientDeviceId = peer.deviceId,
            payload = waiting.requestPayload(unlockSeconds),
        )
        relayApi.relayEvent(token, event)
        return waiting.copy(nextOutboundSequence = waiting.nextOutboundSequence + 1)
            .also { sessionRepository.upsert(it) }
            .let { Outcome.Ok(it) }
    }

    suspend fun acceptRequest(sessionId: String, grantSeconds: Long = DEFAULT_GRANT_SECONDS): Outcome<ResetSession> {
        val token = accessTokenProvider()
            ?: return Outcome.Rejected(RejectionReason.UNKNOWN_SESSION, "register before accepting")
        val session = sessionRepository.byId(sessionId)
            ?: return Outcome.Rejected(RejectionReason.UNKNOWN_SESSION)
        if (session.peerDeviceId != localDeviceId()) {
            return Outcome.Rejected(RejectionReason.WRONG_TARGET, "this device is not the requested peer")
        }
        val requester = peerRepository.byId(session.requesterDeviceId)
            ?: return Outcome.Rejected(RejectionReason.UNKNOWN_PEER)

        val connectionVerified = session
            .transitionTo(ResetState.PEER_ACCEPTED)
            ?.transitionTo(ResetState.CONNECTION_ACTIVE)
            ?.transitionTo(ResetState.CONNECTION_VERIFIED)
            ?: return Outcome.Rejected(RejectionReason.BAD_STATE, session.state.name)

        val acceptedEvent = signedEvent(
            type = EventType.RESET_ACCEPTED,
            session = connectionVerified,
            recipientDeviceId = requester.deviceId,
            payload = mapOf(
                "acceptedAt" to timeSource.wallClockSeconds(),
                "resetType" to session.resetType.name,
            ),
        )
        relayApi.relayEvent(token, acceptedEvent)

        val grantIssued = connectionVerified.copy(nextOutboundSequence = connectionVerified.nextOutboundSequence + 1)
            .transitionTo(ResetState.GRANT_ISSUED)
            ?: return Outcome.Rejected(RejectionReason.BAD_STATE, connectionVerified.state.name)
        val now = timeSource.wallClockSeconds()
        val grant = UnlockGrant(
            grantId = eventIdFactory(),
            sessionId = session.sessionId,
            targetDeviceId = requester.deviceId,
            policyId = session.policyId,
            packageName = session.blockedPackage,
            issuedAt = now,
            expiresAt = minOf(now + grantSeconds, session.expiresAt),
            resetType = session.resetType,
        )
        val grantEvent = signedEvent(
            type = EventType.UNLOCK_GRANT,
            session = grantIssued,
            recipientDeviceId = requester.deviceId,
            payload = grant.toEventPayload(signer),
        )
        relayApi.relayEvent(token, grantEvent)

        return grantIssued.copy(
            nextOutboundSequence = grantIssued.nextOutboundSequence + 1,
            grantId = grant.grantId,
        ).also { sessionRepository.upsert(it) }
            .let { Outcome.Ok(it) }
    }

    suspend fun handleInbound(event: RelayEvent): Outcome<ResetSession> {
        val peer = peerRepository.byId(event.senderDeviceId)
            ?: return Outcome.Rejected(RejectionReason.UNKNOWN_PEER)
        val session = sessionRepository.byId(event.sessionId)
            ?: return acceptNewRequest(event, peer)

        return when (val accepted = eventGuard.accept(event, session, peer, localDeviceId())) {
            is Outcome.Rejected -> accepted
            is Outcome.Ok -> applyAcceptedEvent(accepted.value, session.copy(lastAcceptedSequence = event.sequence), peer)
        }
    }

    private suspend fun acceptNewRequest(event: RelayEvent, peer: TrustedPeer): Outcome<ResetSession> {
        if (event.eventType != EventType.RESET_REQUESTED) {
            return Outcome.Rejected(RejectionReason.UNKNOWN_SESSION)
        }
        val proposed = event.toRequestedSession(localDeviceId())
            ?: return Outcome.Rejected(RejectionReason.MALFORMED)
        return when (val accepted = eventGuard.accept(event, proposed, peer, localDeviceId())) {
            is Outcome.Rejected -> accepted
            is Outcome.Ok -> proposed.copy(
                state = ResetState.WAITING_FOR_PEER,
                lastAcceptedSequence = accepted.value.sequence,
            ).also { sessionRepository.upsert(it) }
                .let { Outcome.Ok(it) }
        }
    }

    private suspend fun applyAcceptedEvent(
        event: RelayEvent,
        session: ResetSession,
        peer: TrustedPeer,
    ): Outcome<ResetSession> = when (event.eventType) {
        EventType.RESET_ACCEPTED -> handleAccepted(session, event)
        EventType.UNLOCK_GRANT -> handleGrant(session, event, peer)
        EventType.RESET_REJECTED -> store(session.copy(state = ResetState.FAILED, failureReason = "peer rejected"))
        EventType.RESET_CANCELLED -> store(session.copy(state = ResetState.CANCELLED))
        EventType.PEER_REVOKED -> {
            peerRepository.revoke(peer.deviceId)
            store(session.copy(state = ResetState.FAILED, failureReason = "peer revoked"))
        }
        EventType.CONNECTION_VERIFIED -> {
            val verified = session.transitionTo(ResetState.CONNECTION_VERIFIED)
                ?: return Outcome.Rejected(RejectionReason.BAD_STATE, session.state.name)
            store(verified)
        }
        EventType.RESET_REQUESTED -> store(session)
        null -> Outcome.Rejected(RejectionReason.MALFORMED, event.type)
    }

    private suspend fun handleAccepted(session: ResetSession, event: RelayEvent): Outcome<ResetSession> {
        val active = session
            .transitionTo(ResetState.PEER_ACCEPTED)
            ?.transitionTo(ResetState.CONNECTION_ACTIVE)
            ?: return Outcome.Rejected(RejectionReason.BAD_STATE, session.state.name)
        val evidence = VerificationEvidence(
            peerAcceptedEventId = event.eventId,
            peerAcceptedAt = event.payload.number("acceptedAt") ?: event.timestamp,
        )
        return when (val result = verifier.verify(active, evidence)) {
            is VerificationResult.Unverified -> {
                store(active.copy(state = ResetState.FAILED, failureReason = result.reason))
            }
            is VerificationResult.Verified -> {
                val verified = active.transitionTo(ResetState.CONNECTION_VERIFIED)
                    ?: return Outcome.Rejected(RejectionReason.BAD_STATE, active.state.name)
                store(verified)
            }
        }
    }

    private suspend fun handleGrant(
        session: ResetSession,
        event: RelayEvent,
        peer: TrustedPeer,
    ): Outcome<ResetSession> {
        val (grant, grantSignature) = UnlockGrant.fromEventPayload(event.payload)
            ?: return Outcome.Rejected(RejectionReason.MALFORMED, "unlock grant payload")
        val validation = TokenValidator(timeSource).validate(
            grant = grant,
            grantSignature = grantSignature,
            senderDeviceId = event.senderDeviceId,
            context = TokenValidator.Context(
                localDeviceId = localDeviceId(),
                peer = peer,
                sessionExists = true,
                sessionState = session.state,
                expectedPackage = session.blockedPackage,
                expectedPolicyId = session.policyId,
                grantAlreadyConsumed = unlockWindowManager.hasConsumed(grant.grantId),
            ),
        )
        if (validation is Outcome.Rejected) return validation

        val grantIssued = session.transitionTo(ResetState.GRANT_ISSUED)
            ?: return Outcome.Rejected(RejectionReason.BAD_STATE, session.state.name)
        return when (val redeemed = unlockWindowManager.redeem(grant)) {
            is Outcome.Rejected -> redeemed
            is Outcome.Ok -> {
                Log.i("Unlock grant redeemed for ${grant.packageName}")
                val active = grantIssued.transitionTo(ResetState.UNLOCK_ACTIVE)
                    ?: return Outcome.Rejected(RejectionReason.BAD_STATE, grantIssued.state.name)
                store(active.copy(grantId = grant.grantId))
            }
        }
    }

    private suspend fun store(session: ResetSession): Outcome<ResetSession> {
        sessionRepository.upsert(session)
        return Outcome.Ok(session)
    }

    private fun signedEvent(
        type: EventType,
        session: ResetSession,
        recipientDeviceId: String,
        payload: Map<String, Any?>,
    ): RelayEvent =
        RelayEvent.signed(
            signer = signer,
            type = type,
            sessionId = session.sessionId,
            senderDeviceId = localDeviceId(),
            recipientDeviceId = recipientDeviceId,
            sequence = session.nextOutboundSequence,
            timestampSeconds = timeSource.wallClockSeconds(),
            payload = payload,
            eventId = eventIdFactory(),
        )

    private fun ResetSession.transitionTo(state: ResetState): ResetSession? =
        (ResetStateMachine.transition(this, state) as? Outcome.Ok)?.value

    private fun ResetSession.requestPayload(unlockSeconds: Long): Map<String, Any?> = mapOf(
        "requesterDeviceId" to requesterDeviceId,
        "peerDeviceId" to peerDeviceId,
        "blockedPackage" to blockedPackage,
        "policyId" to policyId,
        "resetType" to resetType.name,
        "createdAt" to createdAt,
        "expiresAt" to expiresAt,
        "unlockSeconds" to unlockSeconds,
    )

    private fun RelayEvent.toRequestedSession(localDeviceId: String): ResetSession? {
        val requesterDeviceId = payload.string("requesterDeviceId") ?: senderDeviceId
        val peerDeviceId = payload.string("peerDeviceId") ?: recipientDeviceId
        if (requesterDeviceId != senderDeviceId || peerDeviceId != localDeviceId) return null
        return ResetSession(
            sessionId = sessionId,
            requesterDeviceId = requesterDeviceId,
            peerDeviceId = peerDeviceId,
            blockedPackage = payload.string("blockedPackage") ?: return null,
            policyId = payload.string("policyId") ?: return null,
            resetType = payload.string("resetType")?.let { ResetType.valueOf(it) } ?: return null,
            state = ResetState.REQUEST_CREATED,
            createdAt = payload.number("createdAt") ?: timestamp,
            expiresAt = payload.number("expiresAt") ?: return null,
        )
    }

    private fun Map<String, Any?>.string(key: String): String? = this[key] as? String

    private fun Map<String, Any?>.number(key: String): Long? =
        (this[key] as? Long) ?: (this[key] as? Int)?.toLong()

    private companion object {
        const val DEFAULT_GRANT_SECONDS = 10 * 60L
    }
}
