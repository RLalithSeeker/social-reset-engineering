package com.socialreset.app.social

import com.socialreset.app.core.model.EventType
import com.socialreset.app.core.model.ResetType
import com.socialreset.app.core.result.Outcome
import com.socialreset.app.core.time.FakeTimeSource
import com.socialreset.app.crypto.B64
import com.socialreset.app.crypto.InMemorySignatureService
import com.socialreset.app.data.db.entities.UnlockWindowEntity
import com.socialreset.app.data.repository.PeerRepository
import com.socialreset.app.data.repository.SessionRepository
import com.socialreset.app.enforcement.UnlockWindowManager
import com.socialreset.app.network.ApiClient
import com.socialreset.app.network.RelayEvent
import com.socialreset.app.network.ResetRelayApi
import com.socialreset.app.network.WebSocketClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

@RunWith(RobolectricTestRunner::class)
class ResetCoordinatorTest {
    private val time = FakeTimeSource(wallSeconds = 1_770_000_000, elapsedMillis = 0)
    private val localSigner = signer()
    private val peerSigner = signer()
    private val peer = TrustedPeer(
        deviceId = "peer",
        displayName = "Peer",
        publicKey = peerSigner.publicKey(),
        fingerprint = "BLUE-ORBIT-71-MAPLE",
        createdAt = time.wallClockSeconds(),
        status = PeerStatus.ACTIVE,
        protocolVersion = 1,
    )

    @Test
    fun requestResetStoresWaitingSessionAndRelaysRequest() = runTest {
        val relay = FakeRelayApi(time)
        val peerRepository = mockk<PeerRepository>()
        val sessionRepository = mockk<SessionRepository>()
        coEvery { peerRepository.byId("peer") } returns peer
        coEvery { sessionRepository.upsert(any()) } just runs

        val result = coordinator(
            relay = relay,
            peerRepository = peerRepository,
            sessionRepository = sessionRepository,
        ).requestReset(
            peerDeviceId = "peer",
            blockedPackage = "com.example.social",
            policyId = "policy-1",
            unlockSeconds = 600,
        )

        val session = (result as Outcome.Ok).value
        assertEquals(ResetState.WAITING_FOR_PEER, session.state)
        assertEquals(1, session.nextOutboundSequence)
        assertEquals(EventType.RESET_REQUESTED, relay.events.single().eventType)
        assertEquals("com.example.social", relay.events.single().payload["blockedPackage"])
        coVerify { sessionRepository.upsert(match { it.state == ResetState.WAITING_FOR_PEER }) }
    }

    @Test
    fun requesterRedeemsPeerAcceptedGrantAfterVerifiedAcceptance() = runTest {
        var stored = session(state = ResetState.WAITING_FOR_PEER)
        val relay = FakeRelayApi(time)
        val peerRepository = mockk<PeerRepository>()
        val sessionRepository = mockk<SessionRepository>()
        val unlockWindowManager = mockk<UnlockWindowManager>()
        coEvery { peerRepository.byId("peer") } returns peer
        coEvery { sessionRepository.byId("session-1") } answers { stored }
        coEvery { sessionRepository.upsert(any()) } answers { stored = firstArg() }
        coEvery { unlockWindowManager.hasConsumed("grant-1") } returns false
        coEvery { unlockWindowManager.redeem(any()) } returns Outcome.Ok(
            UnlockWindowEntity(
                packageName = "com.example.social",
                policyId = "policy-1",
                grantId = "grant-1",
                sessionId = "session-1",
                issuedAtWallSeconds = time.wallClockSeconds(),
                expiresAtWallSeconds = time.wallClockSeconds() + 600,
                expiresAtElapsedMillis = 600_000,
                bootId = "boot",
            )
        )

        val coordinator = coordinator(
            relay = relay,
            peerRepository = peerRepository,
            sessionRepository = sessionRepository,
            unlockWindowManager = unlockWindowManager,
        )

        val accepted = RelayEvent.signed(
            signer = peerSigner,
            type = EventType.RESET_ACCEPTED,
            sessionId = "session-1",
            senderDeviceId = "peer",
            recipientDeviceId = "local",
            sequence = 0,
            timestampSeconds = time.wallClockSeconds(),
            payload = mapOf("acceptedAt" to time.wallClockSeconds()),
            eventId = "accepted-1",
            nonce = "nonce-accepted",
        )
        assertEquals(ResetState.CONNECTION_VERIFIED, (coordinator.handleInbound(accepted) as Outcome.Ok).value.state)

        val grant = UnlockGrant(
            grantId = "grant-1",
            sessionId = "session-1",
            targetDeviceId = "local",
            policyId = "policy-1",
            packageName = "com.example.social",
            issuedAt = time.wallClockSeconds(),
            expiresAt = time.wallClockSeconds() + 600,
            resetType = ResetType.FRIEND_ACCEPTED,
        )
        val grantEvent = RelayEvent.signed(
            signer = peerSigner,
            type = EventType.UNLOCK_GRANT,
            sessionId = "session-1",
            senderDeviceId = "peer",
            recipientDeviceId = "local",
            sequence = 1,
            timestampSeconds = time.wallClockSeconds(),
            payload = grant.toEventPayload(peerSigner),
            eventId = "grant-event-1",
            nonce = "nonce-grant",
        )

        val result = coordinator.handleInbound(grantEvent)

        assertEquals(ResetState.UNLOCK_ACTIVE, (result as Outcome.Ok).value.state)
        coVerify { unlockWindowManager.redeem(match { it.grantId == "grant-1" }) }
    }

    private fun coordinator(
        relay: ResetRelayApi,
        peerRepository: PeerRepository,
        sessionRepository: SessionRepository,
        unlockWindowManager: UnlockWindowManager = mockk(relaxed = true),
    ): ResetCoordinator =
        ResetCoordinator(
            localDeviceId = { "local" },
            signer = localSigner,
            accessTokenProvider = { "token" },
            relayApi = relay,
            webSocketClient = mockk(relaxed = true),
            peerRepository = peerRepository,
            sessionRepository = sessionRepository,
            unlockWindowManager = unlockWindowManager,
            timeSource = time,
            appScope = TestScope(),
            sessionIdFactory = { "session-1" },
            eventIdFactory = { "grant-1" },
        )

    private fun session(state: ResetState) = ResetSession(
        sessionId = "session-1",
        requesterDeviceId = "local",
        peerDeviceId = "peer",
        blockedPackage = "com.example.social",
        policyId = "policy-1",
        resetType = ResetType.FRIEND_ACCEPTED,
        state = state,
        createdAt = time.wallClockSeconds(),
        expiresAt = time.wallClockSeconds() + 3_600,
    )

    private fun signer(): InMemorySignatureService {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        return InMemorySignatureService(pair.private, B64.encode(pair.public.encoded))
    }

    private class FakeRelayApi(private val time: FakeTimeSource) : ResetRelayApi {
        val events = mutableListOf<RelayEvent>()

        override suspend fun createResetSession(
            bearerToken: String,
            sessionId: String,
            requesterDeviceId: String,
            peerDeviceId: String,
            blockedPackage: String,
            resetType: ResetType,
            ttlSeconds: Int?,
            requestId: String,
        ): ApiClient.ResetSessionStatus =
            ApiClient.ResetSessionStatus(
                sessionId = sessionId,
                requesterDeviceId = requesterDeviceId,
                peerDeviceId = peerDeviceId,
                blockedPackage = blockedPackage,
                resetType = resetType,
                expiresAtEpochSeconds = time.wallClockSeconds() + 3_600,
                state = "open",
            )

        override suspend fun relayEvent(
            bearerToken: String,
            event: RelayEvent,
            requestId: String,
        ): ApiClient.RelayStatus {
            events += event
            return ApiClient.RelayStatus(event.eventId, "relayed")
        }
    }
}
