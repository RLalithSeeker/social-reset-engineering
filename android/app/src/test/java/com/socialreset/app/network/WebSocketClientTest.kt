package com.socialreset.app.network

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class WebSocketClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun connectUsesAuthorizationHeaderNotQueryToken() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.close(1000, "done")
                    }
                }
            )
        )
        val client = WebSocketClient(server.url("/").toString())

        client.connect("dev 1", "secret-token", object : WebSocketClient.Listener {
            override fun onEvent(event: RelayEvent) = Unit
        })
        val request = server.takeRequest()

        assertEquals("Bearer secret-token", request.getHeader("Authorization"))
        assertFalse(request.path.orEmpty().contains("token="))
    }
}
