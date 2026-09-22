# Social Reset — Hackathon Idea Brief

## The problem

People often open Instagram, YouTube, or similar apps automatically—not because they made a deliberate choice, but because they are caught in a habit loop. Existing digital-wellbeing tools mainly block an app or show a timer. They reduce access, but do not offer a meaningful alternative in the moment the habit happens.

## Our solution

**Social Reset** is an Android app that turns a distracting-app block into a small moment of real human connection.

When a user repeatedly opens a chosen social-media app during their focus schedule, Social Reset intervenes locally. Instead of simply saying “blocked,” it offers a **Social Reset**: the user asks a trusted friend to accept a short reset session. Once the friend confirms, the user receives a short, temporary unlock (for example, 10 minutes). The app then automatically blocks the distracting app again.

Simple user flow:

`Open Instagram repeatedly → habit-loop intervention → request trusted friend → friend accepts → temporary unlock → automatic re-lock`

## Why this can stand out

- **Human alternative, not just punishment:** replaces mindless scrolling with connection.
- **Two-phone demo:** makes the experience easy for judges to understand and memorable to watch.
- **Privacy-first architecture:** app-blocking rules stay on the user’s phone; the backend only relays session events.
- **Trustworthy temporary access:** paired devices authenticate each other, and unlocks are short-lived and tied to a specific session.
- **AI is optional:** simple on-device behavior signals detect repeated app opens; AI never decides whether access is granted.

## What we would actually build for the hackathon

The minimum convincing prototype is deliberately narrow:

1. User selects one distracting app, such as Instagram.
2. Android detects that the app was opened during a focus period and shows the Social Reset screen.
3. User sends a request to a paired friend’s phone.
4. Friend taps **Accept**.
5. The requester gets a visible 10-minute unlock countdown.
6. When time ends, the app is blocked again.

For the demo, “friend accepted” is enough verification. A real phone-call-duration check is technically unreliable across Android devices, so we should present calls only as a future/experimental option—not as a hackathon dependency.

## What we should not claim

- It does not diagnose addiction.
- It will not work identically on every Android phone because Android/OEM restrictions differ.
- It is not impossible to bypass.
- AI does not control the unlock decision.

## Main risks

| Risk | Mitigation |
| --- | --- |
| Android app-blocking permissions can be inconsistent | Test early on two real phones; demonstrate one supported device setup. |
| Building secure pairing and live communication takes time | Keep the demo to one paired friend and short-lived unlocks. |
| Live call verification may fail | Use explicit friend acceptance as the reliable demo verifier. |
| The scope can grow too large | Build one complete end-to-end flow before smart scoring, AI, or extra apps. |

## Go / no-go decision

**We should do this if:**

- We have two Android phones available for testing and demo.
- We can spend the first few hours proving foreground-app detection and the intervention screen.
- We agree to ship the narrow flow above, not a full digital-wellbeing platform.
- The hackathon rewards a strong live demo, practical social impact, privacy, or technical depth.

**We should avoid it if:**

- We only have one phone or cannot test Android accessibility/overlay permissions.
- The event requires a polished cross-platform app in very little time.
- The team prefers a low-risk web-only build over a technically ambitious mobile demo.

## One-line pitch

**Social Reset helps people break automatic social-media scrolling by turning a block into a short, trusted human connection—then granting only a temporary, automatic re-locking unlock.**
