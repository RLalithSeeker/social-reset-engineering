# Social Reset

> Break automatic social-media scrolling by turning a block into a short,
> trusted human connection — then granting only a temporary,
> automatically re-locking unlock.

```
blocked app → local intervention → Social Reset → trusted peer
           → authenticated session → short-lived unlock → automatic relock
```

## Demo (60–90 s)

1. Start the emulator (`socialreset-peer`), app installed.
2. Open Social Reset, scroll to **Debug test panel**.
3. Tap **Seed YouTube block rule** → tap **Show sample block overlay**.
4. Record only when the clean overlay is up:
   **"YouTube is blocked / Social Reset required."**

Automated: `.\tools\record-demo.ps1 -Seconds 25` (waits for the overlay,
records to `demo/`). Full take: [`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md).

## What it is

Android-first digital-wellbeing prototype. When a blocked app opens during a
focus window, the phone intervenes locally and offers a **Social Reset** —
a short verified interaction with a trusted paired person. Success yields a
cryptographically signed, short-lived unlock grant; the app re-locks on
expiry. Longer writeup: [`docs/PROJECT_DESCRIPTION.md`](docs/PROJECT_DESCRIPTION.md).
Visual explainer: `LEARN_social-reset.html` (open in a browser).

## Principles

- Local-first blocking — the device decides; no server sends `ALLOW_APP`.
- Backend = relay, not authority.
- Keys never leave Android Keystore; grants are signed + replay-safe.
- AI optional and subordinate — v1 scoring is deterministic.
- Fail closed for unlocking, fail open for social extras.
- No shaming, no call audio, no unnecessary content collection.

## Repo map

| Path | Holds |
|---|---|
| `android/` | Kotlin app (`com.socialreset.app`), Compose + Room + Keystore |
| `backend/` | FastAPI relay (devices, pairing, session events, WebSocket) |
| `tools/record-demo.ps1` | Overlay demo recorder (waits for overlay, saves MP4 to `demo/`) |
| `demo/` | Recorded demo clips |
| `docs/` | `PROJECT_DESCRIPTION.md`, `PROJECT_OVERVIEW.md`, `DEMO_SCRIPT.md`, handoff + traps |
| `00_START_HERE/` | Master implementation prompt — start here to build |
| `01_PRODUCT`–`11_REFERENCE` | Spec, architecture, platform, security, backend, AI, testing, hackathon, open-source, decisions |
| `08_HACKATHON/` | Idea brief + demo plan |
| `TASKS/` | Working handoffs |

## Quick start

```powershell
# Android unit tests + debug APK
gradle -p android :app:testDebugUnitTest
gradle -p android :app:assembleDebug

# Backend suite
python -m pytest backend -q

# Repo checks
.\verify.ps1
```

Debug APK installs on the `socialreset-peer` emulator; relay defaults to
emulator host loopback (`http://10.0.2.2:8099`, cleartext debug-only).

## Status (2026-09-07)

- Two-device flow verified on emulators: pair → request → accept → grant →
  timed unlock → re-lock (Settings package, live WebSockets).
- Local enforcement verified: seeded YouTube rule opened the block overlay
  on real YouTube launch.
- Backend relay (register, pairing, signed events, WS delivery): tests pass.
- Not yet: QR pairing UX, release APK, on-device failure passes
  (reboot, network loss, revoke, replay, permission denial).

## What it is not

Not an addiction diagnosis. Not bypass-proof. Behavior varies across OEM
skins and Android permission models. AI never grants access.
