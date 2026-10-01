package com.resq.app.core.util

import android.util.Log
import com.resq.app.BuildConfig

/**
 * Thin wrapper around [Log] that compiles out in release builds.
 *
 * SOS payloads carry a person's live location — never let that reach
 * logcat on a release build, where it could be pulled off a lost or
 * recovered device.
 */
object Logger {

    fun d(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(tag, message())
    }

    fun w(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (BuildConfig.DEBUG) Log.w(tag, message(), throwable)
    }

    fun e(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (BuildConfig.DEBUG) Log.e(tag, message(), throwable)
    }
}
