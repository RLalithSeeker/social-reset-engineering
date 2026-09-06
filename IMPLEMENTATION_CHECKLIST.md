# Implementation Checklist

## Foundation
- [x] Clean build on fresh clone
- [x] App starts
- [x] Room DB initialized
- [x] Device identity generated
- [x] Keystore integration

## Enforcement
- [x] AccessibilityService wired
- [x] Foreground package events received
- [x] Block rules evaluated
- [x] Overlay shown
- [x] Unlock window evaluated
- [x] Boot recovery

## Social
- [ ] Pairing QR/code
- [x] Fingerprint confirmation
- [x] Peer persisted
- [x] Reset session state machine
- [x] Friend request relay
- [x] Friend acceptance

## Security
- [x] Canonical event serialization
- [x] Signature verification
- [x] Replay protection
- [x] Token expiry
- [x] Audience/package binding
- [x] Peer revocation

## AI
- [x] Rule-based behavior scorer
- [x] Smart intervention policy
- [x] Optional local-model adapter
- [x] AI fallback path

## Backend
- [x] Device register
- [x] Pairing sessions
- [x] Session create
- [x] Event relay
- [x] WebSocket
- [x] Rate limiting
- [x] Expiry/cleanup

## Testing
- [x] Unit suite
- [x] Integration suite
- [ ] Two-device test
- [ ] Reboot
- [ ] Network loss
- [ ] Permission denial
- [ ] Revoke peer
- [ ] Replay token

## Delivery
- [ ] Release APK
- [x] Demo script
- [x] Architecture diagram
- [x] Threat model
- [ ] Third-party notices
- [x] Known limitations
