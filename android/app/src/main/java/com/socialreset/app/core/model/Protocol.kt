package com.socialreset.app.core.model

/** Wire protocol constants shared with the relay backend. */
object Protocol {
    const val VERSION = 1

    /** Maximum lifetime any unlock grant may claim, regardless of what the peer signed. */
    const val MAX_GRANT_SECONDS = 30 * 60L

    /** Accepted wall-clock skew when validating remote timestamps. */
    const val CLOCK_SKEW_SECONDS = 60L

    /** Relay hard limit; envelopes larger than this are rejected before they are sent. */
    const val MAX_EVENT_BYTES = 8 * 1024
}

/** Event types carried in a signed envelope. Names are part of the wire contract. */
enum class EventType {
    RESET_REQUESTED,
    RESET_ACCEPTED,
    RESET_REJECTED,
    RESET_CANCELLED,
    CONNECTION_VERIFIED,
    UNLOCK_GRANT,
    PEER_REVOKED,
}

/** How a reset session proved that a real human connection happened. */
enum class ResetType {
    /** Deterministic path: the peer explicitly accepted in-app. Always available. */
    FRIEND_ACCEPTED,

    /** Experimental telecom path. Disabled by default; may return UNVERIFIED. */
    CALL_ATTEMPT,
}

/** Escalation level chosen by the intervention policy, never by the AI directly. */
enum class InterventionLevel {
    /** Reminder only; the app opens. */
    LIGHT,

    /** Delay + confirmation before the app opens. */
    FRICTION,

    /** Hard block until a trusted peer completes a reset session. */
    SOCIAL_RESET,
}
