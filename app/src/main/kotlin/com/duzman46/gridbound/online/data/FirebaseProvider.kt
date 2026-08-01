package com.duzman46.gridbound.online.data

import android.content.Context
import com.duzman46.gridbound.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
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
        FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }
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
    }

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance(app) }

    val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance(app, BuildConfig.FIREBASE_DATABASE_URL).apply {
            setPersistenceEnabled(true)
        }
    }

    private companion object {
        const val APP_NAME = "gridbound-online"
    }
}

