package com.socialreset.app.security

/**
 * Every identifier that reaches a log goes through here. Key material, pairing codes,
 * tokens and signatures must never be logged at all, not even redacted.
 */
object Redaction {
    fun deviceId(id: String?): String = when {
        id.isNullOrEmpty() -> "<none>"
        id.length <= 8 -> id
        else -> id.take(8) + "…"
    }

    fun packageName(pkg: String?): String = pkg ?: "<none>"

    /** For anything secret. Returns only a length hint. */
    fun secret(value: String?): String = if (value.isNullOrEmpty()) "<none>" else "<redacted:${value.length}>"
}
