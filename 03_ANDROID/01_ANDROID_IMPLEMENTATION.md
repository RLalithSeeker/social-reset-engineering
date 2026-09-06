# Android Implementation Specification

## Target

Use the latest Android SDK available in the build environment, with a minimum supported version selected by the implementation after checking required APIs. The reference blocker prototypes found for this project currently target modern Android; SelfLock reports Android 14+/API 34 and target API 36. Do not lower minSdk solely to increase device count if it complicates enforcement.

## Enforcement provider

Use `AccessibilityService` for the hackathon prototype because it provides foreground UI transition callbacks and can filter on package names. Android's documentation explicitly describes AccessibilityService as a service receiving UI transition events; Android also warns that accessibility services are intended to assist users with disabilities. This distinction must be recorded as a distribution/policy risk. citeturn723659search4turn723659search7

Abstract it:

```kotlin
interface ForegroundAppDetector {
    val foregroundPackages: Flow<String>
    suspend fun start()
    suspend fun stop()
}
```

Potential future provider:

`UsageStatsForegroundDetector`

This lets the project test a less accessibility-dependent approach later.

## Block decision path

```text
onAccessibilityEvent
      |
      v
extract package name
      |
      v
is package configured?
      | no -> return
      |
     yes
      v
is rule active?
      | no -> return
      |
     yes
      v
check active unlock window
      | yes -> return
      |
      no
      v
record behavior event
      |
      v
Behavior/InterventionPolicy
      |
      +--> LIGHT
      +--> FRICTION
      +--> SOCIAL_RESET
      |
      v
show intervention
```

## Overlay

Use a dedicated native `Activity`/window for the intervention UI where possible. Do not assume arbitrary background activity launches are allowed on modern Android. Android 14+ introduced tighter background-activity-launch restrictions, and newer releases add more explicit `ActivityOptions` modes. Use system-supported notification/PendingIntent paths where required. citeturn723659search1turn723659search8

The app must detect and explain missing `SYSTEM_ALERT_WINDOW` rather than silently failing.

## Background execution

Do not create a foreground service merely because one is convenient. If a foreground service is used, follow current Android requirements for declared foreground-service types and permissions. Android 14+ requires an appropriate foreground-service type for foreground services, and Android 12+ restricts starting foreground services from the background. citeturn409517search6turn409517search8

For the enforcement service, prefer an already-bound AccessibilityService as the primary long-lived mechanism. Add a foreground service only when a specific, tested requirement justifies it.

## Scheduling

Schedules should be evaluated from the local clock each time an enforcement event arrives, not solely by alarm broadcasts.

Use alarms only to refresh schedule-boundary state when useful. Android's exact alarm APIs have special-access rules on modern versions; apps targeting Android 12+ need the exact-alarm permission or an applicable exemption for exact scheduling. citeturn409517search2

## Boot recovery

Register a boot receiver only for state restoration that is genuinely necessary. On boot:

1. restore local configuration;
2. check whether AccessibilityService is enabled;
3. show a recovery notification if enforcement is unavailable;
4. do not claim active protection until the service is actually running.

## Notifications

Friend/session notifications are a separate concern from local enforcement.

`NotificationListenerService` is NOT required for the app's own remote notification delivery. Use normal app notifications and a remote push transport for friend requests. `NotificationListenerService` should only be considered if later required for a specific OS-level observation feature; it receives system callbacks for notifications posted/removed/ranking changes and requires the system-granted notification-listener role. citeturn723659search2turn723659search3

## Permissions matrix

| Capability | Mechanism | Required | Failure behavior |
|---|---|---:|---|
| Foreground app detection | AccessibilityService | Yes for prototype | Show protection disabled |
| Show intervention | Overlay | Yes for reliable overlay path | Show setup warning; do not claim blocking |
| Notifications | POST_NOTIFICATIONS | Recommended | Friend request may require in-app refresh |
| Network | INTERNET | For pairing/relay | Local block remains functional |
| Exact schedule boundary | SCHEDULE_EXACT_ALARM | Optional | Evaluate policy on next foreground event |
| Battery exclusion | user-controlled special access | Optional | Explain OEM variability |
| Call screening experiment | ROLE_CALL_SCREENING | Optional | Use FriendAcceptedVerifier |

## Package discovery

Use PackageManager to list launchable user apps. Avoid asking for broad package visibility permissions unless the selected implementation truly needs them and the distribution target permits it.

## Unlock window

Store unlock windows using elapsed-realtime duration for local countdown correctness, while also storing wall-clock issuance/expiry for audit and token validation. The token's signed timestamps remain wall-clock based, but the local countdown should use a monotonic clock to avoid changes caused by manual clock edits.

## Battery/OEM considerations

Test at least:
- Pixel or AOSP-like Android device;
- one aggressive OEM device if available;
- screen off/on;
- service restart;
- battery saver;
- reboot.

Never promise universal OEM reliability until tested.

## Emulator-first development policy

The project is expected to be developed primarily using the Android Emulator. Claude Code must make the core application, UI, local database, scheduling, pairing protocol, crypto, backend relay, and FriendAcceptedVerifier work entirely in emulators before requiring physical hardware.

### Emulator-supported work
- onboarding and permission-flow UI where the emulator exposes the permission normally
- Room/database and repository tests
- block-rule and schedule logic
- behavior-event generation using test/fake foreground-app events
- Social Reset state machine
- QR rendering/scanning using an emulator camera when available, with manual pairing-code fallback
- cryptographic key generation, signing, verification, ECDH/HKDF derivation
- two-emulator pairing over the host LAN or configured backend
- WebSocket relay
- notifications where emulator image/services support them
- temporary unlock timers and automatic re-lock
- failure, replay, expiry, revocation, and offline tests
- Compose UI tests

### Physical-device-only validation
Do not claim the following are proven solely by emulator testing:
- real-world AccessibilityService foreground-app interception across OEM builds
- battery/reliability characteristics of long-running monitoring
- exact behavior of OEM background restrictions
- real telephone call verification
- SIM/carrier/Telecom behavior
- behavior of hardware-specific AI/AICore availability

### Required development order
1. Build and test with two Android emulators.
2. Use FriendAcceptedVerifier as the deterministic Social Reset path.
3. Treat CallAttemptVerifier as experimental and disabled by default.
4. Run the full automated test suite on the emulator setup.
5. Only after the emulator flow is stable, validate enforcement and real-call behavior on at least one physical Android device.

### Emulator configuration recommendation
Use two emulator instances representing two separate phones, preferably the same Android API level as the hackathon target. Keep Google APIs/Play-enabled images available when testing notification-dependent or Google service-dependent behavior. Use a stable emulator snapshot so Claude Code can reproduce tests.

The implementation must not hard-code physical hardware dependencies into the core architecture. Any capability that cannot be exercised in an emulator must be behind an interface with a fake/test implementation.
