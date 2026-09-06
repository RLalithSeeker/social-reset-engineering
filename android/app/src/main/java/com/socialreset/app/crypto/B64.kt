package com.socialreset.app.crypto

import android.util.Base64

/** base64url without padding — the only binary encoding used on the wire. */
object B64 {
    private const val FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, FLAGS)

    /** Remote input: returns null instead of throwing. */
    fun decodeOrNull(value: String): ByteArray? = try {
        Base64.decode(value, FLAGS)
    } catch (e: IllegalArgumentException) {
        null
    }
}
