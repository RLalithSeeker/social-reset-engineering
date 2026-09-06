package com.socialreset.app.debug

import com.socialreset.app.rules.BlockedApp
import com.socialreset.app.rules.RuleRepository

class DebugRuleSeeder(
    private val ruleRepository: RuleRepository,
) {
    suspend fun seedSettingsRule(): BlockedApp {
        val app = BlockedApp(
            packageName = SETTINGS_PACKAGE,
            label = "Android Settings",
            policyId = SETTINGS_POLICY_ID,
            enabled = true,
            schedules = emptyList(),
            unlockSeconds = 120,
        )
        ruleRepository.upsert(app)
        return app
    }

    suspend fun clearSettingsRule() {
        ruleRepository.remove(SETTINGS_PACKAGE)
    }

    suspend fun seedYouTubeRule(): BlockedApp {
        ruleRepository.remove(SETTINGS_PACKAGE)
        val app = BlockedApp(
            packageName = YOUTUBE_PACKAGE,
            label = "YouTube",
            policyId = YOUTUBE_POLICY_ID,
            enabled = true,
            schedules = emptyList(),
            unlockSeconds = 120,
        )
        ruleRepository.upsert(app)
        return app
    }

    suspend fun clearYouTubeRule() {
        ruleRepository.remove(YOUTUBE_PACKAGE)
    }

    companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"
        const val SETTINGS_POLICY_ID = "debug-settings-v1"
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        const val YOUTUBE_POLICY_ID = "debug-youtube-v1"
    }
}
