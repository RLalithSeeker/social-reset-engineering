# Social Reset — Project Description

**One line:** Social Reset breaks automatic social-media scrolling by turning
a block into a short, trusted human connection — then granting only a
temporary, automatically re-locking unlock.

## Problem

People open Instagram, YouTube, or similar apps on autopilot — not a choice,
a habit loop. Existing wellbeing tools block the app or show a timer. They
remove access but offer nothing meaningful in the moment the habit fires.

## Solution

An Android-first app that intervenes locally when a blocked app opens during
a focus schedule and offers a **Social Reset**: ask a trusted paired person
to accept a short reset session. On acceptance the requester gets a
short-lived unlock (e.g. 10 minutes); the app re-blocks automatically.

```
blocked app → local intervention → Social Reset → trusted peer
           → authenticated session → short-lived unlock → automatic relock
```

## Why it stands out

- **Human alternative, not punishment** — connection replaces the scroll.
- **Two-phone demo** — judges watch the full loop live.
- **Privacy-first** — block rules stay on-device; the backend only relays
  signed session events and never decides unlocks.
- **Trustworthy access** — paired devices authenticate each other; grants are
  signed, single-session, short-lived, replay-safe.
- **AI optional** — on-device behavior signals detect repeated opens; AI
  never controls the unlock decision.

## How it works (v1)

1. **Pick + schedule** — user selects distracting apps and focus windows.
2. **Pair once** — QR/code exchange, public keys swapped, short
   authentication string confirmed, peer persisted.
3. **Detect** — AccessibilityService sees the target app foreground;
   local rules decide ALLOW / FRICTION / SOCIAL_RESET.
4. **Reset** — request → peer accepts → signed session events exchanged and
   verified → signed unlock grant issued → countdown → automatic re-lock.
5. **Fail closed** — bad signature, expiry, replay, revoked peer, no
   network: the device stays blocked. Local blocking works offline.

## Tech

- **Android** — Kotlin, Jetpack Compose + Material 3, Room, Coroutines/Flow,
  AccessibilityService + overlay, Keystore + Tink crypto (ECDH/HKDF, signed
  events), OkHttp + WebSocket, WorkManager.
- **Backend (relay only)** — FastAPI + SQLite (SQLModel), JWT device
  identities, WebSocket session relay.
- **Tests** — Android unit suite (ResetCoordinator verified), backend pytest,
  two-device emulator run verified 2026-09-07.

## Status

Working two-phone prototype on emulator (pair → request → accept → grant →
timed unlock → re-lock). Remaining: QR pairing UX polish, release APK,
on-device failure-mode passes (reboot, network loss, revoke, replay).

## What it is not

Not an addiction diagnosis, not bypass-proof, not identical on every
OEM skin, and AI does not grant access.
