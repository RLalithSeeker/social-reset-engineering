package com.socialreset.app.network

import com.socialreset.app.core.model.EventType
import com.socialreset.app.core.model.Protocol
import com.socialreset.app.crypto.CanonicalJson
import com.socialreset.app.crypto.Digest
import com.socialreset.app.crypto.SignatureService
import com.socialreset.app.crypto.SignatureVerifier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/**
 * A signed event as it travels through the relay.
 *
 * The relay validates structure only and forwards bytes untouched, so the security of the
 * whole system rests on this class: the sender signs the canonical form of the envelope's
 * header fields plus a hash of the payload, and the receiver recomputes both from what it
 * parsed — never from what the server claims.
 */
data class RelayEvent(
    val protocolVersion: Int,
    val eventId: String,
    val sessionId: String,
    val senderDeviceId: String,
    val recipientDeviceId: String,
    val sequence: Int,
    val type: String,
    val timestamp: Long,
    val nonce: String,
    val payload: Map<String, Any?>,
    val signature: String,
) {
    val eventType: EventType? get() = EventType.entries.firstOrNull { it.name == type }

    /** Exactly the fields covered by [signature]. Payload enters only as a hash. */
    fun signedFields(): Map<String, Any?> = signedFields(
        protocolVersion = protocolVersion,
        eventId = eventId,
        sessionId = sessionId,
        senderDeviceId = senderDeviceId,
        recipientDeviceId = recipientDeviceId,
        sequence = sequence,
        type = type,
        timestamp = timestamp,
        nonce = nonce,
        payloadHash = Digest.canonicalHash(payload),
    )

    /** Body sent to `POST /v1/sessions/{id}/events`. Also the idempotency key source. */
    fun toRequestBody(requestId: String = eventId): String = CanonicalJson.encode(
        mapOf(
            "protocolVersion" to protocolVersion,
            "requestId" to requestId,
            "eventId" to eventId,
            "sessionId" to sessionId,
            "senderDeviceId" to senderDeviceId,
            "recipientDeviceId" to recipientDeviceId,
            "sequence" to sequence,
            "type" to type,
            "timestamp" to timestamp,
            "nonce" to nonce,
            "payload" to payload,
            "signature" to signature,
        )
    )

    fun verifySignature(senderPublicKey: String): Boolean =
        SignatureVerifier.verify(senderPublicKey, signedFields(), signature)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = false }

        private fun signedFields(
            protocolVersion: Int,
            eventId: String,
            sessionId: String,
            senderDeviceId: String,
            recipientDeviceId: String,
            sequence: Int,
            type: String,
            timestamp: Long,
            nonce: String,
            payloadHash: String,
        ): Map<String, Any?> = mapOf(
            "v" to protocolVersion,
            "eventId" to eventId,
            "sessionId" to sessionId,
            "senderDeviceId" to senderDeviceId,
            "recipientDeviceId" to recipientDeviceId,
            "seq" to sequence,
            "type" to type,
            "timestamp" to timestamp,
            "nonce" to nonce,
            "payloadHash" to payloadHash,
        )

        /** Build and sign an outgoing event. */
        fun signed(
            signer: SignatureService,
            type: EventType,
            sessionId: String,
            senderDeviceId: String,
            recipientDeviceId: String,
            sequence: Int,
            timestampSeconds: Long,
            payload: Map<String, Any?>,
            eventId: String = java.util.UUID.randomUUID().toString(),
            nonce: String = Digest.randomB64(16),
        ): RelayEvent {
            val fields = signedFields(
                protocolVersion = Protocol.VERSION,
                eventId = eventId,
                sessionId = sessionId,
                senderDeviceId = senderDeviceId,
                recipientDeviceId = recipientDeviceId,
                sequence = sequence,
                type = type.name,
                timestamp = timestampSeconds,
                nonce = nonce,
                payloadHash = Digest.canonicalHash(payload),
            )
            return RelayEvent(
                protocolVersion = Protocol.VERSION,
                eventId = eventId,
                sessionId = sessionId,
                senderDeviceId = senderDeviceId,
                recipientDeviceId = recipientDeviceId,
                sequence = sequence,
                type = type.name,
                timestamp = timestampSeconds,
                nonce = nonce,
                payload = payload,
                signature = signer.sign(fields),
            )
        }

        /** Parse an inbound frame. Returns null for anything malformed; never throws. */
        fun parse(raw: String): RelayEvent? {
            return try {
                val obj = json.parseToJsonElement(raw) as? JsonObject ?: return null
                RelayEvent(
                    protocolVersion = obj.int("protocolVersion") ?: return null,
                    eventId = obj.str("eventId") ?: return null,
                    sessionId = obj.str("sessionId") ?: return null,
                    senderDeviceId = obj.str("senderDeviceId") ?: return null,
                    recipientDeviceId = obj.str("recipientDeviceId") ?: return null,
                    sequence = obj.int("sequence") ?: return null,
                    type = obj.str("type") ?: return null,
                    timestamp = obj.long("timestamp") ?: return null,
                    nonce = obj.str("nonce") ?: return null,
                    payload = (obj["payload"] as? JsonObject)?.let { toMap(it) } ?: return null,
                    signature = obj.str("signature") ?: return null,
                )
            } catch (e: Exception) {
                null
            }
        }

        /**
         * JSON -> canonicalizable map. Numbers become Long, so a payload that round-trips
         * through the relay hashes to the same value it did before it was sent.
         */
        fun toMap(obj: JsonObject): Map<String, Any?> = obj.mapValues { (_, element) -> toValue(element) }

        private fun toValue(element: kotlinx.serialization.json.JsonElement): Any? = when (element) {
            is JsonNull -> null
            is JsonObject -> toMap(element)
            is JsonArray -> element.map { toValue(it) }
            is JsonPrimitive -> when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.booleanOrNull
                element.longOrNull != null -> element.longOrNull
                else -> element.content
            }
        }

        private fun JsonObject.str(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun JsonObject.long(key: String): Long? =
            (this[key] as? JsonPrimitive)?.longOrNull

        private fun JsonObject.int(key: String): Int? = long(key)?.toInt()
    }
}
