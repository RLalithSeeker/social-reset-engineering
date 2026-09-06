package com.socialreset.app.core.time

/**
 * Split clock. Wall clock is used for anything that is signed or compared against a
 * peer's timestamps; monotonic time is used for local countdowns so that a user editing
 * the system clock cannot extend an unlock window.
 */
interface TimeSource {
    /** Unix seconds. Comparable across devices, user-editable. */
    fun wallClockSeconds(): Long

    /** Milliseconds since boot. Not user-editable, resets on reboot. */
    fun elapsedRealtimeMillis(): Long
}

class SystemTimeSource : TimeSource {
    override fun wallClockSeconds(): Long = System.currentTimeMillis() / 1000
    override fun elapsedRealtimeMillis(): Long = android.os.SystemClock.elapsedRealtime()
}

/** Test double. */
class FakeTimeSource(
    var wallSeconds: Long = 1_770_000_000L,
    var elapsedMillis: Long = 0L,
) : TimeSource {
    override fun wallClockSeconds(): Long = wallSeconds
    override fun elapsedRealtimeMillis(): Long = elapsedMillis

    fun advance(seconds: Long) {
        wallSeconds += seconds
        elapsedMillis += seconds * 1000
    }
}
