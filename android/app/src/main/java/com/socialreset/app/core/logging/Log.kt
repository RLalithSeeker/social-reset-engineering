package com.socialreset.app.core.logging

import android.util.Log as AndroidLog

/** Thin wrapper so tests can swap the sink and so the tag stays consistent. */
object Log {
    private const val TAG = "SocialReset"

    var sink: (level: Int, message: String, error: Throwable?) -> Unit = { level, msg, err ->
        AndroidLog.println(level, TAG, if (err == null) msg else "$msg :: ${err.javaClass.simpleName}")
    }

    fun d(message: String) = sink(AndroidLog.DEBUG, message, null)
    fun i(message: String) = sink(AndroidLog.INFO, message, null)
    fun w(message: String, error: Throwable? = null) = sink(AndroidLog.WARN, message, error)
    fun e(message: String, error: Throwable? = null) = sink(AndroidLog.ERROR, message, error)
}
