package com.socialreset.app.crypto

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Signing sits behind an interface: production keeps the private key inside the Android
 * Keystore (non-exportable), tests use an in-memory key pair. Verification is a pure
 * function of (public key, message, signature) and is shared by both.
 *
 * Algorithm: ECDSA P-256 / SHA-256. Keystore has supported hardware-backed EC keys since
 * API 23, whereas Ed25519 in Keystore is not available at our minSdk (26). X25519 key
 * agreement is handled separately in [KeyAgreementService].
 */
interface SignatureService {
    /** Sign the canonical bytes of [signedFields]. Returns base64url. */
    fun sign(signedFields: Map<String, Any?>): String

    /** SPKI-encoded public key of this device, base64url. */
    fun publicKey(): String
}

object SignatureVerifier {
    const val ALGORITHM = "SHA256withECDSA"
    const val KEY_ALGORITHM = "EC"

    /**
     * Verify a signature over the canonical form of [signedFields]. Returns false — never
     * throws — for malformed keys or signatures, because every input here is remote.
     */
    fun verify(publicKeyB64: String, signedFields: Map<String, Any?>, signatureB64: String): Boolean {
        val keyBytes = B64.decodeOrNull(publicKeyB64) ?: return false
        val sigBytes = B64.decodeOrNull(signatureB64) ?: return false
        return try {
            val publicKey = KeyFactory.getInstance(KEY_ALGORITHM)
                .generatePublic(X509EncodedKeySpec(keyBytes))
            Signature.getInstance(ALGORITHM).run {
                initVerify(publicKey)
                update(CanonicalJson.encodeToBytes(signedFields))
                verify(sigBytes)
            }
        } catch (e: Exception) {
            false
        }
    }
}

/** Signs with an in-memory key. Used by unit tests and the two-emulator harness. */
class InMemorySignatureService(
    private val privateKey: PrivateKey,
    private val publicKeyB64: String,
) : SignatureService {
    override fun sign(signedFields: Map<String, Any?>): String {
        val signature = Signature.getInstance(SignatureVerifier.ALGORITHM).apply {
            initSign(privateKey)
            update(CanonicalJson.encodeToBytes(signedFields))
        }
        return B64.encode(signature.sign())
    }

    override fun publicKey(): String = publicKeyB64
}
