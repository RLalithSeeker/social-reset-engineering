# Behavioral Engine

## Philosophy

The AI is not the blocker. It is an intervention-selection layer.

The core safety rule is:

> AI may recommend an intervention level, but deterministic policy decides whether an unlock is permitted.

## Event inputs

Local only:

- app foreground event;
- app background event;
- timestamp;
- duration;
- prior reopen history;
- prior reset history;
- local schedule context;
- whether a temporary unlock is active.

Do not ingest message contents or audio.

## Feature vector

```text
opens_10m
opens_30m
median_recent_session_seconds
current_session_seconds
reopens_under_60s
minutes_since_last_session
recent_unlock_count
same_app_reopens_after_unlock
scheduled_block_active
```

## Deterministic v1 score

Suggested defaults:

```text
score = 0
if opens_10m >= 2: score += 2
if opens_10m >= 3: score += 2
if opens_30m >= 5: score += 1
if reopens_under_60s >= 2: score += 1
if same_app_reopens_after_unlock: score += 2
if session_is_long_and_stable: score -= 1
```

Clamp to 0..10.

Policy:

```text
0..2 -> LIGHT
3..5 -> FRICTION
6..10 -> SOCIAL_RESET
```

These are heuristic defaults for a prototype and must be labeled as such.

## Intentional-use escape hatch

Provide a user interaction such as:

`I have a reason -> choose 5/10/20 minute intention`

This lets the system avoid treating purposeful work as a habit loop. The intention selection should be recorded locally and should temporarily reduce friction, not permanently disable the policy.

## Stateful behavior

Behavior scoring must use a bounded local history so it cannot grow without limit.

Keep enough events for the last 24 hours or another small configurable horizon.

## AI adapter

```kotlin
interface InterventionClassifier {
    suspend fun classify(features: BehaviorFeatures): ClassificationResult
}
```

Implement:

1. `RuleBasedClassifier` — mandatory.
2. `OnDeviceLlmClassifier` — optional.

The LLM output must be constrained to a structured enum:

```json
{"classification":"HABITUAL","confidence":0.81}
```

Then deterministic policy maps this into an intervention. Do not allow arbitrary generated text to execute app-unlock logic.

## On-device AI

Android's current ML Kit Prompt API can access on-device generative functionality on supported devices and requires API 26+; the current documentation notes inference quotas and input-size limits. Treat device/model availability as optional. citeturn723659search9

The implementation should therefore:

- detect capability;
- warm up optionally;
- use a tiny structured prompt;
- fall back instantly to RuleBasedClassifier;
- never block enforcement waiting on inference.
