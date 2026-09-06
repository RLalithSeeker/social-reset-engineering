# Backend Specification

## Purpose

Provide minimal coordination between paired devices. The backend is not a policy engine.

## Recommended stack

- Python 3.12+
- FastAPI
- Pydantic v2
- SQLite for demo/local development
- SQLAlchemy 2.x or SQLModel
- WebSocket for active sessions
- Structured logging

For production, PostgreSQL can replace SQLite without changing the client protocol.

## Core endpoints

### `POST /v1/devices/register`

Registers a device's public identity and push metadata.

Request:

```json
{
  "deviceId": "...",
  "protocolVersion": 1,
  "displayName": "Lalith",
  "signingPublicKey": "..."
}
```

The backend should not receive the private key.

### `POST /v1/pairing/sessions`

Creates a short-lived pairing session.

Response:

```json
{
  "sessionId": "...",
  "code": "...",
  "expiresAt": 1770000123
}
```

Code should be random and rate-limited. Never derive it from predictable device identifiers.

### `POST /v1/pairing/sessions/{id}/join`

Second device joins with code + its public identity.

Server verifies the one-time code and closes the session after successful completion.

### `POST /v1/pairing/sessions/{id}/complete`

Marks bootstrap pairing complete after client-side fingerprint confirmation.

### `POST /v1/sessions`

Create a Social Reset session record.

### `POST /v1/sessions/{id}/events`

Relay a signed event. Server stores only the minimal short-lived metadata necessary to route it.

### `GET /v1/sessions/{id}`

Return session coordination state.

### `WS /v1/ws/{deviceId}`

Active-device event stream.

## Server trust boundary

The server verifies that the message is structurally valid, but clients remain responsible for cryptographic authorization.

The server must never have an endpoint like:

`POST /unlock`

that directly authorizes app access.

Instead, it relays the signed unlock grant produced by the authorized party/session.

## Authentication

Use short-lived device access credentials for transport authentication. Do not use a permanent API key embedded in the APK.

For the hackathon, an enrollment token can bootstrap a demo account, but it must be configurable and stored outside source code.

## Data retention

Default TTL for session/event relay data: short. A development implementation may keep records temporarily for debugging, but production policy should minimize retention.

## Abuse controls

- pairing-session expiry;
- one-time codes;
- per-device rate limits;
- session id entropy;
- maximum event size;
- maximum active connections per device;
- disconnect idle sockets;
- reject malformed protocol versions.

## Offline behavior

Client should queue non-sensitive relay events locally only when useful. A queued request is not a completed Social Reset.
