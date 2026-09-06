# Social Reset — Claude Code Engineering Pack

This is the complete implementation handoff for the Social Reset project.

## Start here

Read `00_START_HERE/01_MASTER_IMPLEMENTATION_PROMPT.md` first.

Then read:

`02_ARCHITECTURE -> 03_ANDROID -> 04_SECURITY -> 05_BACKEND -> 06_AI -> 07_TESTING -> 08_HACKATHON -> 09_OPEN_SOURCE -> 10_DECISIONS`

## Expected outcome

A working two-phone Android prototype in which:

`blocked app -> local intervention -> Social Reset -> trusted peer -> authenticated session -> short-lived unlock -> automatic relock`

## Core engineering principles

- Local-first blocking.
- Cryptographic trust between paired devices.
- Backend as relay, not authority.
- AI optional and subordinate to deterministic policy.
- Security failures fail closed for unlocking.
- No call audio.
- No unnecessary user-content collection.
- Explicit handling of Android permission and OEM limitations.

## Current verified Android constraints

Android's current documentation states that AccessibilityService receives UI transition events but is intended for accessibility use cases; foreground activity launches and foreground-service behavior are also restricted on modern Android. CallScreeningService is specifically a call screening/identification API, not a guaranteed generic answered-duration proof mechanism. Current on-device Prompt API documentation also notes device/API requirements and inference quotas. These constraints are incorporated into the architecture rather than hidden. citeturn723659search4turn723659search1turn723659search0turn723659search9
