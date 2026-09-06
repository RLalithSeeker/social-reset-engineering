# Manual Test Checklist

## Setup

- [ ] Fresh install.
- [ ] Onboarding completes.
- [ ] Device key created.
- [ ] Accessibility status detected.
- [ ] Overlay status detected.
- [ ] Notification status detected.

## Blocking

- [ ] Blocked app opens to intervention.
- [ ] Allowed app opens normally.
- [ ] Schedule boundaries behave correctly.
- [ ] Disabled rule does not block.

## Behavior

- [ ] Single intentional open -> light.
- [ ] Repeated opens -> score increases.
- [ ] Habit threshold -> Social Reset.
- [ ] Temporary unlock -> score does not cause immediate false relock.

## Pairing

- [ ] QR generated.
- [ ] QR scanned.
- [ ] Human code works.
- [ ] Fingerprints match.
- [ ] Pair appears on both devices.
- [ ] Revocation works.

## Social Reset

- [ ] Request created.
- [ ] Friend notified.
- [ ] Accept works.
- [ ] Reject works.
- [ ] Completion works.
- [ ] Unlock grant works.
- [ ] Timer works.
- [ ] Expiry re-locks.

## Resilience

- [ ] Backend down.
- [ ] Network toggled off.
- [ ] Device reboot.
- [ ] Battery saver.
- [ ] Accessibility disabled.
- [ ] Overlay disabled.
- [ ] Notifications denied.
- [ ] Peer revoked.

## Emulator-first test pass

### Two-emulator setup
- [ ] Emulator A launches the app successfully.
- [ ] Emulator B launches the app successfully.
- [ ] A and B can reach the development backend/relay.
- [ ] A and B can pair using QR or fallback code.
- [ ] Both fingerprints are displayed and confirmed.
- [ ] Public keys persist after process restart.

### Deterministic Social Reset
- [ ] A enters a blocked state.
- [ ] A creates a Social Reset session.
- [ ] B receives the request.
- [ ] B accepts.
- [ ] Signed session events arrive in order.
- [ ] A validates the peer signature.
- [ ] A receives a valid short-lived unlock grant.
- [ ] The blocked package is allowed only for the configured window.
- [ ] The package becomes blocked again when the grant expires.

### Negative tests
- [ ] Replay an old event: rejected.
- [ ] Change the package name: rejected.
- [ ] Change the requester device id: rejected.
- [ ] Change the session id: rejected.
- [ ] Use an expired grant: rejected.
- [ ] Use a revoked peer key: rejected.
- [ ] Send events out of sequence: rejected.
- [ ] Kill/restart the app mid-session: state recovers safely.
- [ ] Drop the network: no automatic unlock.

### Physical-device follow-up
After all emulator tests pass, repeat the enforcement-critical scenarios on a physical device and record OEM/API-level differences separately rather than weakening the emulator architecture.
