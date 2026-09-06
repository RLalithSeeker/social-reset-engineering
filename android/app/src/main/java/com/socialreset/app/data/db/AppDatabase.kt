package com.socialreset.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.socialreset.app.data.db.dao.BehaviorEventDao
import com.socialreset.app.data.db.dao.BlockedAppDao
import com.socialreset.app.data.db.dao.ResetSessionDao
import com.socialreset.app.data.db.dao.TrustedPeerDao
import com.socialreset.app.data.db.dao.UnlockDao
import com.socialreset.app.data.db.entities.BehaviorEventEntity
import com.socialreset.app.data.db.entities.BlockedAppEntity
import com.socialreset.app.data.db.entities.ConsumedGrantEntity
import com.socialreset.app.data.db.entities.ResetSessionEntity
import com.socialreset.app.data.db.entities.ScheduleEntity
import com.socialreset.app.data.db.entities.TrustedPeerEntity
import com.socialreset.app.data.db.entities.UnlockWindowEntity

@Database(
    entities = [
        BlockedAppEntity::class,
        ScheduleEntity::class,
        TrustedPeerEntity::class,
        ResetSessionEntity::class,
        BehaviorEventEntity::class,
        ConsumedGrantEntity::class,
        UnlockWindowEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun trustedPeerDao(): TrustedPeerDao
    abstract fun resetSessionDao(): ResetSessionDao
    abstract fun behaviorEventDao(): BehaviorEventDao
    abstract fun unlockDao(): UnlockDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "social_reset.db")
                // No destructive fallback: losing the peer table would silently disarm every
                // block. A future schema change gets a real migration.
                .build()
    }
}
