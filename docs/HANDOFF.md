# Handoff

## Where We Are

| Area | State |
|---|---|
| Android reset flow | VERIFIED by unit tests: local request creation, signed relay request, inbound peer acceptance, grant validation, and local unlock redemption are implemented in `android/app/src/main/java/com/socialreset/app/social/ResetCoordinator.kt`. |
| Android UI | DEVICE-VERIFIED for the prototype path: the setup screen registered, paired, connected both relay clients, requested/accepted a reset, and redeemed a grant across two emulators. It remains deliberately compact and technical. |
| Backend relay | VERIFIED: FastAPI registration, pairing, reset session creation, signed event relay, and WebSocket delivery tests pass. |
| Local enforcement | VERIFIED previously on one emulator: a seeded YouTube rule opened `BlockOverlayActivity` after a real YouTube launch. See `TASKS/HANDOFF_ANDROID.md`. |
| End-to-end social reset | VERIFIED on 2026-09-07: two emulators paired through the relay, maintained live WebSockets, relayed a reset request and peer acceptance, issued/redeemed the grant, and allowed the blocked Settings package during the active unlock window. |
| QR pairing and release readiness | NOT IMPLEMENTED: QR render/scan, polished flow, release APK, notices, and real-world failure-mode validation remain. |

## What Is Proven

| Claim | Reproduce |
|---|---|
| Android unit suite passes | `gradle -p E:\projects\social-reset-engineering\android :app:testDebugUnitTest` |
| Android debug APK assembles | `gradle -p E:\projects\social-reset-engineering\android :app:assembleDebug` |
| Backend test suite passes | `python -m pytest E:\projects\social-reset-engineering\backend -q` |
| Request/grant orchestration is covered | Open `android/app/src/test/java/com/socialreset/app/social/ResetCoordinatorTest.kt`; tests cover request relay and accepted-grant redemption. |
| Two-device reset and unlock | Start the local relay, install the debug APK on `emulator-5554` and `emulator-5556`, register and pair both, connect the relay clients, select Settings on the requester, request/accept the reset, then open Android Settings on the requester. Verified 2026-09-07. |

## What Is Assumed

- Reconnect behavior after process death/reboot has not been verified.
- Grant expiry and post-expiry enforcement have not been device-verified; the successful run verified access while the grant was active.
- NOT VERIFIED: the reset UI is usable enough for a non-developer; it remains deliberately compact and technical.

## Next Action

Test reconnect, expiry, replay, revoke, reboot, network loss, and denied permissions on live devices. Keep the two-emulator flow as the baseline regression path.

## Open Forks

| Decision | Recommendation |
|---|---|
| Pairing UX | Add the specified QR URI render/scan path next. Keep manual code entry as a fallback for emulator and accessibility testing. |
| Live test failure | Diagnose contract or lifecycle defects before expanding UI. The first live run exposed the discarded WebSocket reference; `ResetCoordinator` now retains it for the active session, and the repeated run passed. |
| Release scope | Do not claim production readiness until revoke, replay, reboot, network-loss, permission-denial, and two-device flows have manual evidence. |

## Traps Hit This Session

- `ApiClient` methods implementing `ResetRelayApi` cannot repeat Kotlin default parameter values; defaults belong on the interface only.
- This workspace and its children contain no `.git` directory. No commit or push can be made until the owner initializes or restores repository metadata.
- There is no Gradle wrapper; the verified commands use the installed Gradle executable.
