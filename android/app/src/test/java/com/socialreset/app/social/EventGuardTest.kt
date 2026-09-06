package com.socialreset.app.social

import com.socialreset.app.core.model.EventType
import com.socialreset.app.core.model.ResetType
import com.socialreset.app.core.result.Outcome
import com.socialreset.app.core.result.RejectionReason
import com.socialreset.app.core.time.FakeTimeSource
import com.socialreset.app.crypto.B64
import com.socialreset.app.crypto.InMemorySignatureService
import com.socialreset.app.network.RelayEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

@RunWith(RobolectricTestRunner::class)
class EventGuardTest {
    private val time = FakeTimeSource(wallSeconds = 1_770_000_000, elapsedMillis = 0)
    private val signer = signer()
    private val peer = TrustedPeer(
        deviceId = "peer",
        displayName = "Peer",
        publicKey = signer.publicKey(),
        fingerprint = "BLUE-ORBIT-71-MAPLE",
        createdAt = time.wallClockSeconds(),
        status = PeerStatus.ACTIVE,
        protocolVersion = 1,
    )
    private val session = ResetSession(
        sessionId = "session",
        requesterDeviceId = "local",
        peerDeviceId = "peer",
        blockedPackage = "com.example",
        policyId = "policy",
        resetType = ResetType.FRIEND_ACCEPTED,
        state = ResetState.WAITING_FOR_PEER,
        createdAt = time.wallClockSeconds(),
        expiresAt = time.wallClockSeconds() + 600,
    )

    @Test
    fun acceptsValidEventThenRejectsReplayNonce() {
        val guard = EventGuard(time)
        val event = event(sequence = 0, nonce = "nonce-1")

        assertTrue(guard.accept(event, session, peer, "local") is Outcome.Ok)

        val replay = guard.accept(event.copy(sequence = 1), session, peer, "local")
        assertEquals(RejectionReason.REPLAYED, (replay as Outcome.Rejected).reason)
    }

    @Test
    fun rejectsOutOfOrderSequence() {
        val guard = EventGuard(time)
        val event = event(sequence = 1, nonce = "nonce-2")

        val result = guard.accept(event, session.copy(lastAcceptedSequence = 1), peer, "local")

        assertEquals(RejectionReason.OUT_OF_ORDER, (result as Outcome.Rejected).reason)
    }

    private fun event(sequence: Int, nonce: String): RelayEvent =
        RelayEvent.signed(
            signer = signer,
            type = EventType.RESET_ACCEPTED,
            sessionId = session.sessionId,
            senderDeviceId = "peer",
            recipientDeviceId = "local",
            sequence = sequence,
            timestampSeconds = time.wallClockSeconds(),
            payload = mapOf("ok" to true),
            eventId = "event-$sequence-$nonce",
            nonce = nonce,
        )

    private fun signer(): InMemorySignatureService {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        return InMemorySignatureService(pair.private, B64.encode(pair.public.encoded))
    }
}
