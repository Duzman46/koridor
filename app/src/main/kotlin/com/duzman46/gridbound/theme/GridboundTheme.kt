package com.duzman46.gridbound.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.duzman46.gridbound.domain.models.AppSettings
import com.duzman46.gridbound.domain.models.ThemeMode

/**
 * Pine and paper.
 *
 * Warm rather than clinical: the light background is paper, not near-white, and the greens
 * are the deep pine of the launcher icon rather than a mint accent. Every value below is
 * checked against its foreground — primary on light background is 6.9:1, onPrimary on
 * primary is 7.9:1 light and 7.5:1 dark, body text better than 13:1 in both.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF54D6A0),
    onPrimary = Color(0xFF00351F),
    primaryContainer = Color(0xFF0F4A35),
    onPrimaryContainer = Color(0xFFBFF3DC),
    secondary = Color(0xFFF0B75C),
    background = Color(0xFF0B1310),
    onBackground = Color(0xFFE3EFE8),
    surface = Color(0xFF131C18),
    onSurface = Color(0xFFE3EFE8),
    surfaceVariant = Color(0xFF1F2C26),
    onSurfaceVariant = Color(0xFFA9BCB2),
    outline = Color(0xFF3A4A42),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F5D42),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA8E9CC),
    onPrimaryContainer = Color(0xFF04291C),
    secondary = Color(0xFF8A5A00),
    background = Color(0xFFF2EFE6),
    onBackground = Color(0xFF1B1D17),
    surface = Color(0xFFFBF9F3),
    onSurface = Color(0xFF1B1D17),
    surfaceVariant = Color(0xFFE4E1D5),
    onSurfaceVariant = Color(0xFF4E5348),
    outline = Color(0xFFA8A798),
    error = Color(0xFFB3261E),
)

@Composable
fun GridboundTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // No dynamic colour branch. Material You repainted the game in whatever the wallpaper
    // happened to be, which meant it had no look of its own and every screenshot was
    // different. Light, dark and follow-the-system remain; the palette does not.
    val scheme = if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = scheme, content = content)
}

