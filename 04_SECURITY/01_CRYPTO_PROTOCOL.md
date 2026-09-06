# Cryptographic Protocol

## Goal

A paired friend must be a cryptographically identifiable peer, not merely a string in a database. Private keys never leave the device.

## Identity

Each install creates a device identity:

```text
DeviceId = random 128-bit identifier
Identity signing key = generated on-device and protected by Android Keystore
Public identity key = shareable
```

The exact key algorithm should be selected based on Android Keystore/provider support on the target SDK/device. Do not hardcode an algorithm that is unavailable on tested hardware. Use a mature library for X25519/Ed25519 if native Keystore does not provide the exact primitive required by the implementation.

## Pairing

### Step 1 — bootstrap

Device A requests a short-lived pairing session from the relay.

Relay returns:

- pairingSessionId
- expiry
- one-time pairing secret/code

### Step 2 — QR

QR encodes a compact URI:

```text
socialreset://pair?v=1&s=<session>&c=<one-time-code>&exp=<unix>
```

Do not put private keys or long-lived bearer credentials in the QR.

### Step 3 — key exchange

A sends:

- deviceId
- public signing key
- ephemeral ECDH public key
- display name
- protocol version

B returns the same category of data.

### Step 4 — derive secret

```text
shared_secret = ECDH(ephemeral_private_A, ephemeral_public_B)
context = "social-reset-pair-v1" || ordered(deviceIds) || pairingSessionId
session_key = HKDF-SHA256(shared_secret, salt=pairing secret, info=context)
```

The exact library call should use vetted APIs; this pseudocode is a protocol description, not code.

### Step 5 — fingerprint confirmation

Display a short human-verifiable fingerprint derived from the peer's public key/session transcript.

Example:

`BLUE-ORBIT-71-MAPLE`

The friend compares the same code on both screens before finalizing pairing.

### Step 6 — persistence

Persist:

- peer deviceId
- peer public key
- fingerprint
- createdAt
- status
- protocol version

Do not persist ephemeral private key material outside Keystore-backed protected storage.

## Signed event envelope

```json
{
  "v": 1,
  "type": "RESET_ACCEPTED",
  "sessionId": "uuid",
  "senderDeviceId": "device-id",
  "seq": 4,
  "timestamp": 1770000000,
  "nonce": "base64url-random",
  "payloadHash": "sha256-base64url",
  "signature": "base64url"
}
```

Canonicalize the signed fields deterministically before signature generation. Do not sign a free-form JSON serialization whose key ordering may vary.

## Session sequence

Each session begins at sequence `0`.

Receiver maintains the highest accepted sequence number and an event-id/nonce cache sufficient to reject duplicates within the session lifetime.

Reject:

- sequence <= previously accepted when strict ordering is expected;
- duplicate nonce;
- wrong sender;
- wrong session;
- stale timestamp;
- invalid signature.

Permit limited out-of-order transport only if the state machine explicitly allows it; do not introduce unnecessary complexity in v1.

## Unlock grant

The signer creates a canonical grant payload:

```json
{
  "v": 1,
  "grantId": "uuid",
  "sessionId": "uuid",
  "targetDeviceId": "device-a",
  "policyId": "rule-123",
  "packageName": "com.example.social",
  "issuedAt": 1770000000,
  "expiresAt": 1770000600,
  "resetType": "FRIEND_ACCEPTED"
}
```

Sign the canonical payload.

The target device verifies:

1. signer is the currently trusted peer;
2. targetDeviceId equals local device id;
3. sessionId exists locally;
4. package/rule matches the active block;
5. issuedAt is within clock-skew tolerance;
6. expiresAt is in the future and no longer than configured maximum;
7. grantId has not been consumed;
8. signature is valid;
9. session is in `CONNECTION_VERIFIED`/equivalent accepted state.

Only then enter `UNLOCK_ACTIVE`.

## Clock rules

Use a configurable small clock skew window for network validation, e.g. ±60 seconds, while local countdown uses monotonic time.

Do not accept arbitrarily future-dated tokens.

## Revocation

The user can revoke a peer locally. Revocation must invalidate:

- new session acceptance;
- new grants from that peer.

Existing temporary unlock windows may either expire normally or be immediately terminated according to local policy. For v1, immediate termination is optional; new grants must always be rejected.

## Key rotation

Not required for v1. Design metadata so a future protocol can add key versioning.

## Threat model summary

### Protect against

- stolen pairing QR after expiry;
- replayed unlock events;
- forged friend messages;
- server modifying unsigned events;
- server granting arbitrary package access;
- stale token reuse;
- revoked friend continuing to grant access.

### Not intended to protect against

- rooted device;
- user with full ADB control;
- custom ROM/system compromise;
- physical extraction from a compromised device;
- malicious OS/vendor firmware.

The app is a consumer self-control tool, not a high-assurance security product.
