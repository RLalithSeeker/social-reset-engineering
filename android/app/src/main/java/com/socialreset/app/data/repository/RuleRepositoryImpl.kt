package com.socialreset.app.data.repository

import com.socialreset.app.data.db.dao.BlockedAppDao
import com.socialreset.app.rules.BlockedApp
import com.socialreset.app.rules.RuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.atomic.AtomicReference

class RuleRepositoryImpl(
    private val dao: BlockedAppDao,
    scope: CoroutineScope,
) : RuleRepository {
    private val cached = AtomicReference<List<BlockedApp>>(emptyList())

    private val observed = dao.observeApps()
        .combine(dao.observeSchedules()) { apps, schedules ->
            val schedulesByPackage = schedules.groupBy { it.packageName }
            apps
                .map { app -> app.toDomain(schedulesByPackage[app.packageName].orEmpty()) }
                .sortedBy { it.label.lowercase() }
        }
        .onEach { cached.set(it) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun observe(): Flow<List<BlockedApp>> =
        observed

    override fun snapshot(): List<BlockedApp> =
        cached.get()

    suspend fun warmUp() {
        cached.set(loadAll())
    }

    override suspend fun upsert(app: BlockedApp) {
        dao.replaceApp(app.toEntity(), app.schedules.map { it.toEntity(app.packageName) })
        cached.set(loadAll())
    }

    override suspend fun remove(packageName: String) {
        dao.clearSchedules(packageName)
        dao.deleteApp(packageName)
        cached.set(loadAll())
    }

    private suspend fun loadAll(): List<BlockedApp> {
        val schedulesByPackage = dao.allSchedules().groupBy { it.packageName }
        return dao.allApps()
            .map { app -> app.toDomain(schedulesByPackage[app.packageName].orEmpty()) }
            .sortedBy { it.label.lowercase() }
    }
}
