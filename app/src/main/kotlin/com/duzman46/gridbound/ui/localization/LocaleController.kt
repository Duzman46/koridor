package com.duzman46.gridbound.ui.localization

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.domain.models.AppLanguage
import java.util.Locale

/**
 * Hands the player's language choice to the platform.
 *
 * This is the *persistence* half of the language feature, not the visible half. On Android 13
 * and above the choice goes to the per-app language service, so it survives restarts, shows up
 * in system settings alongside every other app, and is what a screen reader or the launcher
 * sees. Below that there is no such service, so [wrap] layers the locale onto the base context
 * of the activity at startup instead.
 *
 * What makes the *current* session follow the choice — every open screen, immediately, without
 * recreating anything — is [ProvideAppLocale], which sits at the top of the composition. The
 * two work together: this one is remembered, that one is seen.
 *
 * Deliberately free of androidx.appcompat: pulling in that library for this alone would cost
 * more download size than the whole feature is worth.
 */
object LocaleController {

    fun apply(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        runCatching {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            manager.applicationLocales = if (language.followsDevice) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(language.tag)
            }
        }.onFailure { AppLog.warn("apply-locale", it) }
    }

    /** What the platform currently reports, so the settings screen shows the real state. */
    fun current(context: Context): AppLanguage {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return AppLanguage.SYSTEM
        val locales = runCatching {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales
        }.getOrNull()
        val tag = locales?.takeUnless(LocaleList::isEmpty)?.get(0)?.toLanguageTag()
        return AppLanguage.fromTag(tag)
    }

    /**
     * Wraps a context in the chosen locale. Used on API levels without a locale service;
     * a no-op when the player follows the device.
     */
    fun wrap(context: Context, language: AppLanguage): Context {
        if (language.followsDevice || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context
        }
        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }
}
