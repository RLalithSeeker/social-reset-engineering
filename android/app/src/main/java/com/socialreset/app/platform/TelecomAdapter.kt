package com.socialreset.app.platform

/**
 * Isolates every telephony assumption behind one interface so the core flow never
 * hard-depends on hardware the emulator cannot reproduce.
 *
 * The default implementation reports no trusted evidence, which is the honest answer on
 * essentially every device: nothing here is proven until it is measured on real hardware.
 */
interface TelecomAdapter {
    fun hasTrustedLifecycleEvidence(): Boolean
    fun lastConnectedSeconds(): Long?
}

class NoTelecomEvidenceAdapter : TelecomAdapter {
    override fun hasTrustedLifecycleEvidence(): Boolean = false
    override fun lastConnectedSeconds(): Long? = null
}
