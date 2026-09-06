# Security Test Cases

## Pairing

- valid pairing -> succeeds;
- expired pairing code -> fails;
- reused pairing code -> fails;
- modified QR payload -> fails;
- fingerprint mismatch -> pairing not finalized;
- peer key changed unexpectedly -> require re-pair.

## Event authentication

- valid signature -> accepted;
- changed payload -> rejected;
- changed sender id -> rejected;
- changed session id -> rejected;
- replayed sequence -> rejected;
- replayed nonce -> rejected;
- stale timestamp -> rejected;
- unknown peer -> rejected.

## Unlock grant

- valid grant -> temporary unlock;
- expired grant -> rejected;
- future-dated grant beyond skew -> rejected;
- wrong package -> rejected;
- wrong device -> rejected;
- wrong session -> rejected;
- revoked peer -> rejected;
- reused grant id -> rejected;
- malformed signature -> rejected.

## Local enforcement

- network disconnected -> existing block remains;
- backend unavailable -> existing block remains;
- AI unavailable -> deterministic policy continues;
- overlay unavailable -> show protection warning, never claim protected state;
- service restarted -> active rules restored.
