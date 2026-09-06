package com.socialreset.app.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

class PermissionChecker(private val context: Context) {
    data class Status(
        val accessibilityEnabled: Boolean,
        val overlayAllowed: Boolean,
        val notificationsAllowed: Boolean,
    ) {
        val canClaimProtection: Boolean get() = accessibilityEnabled && overlayAllowed
    }

    fun status(): Status =
        Status(
            accessibilityEnabled = isAccessibilityServiceEnabled(),
            overlayAllowed = Settings.canDrawOverlays(context),
            notificationsAllowed = areNotificationsAllowed(),
        )

    private fun areNotificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "${context.packageName}/com.socialreset.app.enforcement.AccessibilityEnforcementService"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
