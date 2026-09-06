# Threat Model

## Assets

1. User privacy.
2. Private device keys.
3. Local behavioral history.
4. Trusted peer relationship.
5. Unlock grants.
6. Block policy.

## Actors

### Curious server operator

Can see transport metadata and attempt message alteration.

Mitigation: minimize metadata; cryptographically authenticate security-relevant events.

### Malicious paired friend

May attempt forged grants or replay old ones.

Mitigation: peer key identity, signatures, expiry, session binding, revocation.

### User trying to bypass themselves

May disable service, revoke permissions, kill process, alter clock, or uninstall.

Mitigation: recovery detection, battery guidance, optional device-admin future mode, monotonic timers, local integrity checks. Do not promise perfect prevention.

### Attacker with device compromise

Out of v1 scope.

## Privacy model

Keep raw behavioral events local where possible. If server telemetry is implemented for debugging, send aggregate anonymized diagnostics only when the user explicitly enables a developer/debug mode.

Do not send:

- message contents;
- call audio;
- contact-book contents;
- full usage history;
- accessibility node trees;
- screenshots.

## Security invariants

1. No private key leaves device.
2. No unsigned remote event can grant access.
3. No remote request can override local block policy directly.
4. No expired token can unlock.
5. No revoked peer can issue a valid new grant.
6. Network failure cannot disable local blocking.
