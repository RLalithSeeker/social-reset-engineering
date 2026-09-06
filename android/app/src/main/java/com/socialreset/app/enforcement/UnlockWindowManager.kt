package com.socialreset.app.enforcement

import com.socialreset.app.core.result.Outcome
import com.socialreset.app.core.result.RejectionReason
import com.socialreset.app.core.time.TimeSource
import com.socialreset.app.data.db.dao.UnlockDao
import com.socialreset.app.data.db.entities.ConsumedGrantEntity
import com.socialreset.app.data.db.entities.UnlockWindowEntity
import com.socialreset.app.social.UnlockGrant
import java.io.File

class UnlockWindowManager(
    private val dao: UnlockDao,
    private val timeSource: TimeSource,
    private val bootIdProvider: () -> String = ::currentBootId,
) {
    suspend fun activePackage(): String? {
        val window = dao.current() ?: return null
        if (!window.isActive()) {
            dao.clear()
            return null
        }
        return window.packageName
    }

    suspend fun isActiveFor(packageName: String): Boolean =
        activePackage() == packageName

    suspend fun hasConsumed(grantId: String): Boolean =
        dao.consumedGrant(grantId) != null

    suspend fun redeem(grant: UnlockGrant): Outcome<UnlockWindowEntity> {
        if (dao.consumedGrant(grant.grantId) != null) {
            return Outcome.Rejected(RejectionReason.REPLAYED)
        }
        if (grant.expiresAt <= timeSource.wallClockSeconds()) {
            return Outcome.Rejected(RejectionReason.EXPIRED)
        }

        val remainingMillis = (grant.expiresAt - timeSource.wallClockSeconds()) * 1000
        val window = UnlockWindowEntity(
            packageName = grant.packageName,
            policyId = grant.policyId,
            grantId = grant.grantId,
            sessionId = grant.sessionId,
            issuedAtWallSeconds = grant.issuedAt,
            expiresAtWallSeconds = grant.expiresAt,
            expiresAtElapsedMillis = timeSource.elapsedRealtimeMillis() + remainingMillis,
            bootId = bootIdProvider(),
        )
        dao.markConsumed(
            ConsumedGrantEntity(
                grantId = grant.grantId,
                sessionId = grant.sessionId,
                packageName = grant.packageName,
                consumedAt = timeSource.wallClockSeconds(),
                expiresAt = grant.expiresAt,
            )
        )
        dao.set(window)
        return Outcome.Ok(window)
    }

    suspend fun clear() {
        dao.clear()
    }

    suspend fun pruneConsumed(cutoffSeconds: Long) {
        dao.pruneConsumed(cutoffSeconds)
    }

    private fun UnlockWindowEntity.isActive(): Boolean =
        bootId == bootIdProvider() &&
            expiresAtWallSeconds > timeSource.wallClockSeconds() &&
            expiresAtElapsedMillis > timeSource.elapsedRealtimeMillis()
}

private fun currentBootId(): String =
    runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }
        .getOrDefault("unknown")
