# API Contract

All APIs use JSON unless otherwise stated.

## Envelope metadata

```json
{
  "protocolVersion": 1,
  "requestId": "uuid",
  "clientTimestamp": 1770000000
}
```

## Pairing QR URI

```text
socialreset://pair?v=1&s=<session-id>&c=<one-time-code>&exp=<unix>
```

Validation:

- scheme must equal `socialreset`;
- path must equal `/pair`;
- version supported;
- session id valid;
- code format valid;
- expiry not exceeded.

## Relay event

```json
{
  "eventId": "uuid",
  "sessionId": "uuid",
  "senderDeviceId": "uuid",
  "recipientDeviceId": "uuid",
  "sequence": 5,
  "type": "RESET_ACCEPTED",
  "timestamp": 1770000000,
  "nonce": "...",
  "payload": {},
  "signature": "..."
}
```

## Event types

```text
PAIRING_OFFER
PAIRING_RESPONSE
RESET_REQUESTED
RESET_ACCEPTED
RESET_REJECTED
CONNECTION_STARTED
CONNECTION_VERIFIED
UNLOCK_GRANT
RESET_CANCELLED
PEER_REVOKED
SESSION_EXPIRED
```

The implementation may combine pairing transport into the pairing endpoint rather than exposing those as normal social-session events.

## Idempotency

Every write operation accepts/uses `requestId` or an event id. Repeating the same request should not create multiple sessions or grants.
