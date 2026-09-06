# Open-Source Reference Analysis

## SelfLock

Repository: https://github.com/EtashTyagi/SelfLock

The repository describes a Kotlin/Compose/Room/Hilt architecture and uses AccessibilityService, Usage Stats, foreground services and AlarmManager. It currently reports Android 14+/API 34 with target API 36. Use it as an architectural reference or carefully review it for reusable MIT-licensed components before copying code. citeturn637942search1

Recommended use: enforcement architecture reference; do not assume its code is directly drop-in compatible without reviewing license/version/current files.

## TapBlok

Repository: https://github.com/cajdata/TapBlok

Apache 2.0. Uses Compose, Room, a blocking activity, scheduled blocking, NFC/QR unlock and boot recovery. It is especially useful as a reference for real-world friction and lock-session concepts. citeturn637942search2

Recommended use: session/recovery UX reference and selectively reusable Apache-licensed components after review.

## Nudge

Repository: https://github.com/astraedus/nudge

GPL-3.0. It documents a local-first Android blocker using AccessibilityService, Room/DataStore, schedules, delay-to-open and per-app budgets. citeturn637942search0

Recommended use: architecture/UX reference only unless the final project's licensing strategy explicitly permits GPL integration.

## ExpoBlocker

Repository: https://github.com/YeneKoo/ExpoBlocker

MIT. Demonstrates a React Native/Expo control plane with native Android blocking implementation and scheduling/overlay APIs. citeturn637942search5

Recommended use: reference for cross-language/native separation if UI technology is later changed.

## OpenLock

Repository: https://github.com/MalicKAbdullah/openlock

MIT. Uses Flutter/Dart for UI/configuration and Kotlin for native enforcement. Its documented enforcement approach uses UsageStatsManager + overlay, with device-admin uninstall protection as an optional deterrent. It also documents honest OEM/ADB/factory-reset limitations. citeturn637942search4

Recommended use: evaluate UsageStatsManager + overlay as an alternative enforcement provider and learn from its explicit limitations.

## License rule

Before copying source code:

1. verify repository license at the exact commit/tag;
2. inspect third-party dependencies separately;
3. preserve required notices;
4. avoid mixing GPL code into a codebase whose distribution strategy is incompatible;
5. record reused files and licenses in `THIRD_PARTY_NOTICES.md`.
