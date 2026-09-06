package com.socialreset.app.data.repository

import com.socialreset.app.data.db.dao.ResetSessionDao
import com.socialreset.app.social.ResetSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionRepository(
    private val dao: ResetSessionDao,
) {
    fun observeOpen(): Flow<List<ResetSession>> =
        dao.observeOpen().map { sessions -> sessions.map { it.toDomain() } }

    suspend fun byId(sessionId: String): ResetSession? =
        dao.byId(sessionId)?.toDomain()

    suspend fun upsert(session: ResetSession) {
        dao.upsert(session.toEntity())
    }

    suspend fun pruneClosed(cutoffSeconds: Long) {
        dao.pruneClosed(cutoffSeconds)
    }
}
