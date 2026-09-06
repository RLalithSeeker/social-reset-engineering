# TASK: Build the Social Reset relay backend (FastAPI)

You are a worker agent. Scope is STRICTLY the `backend/` directory of this repo.
Do NOT create, edit or delete anything outside `backend/`. Another agent owns the Android app.

## Read first (read-only, do not edit)
- `05_BACKEND/01_BACKEND_SPEC.md`
- `05_BACKEND/02_API_CONTRACT.md`
- `05_BACKEND/03_BACKEND_SECURITY.md`
- `04_SECURITY/01_CRYPTO_PROTOCOL.md` (sections "Signed event envelope", "Unlock grant") — for field names only.

## Build
Python 3.12+, FastAPI, Pydantic v2, SQLModel (or SQLAlchemy 2.x), SQLite, uvicorn, websockets.
Pin exact versions in `backend/requirements.txt`. Do not pull any package released in the last 7 days.

### Layout
```
backend/
  app/
    __init__.py
    main.py            # FastAPI app factory, router mounting, lifespan
    config.py          # pydantic-settings; ALL secrets from env, no defaults for secrets
    db.py              # engine/session, create_all on startup
    models.py          # SQLModel tables
    schemas.py         # Pydantic request/response models
    security.py        # device auth dependency, rate limiter
    routers/
      devices.py
      pairing.py
      sessions.py
      ws.py
    services/
      pairing_service.py
      relay_service.py
      cleanup.py       # TTL expiry sweep
  tests/
    test_devices.py
    test_pairing.py
    test_sessions.py
    test_relay_auth.py
    test_rate_limit.py
  requirements.txt
  README.md
  .env.example
```

### Endpoints (exact paths)
- `POST /v1/devices/register` — body: deviceId, protocolVersion, displayName, signingPublicKey. Returns a short-lived device access token (JWT or opaque, HS256 with `SR_SERVER_SECRET` from env). Idempotent on deviceId: re-register updates key ONLY if the deviceId is not already bound to a different key; otherwise 409.
- `POST /v1/pairing/sessions` — creates pairing session. Returns sessionId (uuid4), code (>=8 chars, `secrets.token_urlsafe`, NEVER derived from deviceId), expiresAt (default TTL 300s, configurable).
- `POST /v1/pairing/sessions/{id}/join` — second device joins with code + its deviceId + signingPublicKey + ephemeral pubkey. One-time: a second join attempt with the same code MUST fail 409. Expired code MUST fail 410.
- `POST /v1/pairing/sessions/{id}/complete` — marks complete, then the session row is closed/unusable.
- `POST /v1/sessions` — create reset session record: sessionId, requesterDeviceId, peerDeviceId, blockedPackage, resetType, expiresAt. Idempotent on `requestId`.
- `POST /v1/sessions/{id}/events` — relay a signed event. Validate STRUCTURE ONLY (required fields present, size <= 8 KiB, protocolVersion supported, sender is a participant of the session, recipient is a participant). Store minimal metadata, fan out over WS to recipient. NEVER validate/alter/produce a signature.
- `GET /v1/sessions/{id}` — coordination state; only participants may read.
- `WS /v1/ws/{deviceId}` — device event stream. MUST authenticate (token in query param or first message) BEFORE any subscription. Reject mismatch between token subject and path deviceId. Max 2 concurrent connections per device; disconnect idle sockets after 120s without ping.

### Hard rules (these are graded)
1. There is NO endpoint that authorizes app access. No `/unlock`. The server only relays a client-produced `UNLOCK_GRANT` event unchanged, byte-for-byte.
2. The server never generates, alters or re-serializes a signature or a signed payload. Store/forward the client's exact bytes for signed fields.
3. Every write route accepts `requestId` and is idempotent.
4. Per-device rate limits: pairing session creation 5/min, event relay 60/min, register 3/min. In-memory token bucket is fine; return 429.
5. Max request body 8 KiB. Reject larger with 413.
6. No secret in source. `.env.example` lists names only.
7. Structured logging (`logging` + JSON formatter). Redact deviceId to first 8 chars, never log key material, code, token or event payload bodies.
8. Cleanup sweep expires pairing sessions, reset sessions and relayed events past TTL.
9. CORS default-deny; allowed origins come from env.

### Tests (pytest + httpx AsyncClient, must all pass)
- register: new device 200; same deviceId different key 409.
- pairing: valid join 200; reused code 409; expired code 410; wrong code 403.
- sessions: idempotent create with same requestId returns the same sessionId.
- event relay: non-participant sender 403; oversized body 413; unknown session 404.
- ws: unauthenticated subscribe rejected; token/deviceId mismatch rejected.
- rate limit: 6th pairing create in a minute returns 429.

## Definition of done
```
cd backend
python -m venv .venv && .venv/Scripts/pip install -r requirements.txt
.venv/Scripts/python -m pytest -q       # ALL PASS
.venv/Scripts/python -m uvicorn app.main:app --port 8099   # starts clean
```
Then write `backend/README.md` with: run instructions, endpoint table, env var table, and a "Not implemented / deviations" section listing anything you could not do.

## Reporting
When finished, append to `backend/WORKER_REPORT.md`: what you built, test output (paste the pytest summary line), and every deviation from this brief. Be honest — a reported gap is fine, a false claim is not.
