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
 * The brand's jade: the accent, and the colour a wall is drawn in on a dark board.
 *
 * Public because the home screen's board panel is painted outside the theme and still has to
 * agree with it. A wall on the panel and a wall in a live match are the same object, so they
 * are one value rather than two hexes in two files that will drift apart.
 */
val KoridorJade = Color(0xFF16E9A0)

/**
 * A ground that is black first and green second, with the jade kept for things you can act on.
 *
 * Chroma in the ground is what makes a dark game screen look faded: a field with real green in
 * it turns pale the moment anything on it is also green, and it drags the accent down with it
 * by simultaneous contrast. So the neutrals here are black carrying only enough hue to keep
 * them from going blue-grey beside the accent, and every saturated green on the screen belongs
 * to a control.
 *
 * Black on its own is not enough either. Three near-identical blacks with grey lines drawn on
 * them give nothing a surface and nothing an edge, which is the other way a dark screen dies.
 * The three grounds therefore step apart in clear increments — 0x06 / 0x10 / 0x1E — so a card
 * is an object without a shadow under it, and the outlines keep the jade hue so an outlined
 * control is a green-edged thing rather than a grey rectangle.
 *
 * [outline] is pitched against the palest of the three grounds and not the darkest: it is the
 * entire boundary of a secondary button, so what it has to clear 3:1 on is `surfaceVariant`,
 * and a green mixed to look right on near-black is half of what that takes. [outlineVariant]
 * is the divider token and stays quieter on purpose — anything that has to be *identified*
 * rather than merely separated uses [outline] instead.
 *
 * `primary` is a fill colour and `secondary` is the same hue as ink — on a light ground a
 * fill bright enough to sit under white text is too light to be read as text itself. In dark
 * they are the same value, because there the ground does that work.
 */
private val DarkColors = darkColorScheme(
    primary = KoridorJade,
    onPrimary = Color(0xFF00251A),
    secondary = KoridorJade,
    onSecondary = Color(0xFF00251A),
    tertiary = Color(0xFFFF7A3D),
    onTertiary = Color(0xFF2A0A00),
    background = Color(0xFF060C0A),
    onBackground = Color(0xFFE7F1EC),
    surface = Color(0xFF101815),
    onSurface = Color(0xFFE7F1EC),
    surfaceVariant = Color(0xFF1E2A25),
    onSurfaceVariant = Color(0xFFA3BAB0),
    outline = Color(0xFF4C7A69),
    outlineVariant = Color(0xFF2B4A3E),
    error = Color(0xFFFF8F86),
    onError = Color(0xFF3A0603),
)

/**
 * The light scheme is a first-class surface, not the dark one inverted.
 *
 * Its ground is a pale mint paper rather than plain white: white behind white cards gives
 * nothing to separate them, and a green accent on neutral white is dulled by the same
 * simultaneous contrast that flattens a chroma-heavy dark ground. Cards stay pure white so
 * they lift off the paper, and the outline is a deep jade rather than near-black — a black
 * hairline on white is the heaviest mark on the screen and drags every button towards a
 * wireframe.
 *
 * `surfaceVariant` is doing two jobs at once and neither of them may be given up for the
 * other: it is the fill of a borderless section card, so it cannot be lightened towards the
 * paper without the card dissolving into it, and it is the board's alternate tile, so it
 * cannot be deepened without the two pawns losing 3:1 on the squares they stand on.
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

