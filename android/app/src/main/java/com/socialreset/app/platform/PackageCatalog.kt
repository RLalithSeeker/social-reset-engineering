package com.socialreset.app.platform

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build

class PackageCatalog(private val context: Context) {
    data class LaunchableApp(
        val packageName: String,
        val label: String,
        val isSystemApp: Boolean,
    )

    fun launchableApps(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(
                intent,
                android.content.pm.PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0)
        }
        return resolved
            .mapNotNull { info ->
                val appInfo = info.activityInfo?.applicationInfo ?: return@mapNotNull null
                LaunchableApp(
                    packageName = appInfo.packageName,
                    label = context.packageManager.getApplicationLabel(appInfo).toString(),
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
