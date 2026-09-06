# Handoff — Android app (session 2026-09-06)

## Where things stand

| Area | State |
|---|---|
| `backend/` | **Done.** FastAPI relay, 23 pytest tests pass. See `backend/WORKER_REPORT.md`. Not re-touched this session. |
| `android/` gradle config | Present (`build.gradle.kts`, `libs.versions.toml`, `settings.gradle.kts`). **No gradle wrapper** - system Gradle 9.7.1 was used for verification. `local.properties` SDK path was fixed to `E:/dev/android-sdk`. |
| `android/` Kotlin source | Domain layer + repository layer + first enforcement glue + network client + platform helpers + notification helper + app container + basic setup UI are on disk. Pairing/session UI flows and full event orchestration are still pending. |
| Build verification | **Real Android build now passes.** `gradle -p android :app:compileDebugKotlin`, `gradle -p android :app:testDebugUnitTest`, and `gradle -p android :app:assembleDebug` all pass. |
| Emulator smoke | `itodo` AVD found at `E:\dev\avd\itodo.avd` (Pixel 7, API 36). Debug APK installed and `com.socialreset.app/.MainActivity` cold-launched successfully. Accessibility service was enabled through emulator settings/ADB, YouTube was launched, and the app showed `BlockOverlayActivity` with `YouTube is blocked`. |

## What was written this session

All under `android/app/src/main/java/com/socialreset/app/`.

```
core/       Protocol.kt (wire constants, EventType, ResetType, InterventionLevel)
            TimeSource.kt (split wall/monotonic clock + FakeTimeSource)
            Outcome.kt (Ok/Rejected + RejectionReason enum)
            Log.kt
security/   Redaction.kt (deviceId -> first 8 chars; secrets never logged)

crypto/     CanonicalJson.kt      deterministic signing serializer (sorted keys, no floats)
            B64.kt                base64url, no padding
            Digest.kt             sha256, canonicalHash, constant-time compare, randomB64
            SignatureService.kt   interface + SignatureVerifier + InMemorySignatureService
            DeviceIdentityManager.kt  Keystore ECDSA P-256, 128-bit deviceId, never exports key
            KeyAgreementService.kt    X25519 (Tink) + HKDF-SHA256 pairing session key
            FingerprintFormatter.kt   BLUE-ORBIT-71-MAPLE style transcript fingerprint
            TokenValidator.kt     the 9 grant checks from 04_SECURITY/01, each with a reason

rules/      BlockedApp.kt, ScheduleRule.kt, ScheduleMatcher.kt (local-clock, midnight wrap),
            RuleRepository.kt (interface), PolicyEngine.kt (deterministic allow/intervene)

behavior/   BehaviorEvent.kt + BehaviorFeatures, BehaviorRepository.kt + FeatureExtractor,
            BehaviorScorer.kt (spec weights, clamp 0..10), InterventionClassifier.kt
            (RuleBasedClassifier mandatory), OnDeviceAiClassifier.kt (timeout + fallback),
            InterventionPolicy.kt (score -> LIGHT/FRICTION/SOCIAL_RESET)

social/     TrustedPeer.kt (+PeerStatus), ResetSession.kt (+ResetState),
            ResetStateMachine.kt (transition table, idempotent re-apply),
            EventGuard.kt (session/sender/timestamp/sequence/nonce/signature),
            ResetVerifier.kt, FriendAcceptedVerifier.kt (default path),
            CallAttemptVerifier.kt (experimental, disabled, uncertain -> no unlock),
            UnlockGrant.kt (canonical payload + detached grantSignature)

network/    RelayEvent.kt  signed envelope: sign/parse/verify, wire field names matched
                           against backend/app/schemas.py (protocolVersion, eventId,
                           sessionId, senderDeviceId, recipientDeviceId, sequence, type,
                           timestamp, nonce, payload, signature, requestId)

data/db/    entities/Entities.kt  blocked_apps, schedules, trusted_peers, reset_sessions,
                                  behavior_events, consumed_grants, unlock_windows
            dao/Daos.kt           5 DAOs incl. replaceApp transaction, consumed-grant lookup
            AppDatabase.kt        version 1, exportSchema = true, no destructive fallback

data/repository/
            EntityMappers.kt, RuleRepositoryImpl.kt, BehaviorRepositoryImpl.kt,
            PeerRepository.kt, SessionRepository.kt
            RuleRepositoryImpl keeps a volatile snapshot + warmUp() for the
            AccessibilityService path. PeerRepository preserves key pinning:
            same deviceId with a different key revokes existing peer and does
            not overwrite the pinned key.

enforcement/
            ForegroundAppDetector.kt, UnlockWindowManager.kt,
            EnforcementController.kt, BlockOverlayActivity.kt,
            AccessibilityEnforcementService.kt
            First compile-safe glue for the 03_ANDROID/01 decision path.

network/    ApiClient.kt          OkHttp REST client for register, pairing create/join/complete,
                                  reset session create/get, relay event. Uses requestId on writes.
            WebSocketClient.kt    token-in-query authenticated relay stream; parses text/bytes.
            RetryPolicy.kt        bounded exponential retry wrapper.

platform/   TelecomAdapter.kt (interface + NoTelecomEvidenceAdapter)
            PermissionChecker.kt, PackageCatalog.kt, BootReceiver.kt

notifications/
            AppNotifier.kt        status channel + protection-disabled recovery notification.

root app/    SocialResetApp.kt    AppContainer wires DB, repos, identity, network, enforcement.
             MainActivity.kt      Basic Compose setup screen for accessibility/overlay/notification status.

debug/       DebugRuleSeeder.kt   Debug-only manual testing support for seeding/clearing
                                  known Android Settings and YouTube block rules.
```

## Manual emulator checks available now

The debug APK exposes a **Debug test panel** on `MainActivity`:

1. `Seed YouTube block rule` - writes an always-active `com.google.android.youtube` rule into Room, removes the old Settings debug rule, and refreshes the enforcement snapshot.
2. `Run fake YouTube foreground` - calls `EnforcementController.onForegroundPackage("com.google.android.youtube")`; expected result after seeding is `BlockOverlayActivity` opening with `YouTube is blocked`.
3. `Show sample block overlay` - opens the overlay without needing a rule.
4. `Clear YouTube block rule` - removes the seeded rule.

Verified on `itodo` emulator after the latest build:

1. Seed rule changed `Rules cached` to `1`.
2. Fake foreground opened `BlockOverlayActivity`.
3. Real launch of installed `com.google.android.youtube` opened `BlockOverlayActivity` with `YouTube is blocked`.
4. Android window focus reported `com.socialreset.app/.enforcement.BlockOverlayActivity`.
5. App stayed running with no app crash in logcat.
6. Final screenshot saved at `android/app/build/outputs/youtube-final-verified.png`.

ADB retest notes:

```powershell
& 'E:\dev\android-sdk\platform-tools\adb.exe' shell settings put secure enabled_accessibility_services 'com.socialreset.app/com.socialreset.app.enforcement.AccessibilityEnforcementService'
& 'E:\dev\android-sdk\platform-tools\adb.exe' shell settings put secure accessibility_enabled 1
& 'E:\dev\android-sdk\platform-tools\adb.exe' shell am start -W -n com.socialreset.app/.MainActivity
# Tap Seed YouTube block rule in the debug panel, then:
& 'E:\dev\android-sdk\platform-tools\adb.exe' shell monkey -p com.google.android.youtube -c android.intent.category.LAUNCHER 1
```

Do not use `am force-stop com.socialreset.app` immediately before testing interception; it can leave the AccessibilityService unbound in emulator automation. After reinstalling the debug APK, re-check `settings get secure enabled_accessibility_services` before opening YouTube.

Design decisions worth not re-deriving:

- **ECDSA P-256 for identity, X25519 for pairing ECDH.** Keystore has no Ed25519 at minSdk 26 and no `AGREE_KEY` before API 31, so signing stays in Keystore and only the ephemeral pairing key uses Tink in memory.
- **Signature covers header fields + `payloadHash`**, not the raw body, so the relay can forward bytes and the receiver can re-derive the hash from what it parsed.
- **JSON numbers parse to `Long`** in `RelayEvent.toMap`, otherwise a round-tripped payload would hash differently than it did when signed.
- **`consumed_grants` rows outlive expiry** so a replay is reported as REPLAYED, not EXPIRED.
- **`unlock_windows` stores both clocks plus a `bootId`** — countdown uses elapsed-realtime so a clock edit cannot extend a window; `bootId` invalidates it after reboot.

## Cleartext relay fix (opencode session 2026-09-06)

Pairing create failed with `CLEARTEXT communication to 10.0.2.2 not permitted by network security policy`.

**Files added/changed:**

| File | What |
|---|---|
| `android/app/src/main/AndroidManifest.xml` | Added `android:networkSecurityConfig="@xml/network_security_config"` to `<application>`. |
| `android/app/src/main/res/xml/network_security_config.xml` | **New.** Base config: `cleartextTrafficPermitted="false"` (release stays strict). |
| `android/app/src/debug/res/xml/debug_network_security_config.xml` | **New.** Debug override: `cleartextTrafficPermitted="true"` for `10.0.2.2` only. |
| `android/app/src/debug/AndroidManifest.xml` | Already existed; added `tools:replace="android:networkSecurityConfig"` so debug manifest wins over main. |

Build and relay are working:

- `gradle -p android :app:assembleDebug` → **BUILD SUCCESSFUL**.
- Relay running: `python -m uvicorn app.main:app --host 127.0.0.1 --port 8099` (from `backend/` dir).
- Emulators `emulator-5554` / `emulator-5556` both booted (`sys.boot_completed=1`); AVD name is `itodo` (Pixel 7, API 36).
- Debug APK installed on `emulator-5554`, `MainActivity` launched (COLD, `TotalTime: 3163`).
- **Not verified yet:** pairing create itself — quota ran out before the user could confirm.

## Next session — pick up here

1. Pairing orchestration - QR/manual-code parsing, call `ApiClient` create/join/complete, run X25519 agreement, compute/display fingerprint, persist `TrustedPeer`.
2. Reset-session orchestration - create local + relay session, consume inbound `RelayEvent`s from `WebSocketClient`, apply `EventGuard`, `ResetStateMachine`, `ResetVerifier`, `TokenValidator`, then redeem grants through `UnlockWindowManager`.
3. Rule-management UI - package picker from `PackageCatalog`, add/remove blocked apps, schedule editor, local policy preview.
4. Enforcement follow-up - replace static controller handoff with a lifecycle-safe binding, add permission-aware overlay/notification fallback, and emulator tests with a fake foreground detector.
5. Tests - remaining priority: `TokenValidator` all nine rejections, repository Room mapping, unlock-window monotonic/reboot expiry, ApiClient error handling, pairing/session orchestration once written.

## Known gaps / not claimed

- No Gradle wrapper exists; verification used installed Gradle 9.7.1.
- `:app:testDebugUnitTest` now has focused tests for canonical JSON, state transitions, schedule wrap, behavior scoring, API request shape, EventGuard replay/order checks, and debug seeding for Settings/YouTube rules.
- `AccessibilityEnforcementService` compiles, binds in emulator settings, and has been exercised against a real YouTube foreground launch.
- Headless `itodo` boot showed a SystemUI ANR dialog during screenshot capture; app itself stayed alive with no `AndroidRuntime`/`FATAL EXCEPTION` logcat hit.
- Two-emulator flow and any telecom behaviour remain unproven, per 03_ANDROID/01's emulator-first policy.
