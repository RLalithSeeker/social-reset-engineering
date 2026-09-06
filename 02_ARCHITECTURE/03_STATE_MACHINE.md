# Social Reset State Machine

## States

```text
IDLE
  |
  v
REQUEST_CREATED
  |
  v
WAITING_FOR_PEER
  |
  +---- REJECTED ----> FAILED
  |
  v
PEER_ACCEPTED
  |
  v
CONNECTION_ACTIVE
  |
  +---- CANCELLED ---> CANCELLED
  |
  v
CONNECTION_VERIFIED
  |
  v
GRANT_ISSUED
  |
  v
UNLOCK_ACTIVE
  |
  v
EXPIRED
  |
  v
LOCKED
```

## Allowed transitions

- IDLE -> REQUEST_CREATED
- REQUEST_CREATED -> WAITING_FOR_PEER
- WAITING_FOR_PEER -> PEER_ACCEPTED
- WAITING_FOR_PEER -> FAILED
- PEER_ACCEPTED -> CONNECTION_ACTIVE
- CONNECTION_ACTIVE -> CONNECTION_VERIFIED
- CONNECTION_ACTIVE -> FAILED
- CONNECTION_ACTIVE -> CANCELLED
- CONNECTION_VERIFIED -> GRANT_ISSUED
- GRANT_ISSUED -> UNLOCK_ACTIVE
- GRANT_ISSUED -> FAILED
- UNLOCK_ACTIVE -> EXPIRED
- EXPIRED -> LOCKED
- FAILED -> IDLE
- CANCELLED -> IDLE

No code path may jump directly from `WAITING_FOR_PEER` to `UNLOCK_ACTIVE`.

## Idempotency

Every event has a session id and sequence number. Reprocessing the same event should produce the same state outcome and must not issue a second valid grant.
