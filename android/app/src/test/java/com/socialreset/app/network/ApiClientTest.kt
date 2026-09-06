package com.socialreset.app.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = ApiClient(server.url("/").toString(), retryPolicy = RetryPolicy(maxAttempts = 1))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun registerDeviceMatchesBackendShape() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"deviceId":"dev-1","accessToken":"token","expiresAt":"2026-09-06T10:00:00Z"}"""
                )
        )

        val result = client.registerDevice("dev-1", "Phone", "pub")
        val request = server.takeRequest()

        assertEquals("/v1/devices/register", request.path)
        assertTrue(request.body.readUtf8().contains(""""protocolVersion":1"""))
        assertEquals("token", result.accessToken)
    }

    @Test
    fun createPairingUsesBearerToken() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody(
                    """{"sessionId":"pair-1","code":"ABCDEF12","expiresAt":"2026-09-06T10:00:00Z"}"""
                )
        )

        client.createPairingSession("secret-token", creatorEphemeralPublicKey = "eph", requestId = "req-1")
        val request = server.takeRequest()

        assertEquals("Bearer secret-token", request.getHeader("Authorization"))
        assertEquals("""{"creatorEphemeralPublicKey":"eph","requestId":"req-1"}""", request.body.readUtf8())
    }

    @Test
    fun createPairingAcceptsTimezoneLessBackendTimestampAsUtc() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody(
                    """{"sessionId":"pair-1","code":"ABCDEF12","expiresAt":"2026-09-06T10:00:00.355540"}"""
                )
        )

        val result = client.createPairingSession("secret-token", creatorEphemeralPublicKey = "eph")

        assertEquals(1788688800L, result.expiresAtEpochSeconds)
    }

    @Test
    fun joinPairingReadsPeerFields() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"sessionId":"pair-1","status":"joined","peerDeviceId":"dev-a","peerDisplayName":"A","peerSigningPublicKey":"sig","peerEphemeralPublicKey":"eph"}"""
                )
        )

        val result = client.joinPairingSession(
            bearerToken = "secret-token",
            pairingSessionId = "pair-1",
            code = "code",
            deviceId = "dev-b",
            signingPublicKey = "sig-b",
            ephemeralPublicKey = "eph-b",
            requestId = "join-1",
        )

        assertEquals("dev-a", result.peerDeviceId)
        assertEquals("sig", result.peerSigningPublicKey)
    }
}
