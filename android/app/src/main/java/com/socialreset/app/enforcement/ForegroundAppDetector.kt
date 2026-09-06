package com.socialreset.app.enforcement

import kotlinx.coroutines.flow.Flow

interface ForegroundAppDetector {
    val foregroundPackages: Flow<String>
    suspend fun start()
    suspend fun stop()
}
