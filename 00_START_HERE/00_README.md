# Social Reset — Engineering Handoff

This folder is the implementation contract for the Social Reset Android project.

## What Claude Code is expected to do

Build a working Android-first prototype from this specification, not a mockup and not a redesign of the product concept.

The product thesis is:

> Don't block social media. Replace the fake social with real social.

The implementation must make the user's own Android device enforce distracting-app rules locally, while a trusted second device provides a human connection path called a **Social Reset**.

## Locked v1 scope

1. Android app written in Kotlin.
2. Jetpack Compose UI.
3. Room local database.
4. Android Keystore-backed device cryptographic identity.
5. Pairing between two devices using a short-lived pairing code/QR plus public-key exchange.
6. Local blocked-app rules and schedules.
7. Foreground-app detection using AccessibilityService for the prototype, with the enforcement layer isolated behind an interface so it can later be replaced/augmented.
8. Blocking/intervention UI shown over a blocked app.
9. Friend notification/session relay through a minimal backend.
10. Social Reset session state machine.
11. Signed, short-lived unlock token.
12. Temporary unlock window (default 10 minutes).
13. Behavioral engine using deterministic local features first.
14. Optional on-device AI adapter; AI must never be required for enforcement.
15. Offline-first behavior for blocking. Network loss must not disable a configured local block.
16. Test suite for crypto, state machine, blocking decisions, token expiry and server authorization.

## Out of scope for v1

- iOS implementation.
- Recording or analyzing call audio.
- Reading message contents.
- Public social feed.
- Leaderboards.
- Monetization/subscriptions.
- Cloud behavioral analytics.
- Building a full custom ML training pipeline.
- Device-owner enforcement as the default path.
- Root/Magisk dependencies.

## How to use this folder

Start with:

1. `00_START_HERE/01_MASTER_IMPLEMENTATION_PROMPT.md`
2. `02_ARCHITECTURE/01_SYSTEM_ARCHITECTURE.md`
3. `03_ANDROID/01_ANDROID_IMPLEMENTATION.md`
4. `04_SECURITY/01_CRYPTO_PROTOCOL.md`
5. `05_BACKEND/01_BACKEND_SPEC.md`
6. `06_AI/01_BEHAVIOR_ENGINE.md`
7. `07_TESTING/01_TEST_STRATEGY.md`
8. `08_HACKATHON/01_30_HOUR_BUILD_PLAN.md`

Claude Code should read all Markdown files before making architectural changes.

## Non-negotiable engineering rules

- Do not silently change the product thesis.
- Do not move behavioral decision-making to the cloud.
- Do not store private keys in the database or preferences.
- Do not transmit call audio.
- Do not make successful cloud communication a prerequisite for local blocking.
- Do not implement security by inventing cryptography; use well-reviewed primitives/libraries.
- Do not copy GPL code into a MIT/Apache/commercially-oriented codebase unless the licensing decision is explicitly changed.
- Every new permission must have a written reason and a test of the degraded behavior when it is denied.
- Every feature must have an automated test or a documented manual test.
- Prefer the smallest implementation that satisfies this specification.

## Current engineering stance

This is a hackathon-grade vertical slice first. Reliability of the core loop is more important than feature count.

Core demo loop:

`blocked app -> intervention -> social reset -> friend connection -> verified session -> signed unlock -> 10 minute access -> automatic re-lock`
