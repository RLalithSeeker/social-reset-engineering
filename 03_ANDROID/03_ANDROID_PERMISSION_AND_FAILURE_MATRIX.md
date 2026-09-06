# Android Permission and Failure Matrix

## Principle

Every permission is a capability, not an entitlement. The app must explain why it needs the capability and what happens when it is denied.

## Accessibility disabled

Status: `ENFORCEMENT_OFF`

UI:

> Protection is paused because Android's monitoring service is off.

Actions:

- Open Accessibility Settings.
- Recheck state on resume.

## Overlay disabled

Status: `INTERVENTION_UI_OFF`

Do not claim full blocking. Allow the user to fix the permission.

## Notifications denied

Local blocking remains valid.

Friend notification pathway becomes `PUSH_UNAVAILABLE`.

Show an in-app pending state.

## Network unavailable

Local enforcement is unchanged.

Social Reset requests can be queued locally. They do not count as completed until a valid peer event is received.

## Call-screening role unavailable

Disable call verification mode and fall back to FriendAcceptedVerifier.

## Exact alarms unavailable

Rules are still evaluated at every relevant foreground event. Exact boundary alarms are treated as an optimization, not a security boundary.
