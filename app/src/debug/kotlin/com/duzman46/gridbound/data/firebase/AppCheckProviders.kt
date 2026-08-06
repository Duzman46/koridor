package com.duzman46.gridbound.data.firebase

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug attestation.
 *
 * Prints a token to logcat on first run (tag `DebugAppCheckProvider`); paste it into
 * Firebase console → App Check → Apps → Manage debug tokens once per machine, or every
 * request from this build is rejected while enforcement is on.
 */
object AppCheckProviders {
    fun factory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
}
