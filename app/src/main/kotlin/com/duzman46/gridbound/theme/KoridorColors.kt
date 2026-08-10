package com.duzman46.gridbound.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colour roles Material3 has no slot for.
 *
 * Two invariants live here, and both exist because breaking them produces something that
 * looks fine in the IDE preview and fails on a real screen:
 *
 * - `colorScheme.primary` is a **fill**. When the accent has to be text or an icon, use
 *   `colorScheme.secondary`, which is the same hue darkened enough to be read as ink.
 * - `live` is the ember, and it is budgeted: at most one live control on a screen, and never
 *   on a screen that draws a board. Warm already belongs to a seat wherever there are pawns,
 *   and two warm accents in one frame make neither of them mean anything.
 */
@Immutable
data class KoridorColors(
    val primaryPressed: Color,
    val live: Color,
    val onLive: Color,
    val livePressed: Color,
    val liveInk: Color,
    val provisionalRing: Color,
    val disabledFill: Color,
    val disabledInk: Color,
    val accents: AccentPalette,
)

/**
 * How loud a card is, said in hue rather than in size.
 *
 * A screen of identical outlined rows makes the player read every label to find the one they
 * want. Giving each destination a fixed colour lets it be found by shape instead: online is
 * always green, the bot is always violet, the tutorial is always amber, and after the second
 * session nobody reads those labels any more.
 *
 * The rule that keeps this from becoming confetti: **a tone belongs to a destination, not to a
 * screen.** "Çevrim içi" is [Online] wherever it appears — on the home grid, in the play list,
 * in a section header. Picking a colour because it looks nice next to the one above it is how
 * a palette stops carrying meaning.
 *
 * Each tone is a pair, because a tinted square has two jobs and one value cannot do both:
 * [Tone.fill] is the square, mixed to sit a step off the card behind it, and [Tone.ink] is the
 * glyph on it, which has to clear 3:1 on that square in both themes. The dark inks are close
 * to the source hue; the light fills are much paler than their inks, because on white paper a
 * fill saturated enough to be seen is far too dark to draw a glyph on.
 */
@Immutable
data class AccentPalette(
    val online: Tone,
    val bot: Tone,
    val local: Tone,
    val learn: Tone,
    val social: Tone,
    val reward: Tone,
) {
    @Immutable
    data class Tone(val fill: Color, val ink: Color)
}

private val DarkAccents = AccentPalette(
    online = AccentPalette.Tone(fill = Color(0xFF10362A), ink = Color(0xFF2FE39C)),
    bot = AccentPalette.Tone(fill = Color(0xFF2A1E4A), ink = Color(0xFFA78BFA)),
    local = AccentPalette.Tone(fill = Color(0xFF16304A), ink = Color(0xFF6FA8F5)),
    learn = AccentPalette.Tone(fill = Color(0xFF3A2A12), ink = Color(0xFFF0A855)),
    social = AccentPalette.Tone(fill = Color(0xFF123A38), ink = Color(0xFF4FD6C8)),
    reward = AccentPalette.Tone(fill = Color(0xFF3D1C2C), ink = Color(0xFFF57FA6)),
)

private val LightAccents = AccentPalette(
    online = AccentPalette.Tone(fill = Color(0xFFD3F0E2), ink = Color(0xFF00694A)),
    bot = AccentPalette.Tone(fill = Color(0xFFE6E0FB), ink = Color(0xFF5334B8)),
    local = AccentPalette.Tone(fill = Color(0xFFDCE8FB), ink = Color(0xFF1A4E93)),
    learn = AccentPalette.Tone(fill = Color(0xFFFAE6CE), ink = Color(0xFF8A4B08)),
    social = AccentPalette.Tone(fill = Color(0xFFD1EFEC), ink = Color(0xFF0A5E58)),
    reward = AccentPalette.Tone(fill = Color(0xFFFBDDE7), ink = Color(0xFF9B2B51)),
)

val LightKoridor = KoridorColors(
    primaryPressed = Color(0xFF005E3E),
    live = Color(0xFFBE3A0E),
    onLive = Color(0xFFFFFFFF),
    livePressed = Color(0xFF9E2F08),
    liveInk = Color(0xFF9A3208),
    // A darkened sibling of the dark scheme's ring below: that value is only 2.05:1 on a light
    // tile, this is 3.96:1. The same mark, still readable on the opposite ground.
    provisionalRing = Color(0xFF5E7568),
    // WCAG exempts inactive controls, and this pair still clears 3:1 anyway. "Unavailable" is
    // carried by the flat fill and the missing border; it does not also need a label nobody
    // with low vision can read.
    disabledFill = Color(0xFFD3D6D2),
    disabledInk = Color(0xFF6F7572),
    accents = LightAccents,
)

val DarkKoridor = KoridorColors(
    primaryPressed = Color(0xFF08CB86),
    live = Color(0xFFFF6A2B),
    onLive = Color(0xFF1C0500),
    livePressed = Color(0xFFE45A1F),
    liveInk = Color(0xFFFF6A2B),
    // The provisional mark reads the same everywhere it appears on a dark ground — the crest's
    // dashed ring and a provisional rating are one idea, so they are one colour.
    provisionalRing = Color(0xFF8FA79A),
    // The fill sits one step off the near-black ground so a dead control is still a control,
    // and the ink clears 3:1 on it; see the light pair above for why.
    disabledFill = Color(0xFF1A211E),
    disabledInk = Color(0xFF6E7A75),
    accents = DarkAccents,
)

val LocalKoridorColors = staticCompositionLocalOf { LightKoridor }
