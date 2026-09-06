package com.socialreset.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Storage shapes. They are deliberately separate from the domain models: the domain types
 * are what the security logic reasons about, these are what SQLite happens to hold, and
 * conflating the two makes a schema change a protocol change.
 */

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val policyId: String,
    val enabled: Boolean,
    val unlockSeconds: Long,
)

@Entity(
    tableName = "schedules",
    indices = [Index("packageName")],
)
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val label: String,
    /** Comma-separated ISO day numbers, 1 = Monday. */
    val daysOfWeek: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val enabled: Boolean,
)

@Entity(tableName = "trusted_peers")
data class TrustedPeerEntity(
    @PrimaryKey val deviceId: String,
    val displayName: String,
    val publicKey: String,
    val fingerprint: String,
    val createdAt: Long,
    val status: String,
    val protocolVersion: Int,
)

@Entity(
    tableName = "reset_sessions",
    indices = [Index("peerDeviceId"), Index("blockedPackage")],
)
data class ResetSessionEntity(
    @PrimaryKey val sessionId: String,
    val requesterDeviceId: String,
    val peerDeviceId: String,
    val blockedPackage: String,
    val policyId: String,
    val resetType: String,
    val state: String,
    val createdAt: Long,
    val expiresAt: Long,
    val lastAcceptedSequence: Int,
    val nextOutboundSequence: Int,
    val grantId: String?,
    val failureReason: String?,
)

@Entity(
    tableName = "behavior_events",
    indices = [Index("packageName"), Index("timestampSeconds")],
)
data class BehaviorEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val kind: String,
    val timestampSeconds: Long,
    val durationSeconds: Long?,
    val duringUnlockWindow: Boolean,
)

/**
 * Grants that have already been redeemed. Kept past their expiry so a replayed grant is
 * still rejected as REPLAYED rather than merely EXPIRED, which is a much clearer signal
 * when reading diagnostics.
 */
@Entity(tableName = "consumed_grants")
data class ConsumedGrantEntity(
    @PrimaryKey val grantId: String,
    val sessionId: String,
    val packageName: String,
    val consumedAt: Long,
    val expiresAt: Long,
)

/**
 * The live unlock window, at most one row.
 *
 * Both clocks are stored: wall clock for audit and for matching the signed grant,
 * elapsed-realtime for the countdown that actually gates enforcement, so editing the
 * system clock cannot extend a window. `bootId` invalidates the monotonic value after a
 * reboot, when elapsed-realtime restarts from zero.
 */
@Entity(tableName = "unlock_windows")
data class UnlockWindowEntity(
    @PrimaryKey val id: Int = 0,
    val packageName: String,
    val policyId: String,
    val grantId: String,
    val sessionId: String,
    val issuedAtWallSeconds: Long,
    val expiresAtWallSeconds: Long,
    val expiresAtElapsedMillis: Long,
    val bootId: String,
)
