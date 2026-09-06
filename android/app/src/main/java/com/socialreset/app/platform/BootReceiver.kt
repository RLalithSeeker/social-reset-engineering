package com.socialreset.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.socialreset.app.notifications.AppNotifier

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val permissions = PermissionChecker(context).status()
        if (!permissions.canClaimProtection) {
            AppNotifier(context).showProtectionDisabled()
        }
    }
}
