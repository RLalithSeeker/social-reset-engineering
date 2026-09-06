# Backend Worker Report

**Task:** `TASKS/TASK_BACKEND.md` — FastAPI relay backend, strictly inside `backend/`.
**Result:** All 23 tests pass; app boots and routes correctly.

## Scope

Built exclusively under `backend/`. No files created or modified outside it.
References read but never written: `05_BACKEND/{01_BACKEND_SPEC,02_API_CONTRACT,03_BACKEND_SECURITY}.md`, `04_SECURITY/01_CRYPTO_PROTOCOL.md` (signed-event-envelope + unlock-grant sections only).

## Layout

```
backend/
  requirements.txt        pinned, exact versions
  pytest.ini              asyncio_mode=auto, function-scoped async fixtures
  .env.example            all SR_* vars documented (no real secrets)
  README.md               run/install instructions
  app/
    main.py               app factory, lifespan (create_all + cleanup loop), CORS, MaxBodySizeMiddleware
    config.py             pydantic-settings, env_prefix="SR_", secrets without defaults
    db.py                 lazy engine + engine factory for tests
    models.py             Device, PairingSession, ResetSession, RelayedEvent
    schemas.py            Pydantic v2 request/response models
    security.py           JWT HS256 device tokens, require_device, RateLimiter
    logging.py            JSON logging + redaction
    routers/              devices.py, pairing.py, sessions.py, ws.py
    services/             pairing_service.py, relay_service.py, cleanup.py
  tests/                  23 tests in 6 files
```

## Endpoints

- `POST /v1/devices/register` — create or idempotently re-issue a device token (same device + same signing key → 200; different key → 409; >3/min → 429)
- `POST /v1/pairing/sessions` — open a pairing session; code returned plaintext only on first creation (idempotent same-`requestId` replay hides it again)
- `POST /v1/pairing/sessions/{id}/join` — mark joined (open → joined), one-time
- `POST /v1/pairing/sessions/{id}/complete` — create the paired reset session
- `POST /v1/sessions` — create a reset session
- `POST /v1/sessions/{id}/events` — relay a signed event
- `GET /v1/sessions/{id}` — fetch a session with its relayed events
- `WS /v1/ws/{deviceId}` — authenticated event stream

## Security-relevant decisions

- **No unlock grant synthesis.** There is no `/unlock` endpoint; unlock-grant material only travels as a relayed `UNLOCK_GRANT` event between participants.
- **Byte-exact relay.** `RelayedEvent.raw_payload` stores the raw HTTP body as bytes; recipients receive those exact bytes over the socket (`send_bytes`). The server never re-serializes, parses signatures, or inspects crypto/private-key material.
- **Structure-only validation.** Envelope fields are validated for structure; signature bytes are opaque and forwarded untouched.
- **Auth before subscription.** WS either carries `?token=` or a first-JSON-message token; the connection is accepted, but no subscription/fan-out happens until the token is verified. Subject must equal the URL `deviceId`. Failures close with 1008.
- **Authorization per relay.** `relay_event` only delivers between the two participants of the owning reset session; a relayed event's `senderDeviceId`/`recipientDeviceId` are checked but their signature is not — per spec, signature verification is the Android client's job.
- **Rate limiting.** Per-device in-process sliding window (register 3/min, pairing create 5/min, event relay 60/min). Documented as demo-grade; README notes the Redis swap.
- **TTL expiry.** Pairing sessions (10 min), reset sessions (24 h), relayed events (24 h behind their session) are swept by a background task disabled in tests.
- **Secrets via env only.** `SR_SERVER_SECRET` has no default; boot hard-fails without it. Pairing codes stored hashed (SHA-256), compared with `hmac.compare_digest`.
- **Logging.** Structured JSON; `deviceId` redacted to first 8 chars; codes/tokens/signatures/key material never logged.
- **Body cap.** 8192-byte max body enforced via Content-Length middleware (413) plus a route-level post-read check for chunked bodies.

## Test failures found and fixed

1. **Aware-vs-naive datetime** — `_now()` returned aware UTC but SQLite round-trips naive, so expiry compares blew up with TypeError after a DB reload. Fixed: naive UTC everywhere (`datetime.now(timezone.utc).replace(tzinfo=None)`).
2. **WS unauthenticated test** — the handler read the first frame before calling `accept()`, which makes Starlette raise `RuntimeError`. Fixed: `accept()` first, then authenticate, then close on failure; the subscription still happens only after auth.
3. **Register rate-limit test bug** — each call used a fresh signing key, so the second call correctly returned 409 (same device, different key) before the limiter could trip. Fixed the test to reuse one key so the 4th call is the 429.
4. **WS fan-out test race** — 1s idle timeout expired before the relay POST landed on a cold run. Bumped test idle timeout to 2s.

## Verification

- `pytest -q` → **23 passed** (`pytest.ini` now also sets `asyncio_default_fixture_loop_scope=function`, clearing the pytest-asyncio warning).
- Boot smoke test: `SR_SERVER_SECRET=... uvicorn app.main:app --port 8099` starts clean; `GET /v1/sessions` correctly returns 405 (only POST routed).
- Generated sqlite files removed after the run (`socialreset.db`, `test_socialreset.db`).

## Run

```
cd backend
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
$env:SR_SERVER_SECRET="<production secret>"
.\.venv\Scripts\python -m uvicorn app.main:app --port 8000
```