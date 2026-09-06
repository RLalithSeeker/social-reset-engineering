# Opencode Task: Debug Local Relay Cleartext

Repo: `E:\projects\social-reset-engineering`

Goal: make the Android debug APK allowed to call the local development relay at `http://10.0.2.2:8099` without loosening release builds.

Context:
- The app UI is working but pairing create currently fails with:
  `CLEARTEXT communication to 10.0.2.2 not permitted by network security policy.`
- Backend relay is local dev only.
- Keep the fix debug-scoped if possible.

Tasks:
1. Inspect Android manifest/source-set structure.
2. Add the smallest debug-only network security config that permits cleartext to `10.0.2.2`.
3. Do not enable broad cleartext in release.
4. Run `gradle -p E:\projects\social-reset-engineering\android :app:assembleDebug`.
5. Report files changed and verification result.

Constraints:
- Do not edit unrelated files.
- Do not delete files.
- If Gradle fails for an existing unrelated reason, report exact failure and stop.
