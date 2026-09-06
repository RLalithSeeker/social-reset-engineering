package com.socialreset.app.network

import kotlinx.coroutines.delay

class RetryPolicy(
    private val maxAttempts: Int = 3,
    private val initialDelayMillis: Long = 250,
    private val maxDelayMillis: Long = 2_000,
) {
    suspend fun <T> run(block: suspend () -> T): T {
        var delayMillis = initialDelayMillis
        var lastError: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                if (attempt == maxAttempts - 1) throw e
                delay(delayMillis)
                delayMillis = (delayMillis * 2).coerceAtMost(maxDelayMillis)
            }
        }
        throw lastError ?: IllegalStateException("retry failed without an exception")
    }
}
