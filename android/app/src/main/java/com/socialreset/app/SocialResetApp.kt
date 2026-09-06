package com.socialreset.app

import android.app.Application
import com.socialreset.app.behavior.FeatureExtractor
import com.socialreset.app.behavior.InterventionPolicy
import com.socialreset.app.behavior.RuleBasedClassifier
import com.socialreset.app.core.time.SystemTimeSource
import com.socialreset.app.crypto.DeviceIdentityManager
import com.socialreset.app.debug.DebugRuleSeeder
import com.socialreset.app.data.db.AppDatabase
import com.socialreset.app.data.repository.BehaviorRepositoryImpl
import com.socialreset.app.data.repository.PeerRepository
import com.socialreset.app.data.repository.RuleRepositoryImpl
import com.socialreset.app.data.repository.SessionRepository
import com.socialreset.app.enforcement.AccessibilityEnforcementService
import com.socialreset.app.enforcement.EnforcementController
import com.socialreset.app.enforcement.UnlockWindowManager
import com.socialreset.app.network.ApiClient
import com.socialreset.app.network.WebSocketClient
import com.socialreset.app.notifications.AppNotifier
import com.socialreset.app.pairing.PairingCoordinator
import com.socialreset.app.platform.PackageCatalog
import com.socialreset.app.platform.PermissionChecker
import com.socialreset.app.social.ResetCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class SocialResetApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AccessibilityEnforcementService.controller = container.enforcementController
        container.appScope.launch {
            container.ruleRepository.warmUp()
        }
    }
}

class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val timeSource = SystemTimeSource()
    val database = AppDatabase.build(app)
    val identity = DeviceIdentityManager(app)
    val httpClient = OkHttpClient()

    val ruleRepository = RuleRepositoryImpl(database.blockedAppDao(), appScope)
    val behaviorRepository = BehaviorRepositoryImpl(database.behaviorEventDao())
    val peerRepository = PeerRepository(database.trustedPeerDao())
    val sessionRepository = SessionRepository(database.resetSessionDao())
    val unlockWindowManager = UnlockWindowManager(database.unlockDao(), timeSource)

    val enforcementController = EnforcementController(
        rules = ruleRepository,
        unlockWindows = unlockWindowManager,
        behaviorRepository = behaviorRepository,
        classifier = RuleBasedClassifier(),
        interventionPolicy = InterventionPolicy(),
        timeSource = timeSource,
        featureExtractor = FeatureExtractor(),
    )

    val apiClient = ApiClient(BuildConfig.RELAY_BASE_URL, httpClient)
    val webSocketClient = WebSocketClient(BuildConfig.RELAY_WS_URL, httpClient)
    val permissionChecker = PermissionChecker(app)
    val packageCatalog = PackageCatalog(app)
    val notifier = AppNotifier(app)
    val debugRuleSeeder = DebugRuleSeeder(ruleRepository)
    val pairingCoordinator = PairingCoordinator(
        identity = identity,
        keyAgreement = com.socialreset.app.crypto.KeyAgreementService(),
        apiClient = apiClient,
        peerRepository = peerRepository,
    )
    val resetCoordinator = ResetCoordinator(
        localDeviceId = { identity.deviceId },
        signer = identity,
        accessTokenProvider = { pairingCoordinator.currentAccessToken },
        relayApi = apiClient,
        webSocketClient = webSocketClient,
        peerRepository = peerRepository,
        sessionRepository = sessionRepository,
        unlockWindowManager = unlockWindowManager,
        timeSource = timeSource,
        appScope = appScope,
    )
}
