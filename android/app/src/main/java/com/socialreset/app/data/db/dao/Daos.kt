package com.socialreset.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.socialreset.app.data.db.entities.BehaviorEventEntity
import com.socialreset.app.data.db.entities.BlockedAppEntity
import com.socialreset.app.data.db.entities.ConsumedGrantEntity
import com.socialreset.app.data.db.entities.ResetSessionEntity
import com.socialreset.app.data.db.entities.ScheduleEntity
import com.socialreset.app.data.db.entities.TrustedPeerEntity
import com.socialreset.app.data.db.entities.UnlockWindowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps")
    fun observeApps(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps")
    suspend fun allApps(): List<BlockedAppEntity>

    @Query("SELECT * FROM schedules")
    fun observeSchedules(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules")
    suspend fun allSchedules(): List<ScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApp(app: BlockedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedules(schedules: List<ScheduleEntity>)

    @Query("DELETE FROM schedules WHERE packageName = :packageName")
    suspend fun clearSchedules(packageName: String)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun deleteApp(packageName: String)

    @Transaction
    suspend fun replaceApp(app: BlockedAppEntity, schedules: List<ScheduleEntity>) {
        upsertApp(app)
        clearSchedules(app.packageName)
        upsertSchedules(schedules)
    }
}

@Dao
interface TrustedPeerDao {
    @Query("SELECT * FROM trusted_peers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TrustedPeerEntity>>

    @Query("SELECT * FROM trusted_peers WHERE deviceId = :deviceId")
    suspend fun byId(deviceId: String): TrustedPeerEntity?

    @Query("SELECT * FROM trusted_peers")
    suspend fun all(): List<TrustedPeerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(peer: TrustedPeerEntity)

    @Query("UPDATE trusted_peers SET status = :status WHERE deviceId = :deviceId")
    suspend fun setStatus(deviceId: String, status: String)
}

@Dao
interface ResetSessionDao {
    @Query("SELECT * FROM reset_sessions WHERE sessionId = :sessionId")
    suspend fun byId(sessionId: String): ResetSessionEntity?

    @Query("SELECT * FROM reset_sessions WHERE state NOT IN ('LOCKED','FAILED','CANCELLED') ORDER BY createdAt DESC")
    fun observeOpen(): Flow<List<ResetSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ResetSessionEntity)

    @Query("DELETE FROM reset_sessions WHERE expiresAt < :cutoff AND state IN ('LOCKED','FAILED','CANCELLED')")
    suspend fun pruneClosed(cutoff: Long)
}

@Dao
interface BehaviorEventDao {
    @Insert
    suspend fun insert(event: BehaviorEventEntity)

    @Query("SELECT * FROM behavior_events WHERE packageName = :packageName AND timestampSeconds >= :since ORDER BY timestampSeconds")
    suspend fun recentFor(packageName: String, since: Long): List<BehaviorEventEntity>

    @Query("DELETE FROM behavior_events WHERE timestampSeconds < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)
}

@Dao
interface UnlockDao {
    @Query("SELECT * FROM unlock_windows WHERE id = 0")
    suspend fun current(): UnlockWindowEntity?

    @Query("SELECT * FROM unlock_windows WHERE id = 0")
    fun observeCurrent(): Flow<UnlockWindowEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(window: UnlockWindowEntity)

    @Query("DELETE FROM unlock_windows")
    suspend fun clear()

    @Query("SELECT * FROM consumed_grants WHERE grantId = :grantId")
    suspend fun consumedGrant(grantId: String): ConsumedGrantEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markConsumed(grant: ConsumedGrantEntity)

    @Query("DELETE FROM consumed_grants WHERE expiresAt < :cutoff")
    suspend fun pruneConsumed(cutoff: Long)
}
