# User Journeys

## Journey 1 — first setup

`Launch -> explain -> grant permissions -> select apps -> schedule -> pair -> test`

The permission wizard must explain what each permission does and must show whether it is currently working.

## Journey 2 — normal intentional use

If the app is not in an active block, allow it normally.

If a block is active but the behavior score is low, show light friction rather than immediately escalating.

## Journey 3 — habitual loop

Example:

`Instagram opened -> closed -> Instagram reopened -> reopened again within 10 minutes`

Behavior score rises. Once the score crosses the configured threshold, show Social Reset.

## Journey 4 — trusted-person session

`Request -> friend notification -> accept -> connection -> completion -> cryptographic grant -> 10 min -> relock`

## Journey 5 — friend unavailable

In Light/Smart Mode: allow the user to dismiss the social suggestion according to policy.

In Hard Mode: stay blocked and show a non-shaming explanation.

## Journey 6 — network lost

The local blocker continues working. Social reset remains pending/unavailable. The UI must not claim that a reset succeeded.

## Journey 7 — peer revoked

Any token generated for the revoked peer becomes invalid. The target device denies new grants from that peer immediately.
