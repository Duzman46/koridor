package com.duzman46.gridbound.data

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.domain.models.AppLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Reads the language preference before the UI exists.
 *
 * `attachBaseContext` runs before any coroutine scope is available and must return a
 * configured context synchronously, so this is the one place a blocking read of the
 * preference store is justified. Everything else observes the settings flow.
 *
 * A failure falls back to following the device language rather than propagating: the app
 * starting in the wrong language is a far smaller problem than the app not starting.
 *
 * Called only below Android 13. From API 33 the platform locale service already holds the
 * choice and `LocaleController.wrap` hands the context straight back, so a read here would
 * block the main thread of a cold start for an answer nobody looks at. The caller checks the
 * API level before asking; that check has to live at the call site, because it is the only
 * place that knows the answer will be used.
 */
object SettingsBootstrap {

    private val languageKey = stringPreferencesKey(Constants.Data.KEY_LANGUAGE)

    fun readLanguageBlocking(context: Context): AppLanguage = runCatching {
        runBlocking {
            val preferences = context.applicationContext.gridboundDataStore.data.first()
            AppLanguage.entries.firstOrNull { it.name == preferences[languageKey] }
                ?: AppLanguage.SYSTEM
        }
    }.getOrElse { error ->
        AppLog.warn("bootstrap-language", error)
        AppLanguage.SYSTEM
    }
}
