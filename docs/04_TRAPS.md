# Traps

- `ApiClient` overrides of `ResetRelayApi` must not declare default parameter values. Kotlin permits those defaults only on the interface declaration.
- The Android project has no Gradle wrapper. Use installed Gradle with `-p E:\projects\social-reset-engineering\android`.
- The debug relay allows cleartext only to `10.0.2.2`; release remains strict. Do not broaden this exception.
- After reinstalling the APK, confirm the accessibility service remains enabled before testing interception. Do not force-stop Social Reset immediately before that test; it can leave the service unbound in emulator automation.
