package com.socialreset.app.network

import com.socialreset.app.core.logging.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.URLEncoder

class WebSocketClient(
    private val wsBaseUrl: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    interface Listener {
        fun onEvent(event: RelayEvent)
        fun onConnected() = Unit
        fun onClosed(reason: String) = Unit
        fun onFailure(error: Throwable) = Unit
    }

    fun connect(deviceId: String, accessToken: String, listener: Listener): WebSocket {
        val url = "${wsBaseUrl.trimEnd('/')}/v1/ws/${deviceId.urlEncode()}"
        Log.i("Opening relay websocket for ${deviceId.take(8)}")
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .build()
        return httpClient.newWebSocket(request, RelayListener(listener))
    }

    private class RelayListener(private val listener: Listener) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i("Relay websocket opened")
            listener.onConnected()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            RelayEvent.parse(text)?.let(listener::onEvent)
                ?: Log.w("Dropped malformed websocket text frame")
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            RelayEvent.parse(bytes.utf8())?.let(listener::onEvent)
                ?: Log.w("Dropped malformed websocket binary frame")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.w("Relay websocket closed: $code $reason")
            listener.onClosed(reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e("Relay websocket failed", t)
            listener.onFailure(t)
        }
    }
}

private fun String.urlEncode(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name())
