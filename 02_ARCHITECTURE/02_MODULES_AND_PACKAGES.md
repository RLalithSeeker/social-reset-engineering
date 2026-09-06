# Recommended Package Structure

```text
app/src/main/java/com/socialreset/app/

core/
  model/
  time/
  result/
  logging/

crypto/
  DeviceIdentityManager.kt
  KeyAgreementService.kt
  SignatureService.kt
  TokenValidator.kt
  FingerprintFormatter.kt

rules/
  BlockedApp.kt
  ScheduleRule.kt
  RuleRepository.kt
  ScheduleMatcher.kt
  PolicyEngine.kt

enforcement/
  ForegroundAppDetector.kt
  AccessibilityEnforcementService.kt
  EnforcementController.kt
  BlockOverlayActivity.kt
  UnlockWindowManager.kt

behavior/
  BehaviorEvent.kt
  BehaviorRepository.kt
  BehaviorScorer.kt
  InterventionPolicy.kt
  OnDeviceAiClassifier.kt

social/
  TrustedPeer.kt
  PairingManager.kt
  ResetSession.kt
  ResetStateMachine.kt
  ResetVerifier.kt
  FriendAcceptedVerifier.kt
  CallAttemptVerifier.kt
  UnlockGrant.kt

network/
  ApiClient.kt
  WebSocketClient.kt
  RelayEvent.kt
  RetryPolicy.kt

notifications/
  NotificationPublisher.kt
  RemoteEventNotificationHandler.kt

platform/
  PermissionChecker.kt
  BootReceiver.kt
  PackageCatalog.kt
  BatteryOptimizationHelper.kt
  TelecomAdapter.kt

ui/
  navigation/
  onboarding/
  home/
  blockedapps/
  schedule/
  pairing/
  trustedpeer/
  reset/
  settings/
  diagnostics/

data/
  db/
    AppDatabase.kt
    entities/
    dao/
  repository/

security/
  ThreatModel.kt
  Redaction.kt
```

Adjust to existing repository conventions if it is already structured differently.
