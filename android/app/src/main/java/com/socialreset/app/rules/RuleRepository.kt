package com.socialreset.app.rules

import kotlinx.coroutines.flow.Flow

/**
 * Read model for enforcement. The accessibility callback runs on the main thread and must
 * not touch the database, so [snapshot] is the cached view the enforcement path reads and
 * [observe] is what keeps it fresh.
 */
interface RuleRepository {
    fun observe(): Flow<List<BlockedApp>>
    fun snapshot(): List<BlockedApp>
    suspend fun upsert(app: BlockedApp)
    suspend fun remove(packageName: String)
}
