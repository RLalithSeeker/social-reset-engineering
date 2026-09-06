package com.socialreset.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.socialreset.app.core.model.InterventionLevel
import com.socialreset.app.core.result.Outcome
import com.socialreset.app.debug.DebugRuleSeeder
import com.socialreset.app.enforcement.BlockOverlayActivity
import com.socialreset.app.enforcement.EnforcementController
import com.socialreset.app.platform.PackageCatalog
import com.socialreset.app.platform.PermissionChecker
import com.socialreset.app.rules.AppBlockRuleFactory
import com.socialreset.app.rules.BlockedApp
import com.socialreset.app.social.ResetSession
import com.socialreset.app.social.ResetState
import com.socialreset.app.social.TrustedPeer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var container: AppContainer
    private lateinit var checker: PermissionChecker
    private var status by mutableStateOf(PermissionChecker.Status(false, false, false))
    private var installedApps by mutableStateOf<List<PackageCatalog.LaunchableApp>>(emptyList())
    private var blockedRules by mutableStateOf<List<BlockedApp>>(emptyList())
    private var trustedPeers by mutableStateOf<List<TrustedPeer>>(emptyList())
    private var openSessions by mutableStateOf<List<ResetSession>>(emptyList())
    private var hideSystemApps by mutableStateOf(true)
    private var appMessage by mutableStateOf("Loading apps...")
    private var relayName by mutableStateOf("")
    private var pairingSessionId by mutableStateOf("")
    private var pairingCode by mutableStateOf("")
    private var pairingMessage by mutableStateOf("Register this device before pairing.")
    private var pendingFingerprint by mutableStateOf<String?>(null)
    private var resetMessage by mutableStateOf("Pair a trusted peer and select a blocked app first.")
    private var debugMessage by mutableStateOf("No debug action run yet.")

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = (application as SocialResetApp).container
        checker = container.permissionChecker
        relayName = container.identity.displayName.ifBlank { "${Build.MANUFACTURER} ${Build.MODEL}" }
        refreshStatus()
        loadLaunchableApps()
        lifecycleScope.launch {
            container.ruleRepository.observe().collectLatest { blockedRules = it }
        }
        lifecycleScope.launch {
            container.peerRepository.observeAll().collectLatest { trustedPeers = it }
        }
        lifecycleScope.launch {
            container.sessionRepository.observeOpen().collectLatest { openSessions = it }
        }
        setContent {
            SocialResetTheme {
                SetupScreen(
                    status = status,
                    launchableApps = installedApps,
                    blockedRules = blockedRules,
                    trustedPeers = trustedPeers,
                    ownPackageName = packageName,
                    hideSystemApps = hideSystemApps,
                    appMessage = appMessage,
                    relayName = relayName,
                    pairingSessionId = pairingSessionId,
                    pairingCode = pairingCode,
                    pairingMessage = pairingMessage,
                    pendingFingerprint = pendingFingerprint,
                    openSessions = openSessions,
                    resetMessage = resetMessage,
                    ownDeviceId = container.identity.deviceId,
                    debugEnabled = BuildConfig.DEBUG,
                    debugMessage = debugMessage,
                    onRelayNameChange = { relayName = it.take(64) },
                    onToggleSystemApps = { hideSystemApps = it },
                    onToggleApp = ::toggleBlockedApp,
                    onRegisterDevice = { registerDevice() },
                    onCreatePairing = { createPairing() },
                    onPairingSessionIdChange = { pairingSessionId = it },
                    onPairingCodeChange = { pairingCode = it },
                    onJoinPairing = { joinPairing() },
                    onCompletePairing = { completePairing() },
                    onConfirmPeer = { confirmPeer() },
                    onConnectRelay = { connectRelay() },
                    onRequestReset = { requestReset() },
                    onAcceptReset = { acceptReset() },
                    onAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onOverlay = {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName"),
                            )
                        )
                    },
                    onNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onSeedYouTubeRule = { seedYouTubeRule() },
                    onClearYouTubeRule = { clearYouTubeRule() },
                    onFakeYouTubeEvent = { runFakeYouTubeEvent() },
                    onShowOverlay = { showSampleOverlay() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        if (::checker.isInitialized) {
            status = checker.status()
        }
    }

    private fun loadLaunchableApps() {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { container.packageCatalog.launchableApps() }
            }.onSuccess {
                installedApps = it
                appMessage = if (it.isEmpty()) "No launchable apps found." else "${it.size} apps found."
            }.onFailure {
                appMessage = "Could not load apps: ${it.message ?: it::class.java.simpleName}."
            }
        }
    }

    private fun toggleBlockedApp(app: PackageCatalog.LaunchableApp, shouldBlock: Boolean) {
        lifecycleScope.launch {
            runCatching {
                if (shouldBlock) {
                    container.ruleRepository.upsert(AppBlockRuleFactory.newRule(app.packageName, app.label))
                    "${app.label} is now blocked."
                } else {
                    container.ruleRepository.remove(app.packageName)
                    "${app.label} is no longer blocked."
                }
            }.onSuccess {
                appMessage = it
            }.onFailure {
                appMessage = "Rule update failed: ${it.message ?: it::class.java.simpleName}."
            }
        }
    }

    private fun registerDevice() {
        lifecycleScope.launch {
            runCatching {
                container.pairingCoordinator.register(relayName.ifBlank { "Social Reset device" })
            }.onSuccess {
                pairingMessage = "Registered device ${it.take(8)}. Ready to pair."
            }.onFailure {
                pairingMessage = "Register failed: ${it.message ?: it::class.java.simpleName}."
            }
        }
    }

    private fun createPairing() {
        lifecycleScope.launch {
            runCatching {
                ensureRegistered()
                container.pairingCoordinator.createPairing()
            }.onSuccess {
                pairingSessionId = it.sessionId
                pairingCode = it.code
                pendingFingerprint = null
                pairingMessage = "Pairing code created. Enter this session and code on the second device."
            }.onFailure {
                pairingMessage = "Create pairing failed: ${it.message ?: it::class.java.simpleName}."
            }
        }
    }

    private fun joinPairing() {
        lifecycleScope.launch {
            runCatching {
                ensureRegistered()
                container.pairingCoordinator.joinPairing(pairingSessionId, pairingCode)
            }.onSuccess {
                pendingFingerprint = it.fingerprint
                pairingMessage = "Joined ${it.peerDisplayName}. Compare fingerprint on both phones."
            }.onFailure {
                pairingMessage = "Join failed: ${it.message ?: it::class.java.simpleName}."
            }
        }
    }

    private fun completePairing() {
        lifecycleScope.launch {
            runCatching {
                ensureRegistered()
                container.pairingCoordinator.completeCreatedPairing(pairingSessionId, pairingCode)
            }.onSuccess {
                pendingFingerprint = it.fingerprint
                pairingMessage = "Peer joined. Compare fingerprint before confirming."
            }.onFailure {
                pairingMessage = "Complete failed: ${it.message ?: it::class.java.simpleName}."
            }
        }
    }

    private fun confirmPeer() {
        lifecycleScope.launch {
            runCatching {
                container.pairingCoordinator.confirmPendingPeer()
            }.onSuccess {
                pendingFingerprint = null
                pairingMessage = "Paired with ${it.displayName}."
            }.onFailure {
                pairingMessage = "Confirm failed: ${it.message ?: it::class.java.simpleName}."
            }
        }
    }

    private fun connectRelay() {
        when (val result = container.resetCoordinator.connectRelay()) {
            is Outcome.Ok -> resetMessage = "Relay socket connecting."
            is Outcome.Rejected -> resetMessage = "Relay connect rejected: ${result.reason}."
        }
    }

    private fun requestReset() {
        lifecycleScope.launch {
            runCatching {
                ensureRegistered()
                val peer = trustedPeers.firstOrNull { it.isActive }
                    ?: error("No active trusted peer.")
                val rule = blockedRules.firstOrNull()
                    ?: error("No blocked app selected.")
                when (val result = container.resetCoordinator.requestReset(
                    peerDeviceId = peer.deviceId,
                    blockedPackage = rule.packageName,
                    policyId = rule.policyId,
                    unlockSeconds = rule.unlockSeconds,
                )) {
                    is Outcome.Ok -> "Reset requested from ${peer.displayName} for ${rule.label}."
                    is Outcome.Rejected -> "Reset request rejected: ${result.reason}."
                }
            }.onSuccess {
                resetMessage = it
            }.onFailure {
                resetMessage = "Reset request failed: ${it.message ?: it::class.java.simpleName}."
            }
        }
    }

    private fun acceptReset() {
        lifecycleScope.launch {
            runCatching {
                ensureRegistered()
                val session = openSessions.firstOrNull {
                    it.peerDeviceId == container.identity.deviceId && it.state == ResetState.WAITING_FOR_PEER
                } ?: error("No inbound reset request waiting.")
                when (val result = container.resetCoordinator.acceptRequest(session.sessionId)) {
                    is Outcome.Ok -> "Accepted reset ${result.value.sessionId.take(8)} and issued unlock grant."
                    is Outcome.Rejected -> "Accept rejected: ${result.reason}."
                }
            }.onSuccess {
                resetMessage = it
            }.onFailure {
                resetMessage = "Accept failed: ${it.message ?: it::class.java.simpleName}."
            }
        }
    }

    private suspend fun ensureRegistered() {
        if (!container.pairingCoordinator.isRegistered) {
            container.pairingCoordinator.register(relayName.ifBlank { "Social Reset device" })
        }
    }

    private fun seedYouTubeRule() {
        lifecycleScope.launch {
            val rule = container.debugRuleSeeder.seedYouTubeRule()
            container.ruleRepository.warmUp()
            debugMessage = "Seeded ${rule.label} (${rule.packageName})."
        }
    }

    private fun clearYouTubeRule() {
        lifecycleScope.launch {
            container.debugRuleSeeder.clearYouTubeRule()
            container.ruleRepository.warmUp()
            debugMessage = "Cleared YouTube test rule."
        }
    }

    private fun runFakeYouTubeEvent() {
        lifecycleScope.launch {
            when (val decision = container.enforcementController.onForegroundPackage(DebugRuleSeeder.YOUTUBE_PACKAGE)) {
                is EnforcementController.Decision.Allow -> {
                    debugMessage = "Fake YouTube event allowed: ${decision.reason}."
                }
                is EnforcementController.Decision.ShowIntervention -> {
                    debugMessage = "Fake YouTube event blocked: ${decision.level}, score ${decision.score}."
                    startActivity(BlockOverlayActivity.intent(this@MainActivity, decision))
                }
            }
        }
    }

    private fun showSampleOverlay() {
        val decision = EnforcementController.Decision.ShowIntervention(
            packageName = DebugRuleSeeder.YOUTUBE_PACKAGE,
            appLabel = "Debug sample",
            policyId = DebugRuleSeeder.YOUTUBE_POLICY_ID,
            unlockSeconds = 120,
            level = InterventionLevel.SOCIAL_RESET,
            score = 7,
            rationale = "manual overlay smoke",
        )
        debugMessage = "Launched sample overlay."
        startActivity(BlockOverlayActivity.intent(this, decision))
    }
}

@Composable
private fun SocialResetTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Composable
private fun SetupScreen(
    status: PermissionChecker.Status,
    launchableApps: List<PackageCatalog.LaunchableApp>,
    blockedRules: List<BlockedApp>,
    trustedPeers: List<TrustedPeer>,
    ownPackageName: String,
    hideSystemApps: Boolean,
    appMessage: String,
    relayName: String,
    pairingSessionId: String,
    pairingCode: String,
    pairingMessage: String,
    pendingFingerprint: String?,
    openSessions: List<ResetSession>,
    resetMessage: String,
    ownDeviceId: String,
    debugEnabled: Boolean,
    debugMessage: String,
    onRelayNameChange: (String) -> Unit,
    onToggleSystemApps: (Boolean) -> Unit,
    onToggleApp: (PackageCatalog.LaunchableApp, Boolean) -> Unit,
    onRegisterDevice: () -> Unit,
    onCreatePairing: () -> Unit,
    onPairingSessionIdChange: (String) -> Unit,
    onPairingCodeChange: (String) -> Unit,
    onJoinPairing: () -> Unit,
    onCompletePairing: () -> Unit,
    onConfirmPeer: () -> Unit,
    onConnectRelay: () -> Unit,
    onRequestReset: () -> Unit,
    onAcceptReset: () -> Unit,
    onAccessibility: () -> Unit,
    onOverlay: () -> Unit,
    onNotifications: () -> Unit,
    onSeedYouTubeRule: () -> Unit,
    onClearYouTubeRule: () -> Unit,
    onFakeYouTubeEvent: () -> Unit,
    onShowOverlay: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Social Reset", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                if (status.canClaimProtection) "Protection can run on this device."
                else "Protection setup is incomplete.",
                style = MaterialTheme.typography.bodyLarge,
            )
            PermissionSection(status, onAccessibility, onOverlay, onNotifications)
            PairingSection(
                relayName = relayName,
                pairingSessionId = pairingSessionId,
                pairingCode = pairingCode,
                message = pairingMessage,
                pendingFingerprint = pendingFingerprint,
                trustedPeers = trustedPeers,
                onRelayNameChange = onRelayNameChange,
                onRegisterDevice = onRegisterDevice,
                onCreatePairing = onCreatePairing,
                onPairingSessionIdChange = onPairingSessionIdChange,
                onPairingCodeChange = onPairingCodeChange,
                onJoinPairing = onJoinPairing,
                onCompletePairing = onCompletePairing,
                onConfirmPeer = onConfirmPeer,
            )
            ResetSection(
                blockedRules = blockedRules,
                trustedPeers = trustedPeers,
                openSessions = openSessions,
                message = resetMessage,
                ownDeviceId = ownDeviceId,
                onConnectRelay = onConnectRelay,
                onRequestReset = onRequestReset,
                onAcceptReset = onAcceptReset,
            )
            AppSelectionSection(
                launchableApps = launchableApps,
                blockedRules = blockedRules,
                ownPackageName = ownPackageName,
                hideSystemApps = hideSystemApps,
                message = appMessage,
                onToggleSystemApps = onToggleSystemApps,
                onToggleApp = onToggleApp,
            )
            if (debugEnabled) {
                DebugTestPanel(
                    ruleCount = blockedRules.size,
                    message = debugMessage,
                    onSeedYouTubeRule = onSeedYouTubeRule,
                    onClearYouTubeRule = onClearYouTubeRule,
                    onFakeYouTubeEvent = onFakeYouTubeEvent,
                    onShowOverlay = onShowOverlay,
                )
            }
        }
    }
}

@Composable
private fun ResetSection(
    blockedRules: List<BlockedApp>,
    trustedPeers: List<TrustedPeer>,
    openSessions: List<ResetSession>,
    message: String,
    ownDeviceId: String,
    onConnectRelay: () -> Unit,
    onRequestReset: () -> Unit,
    onAcceptReset: () -> Unit,
) {
    val activePeerCount = trustedPeers.count { it.isActive }
    val waitingInbound = openSessions.count { it.peerDeviceId == ownDeviceId && it.state == ResetState.WAITING_FOR_PEER }
    SectionCard(title = "Social reset") {
        Text(
            "$activePeerCount trusted peers. ${blockedRules.size} blocked apps. $waitingInbound inbound requests.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onConnectRelay) {
                Text("Connect relay")
            }
            Button(onClick = onRequestReset, enabled = activePeerCount > 0 && blockedRules.isNotEmpty()) {
                Text("Request reset")
            }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onAcceptReset,
            enabled = waitingInbound > 0,
        ) {
            Text("Accept inbound reset")
        }
        Text(message, style = MaterialTheme.typography.bodyMedium)
        openSessions.take(3).forEach { session ->
            Text(
                "${session.sessionId.take(8)} ${session.state} ${session.blockedPackage}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PermissionSection(
    status: PermissionChecker.Status,
    onAccessibility: () -> Unit,
    onOverlay: () -> Unit,
    onNotifications: () -> Unit,
) {
    SectionCard(title = "Permissions") {
        PermissionRow("Accessibility monitoring", status.accessibilityEnabled, "Open settings", onAccessibility)
        PermissionRow("Overlay intervention", status.overlayAllowed, "Allow overlay", onOverlay)
        PermissionRow("Notifications", status.notificationsAllowed, "Enable", onNotifications)
    }
}

@Composable
private fun PairingSection(
    relayName: String,
    pairingSessionId: String,
    pairingCode: String,
    message: String,
    pendingFingerprint: String?,
    trustedPeers: List<TrustedPeer>,
    onRelayNameChange: (String) -> Unit,
    onRegisterDevice: () -> Unit,
    onCreatePairing: () -> Unit,
    onPairingSessionIdChange: (String) -> Unit,
    onPairingCodeChange: (String) -> Unit,
    onJoinPairing: () -> Unit,
    onCompletePairing: () -> Unit,
    onConfirmPeer: () -> Unit,
) {
    SectionCard(title = "Pair devices") {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = relayName,
            onValueChange = onRelayNameChange,
            label = { Text("This phone name") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRegisterDevice) { Text("Register") }
            OutlinedButton(onClick = onCreatePairing) { Text("Create code") }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = pairingSessionId,
            onValueChange = onPairingSessionIdChange,
            label = { Text("Pairing session") },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = pairingCode,
            onValueChange = onPairingCodeChange,
            label = { Text("Pairing code") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onJoinPairing) { Text("Join") }
            OutlinedButton(onClick = onCompletePairing) { Text("Complete") }
        }
        pendingFingerprint?.let {
            Text("Fingerprint", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(it, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(modifier = Modifier.fillMaxWidth(), onClick = onConfirmPeer) {
                Text("Confirm matching fingerprint")
            }
        }
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Text("Trusted peers: ${trustedPeers.count { it.isActive }}", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AppSelectionSection(
    launchableApps: List<PackageCatalog.LaunchableApp>,
    blockedRules: List<BlockedApp>,
    ownPackageName: String,
    hideSystemApps: Boolean,
    message: String,
    onToggleSystemApps: (Boolean) -> Unit,
    onToggleApp: (PackageCatalog.LaunchableApp, Boolean) -> Unit,
) {
    val blockedPackages = blockedRules.map { it.packageName }.toSet()
    val visibleApps = launchableApps
        .asSequence()
        .filter { it.packageName != ownPackageName }
        .filter { !hideSystemApps || !it.isSystemApp || it.packageName in blockedPackages }
        .toList()

    SectionCard(title = "Blocked apps") {
        Text("${blockedRules.size} selected. $message", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = hideSystemApps, onCheckedChange = onToggleSystemApps)
            Text("Hide system apps")
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visibleApps, key = { it.packageName }) { app ->
                val selected = app.packageName in blockedPackages
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleMedium)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    }
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleApp(app, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugTestPanel(
    ruleCount: Int,
    message: String,
    onSeedYouTubeRule: () -> Unit,
    onClearYouTubeRule: () -> Unit,
    onFakeYouTubeEvent: () -> Unit,
    onShowOverlay: () -> Unit,
) {
    SectionCard(title = "Debug test panel") {
        Text("Rules cached: $ruleCount", style = MaterialTheme.typography.bodyMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Button(modifier = Modifier.fillMaxWidth(), onClick = onSeedYouTubeRule) {
            Text("Seed YouTube block rule")
        }
        Button(modifier = Modifier.fillMaxWidth(), onClick = onFakeYouTubeEvent) {
            Text("Run fake YouTube foreground")
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onShowOverlay) {
            Text("Show sample block overlay")
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onClearYouTubeRule) {
            Text("Clear YouTube block rule")
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    ok: Boolean,
    action: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        if (ok) {
            Icon(Icons.Filled.Security, contentDescription = null)
        } else if (title == "Notifications") {
            OutlinedButton(onClick = onAction) {
                Icon(Icons.Filled.Notifications, contentDescription = null)
                Text(action)
            }
        } else {
            Button(onClick = onAction) {
                Text(action)
            }
        }
    }
}
