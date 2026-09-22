# Social Reset — Project Overview

> One doc: what it is, how it works, and what it is built with.
> Companion visual explainer: `LEARN_social-reset.html` (repo root, open in any browser).

## What it is

Social Reset is an Android-first digital wellbeing app. Instead of just blocking distracting apps, it replaces the habit loop with real human connection: when a blocked app opens during a schedule window, the phone intervenes and offers a **Social Reset** — a short, verified interaction with a trusted paired person. On success, the device receives a cryptographically signed, short-lived unlock grant. The app re-locks automatically when the grant expires.

The core loop:

```
blocked app → local intervention → Social Reset → trusted peer
           → authenticated session → short-lived unlock → automatic relock
```

## Design principles

- **Local-first blocking** — the device decides; no server ever sends an `ALLOW_APP` command.
- **Backend = relay, not authority** — FastAPI only relays signed events and coordinates sessions.
- **Crypto trust between paired devices** — private keys never leave Android Keystore.
- **AI optional and subordinate** — v1 scoring is deterministic; AI never unlocks anything.
- **Fail closed for unlocking** — bad signature, expired token, replay, revoked peer, network loss → stay blocked.
- **Fail open for social extras** — relay down or AI unavailable never breaks blocking itself.
- **No shaming** — the intervention screen says "This looks like a habit loop. Instead of another scroll, connect with someone."

## How it works

### 1. Onboarding
Launch → one-screen product explanation → progressive permission requests → device identity created (Keystore keypair) → pick blocked apps → create a schedule → offer pairing.

### 2. Pairing
Device A creates a short-lived pairing session (QR + human-readable fallback code). Device B scans/enters it. Both exchange public keys and display names, derive a shared secret via ECDH + HKDF, confirm a short authentication string (fingerprint), then persist only the peer's public key and metadata.

### 3. Blocked app detected
`AccessibilityService` sees the target package enter the foreground → `EnforcementEngine` checks local rules (Room) → decision is `ALLOW`, `FRICTION`, or `SOCIAL_RESET` → intervention UI or overlay shown. Hard Mode leaves no bypass except explicitly configured emergency access.

### 4. Social Reset (the state machine)

```
IDLE → REQUEST_CREATED → WAITING_FOR_PEER → PEER_ACCEPTED
     → CONNECTION_ACTIVE → CONNECTION_VERIFIED → GRANT_ISSUED
     → UNLOCK_ACTIVE → EXPIRED → LOCKED
      (+ FAILED / CANCELLED → IDLE)
```

The requester creates a `reset_session` with a random session id + nonce; the friend is notified and accepts; both devices exchange **signed session events** (protocol version, session id, monotonic sequence number, sender device id, event type, timestamp, nonce, payload hash, signature); the verifier checks ordering, timestamps, nonce, peer identity, and signature. A valid completion yields a signed unlock grant bound to requester device, peer, session, package/policy, issue/expiry times, grant id, and reset type. Replay, stale tokens, and cross-device tokens are rejected. After expiry the state returns to blocked.

There is no code path from `WAITING_FOR_PEER` directly to `UNLOCK_ACTIVE`. Every event is idempotent — reprocessing cannot mint a second grant.

### 5. Failure
Network unavailable, friend rejects, invalid token, clock outside tolerance, nonce mismatch, revoked peer, or invalid server response → the device stays blocked. Local blocks keep working with zero connectivity.

### Verification strategies (pluggable `ResetVerifier`)
1. `FriendAcceptedVerifier` — deterministic v1 demo path: friend explicitly confirms.
2. `CallAttemptVerifier` — experimental adapter; Android cannot guarantee answered-call duration, so it reports uncertainty and never grants on a mere dial.

### Behavior engine v1 (deterministic)
Score inputs: repeated reopens (+2 each after the first in 10 min), opens past threshold in 30 min (+1), short repeated sessions (+1), unlock followed by immediate reopen (+2), long intentional session (−1), explicit pre-open intent (−2). Clamped 0–10. `0–2` = intentional, `3–5` = friction, `6–10` = social reset. Weights are configurable defaults, not scientific claims.

## Tech stack

### Android (`android/`, single `:app` module, package `com.socialreset.app`)
| Concern | Choice |
|---|---|
| Language | Kotlin (JVM target 17) |
| UI | Jetpack Compose + Material 3, Navigation Compose |
| SDK | compileSdk 35, minSdk 26, targetSdk 35 |
| Architecture | MVVM / clean-ish boundaries: `ui → domain ← data`, platform + network underneath |
| Persistence | Room (KSP), schema export on |
| Async | Coroutines + Flow |
| Enforcement | AccessibilityService + overlay Activity |
| Crypto | Android Keystore + Tink; ECDH/HKDF pairing; signed events; ZXing for QR |
| Network | OkHttp (HTTP bootstrap) + WebSocket (live session events) |
| Scheduling | WorkManager for maintenance; AlarmManager only for exact schedule transitions |
| Tests | JUnit, MockK, Turbine, Robolectric, MockWebServer, Room testing |
| Relay endpoint (debug) | `http://10.0.2.2:8099` / `ws://10.0.2.2:8099` (emulator host loopback; cleartext debug-only) |

Android code packages: `enforcement/`, `rules/`, `behavior/`, `social/`, `pairing/`, `crypto/`, `network/`, `data/`, `ui/`, `notifications/`, `platform/`, `security/`, `debug/`, `core/`.

### Backend (`backend/`, relay/coordinator)
| Concern | Choice |
|---|---|
| Framework | FastAPI 0.115.6 + Uvicorn |
| Data | SQLModel 0.0.22 over SQLite (dev/demo) |
| Validation | Pydantic 2.10.5 + pydantic-settings |
| Auth | PyJWT 2.9.0 device identity tokens |
| Realtime | WebSocket relay (`websockets`) for active sessions |
| Push | FCM-compatible abstraction planned; not a prototype blocker |
| Tests | pytest + pytest-asyncio + httpx (auth, pairing, relay, WS delivery) |

The server stores: registered device identities, pairing state, short-lived session coordination, and relayed signed events. It never decides unlocks.

## Data model (Room / SQLModel)

`BlockedApp`, `ScheduleRule`, `BehaviorEvent`, `TrustedPeer` (public key + fingerprint + revokedAt), `ResetSession`, `UnlockGrant` (see the Room entities under `android/app/src` and the SQLModel models under `backend/`). Private key bytes are never stored in Room — they live only in Keystore.

## Current status (2026-09-07)

| Area | State |
|---|---|
| Android reset flow | Verified by unit tests (`ResetCoordinator.kt`) |
| Two-device e2e | **Verified 2026-09-07**: two emulators paired via relay, live WebSockets, reset request/accept, grant issued + redeemed, blocked Settings package unlocked during window |
| Local enforcement | Verified on emulator: seeded YouTube rule opened `BlockOverlayActivity` on real YouTube launch |
| Backend relay | Registration, pairing, signed event relay, WS delivery — tests pass |
| QR pairing UX, release APK, polish | Not implemented |
| Manual failure modes (reconnect, expiry, replay, revoke, reboot, network loss, permission denial) | Not yet device-verified |

## Verify it yourself

```
gradle -p android :app:testDebugUnitTest        # Android unit suite
gradle -p android :app:assembleDebug            # Debug APK
python -m pytest backend -q                     # Backend suite
```

## Repo map

`android/` native app · `backend/` relay server · `docs/` overview, brief, handoff + traps.
