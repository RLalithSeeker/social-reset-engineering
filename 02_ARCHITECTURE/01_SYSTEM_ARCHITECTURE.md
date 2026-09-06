# System Architecture

## High-level model

```text
PHONE A

AccessibilityService
       |
       v
EnforcementEngine <---- RuleRepository ---- Room
       |
       v
PolicyDecision
       |
       +---- ALLOW
       |
       +---- FRICTION
       |
       +---- SOCIAL_RESET
                     |
                     v
              ResetSessionManager
                     |
          +----------+----------+
          |                     |
          v                     v
      CryptoService        NetworkClient
          |                     |
          v                     v
      Keystore             Relay Backend
                                |
                                v
                             PHONE B
```

## Android layers

### UI layer

Compose screens and ViewModels. UI never directly calls Android system APIs when a domain service can abstract them.

### Domain layer

Pure Kotlin logic:

- RuleEvaluator
- ScheduleMatcher
- BehaviorScorer
- InterventionPolicy
- ResetStateMachine
- UnlockPolicy

### Platform layer

- AccessibilityService
- overlay Activity/Window logic
- notifications
- package manager
- Keystore
- telecom adapter
- boot receiver

### Data layer

Room entities and DAOs.

### Network layer

HTTP for pairing/session bootstrap and WebSocket for active events. Push notification is an optional transport adapter rather than a source of truth.

## Dependency direction

```text
UI -> Domain <- Data
          ^
          |
       Platform
          |
       Network
```

Keep Android framework calls outside the pure domain package as much as possible.

## Local source of truth

The local device decides whether a target package is currently blocked.

The backend never sends a raw `ALLOW_APP` command.

The backend can relay:

- reset request;
- reset acceptance;
- session events;
- signed unlock grant;
- peer revocation event.

The receiving device still validates the message and applies its local policy.

## Failure philosophy

Fail closed for unlocking, fail open for optional social enhancements.

Example: if AI is unavailable, fall back to deterministic behavioral scoring. If the relay is unavailable, blocking still works.
