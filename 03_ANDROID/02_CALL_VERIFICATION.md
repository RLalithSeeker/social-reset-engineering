# Call Verification Experiment

## Important limitation

Do not design the security protocol around an assumption that Android exposes a perfect `call answered + stayed connected for N seconds` callback to an ordinary app.

The official `CallScreeningService` API lets a user-selected app participate in screening incoming/outgoing calls and receive `Call.Details`. Incoming screening requires a timely response, and the service's documented purpose is screening/identification rather than generic post-call proof. citeturn723659search0

## MVP hierarchy

1. `FriendAcceptedVerifier` — guaranteed deterministic implementation for the hackathon.
2. `CallAttemptVerifier` — experimental.
3. Future: mutual app-side acknowledgment after call/voice session.

## CallAttemptVerifier behavior

When user chooses Call:

1. Create reset session.
2. Record target peer and session nonce.
3. Launch the system dialer through a normal user action.
4. If the app can obtain reliable telecom lifecycle evidence on the specific test device, record it.
5. Never treat `ACTION_DIAL` or call initiation alone as proof of connection.
6. If verification evidence is insufficient, return `UNVERIFIED` and require FriendAcceptedVerifier.

## Why this is deliberate

A false positive defeats the whole idea of proof-of-connection. A false negative is annoying but safe. Therefore the security posture is:

`uncertain -> no unlock`

## Future options

- Explicit peer-side `call completed` confirmation.
- In-app VoIP session where both app instances can cryptographically observe session establishment and teardown.
- BLE/NFC proximity only for optional physical-presence modes.
