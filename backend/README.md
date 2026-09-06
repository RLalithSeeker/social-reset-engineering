# Social Reset — relay backend

FastAPI + SQLModel + SQLite relay that coordinates pairing and reset sessions
between Android devices. The server is **not** a policy engine: it validates
event *structure only*, never produces/alters signatures, and forwards the
client's exact bytes to the recipient over WebSocket.

## Run

```bash
cd backend
python -m venv .venv
.venv/Scripts/pip install -r requirements.txt
cp .env.example .env        # then set SR_SERVER_SECRET
.venv/Scripts/python -m uvicorn app.main:app --port 8099
```

`SR_SERVER_SECRET` is required (no default) — the app refuses to start without it.

## Endpoints

| Method | Path                          | Auth      | Purpose                                                    |
|--------|-------------------------------|-----------|------------------------------------------------------------|
| POST   | `/v1/devices/register`        | none      | Register a device, get short-lived HS256 access token      |
| POST   | `/v1/pairing/sessions`        | Bearer    | Create one-time pairing session (returns code, 8+ chars)   |
| POST   | `/v1/pairing/sessions/{id}/join` | Bearer  | Second device joins with code                              |
| POST   | `/v1/pairing/sessions/{id}/complete` | Bearer | Mark pairing complete, closes session                    |
| POST   | `/v1/sessions`                | Bearer    | Create a reset session record (idempotent on `requestId`)  |
| POST   | `/v1/sessions/{id}/events`    | Bearer    | Relay a signed event; ONLY structure-validated             |
| GET    | `/v1/sessions/{id}`           | Bearer    | Coordination state — participants only                     |
| WS     | `/v1/ws/{deviceId}`           | `?token=` | Device event stream (auth before subscribe)                |

There is **no** `/unlock` endpoint. An unlock grant is just a `UNLOCK_GRANT`
event relayed byte-for-byte to the peer.

## Environment variables

| Variable                    | Default                       | Notes                       |
|-----------------------------|-------------------------------|-----------------------------|
| `SR_SERVER_SECRET`          | *(required)*                  | HS256 signing secret, no default |
| `SR_DATABASE_URL`           | `sqlite:///./socialreset.db` | SQLAlchemy URL              |
| `SR_JWT_TTL_SECONDS`        | `300`                         | Device token lifetime       |
| `SR_PAIRING_TTL_SECONDS`    | `300`                         | Pairing code lifetime       |
| `SR_RESET_SESSION_TTL_SECONDS` | `86400`                    | Reset session lifetime      |
| `SR_EVENT_TTL_SECONDS`      | `86400`                       | Relayed event retention     |
| `SR_CLEANUP_INTERVAL_SECONDS` | `60`                        | TTL sweep period (0=off)    |
| `SR_MAX_REQUEST_BYTES`      | `8192`                        | Reject bodies over this (413)|
| `SR_MAX_CONCURRENT_WS_PER_DEVICE` | `2`                     | Max sockets per device      |
| `SR_WS_IDLE_TIMEOUT_SECONDS`| `120`                         | Idle disconnect (no traffic)|
| `SR_CORS_ALLOWED_ORIGINS`   | *(empty → deny all)*          | Comma-separated origins     |
| `SR_LOG_LEVEL`              | `INFO`                        | JSON structured logging     |

## Security posture

- No secret in source; `.env.example` lists names only.
- Device tokens: JWT HS256, short-lived, subject = `deviceId`.
- WS auth **before** subscribe + subject/path mismatch rejected.
- Rate limits (per-device, in-memory token bucket): pairing create 5/min,
  event relay 60/min, register 3/min → `429`.
- Max request body 8 KiB → `413`.
- Structured JSON logs; deviceId redacted to 8 chars; no code/token/key
  material / payload bodies are logged.
- TTL sweep expires pairing sessions, reset sessions and relayed events.
- CORS default-deny; allowed origins come from `SR_CORS_ALLOWED_ORIGINS`.
- Idempotency: `requestId` on write routes; relay idempotent on `eventId`.

## Tests

```bash
.venv/Scripts/python -m pytest -q
```

## Not implemented / deviations

- WS auth supports token via query param **or** as a first JSON message.
- HTTP size cap (413) is enforced on `Content-Length`; chunked bodies without
  a length are caught in the relay route after read.
- Rate limiter is per-process in-memory (fine for a demo; swap for Redis to
  scale across workers).
- No offline replay queue: events are fanned out only to sockets currently
  connected. Offline clients queue locally (per spec).
- `protocolVersion` supported set is `{1}`.