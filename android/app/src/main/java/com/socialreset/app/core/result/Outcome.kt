package com.socialreset.app.core.result

/**
 * Explicit success/failure with a machine-readable reason. Used everywhere a security
 * decision is made so that a rejection reason can be logged and surfaced without
 * throwing exceptions across layers.
 */
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Rejected(val reason: RejectionReason, val detail: String = "") : Outcome<Nothing>

    fun valueOrNull(): T? = (this as? Ok)?.value
    val isOk: Boolean get() = this is Ok
}

enum class RejectionReason {
    UNKNOWN_PEER,
    PEER_REVOKED,
    BAD_SIGNATURE,
    WRONG_TARGET,
    WRONG_SESSION,
    UNKNOWN_SESSION,
    PACKAGE_MISMATCH,
    POLICY_MISMATCH,
    STALE_TIMESTAMP,
    FUTURE_TIMESTAMP,
    EXPIRED,
    GRANT_TOO_LONG,
    REPLAYED,
    OUT_OF_ORDER,
    BAD_STATE,
    MALFORMED,
    UNSUPPORTED_VERSION,
    UNVERIFIED,
}
