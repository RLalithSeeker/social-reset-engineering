package com.socialreset.app.network

import com.socialreset.app.core.model.Protocol
import com.socialreset.app.core.model.ResetType
import com.socialreset.app.crypto.CanonicalJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

interface ResetRelayApi {
    suspend fun createResetSession(
        bearerToken: String,
        sessionId: String,
        requesterDeviceId: String,
        peerDeviceId: String,
        blockedPackage: String,
        resetType: ResetType,
        ttlSeconds: Int? = null,
        requestId: String = UUID.randomUUID().toString(),
    ): ApiClient.ResetSessionStatus

    suspend fun relayEvent(
        bearerToken: String,
        event: RelayEvent,
        requestId: String = event.eventId,
    ): ApiClient.RelayStatus
}

class ApiClient(
    private val baseUrl: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) : ResetRelayApi {
    data class DeviceRegistration(
        val deviceId: String,
        val accessToken: String,
        val expiresAtEpochSeconds: Long,
    )

    data class PairingSession(
        val sessionId: String,
        val code: String,
        val expiresAtEpochSeconds: Long,
    )

    data class PairingStatus(
        val sessionId: String,
        val status: String,
        val peerDeviceId: String? = null,
        val peerDisplayName: String? = null,
        val peerSigningPublicKey: String? = null,
        val peerEphemeralPublicKey: String? = null,
    )

    data class ResetSessionStatus(
        val sessionId: String,
        val requesterDeviceId: String,
        val peerDeviceId: String,
        val blockedPackage: String,
        val resetType: ResetType,
        val expiresAtEpochSeconds: Long,
        val state: String,
    )

    data class RelayStatus(
        val eventId: String,
        val status: String,
    )

    suspend fun registerDevice(
        deviceId: String,
        displayName: String,
        signingPublicKey: String,
    ): DeviceRegistration = retryPolicy.run {
        val obj = postJson(
            path = "/v1/devices/register",
            bearerToken = null,
            body = mapOf(
                "deviceId" to deviceId,
                "protocolVersion" to Protocol.VERSION,
                "displayName" to displayName,
                "signingPublicKey" to signingPublicKey,
            )
        )
        DeviceRegistration(
            deviceId = obj.requireString("deviceId"),
            accessToken = obj.requireString("accessToken"),
            expiresAtEpochSeconds = obj.requireInstant("expiresAt"),
        )
    }

    suspend fun createPairingSession(
        bearerToken: String,
        creatorEphemeralPublicKey: String? = null,
        requestId: String = UUID.randomUUID().toString(),
    ): PairingSession = retryPolicy.run {
        val obj = postJson(
            path = "/v1/pairing/sessions",
            bearerToken = bearerToken,
            body = mapOf(
                "requestId" to requestId,
                "creatorEphemeralPublicKey" to creatorEphemeralPublicKey,
            ),
        )
        PairingSession(
            sessionId = obj.requireString("sessionId"),
            code = obj.requireString("code"),
            expiresAtEpochSeconds = obj.requireInstant("expiresAt"),
        )
    }

    suspend fun joinPairingSession(
        bearerToken: String,
        pairingSessionId: String,
        code: String,
        deviceId: String,
        signingPublicKey: String,
        ephemeralPublicKey: String,
        requestId: String = UUID.randomUUID().toString(),
    ): PairingStatus = retryPolicy.run {
        val obj = postJson(
            path = "/v1/pairing/sessions/$pairingSessionId/join",
            bearerToken = bearerToken,
            body = mapOf(
                "code" to code,
                "deviceId" to deviceId,
                "signingPublicKey" to signingPublicKey,
                "ephemeralPublicKey" to ephemeralPublicKey,
                "protocolVersion" to Protocol.VERSION,
                "requestId" to requestId,
            )
        )
        obj.toPairingStatus()
    }

    suspend fun completePairingSession(
        bearerToken: String,
        pairingSessionId: String,
        requestId: String = UUID.randomUUID().toString(),
    ): PairingStatus = retryPolicy.run {
        val obj = postJson(
            path = "/v1/pairing/sessions/$pairingSessionId/complete",
            bearerToken = bearerToken,
            body = mapOf("requestId" to requestId),
        )
        obj.toPairingStatus()
    }

    override suspend fun createResetSession(
        bearerToken: String,
        sessionId: String,
        requesterDeviceId: String,
        peerDeviceId: String,
        blockedPackage: String,
        resetType: ResetType,
        ttlSeconds: Int?,
        requestId: String,
    ): ResetSessionStatus = retryPolicy.run {
        val obj = postJson(
            path = "/v1/sessions",
            bearerToken = bearerToken,
            body = mapOf(
                "sessionId" to sessionId,
                "requestId" to requestId,
                "requesterDeviceId" to requesterDeviceId,
                "peerDeviceId" to peerDeviceId,
                "blockedPackage" to blockedPackage,
                "resetType" to resetType.name,
                "ttl_seconds" to ttlSeconds,
            )
        )
        obj.toResetSessionStatus()
    }

    override suspend fun relayEvent(
        bearerToken: String,
        event: RelayEvent,
        requestId: String,
    ): RelayStatus = retryPolicy.run {
        val obj = postRawJson(
            path = "/v1/sessions/${event.sessionId}/events",
            bearerToken = bearerToken,
            body = event.toRequestBody(requestId),
        )
        RelayStatus(
            eventId = obj.requireString("eventId"),
            status = obj.string("status") ?: "relayed",
        )
    }

    suspend fun getResetSession(
        bearerToken: String,
        sessionId: String,
    ): ResetSessionStatus = retryPolicy.run {
        getJson("/v1/sessions/$sessionId", bearerToken).toResetSessionStatus()
    }

    private suspend fun postJson(path: String, bearerToken: String?, body: Map<String, Any?>): JsonObject =
        postRawJson(path, bearerToken, CanonicalJson.encode(body))

    private suspend fun postRawJson(path: String, bearerToken: String?, body: String): JsonObject =
        executeJson(
            Request.Builder()
                .url(baseUrl.trimEnd('/') + path)
                .applyBearer(bearerToken)
                .post(body.toRequestBody(JSON))
                .build()
        )

    private suspend fun getJson(path: String, bearerToken: String): JsonObject =
        executeJson(
            Request.Builder()
                .url(baseUrl.trimEnd('/') + path)
                .applyBearer(bearerToken)
                .get()
                .build()
        )

    private suspend fun executeJson(request: Request): JsonObject = withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(response.code, body)
            }
            JSON_PARSER.parseToJsonElement(body) as? JsonObject
                ?: throw ApiException(response.code, "response was not a JSON object")
        }
    }

    private fun JsonObject.toResetSessionStatus(): ResetSessionStatus =
        ResetSessionStatus(
            sessionId = requireString("sessionId"),
            requesterDeviceId = requireString("requesterDeviceId"),
            peerDeviceId = requireString("peerDeviceId"),
            blockedPackage = requireString("blockedPackage"),
            resetType = ResetType.valueOf(requireString("resetType")),
            expiresAtEpochSeconds = requireInstant("expiresAt"),
            state = requireString("state"),
        )

    private fun JsonObject.toPairingStatus(): PairingStatus =
        PairingStatus(
            sessionId = requireString("sessionId"),
            status = requireString("status"),
            peerDeviceId = string("peerDeviceId"),
            peerDisplayName = string("peerDisplayName"),
            peerSigningPublicKey = string("peerSigningPublicKey"),
            peerEphemeralPublicKey = string("peerEphemeralPublicKey"),
        )

    private fun Request.Builder.applyBearer(token: String?): Request.Builder =
        if (token.isNullOrBlank()) this else header("Authorization", "Bearer $token")

    class ApiException(val statusCode: Int, message: String) : Exception("HTTP $statusCode: $message")

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val JSON_PARSER = Json { ignoreUnknownKeys = true }
    }
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.content

private fun JsonObject.requireString(key: String): String =
    string(key) ?: throw ApiClient.ApiException(200, "missing string field $key")

private fun JsonObject.requireInstant(key: String): Long =
    string(key)?.let { parseApiInstant(it).epochSecond }
        ?: (this[key] as? JsonPrimitive)?.longOrNull
        ?: (this[key] as? JsonPrimitive)?.intOrNull?.toLong()
        ?: throw ApiClient.ApiException(200, "missing timestamp field $key")

private fun parseApiInstant(value: String): Instant =
    runCatching { Instant.parse(value) }
        .getOrElse { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }
