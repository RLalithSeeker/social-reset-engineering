package com.socialreset.app.crypto

import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.X25519

/**
 * Ephemeral key agreement for pairing.
 *
 * Keystore key agreement (AGREE_KEY) only exists from API 31, and the protocol calls for
 * X25519, so pairing uses Tink's X25519 with the ephemeral private key held in memory for
 * the lifetime of one pairing attempt and never persisted (see the protocol spec: no
 * ephemeral private material outside protected storage).
 *
 * The derived session key is not what authorises anything — identity signatures do that.
 * Its job is to bind both devices to the same pairing transcript so the confirmation
 * fingerprint cannot be produced by a third party that only replayed the QR.
 */
class KeyAgreementService {

    /** One pairing attempt. Discard by dropping the reference; nothing is written to disk. */
    class Ephemeral internal constructor(private val privateKey: ByteArray) {
        val publicKeyB64: String = B64.encode(X25519.publicFromPrivate(privateKey))

        /**
         * Derive the pairing session key.
         *
         * context = "social-reset-pair-v1" || ordered(deviceIds) || pairingSessionId
         * The device ids are sorted so both sides build byte-identical context without
         * needing to agree on who is "A".
         */
        fun deriveSessionKey(
            peerPublicKeyB64: String,
            pairingCode: String,
            pairingSessionId: String,
            localDeviceId: String,
            peerDeviceId: String,
        ): ByteArray? {
            val peerPublic = B64.decodeOrNull(peerPublicKeyB64) ?: return null
            val shared = try {
                X25519.computeSharedSecret(privateKey, peerPublic)
            } catch (e: Exception) {
                return null
            }
            val ordered = listOf(localDeviceId, peerDeviceId).sorted()
            val info = (CONTEXT_PREFIX + ordered[0] + "|" + ordered[1] + "|" + pairingSessionId)
                .toByteArray(Charsets.UTF_8)
            return Hkdf.computeHkdf(
                "HMACSHA256",
                shared,
                pairingCode.toByteArray(Charsets.UTF_8),
                info,
                32,
            )
        }
    }

    fun newEphemeral(): Ephemeral = Ephemeral(X25519.generatePrivateKey())

    companion object {
        const val CONTEXT_PREFIX = "social-reset-pair-v1|"
    }
}
