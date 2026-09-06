package com.socialreset.app.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature

/**
 * Owns the durable identity: a random 128-bit device id plus a Keystore-resident ECDSA
 * P-256 signing key. The private key is generated inside the Keystore and never leaves
 * it — signing hands the message to the Keystore, not the other way round.
 *
 * Signing does not require user authentication: an unlock grant must be verifiable with
 * the screen off, and the threat model already excludes an attacker holding the unlocked
 * device.
 */
class DeviceIdentityManager(context: Context) : SignatureService {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val deviceId: String by lazy {
        prefs.getString(KEY_DEVICE_ID, null) ?: newDeviceId().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
    }

    var displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DISPLAY_NAME, value.take(64)).apply()

    init {
        ensureKeyPair()
    }

    override fun publicKey(): String {
        val certificate = keyStore().getCertificate(KEY_ALIAS)
            ?: error("Identity key missing after generation")
        return B64.encode(certificate.publicKey.encoded)
    }

    override fun sign(signedFields: Map<String, Any?>): String {
        val entry = keyStore().getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        val signature = Signature.getInstance(SignatureVerifier.ALGORITHM).apply {
            initSign(entry.privateKey)
            update(CanonicalJson.encodeToBytes(signedFields))
        }
        return B64.encode(signature.sign())
    }

    /** Short code shown in Settings so a user can read their own identity aloud. */
    fun ownFingerprint(): String = FingerprintFormatter.forKey(publicKey())

    private fun ensureKeyPair() {
        if (keyStore().containsAlias(KEY_ALIAS)) return
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER)
        generator.initialize(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        generator.generateKeyPair()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun newDeviceId(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return B64.encode(bytes)
    }

    companion object {
        private const val PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "social_reset_identity_v1"
        private const val PREFS = "social_reset_identity"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}
