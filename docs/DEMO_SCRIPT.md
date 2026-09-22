# Demo video script — block overlay (60–90 s)

Goal: a clean clip that opens on **"YouTube is blocked / Social Reset required."**
Record with `tools/record-demo.ps1` — it waits for the overlay, then captures.

## Before you start

- Emulator `socialreset-peer` running, app `com.socialreset.app` installed (debug APK).
- No notifications pulled down, no other windows over the emulator.

## The take (do exactly this)

1. Start the script:
   ```
   .\tools\record-demo.ps1 -Seconds 25
   ```
2. On the device: open **Social Reset**, scroll to the bottom.
3. Under **Debug test panel**, tap **Seed YouTube block rule**.
4. Tap **Show sample block overlay**.
5. Hands off — the script detects the overlay and records 25 s.
6. Play back `demo/social-reset-demo-<timestamp>.mp4`:
   - Opens on the clean overlay, text readable.
   - No setup taps visible (seeding happens before recording starts).

## If it fails

| Symptom | Fix |
|---|---|
| `not booted` | Wait for the emulator to finish starting, re-run. |
| `Timed out waiting for the block overlay` | Overlay was not in the foreground — tap Show sample block overlay again and re-run. |
| `device not found` | Check `adb devices`; pass `-Serial` with the right serial. |
| Overlay shows wrong app label | Re-tap Seed YouTube block rule first; the sample overlay uses the seeded rule. |

## Two-phone version (hackathon)

Same overlay moment on device A, then: request reset → device B taps
**Accept** → device A shows the unlock countdown → clip ends on automatic
re-lock. Keep "friend accepted" as the verifier on camera.
