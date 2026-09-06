package com.socialreset.app.data.repository

import com.socialreset.app.behavior.BehaviorEvent
import com.socialreset.app.core.model.ResetType
import com.socialreset.app.data.db.entities.BehaviorEventEntity
import com.socialreset.app.data.db.entities.BlockedAppEntity
import com.socialreset.app.data.db.entities.ResetSessionEntity
import com.socialreset.app.data.db.entities.ScheduleEntity
import com.socialreset.app.data.db.entities.TrustedPeerEntity
import com.socialreset.app.rules.BlockedApp
import com.socialreset.app.rules.ScheduleRule
import com.socialreset.app.social.PeerStatus
import com.socialreset.app.social.ResetSession
import com.socialreset.app.social.ResetState
import com.socialreset.app.social.TrustedPeer

internal fun BlockedAppEntity.toDomain(schedules: List<ScheduleEntity>): BlockedApp =
    BlockedApp(
        packageName = packageName,
        label = label,
        policyId = policyId,
        enabled = enabled,
        schedules = schedules.map { it.toDomain() },
        unlockSeconds = unlockSeconds,
    )

internal fun BlockedApp.toEntity(): BlockedAppEntity =
    BlockedAppEntity(
        packageName = packageName,
        label = label,
        policyId = policyId,
        enabled = enabled,
        unlockSeconds = unlockSeconds,
    )

internal fun ScheduleEntity.toDomain(): ScheduleRule =
    ScheduleRule(
        id = id,
        label = label,
        daysOfWeek = daysOfWeek
            .split(',')
            .filter { it.isNotBlank() }
            .map { it.toInt() }
            .toSet(),
        startMinuteOfDay = startMinuteOfDay,
        endMinuteOfDay = endMinuteOfDay,
        enabled = enabled,
    )

internal fun ScheduleRule.toEntity(packageName: String): ScheduleEntity =
    ScheduleEntity(
        id = id,
        packageName = packageName,
        label = label,
        daysOfWeek = daysOfWeek.sorted().joinToString(","),
        startMinuteOfDay = startMinuteOfDay,
        endMinuteOfDay = endMinuteOfDay,
        enabled = enabled,
    )

internal fun BehaviorEventEntity.toDomain(): BehaviorEvent =
    BehaviorEvent(
        id = id,
        packageName = packageName,
        kind = BehaviorEvent.Kind.valueOf(kind),
        timestampSeconds = timestampSeconds,
        durationSeconds = durationSeconds,
        duringUnlockWindow = duringUnlockWindow,
    )

internal fun BehaviorEvent.toEntity(): BehaviorEventEntity =
    BehaviorEventEntity(
        id = id,
        packageName = packageName,
        kind = kind.name,
        timestampSeconds = timestampSeconds,
        durationSeconds = durationSeconds,
        duringUnlockWindow = duringUnlockWindow,
    )

internal fun TrustedPeerEntity.toDomain(): TrustedPeer =
    TrustedPeer(
        deviceId = deviceId,
        displayName = displayName,
        publicKey = publicKey,
        fingerprint = fingerprint,
        createdAt = createdAt,
        status = PeerStatus.valueOf(status),
        protocolVersion = protocolVersion,
    )

internal fun TrustedPeer.toEntity(): TrustedPeerEntity =
    TrustedPeerEntity(
        deviceId = deviceId,
        displayName = displayName,
        publicKey = publicKey,
        fingerprint = fingerprint,
        createdAt = createdAt,
        status = status.name,
        protocolVersion = protocolVersion,
    )

internal fun ResetSessionEntity.toDomain(): ResetSession =
    ResetSession(
        sessionId = sessionId,
        requesterDeviceId = requesterDeviceId,
        peerDeviceId = peerDeviceId,
        blockedPackage = blockedPackage,
        policyId = policyId,
        resetType = ResetType.valueOf(resetType),
        state = ResetState.valueOf(state),
        createdAt = createdAt,
        expiresAt = expiresAt,
        lastAcceptedSequence = lastAcceptedSequence,
        nextOutboundSequence = nextOutboundSequence,
        grantId = grantId,
        failureReason = failureReason,
    )

internal fun ResetSession.toEntity(): ResetSessionEntity =
    ResetSessionEntity(
        sessionId = sessionId,
        requesterDeviceId = requesterDeviceId,
        peerDeviceId = peerDeviceId,
        blockedPackage = blockedPackage,
        policyId = policyId,
        resetType = resetType.name,
        state = state.name,
        createdAt = createdAt,
        expiresAt = expiresAt,
        lastAcceptedSequence = lastAcceptedSequence,
        nextOutboundSequence = nextOutboundSequence,
        grantId = grantId,
        failureReason = failureReason,
    )
