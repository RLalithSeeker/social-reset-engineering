# 30-Hour Hackathon Build Plan

## Goal

Build a convincing vertical slice, not a giant product.

## Before the event

Pre-build/test:

- base Android project;
- enforcement service;
- overlay;
- local database;
- schedule engine;
- crypto identity;
- pairing screen;
- backend skeleton;
- two physical test devices;
- sample blocked app configuration.

## During the 30 hours

### Hours 0–3

- clone/build cleanly;
- verify permissions;
- verify foreground-app detection;
- verify blocked overlay.

### Hours 3–7

- complete Room model;
- rules/schedule engine;
- temporary unlock engine;
- state persistence.

### Hours 7–11

- crypto identity;
- pairing QR/code;
- peer fingerprints;
- signed event envelope;
- verification tests.

### Hours 11–15

- backend endpoints;
- WebSocket relay;
- friend request UI;
- accept/reject.

### Hours 15–19

- complete Social Reset state machine;
- signed unlock grant;
- 10-minute unlock;
- automatic relock.

### Hours 19–22

- behavioral scoring;
- Smart Mode;
- optional on-device AI adapter;
- diagnostics.

### Hours 22–25

- failure tests;
- reboot;
- network loss;
- peer revocation;
- stale/replay tokens.

### Hours 25–27

- UI polish;
- empty/error/loading states;
- permission recovery;
- demo preparation.

### Hours 27–29

- full two-phone rehearsal;
- fresh-install test;
- backup demo flow if internet fails.

### Hours 29–30

- freeze features;
- build APK;
- verify demo devices;
- prepare architecture slide and security slide.

## Demo sequence

1. Show blocked Instagram.
2. Show repeated-open behavior detection.
3. Social Reset appears.
4. Friend receives request.
5. Friend accepts.
6. Show matching session/fingerprint indicator.
7. Complete verification.
8. Signed grant is accepted.
9. Instagram opens for 10 minutes.
10. Explain that the local device, not the server, enforces the block.
11. Explain that AI only chooses intervention level and cannot grant access.

## Demo failure fallback

If live call verification is unreliable, use FriendAcceptedVerifier. Present call verification as an experimental adapter, not a fake guaranteed feature.
