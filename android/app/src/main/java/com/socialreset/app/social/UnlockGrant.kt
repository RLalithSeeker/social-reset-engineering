package com.socialreset.app.social

import com.socialreset.app.core.model.Protocol
import com.socialreset.app.core.model.ResetType
import com.socialreset.app.crypto.SignatureService

/**
 * The only thing in the system that can open a blocked app.
 *
 * It is produced by the peer's device, signed with the peer's identity key, and relayed
 * byte-for-byte. The server cannot mint one: there is no server endpoint that authorises
 * app access, and the server never signs anything.
 */
data class UnlockGrant(
    val version: Int = Protocol.VERSION,
    val grantId: String,
    val sessionId: String,
    val targetDeviceId: String,
    val policyId: String,
    val packageName: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val resetType: ResetType,
) {
    val durationSeconds: Long get() = expiresAt - issuedAt

    /** Canonical payload; this exact map is what gets signed and what gets hashed. */
    fun canonicalPayload(): Map<String, Any?> = mapOf(
        "v" to version,
        "grantId" to grantId,
        "sessionId" to sessionId,
        "targetDeviceId" to targetDeviceId,
        "policyId" to policyId,
        "packageName" to packageName,
        "issuedAt" to issuedAt,
        "expiresAt" to expiresAt,
        "resetType" to resetType.name,
    )

    /** Payload of the UNLOCK_GRANT relay event: the grant plus its detached signature. */
    fun toEventPayload(signer: SignatureService): Map<String, Any?> =
        canonicalPayload() + mapOf("grantSignature" to signer.sign(canonicalPayload()))

    companion object {
        /** Rebuild a grant from a received event payload. Null if any field is off-shape. */
        fun fromEventPayload(payload: Map<String, Any?>): Pair<UnlockGrant, String>? {
            fun str(key: String) = payload[key] as? String
            fun num(key: String) = (payload[key] as? Long) ?: (payload[key] as? Int)?.toLong()

            val grant = UnlockGrant(
                version = num("v")?.toInt() ?: return null,
                grantId = str("grantId") ?: return null,
                sessionId = str("sessionId") ?: return null,
                targetDeviceId = str("targetDeviceId") ?: return null,
                policyId = str("policyId") ?: return null,
                packageName = str("packageName") ?: return null,
                issuedAt = num("issuedAt") ?: return null,
                expiresAt = num("expiresAt") ?: return null,
                resetType = ResetType.entries.firstOrNull { it.name == str("resetType") } ?: return null,
            )
            val signature = str("grantSignature") ?: return null
            return grant to signature
        }
    }
}
