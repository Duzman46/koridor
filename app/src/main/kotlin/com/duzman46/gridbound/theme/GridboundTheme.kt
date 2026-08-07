package com.duzman46.gridbound.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.duzman46.gridbound.domain.models.AppSettings
import com.duzman46.gridbound.domain.models.ThemeMode

/**
 * Jade on a ground that is green all the way down, not black with a green light on it.
 *
 * The dark scheme read as washed out, and the cause was not the accent — it was the ground.
 * `#06100C` is black to any eye and to any OLED panel, `surface` and `surfaceVariant` sat
 * within six points of it, and `outline` was a neutral grey. So the screen was one flat black
 * sheet with grey lines drawn on it: nothing had a surface, nothing had an edge, and the one
 * green control had no field to be green *against*. A single accent on a colourless ground
 * does not read as vivid, it reads as a lone bright thing on a dead one.
 *
 * The fix is depth and hue rather than more saturation. The ground lifts off black and keeps
 * real chroma; surface and surfaceVariant step up in clear increments so panels are objects;
 * and the outlines carry the same hue, so an outlined control is a green-edged thing rather
 * than a grey rectangle.
 *
 * `primary` is a fill colour and `secondary` is the same hue as ink — on a light ground a
 * fill bright enough to sit under white text is too light to be read as text itself. In dark
 * they are the same value, because there the ground does that work.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF16E9A0),
    onPrimary = Color(0xFF00251A),
    secondary = Color(0xFF16E9A0),
    onSecondary = Color(0xFF00251A),
    tertiary = Color(0xFFFF7A3D),
    onTertiary = Color(0xFF2A0A00),
    // Three clearly separated steps — 0x07/0x0D/0x16 — instead of three shades of the same
    // black. This is what lets a card look like a card without a shadow under it.
    background = Color(0xFF071A15),
    onBackground = Color(0xFFE6F6EE),
    surface = Color(0xFF0D251E),
    onSurface = Color(0xFFE6F6EE),
    surfaceVariant = Color(0xFF163329),
    onSurfaceVariant = Color(0xFF9CC7B3),
    outline = Color(0xFF2F6B57),
    outlineVariant = Color(0xFF24503F),
    error = Color(0xFFFF8F86),
    onError = Color(0xFF3A0603),
)

/**
 * The light scheme is a first-class surface, not the dark one inverted.
 *
 * Its ground is a pale mint paper rather than plain white: white behind white cards gives
 * nothing to separate them, and a green accent on neutral white is dulled by the same
 * simultaneous contrast that flattened the old dark ground. Cards stay pure white so they
 * lift off the paper, and the outline is a deep jade rather than near-black — a black hairline
 * on white is the heaviest mark on the screen and drags every button towards a wireframe.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF00875C),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF00644A),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFFB4400F),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFEDF5F0),
    onBackground = Color(0xFF08211A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF08211A),
    surfaceVariant = Color(0xFFD9EBE1),
    onSurfaceVariant = Color(0xFF3D5A4D),
    outline = Color(0xFF1E5643),
    outlineVariant = Color(0xFF9DBFAE),
    error = Color(0xFFC4261C),
    onError = Color(0xFFFFFFFF),
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
    CompositionLocalProvider(LocalKoridorColors provides if (dark) DarkKoridor else LightKoridor) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

