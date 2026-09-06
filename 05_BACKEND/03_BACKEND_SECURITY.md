# Backend Security Checklist

- TLS in production.
- No secrets committed to Git.
- Short-lived pairing sessions.
- Strong random identifiers.
- Request size limits.
- Schema validation.
- Rate limiting.
- Origin/CORS restrictions appropriate to deployment.
- Structured logs with sensitive fields redacted.
- No plaintext private keys.
- No behavioral-history analytics by default.
- Expire old sessions/events.
- WebSocket authentication before subscription.
- Recipient authorization on every event relay.
- Server must not synthesize unlock grants.
