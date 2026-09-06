package com.socialreset.app.enforcement

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.socialreset.app.core.logging.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class AccessibilityEnforcementService : AccessibilityService(), ForegroundAppDetector {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val packages = MutableSharedFlow<String>(extraBufferCapacity = 64)

    override val foregroundPackages: Flow<String> = packages

    override suspend fun start() = Unit

    override suspend fun stop() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isBlank() || packageName == this.packageName) return

        packages.tryEmit(packageName)
        val controller = controller
        if (controller == null) {
            Log.w("Accessibility event received before enforcement controller is wired")
            return
        }

        serviceScope.launch {
            when (val decision = controller.onForegroundPackage(packageName)) {
                is EnforcementController.Decision.Allow -> Unit
                is EnforcementController.Decision.ShowIntervention -> {
                    startActivity(BlockOverlayActivity.intent(this@AccessibilityEnforcementService, decision))
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w("Accessibility enforcement interrupted")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile
        var controller: EnforcementController? = null
    }
}
