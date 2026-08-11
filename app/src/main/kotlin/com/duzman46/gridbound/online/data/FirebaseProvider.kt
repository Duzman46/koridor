package com.duzman46.gridbound.online.data

import android.content.Context
import com.duzman46.gridbound.BuildConfig
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.data.firebase.AppCheckProviders
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val isConfigured: Boolean = listOf(
        BuildConfig.FIREBASE_API_KEY,
        BuildConfig.FIREBASE_APP_ID,
        BuildConfig.FIREBASE_PROJECT_ID,
        BuildConfig.FIREBASE_DATABASE_URL,
    ).all(String::isNotBlank)

    private val app: FirebaseApp by lazy {
        check(isConfigured) { "Çevrimiçi servis henüz yapılandırılmadı." }
        val instance = FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setDatabaseUrl(BuildConfig.FIREBASE_DATABASE_URL)
                    .build(),
                APP_NAME,
            )
        // Installed here rather than beside a particular service, so auth and the database
        // are both attested and neither can be reached before the provider is in place.
        installAppCheck(instance)
        instance
    }

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance(app) }

    /**
     * The database, with a disk cache that has a ceiling.
     *
     * Persistence is on because a match must survive a tunnel: without it, a dropped connection
     * means the board is re-fetched from nothing and a player who came back to signal found an
     * empty screen instead of their game.
     *
     * What it did not have was a limit the app chose. The library's own default is ten
     * megabytes, which is a great deal of room for a game whose entire state is a nine-by-nine
     * board and a list of walls — and every room ever browsed, every match ever played and
     * every profile ever read goes into it and stays until the cache decides to evict. That is
     * the storage a player watches grow after the download is long finished.
     *
     * Two megabytes holds a match, the rooms on screen and the profiles behind them several
     * times over. Past that, eviction is doing the right thing.
     */
    val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance(app, BuildConfig.FIREBASE_DATABASE_URL).apply {
            setPersistenceEnabled(true)
            setPersistenceCacheSizeBytes(Constants.Online.PERSISTENCE_CACHE_BYTES)
        }
    }

    /**
     * Attests that requests come from a genuine install of this app.
     *
     * Off until `APP_CHECK_ENABLED` is turned on in firebase.properties, because enabling it
     * in the app before the Firebase console has Play Integrity registered and Realtime
     * Database enforcement switched on would reject every request from every device. The
     * order is: ship a build with this on, watch the console's unverified-request count fall
     * to zero, then turn enforcement on. See docs/FIREBASE_SETUP.md.
     *
     * A failure here is logged and swallowed: an install that cannot attest should fall back
     * to being refused by the backend, not crash on launch.
     */
    private fun installAppCheck(instance: FirebaseApp) {
        if (!BuildConfig.APP_CHECK_ENABLED) return
        runCatching {
            FirebaseAppCheck.getInstance(instance)
                .installAppCheckProviderFactory(AppCheckProviders.factory())
        }.onFailure { AppLog.warn("app-check-install", it) }
    }

    private companion object {
        const val APP_NAME = "gridbound-online"
    }
}

