package com.socialreset.app.crypto

/**
 * Human-verifiable short code, e.g. `BLUE-ORBIT-71-MAPLE`.
 *
 * Both devices show it before pairing is finalised; the users compare out of band. It is
 * derived from the full pairing transcript (both public keys + derived session key) so a
 * man-in-the-middle that swapped a key cannot produce a matching code.
 *
 * ~11 bits per word + 7 bits of number = about 40 bits of comparison material, which is
 * the point where a human will actually read the whole thing aloud.
 */
object FingerprintFormatter {

    private val ADJECTIVES = listOf(
        "BLUE", "RED", "GREEN", "AMBER", "VIOLET", "SILVER", "GOLDEN", "IRON",
        "CORAL", "JADE", "SLATE", "IVORY", "COBALT", "CRIMSON", "OLIVE", "RUST",
        "FROST", "EMBER", "DUSK", "DAWN", "STORM", "CALM", "SWIFT", "STILL",
        "BRIGHT", "DEEP", "WILD", "QUIET", "SHARP", "SOFT", "TALL", "BROAD",
    )

    private val NOUNS = listOf(
        "ORBIT", "RIVER", "MAPLE", "CANYON", "HARBOR", "LANTERN", "MEADOW", "SUMMIT",
        "ANCHOR", "COMPASS", "FALCON", "GLACIER", "HOLLOW", "JUNIPER", "KESTREL", "LEDGER",
        "MARBLE", "NEBULA", "OTTER", "PRISM", "QUARRY", "RIDGE", "SIGNAL", "THICKET",
        "UMBRA", "VALLEY", "WILLOW", "YARROW", "ZENITH", "BEACON", "CIPHER", "DELTA",
    )

    /** Fingerprint of a bare public key — used for "this is my own identity" display. */
    fun forKey(publicKeyB64: String): String =
        format(Digest.sha256(publicKeyB64.toByteArray(Charsets.UTF_8)))

    /**
     * Fingerprint of a pairing transcript. Both sides must pass the same inputs; keys are
     * sorted so neither side depends on being the initiator.
     */
    fun forTranscript(
        localPublicKeyB64: String,
        peerPublicKeyB64: String,
        sessionKey: ByteArray,
    ): String {
        val ordered = listOf(localPublicKeyB64, peerPublicKeyB64).sorted()
        val transcript = (ordered[0] + "|" + ordered[1] + "|").toByteArray(Charsets.UTF_8) + sessionKey
        return format(Digest.sha256(transcript))
    }

    private fun format(hash: ByteArray): String {
        fun idx(i: Int) = (hash[i].toInt() and 0xFF) % 32
        val number = (hash[4].toInt() and 0xFF) % 100
        return buildString {
            append(ADJECTIVES[idx(0)]).append('-')
            append(NOUNS[idx(1)]).append('-')
            append(String.format("%02d", number)).append('-')
            append(NOUNS[idx(2)])
        }
    }
}
