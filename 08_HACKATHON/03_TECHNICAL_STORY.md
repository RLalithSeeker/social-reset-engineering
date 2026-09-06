# Technical Story

## Strongest engineering points

1. Local-first enforcement.
2. Human-in-the-loop intervention.
3. Cryptographically authenticated paired devices.
4. Replay-resistant sessions.
5. Short-lived unlock grants.
6. Adaptive intervention from local behavior.
7. AI separated from authorization.
8. Graceful degradation without network.

## What makes the implementation more defensible

The idea itself cannot be made impossible to copy. Defensibility comes from the implementation stack:

`behavior model + intervention engine + social protocol + cryptographic trust + reliable Android enforcement + privacy architecture + polished two-phone UX`

## Claims to avoid

Do not say:

- "unbreakable";
- "AI knows when you're addicted";
- "Android guarantees call duration events";
- "server cannot ever see metadata" unless the exact deployment proves it;
- "works on every Android phone".

Use:

- "habit-loop heuristic";
- "on-device behavioral signals";
- "cryptographically authenticated session";
- "short-lived unlock grant";
- "prototype tested on these devices".
