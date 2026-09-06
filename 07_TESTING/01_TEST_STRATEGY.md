# Test Strategy

## Test layers

### Unit

Pure Kotlin:

- ScheduleMatcher
- RuleEvaluator
- BehaviorScorer
- InterventionPolicy
- ResetStateMachine
- CanonicalSerializer
- TokenValidator
- ReplayWindow

### Integration

- Room repositories;
- HTTP API;
- WebSocket event relay;
- crypto provider adapter;
- app/service coordinator.

### Instrumentation/device

- Accessibility service;
- overlay;
- real installed apps;
- notification flow;
- reboot recovery;
- permission denial;
- Android version differences.

## Critical test scenario

```text
1. Configure Instagram as blocked.
2. Configure current time as inside block.
3. Open Instagram.
4. Intervention appears.
5. Request Social Reset.
6. Friend phone receives request.
7. Friend accepts.
8. Session event is signed.
9. Connection verification completes.
10. Unlock grant arrives.
11. Target validates signature.
12. Target app becomes accessible for 10 minutes.
13. Grant expires.
14. Instagram is blocked again.
```

## Negative scenarios

Every success path must have matching failure tests.

Examples:

- altered signature;
- wrong peer;
- replay;
- expired grant;
- revoked peer;
- network loss;
- service disabled;
- overlay disabled;
- device reboot;
- clock changed;
- duplicate request;
- friend rejects.

## Device matrix

At minimum, test one current Google/AOSP-style device and the available iQOO device used for the hackathon. If possible, add one more OEM skin to identify manufacturer-specific behavior.
