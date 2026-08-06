package com.duzman46.gridbound.core

import android.util.Log
import com.duzman46.gridbound.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Diagnostic logging that is compiled to a no-op message in release builds.
 *
 * Only a short operation tag and the exception type reach logcat in release; messages and
 * stack traces are debug-only so a token, email address or room code cannot leak into a
 * device log.
 *
 * In release the failure is also reported to Crashlytics as a non-fatal, because a handled
 * error that only ever reached the device's own logcat told nobody anything. The operation
 * tag is a fixed string chosen at the call site — never player content — so nothing
 * identifying travels with it.
 */
object AppLog {
    private const val TAG = "Koridor"

    fun warn(operation: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "$operation failed", error)
        } else {
            if (error != null) {
                Log.w(TAG, "$operation failed: ${error.javaClass.simpleName}")
            } else {
                Log.w(TAG, "$operation failed")
            }
            report(operation, error)
        }
    }

    fun debug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    /**
     * Crashlytics may not be initialised — the default FirebaseApp needs google-services.json,
     * and a build without one must still run. A reporting failure can never be allowed to
     * become the actual crash.
     */
    private fun report(operation: String, error: Throwable?) {
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("operation", operation)
            crashlytics.recordException(error ?: IllegalStateException(operation))
        }
    }
}
