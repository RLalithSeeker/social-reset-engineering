# Social Reset

> Break automatic social-media scrolling by turning a block into a short,
> trusted human connection — then granting only a temporary,
> automatically re-locking unlock.

```
blocked app → local intervention → Social Reset → trusted peer
           → authenticated session → short-lived unlock → automatic relock
```

## Why this exists

People open distracting apps on autopilot — not a choice, a habit loop.
Blockers and timers remove access but offer nothing meaningful in the
moment the habit fires. Social Reset replaces the loop with connection:
when a blocked app opens during a focus window, the phone intervenes
locally and offers a short, verified interaction with a trusted person.
On success the device earns a brief unlock; the app re-blocks on expiry.

## Key ideas

- **Local-first enforcement** — the device decides; no server ever sends
  an `ALLOW_APP` command. Blocking works fully offline.
- **Relay, not authority** — the backend coordinates sessions and relays
  signed events; it cannot grant access.
- **Cryptographic trust** — paired devices authenticate each other;
  unlock grants are signed, single-session, short-lived, and replay-safe.
- **Fail closed** — bad signature, expiry, replay, revoked peer, or no
  network means the device stays blocked.
- **AI subordinate** — on-device behavior signals detect repeated opens;
  AI never controls the unlock decision.
- **Private by design** — block rules stay on-device; no call audio; no
  unnecessary content collection.

## How it works

1. **Focus schedule** — the user picks distracting apps and focus windows.
2. **Pair once** — two devices exchange public keys (QR/code), confirm a
   short authentication string, and persist the peer.
3. **Detect** — when a target app comes to the foreground, local rules
   decide: allow, add friction, or require a Social Reset.
4. **Reset** — the requester asks their peer; the peer accepts; both
   devices exchange signed session events; a valid completion mints a
   short-lived unlock grant bound to that session.
5. **Re-lock** — the grant expires automatically and blocking resumes.

Full protocol and state machine:
[`docs/PROJECT_OVERVIEW.md`](docs/PROJECT_OVERVIEW.md) ·
one-page brief:
[`docs/PROJECT_DESCRIPTION.md`](docs/PROJECT_DESCRIPTION.md).

## Tech stack

**Android** (`android/`, `com.socialreset.app`) — Kotlin · Jetpack Compose
+ Material 3 · Room (KSP) · Coroutines/Flow · AccessibilityService +
overlay intervention UI · Android Keystore + Tink (ECDH/HKDF pairing,
signed events) · OkHttp + WebSocket · WorkManager.

**Backend** (`backend/`, relay/coordinator) — FastAPI · SQLite via
SQLModel · Pydantic validation · PyJWT device identities · WebSocket
session relay · pytest suite.

## Project layout

| Path | Contents |
|---|---|
| `android/` | Native app (single `:app` module) |
| `backend/` | Relay server |
| `docs/` | Overview, project description, handoff, traps |

## Getting started

```powershell
# Android unit tests
gradle -p android :app:testDebugUnitTest

# Debug APK
gradle -p android :app:assembleDebug

# Backend suite
python -m pytest backend -q

# Repo checks
.\verify.ps1
```

The debug build points at the emulator host loopback
(`http://10.0.2.2:8099`; cleartext is debug-only). Copy `.env.example`
patterns as needed — never commit real credentials.

## Status

Two-device flow verified on emulators (pair → request → accept → grant →
timed unlock → re-lock, live WebSockets); local enforcement verified
against a real app launch; backend relay covered by tests. In progress:
pairing UX polish, release APK, and on-device failure-mode passes
(reboot, network loss, peer revocation, replay, permission denial).

## What it is not

Not a medical diagnosis, not bypass-proof, and behavior varies across
OEM skins and Android permission models.
