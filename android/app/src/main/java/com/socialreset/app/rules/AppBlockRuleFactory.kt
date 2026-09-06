package com.socialreset.app.rules

import java.util.UUID

object AppBlockRuleFactory {
    fun newRule(packageName: String, label: String): BlockedApp =
        BlockedApp(
            packageName = packageName,
            label = label.ifBlank { packageName },
            policyId = "local-${UUID.randomUUID()}",
            enabled = true,
            schedules = emptyList(),
            unlockSeconds = DEFAULT_UNLOCK_SECONDS,
        )

    const val DEFAULT_UNLOCK_SECONDS: Long = 120
}
