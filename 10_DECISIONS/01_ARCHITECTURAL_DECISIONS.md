# Architectural Decisions

## ADR-001 — Android native Kotlin

Decision: Kotlin + Compose.

Reason: critical APIs are Android-native and the hackathon needs reliable system integration more than cross-platform UI reuse.

## ADR-002 — Local blocking is authoritative

Decision: phone enforces local policy.

Reason: backend failure must not disable the user's configured protection.

## ADR-003 — AccessibilityService in prototype

Decision: use AccessibilityService behind an interface for the hackathon prototype.

Reason: direct foreground UI transition events are useful for timely intervention.

Risk: Android documentation says AccessibilityService is intended to assist users with disabilities; distribution/policy suitability must be evaluated separately. citeturn723659search4

## ADR-004 — Social Reset verification is pluggable

Decision: friend acceptance is deterministic MVP; call verification is experimental.

Reason: Android CallScreeningService is not a guaranteed generic answered-duration proof API. citeturn723659search0

## ADR-005 — AI is optional

Decision: rule-based behavior engine is mandatory; on-device AI is optional.

Reason: enforcement must never depend on model availability, inference latency or quotas. Current Android Prompt API documentation notes supported-device requirements and quotas. citeturn723659search9

## ADR-006 — Backend is a relay

Decision: no backend endpoint directly authorizes app access.

Reason: reduces trust in server and limits attack impact.

## ADR-007 — No call audio

Decision: never record or analyze call audio.

Reason: unnecessary for the product and materially increases privacy/security risk.
