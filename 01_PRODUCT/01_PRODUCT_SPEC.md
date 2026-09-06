# Product Specification

## Product name

Social Reset (working name)

## Problem

Traditional app blockers treat all access to a distracting app as the same event. Social-media use can be intentional, but repeated reopening can also be a habitual loop triggered by boredom, anxiety, or a desire for stimulation.

The product should intervene on the behavior rather than merely punish the app choice.

## Core thesis

> Don't block social media. Replace the fake social with real social.

## Core experience

A user selects distracting apps and creates a schedule. During blocked periods, the system watches local app-opening behavior. When the behavior suggests a habit loop, the user is offered a Social Reset with a trusted person.

A Social Reset is deliberately small: call, voice interaction, or explicit friend confirmation in the MVP. Successful completion can grant a short access window. The objective is substitution, not prohibition.

## Modes

### Light Mode

The user sees friction and a social suggestion. No friend session is required.

### Smart Mode

The behavioral engine chooses the intervention level based on local context.

### Hard Mode

A Social Reset is required during an active block. Inability to contact the trusted person keeps the app blocked.

## Important product invariants

- The friend is not a permanent administrator.
- The friend is a connection partner.
- The user retains ownership of their device and rules.
- The system never needs to inspect message content or call audio.
- Blocking works locally.
- Social connection is the escape route.

## Social Reset types

1. `CALL` — attempt/complete a phone call; verification capability is device/OS dependent.
2. `VOICE` — exchange a short in-app voice interaction only if later implemented; not required for MVP.
3. `FRIEND_CONFIRM` — trusted friend explicitly confirms the reset; deterministic MVP fallback.

## v1 acceptance criteria

A new user can:

1. choose at least one installed app;
2. create a schedule;
3. pair a second phone;
4. activate blocking;
5. trigger a Social Reset;
6. have the second phone receive the request;
7. complete an accepted reset;
8. receive a signed unlock grant;
9. use the target app temporarily;
10. automatically become blocked again;
11. view a local history of reset sessions.
