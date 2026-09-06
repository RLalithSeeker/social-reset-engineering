package com.socialreset.app.crypto

import java.security.MessageDigest
import java.security.SecureRandom

object Digest {
    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    /** Hash of a canonical structure, base64url encoded — the `payloadHash` wire field. */
    fun canonicalHash(value: Any?): String = B64.encode(sha256(CanonicalJson.encodeToBytes(value)))

    /** Constant-time compare for anything derived from remote input. */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun randomB64(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return B64.encode(bytes)
    }
}
