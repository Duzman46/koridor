package com.duzman46.gridbound.data.firebase

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Play Integrity attestation for shipped builds.
 *
 * The debug variant has its own copy of this file returning the debug provider, so the debug
 * App Check library is never on the release classpath and cannot be installed by accident.
 */
object AppCheckProviders {
    fun factory(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
}
