package com.duzman46.gridbound.ui.localization

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.LayoutDirection
import com.duzman46.gridbound.domain.models.AppLanguage
import java.util.Locale

/**
 * Makes the chosen language take effect immediately, on every API level, without restarting
 * anything.
 *
 * The platform's per-app language service only exists on Android 13+, and even there it is a
 * process-level switch that the app has to survive. Recreating the activity would work but
 * throws away the back stack and every screen's scroll position for what is a one-tap
 * preference — so instead the locale is layered on at the top of the composition: resources,
 * context and layout direction are all re-provided, and Compose recomposes every screen that
 * reads a string.
 *
 * [LocaleController] still hands the choice to the platform on Android 13+, so it shows up in
 * system settings and survives a cold start; this is what makes the *current* session follow.
 */
@Composable
fun ProvideAppLocale(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val localized = remember(context, language) { context.withLanguage(language) }
    val direction = remember(localized) {
        when (localized.resources.configuration.layoutDirection) {
            android.view.View.LAYOUT_DIRECTION_RTL -> LayoutDirection.Rtl
            else -> LayoutDirection.Ltr
        }
    }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalResources provides localized.resources,
        LocalLayoutDirection provides direction,
        content = content,
    )
}

/**
 * A context whose resources resolve in [language].
 *
 * Returns the receiver untouched when the player follows the device, so the common case adds
 * no wrapper and no second Resources instance.
 */
private fun Context.withLanguage(language: AppLanguage): Context {
    if (language.followsDevice) return this
    val locale = Locale.forLanguageTag(language.tag)
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    configuration.setLayoutDirection(locale)
    return LocalizedContext(this, createConfigurationContext(configuration))
}

/**
 * Localized resources over the original context.
 *
 * `createConfigurationContext` returns a standalone context, not a wrapper — handing that to
 * the composition would sever the chain `findActivity` walks, and Credential Manager and the
 * billing flow both need the Activity at the end of it. So the configured context is used for
 * resources only, and the real one stays underneath.
 */
private class LocalizedContext(
    base: Context,
    private val configured: Context,
) : ContextWrapper(base) {
    // Context.getString and friends are final but read through getResources(), so overriding
    // this one accessor is enough to redirect every resource lookup made on this context.
    override fun getResources(): Resources = configured.resources

    override fun getAssets(): AssetManager = configured.assets

    override fun getTheme(): Resources.Theme = configured.theme
}
