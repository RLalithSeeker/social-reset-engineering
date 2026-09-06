# Agent Execution Rules

These rules are for an autonomous coding agent.

## Priority order

1. Security and data correctness.
2. Core enforcement reliability.
3. Core Social Reset protocol.
4. Test coverage.
5. Usable UI.
6. AI enhancement.
7. Polish.

## If blocked by an API limitation

Do not invent a capability.

Use this sequence:

1. isolate the interface;
2. implement the strongest supported behavior;
3. implement a deterministic fallback;
4. record the limitation;
5. continue the rest of the project.

## Never do these

- Never collect call audio.
- Never scrape message bodies.
- Never upload full app-usage histories.
- Never put secrets in Git.
- Never use a hardcoded private key.
- Never treat a push-notification receipt as proof of a completed reset.
- Never trust a server response without cryptographic validation where the protocol requires it.
- Never unlock merely because a friend says "yes" in an unauthenticated request.
- Never make AI output directly execute privileged actions without deterministic policy validation.

## When the repository differs from this spec

Prefer the existing project's correct working implementation, but preserve the interfaces and security invariants described here. Document deviations.

## Commit/checkpoint discipline

After each major milestone:

- build;
- run tests;
- run lint/static checks;
- update the relevant decision/README file;
- leave the tree in a working state.

## Emulator-first execution rule

Assume the developer's normal workflow is Android Emulator-first. Do not block implementation waiting for physical hardware.

Claude Code should:
- run/build the Android app on an emulator whenever possible;
- use two emulator instances for the two-phone protocol;
- implement fake interfaces for hardware-dependent capabilities;
- make FriendAcceptedVerifier the default deterministic verification path;
- mark real-call verification as an experimental physical-device test;
- never replace a testable interface with hard-coded emulator-only behavior;
- document any feature that requires later physical-device validation.
