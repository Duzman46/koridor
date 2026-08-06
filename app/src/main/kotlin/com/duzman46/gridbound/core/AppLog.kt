package com.duzman46.gridbound.core

import android.util.Log
import com.duzman46.gridbound.BuildConfig

/**
 * Diagnostic logging that is compiled to a no-op message in release builds.
 *
 * Only a short operation tag and the exception type reach logcat in release; messages and
 * stack traces are debug-only so a token, email address or room code cannot leak into a
 * device log or a crash reporter breadcrumb.
 */
object AppLog {
    private const val TAG = "Koridor"

    fun warn(operation: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "$operation failed", error)
        } else if (error != null) {
            Log.w(TAG, "$operation failed: ${error.javaClass.simpleName}")
        } else {
            Log.w(TAG, "$operation failed")
        }
    }

    fun debug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
