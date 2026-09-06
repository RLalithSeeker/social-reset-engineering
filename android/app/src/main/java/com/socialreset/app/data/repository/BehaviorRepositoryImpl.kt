package com.socialreset.app.data.repository

import com.socialreset.app.behavior.BehaviorEvent
import com.socialreset.app.behavior.BehaviorRepository
import com.socialreset.app.data.db.dao.BehaviorEventDao

class BehaviorRepositoryImpl(
    private val dao: BehaviorEventDao,
) : BehaviorRepository {
    override suspend fun record(event: BehaviorEvent) {
        dao.insert(event.toEntity())
    }

    override suspend fun recentFor(packageName: String, sinceSeconds: Long): List<BehaviorEvent> =
        dao.recentFor(packageName, sinceSeconds).map { it.toDomain() }

    override suspend fun pruneOlderThan(cutoffSeconds: Long) {
        dao.pruneOlderThan(cutoffSeconds)
    }
}
