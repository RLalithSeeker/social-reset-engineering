# MASTER IMPLEMENTATION PROMPT FOR CLAUDE CODE

You are implementing **Social Reset**, an Android-first digital wellbeing application. Treat the Markdown files in this repository as the primary technical specification.

## Mission

Build a production-quality hackathon prototype that replaces habitual social-media consumption with real human connection instead of merely blocking apps.

The user chooses distracting apps and schedules. When a configured app is opened during a block, the local enforcement engine decides whether to allow, add friction, or require a Social Reset. A Social Reset is an interaction with a trusted paired person. After the reset is successfully completed according to the selected verification mode, the local device receives a cryptographically authenticated short-lived unlock grant. The app then automatically re-locks.

## First action

Before editing code:

1. Read every file under `00_START_HERE`, `01_PRODUCT`, `02_ARCHITECTURE`, `03_ANDROID`, `04_SECURITY`, `05_BACKEND`, `06_AI`, `07_TESTING`, `08_HACKATHON`, `09_OPEN_SOURCE`, and `10_DECISIONS`.
2. Inspect the actual repository tree and existing code.
3. Reconcile the existing project with the specification.
4. Preserve existing working code unless it conflicts with a MUST rule here.
5. Record any necessary deviations in `10_DECISIONS/`.

Do not ask the user architectural questions that are already answered by these files. Make a reasonable implementation choice and continue.

## Locked stack

### Android
- Kotlin.
- Jetpack Compose + Material 3.
- Single Android application module unless the existing repository strongly benefits from modularization.
- MVVM/Clean-ish boundaries without ceremony.
- Room.
- Kotlin Coroutines + Flow.
- Android Keystore.
- AndroidX Security where appropriate.
- WorkManager for deferrable maintenance work.
- AlarmManager only where exact user-visible schedule transitions justify it.
- AccessibilityService for the prototype enforcement provider.
- NotificationManager/notifications for friend/session events.
- BiometricPrompt only if it helps protect local settings; it is not required for the core flow.

### Backend
- FastAPI.
- SQLite for local development/demo unless an existing backend already uses PostgreSQL.
- WebSocket for active-session event relay.
- FCM-compatible push abstraction for production notifications, but do not block the local prototype on Firebase credentials.
- Pydantic models.
- Ed25519/X25519-compatible cryptographic primitives through a well-reviewed library. Do not implement primitives manually.

## Architecture requirements

The Android app must have these boundaries:

- `enforcement/` — foreground app detection and intervention.
- `rules/` — local block rules/schedules.
- `behavior/` — local event aggregation and behavior classification.
- `social/` — trusted contacts and Social Reset sessions.
- `crypto/` — key management, pairing, signing, token verification.
- `network/` — API/WebSocket client and retry logic.
- `data/` — Room entities/DAOs/repositories.
- `ui/` — Compose screens/state.
- `notifications/` — local and remote notification handling.

The backend must be a **relay/coordinator**, not the source of truth for local blocking.

## Source of truth rules

- Local block policy: Android device.
- Local schedule: Android device.
- Private key: Android Keystore.
- Paired relationship: local database + signed peer metadata.
- Session status: both devices may observe it, but each device validates cryptographic messages locally.
- Unlock decision: local policy + verified token.
- Server: transports events, authenticates device identity, maintains short-lived session coordination state.

## Required user flows

### Flow A — onboarding

1. Launch app.
2. Explain product in one screen.
3. Ask for required permissions progressively.
4. Create device identity.
5. Let user select blocked apps.
6. Let user create a simple schedule.
7. Offer pairing.

### Flow B — pairing

1. Device A chooses `Pair with friend`.
2. Generate short-lived pairing session.
3. Display QR + human-readable fallback code.
4. Device B scans/enters code.
5. Exchange public keys and device display names.
6. Both sides derive a shared session key using an established ECDH scheme.
7. Confirm fingerprints/short authentication string.
8. Persist only the peer public key and required metadata.
9. Close pairing session.

### Flow C — blocked app

1. AccessibilityService sees target package enter foreground.
2. Enforcement engine checks the local policy.
3. If not blocked, do nothing.
4. If blocked, determine intervention level.
5. Show native intervention UI.
6. User can start Social Reset.
7. User can take an allowed alternative if configured.
8. In Hard Mode, bypass must remain unavailable except for explicitly configured emergency access.

### Flow D — Social Reset

1. Requester creates `reset_session` with random session id and nonce.
2. Friend receives notification.
3. Friend accepts.
4. Connection method starts.
5. Both devices exchange signed session events.
6. Verifier checks event ordering, timestamps, nonce, peer identity and signature.
7. On successful completion, requester derives/accepts a signed unlock grant.
8. Local enforcement engine enters temporary allow state.
9. Countdown UI is shown.
10. Expiry returns state to blocked.

### Flow E — failure

If network is unavailable, friend rejects, token is invalid, clock/timestamp is outside tolerance, nonce mismatches, or the peer key is revoked, the device must stay blocked.

## Verification strategy

Implement Social Reset verification as a pluggable interface:

```kotlin
interface ResetVerifier {
    suspend fun start(session: ResetSession): VerificationStart
    suspend fun handleEvent(event: ResetEvent): VerificationResult
    suspend fun cancel(sessionId: String)
}
```

Implement these v1 strategies:

1. `FriendAcceptedVerifier` — deterministic demo fallback, friend explicitly confirms the reset.
2. `CallAttemptVerifier` — experimental adapter. Do not claim that Android can always expose answered duration. It must clearly report uncertainty/failure and never silently grant an unlock because a call was merely initiated.

Later strategies can include QR proximity/BLE/NFC, but do not expand v1 unless the core loop is stable.

## Cryptography rules

- Use per-device asymmetric identity keys.
- Private material stays in Android Keystore.
- Pairing is authenticated by short-lived pairing session + peer key exchange + user-visible fingerprint confirmation.
- Use ECDH to derive a shared secret and HKDF to derive purpose-specific keys.
- Use a standard signature scheme for event authorization.
- Every session event must include:
  - protocol version
  - session id
  - monotonic sequence number
  - sender device id
  - event type
  - timestamp
  - random nonce/session nonce reference
  - payload hash when appropriate
  - signature
- Every unlock grant must be short-lived and bound to:
  - requester device id
  - trusted peer id/session
  - session id
  - blocked package name(s) or policy id
  - issue time
  - expiry time
  - grant id
  - reset type
- Reject replays.
- Reject stale tokens.
- Reject tokens for another device/session/policy.

## Data model

Implement at least:

`BlockedApp`
- id
- packageName
- displayName
- enabled

`ScheduleRule`
- id
- name
- enabled
- startMinuteOfDay
- endMinuteOfDay
- weekdaysBitmask
- mode

`BehaviorEvent`
- id
- packageName
- timestamp
- eventType
- sessionId?

`TrustedPeer`
- id
- deviceId
- displayName
- publicKey
- keyFingerprint
- status
- createdAt
- revokedAt?

`ResetSession`
- sessionId
- requesterDeviceId
- peerDeviceId
- blockedPackage
- createdAt
- state
- resetType
- expiresAt

`UnlockGrant`
- grantId
- sessionId
- targetDeviceId
- policyId/packageName
- issuedAt
- expiresAt
- consumedAt?
- signerDeviceId
- signature

Never store private key bytes in Room.

## UI requirements

Keep the UI minimal and functional.

Required screens:
- Home/status.
- Blocked apps.
- Schedule editor.
- Pairing.
- Trusted person detail.
- Active Social Reset.
- Block intervention.
- Unlock countdown.
- Settings/permissions.
- Diagnostic/debug screen for hackathon builds.

The intervention screen should communicate:

> This looks like a habit loop.
> Instead of another scroll, connect with someone.

Do not shame the user.

## Local behavioral engine v1

Start with deterministic scoring.

Suggested score inputs:

- opens in last 10 minutes: +2 per repeated reopen after the first.
- opens in last 30 minutes: +1 after threshold.
- short repeated sessions: +1.
- recent unlock followed by immediate reopen: +2.
- long intentional session: -1.
- explicit user intent before opening: -2.

Clamp score to 0..10.

Map:
- 0..2 -> intentional/light.
- 3..5 -> uncertain/friction.
- 6..10 -> habitual/social reset.

These exact weights are implementation defaults, not scientific claims. Keep them configurable for experimentation.

## Background behavior

The app must degrade safely:

- If network unavailable: local blocks still work.
- If friend unavailable: show pending state and remain blocked in Hard Mode.
- If AccessibilityService is disabled: app must explain that enforcement is not active.
- If overlay permission is disabled: app must not falsely claim blocking is active.
- If notification permission is denied: in-app status must explain friend notification limitations.
- If device reboots: restore local rules and monitoring according to the OS-supported lifecycle.

Modern Android restricts background activity/foreground-service behavior. Do not use obsolete unrestricted background-start assumptions. Prefer user-triggered PendingIntent/notification flows where required.

## Security failure policy

Security failure always fails closed for unlocking.

Examples:
- bad signature -> deny.
- unknown peer -> deny.
- expired grant -> deny.
- replayed nonce/sequence -> deny.
- mismatched target package -> deny.
- revoked relationship -> deny.
- invalid server response -> deny.

## Testing requirements

Minimum automated coverage:

- unit tests for schedule matching.
- unit tests for behavior score.
- unit tests for state machine transitions.
- crypto tests for key creation and signature verification.
- token expiry and audience binding tests.
- replay rejection tests.
- backend authorization tests.
- serialization round-trip tests.

Manual device tests:
- reboot.
- app launch.
- blocked app foregrounding.
- overlay.
- scheduled transition.
- 10-minute unlock expiry.
- pairing on two real phones.
- network disconnect during blocked state.
- friend rejection.
- stale token.
- revoked peer.
- Android OEM/battery restriction behavior.

## Coding style

- Kotlin idiomatic.
- Small classes.
- Explicit domain models.
- No singleton global state unless necessary for Android service lifecycle.
- Use dependency injection only where it meaningfully reduces coupling.
- Avoid reflection-heavy frameworks.
- Log security-relevant decisions with redacted identifiers in debug builds only.
- No sensitive payloads in logs.

## Definition of done

The project is done when a fresh clone can build, install and demonstrate the complete core loop on two Android devices, with automated tests passing and with the documented degraded behavior for permissions/network failures.

Do not stop after scaffolding. Implement, test, fix, and document the working system.
