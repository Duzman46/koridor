package com.duzman46.gridbound.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.duzman46.gridbound.domain.models.AppSettings
import com.duzman46.gridbound.domain.models.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7BE0B5),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF14513F),
    secondary = Color(0xFFFFC56E),
    background = Color(0xFF0D1512),
    surface = Color(0xFF15201C),
    surfaceVariant = Color(0xFF26332E),
    onSurface = Color(0xFFE4F1EA),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C4E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8CF8CB),
    secondary = Color(0xFF7B5800),
    background = Color(0xFFF5FBF7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFDCE8E1),
    onSurface = Color(0xFF17201C),
    error = Color(0xFFBA1A1A),
)

@Composable
fun GridboundTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

