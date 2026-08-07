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
)

val LocalKoridorColors = staticCompositionLocalOf { LightKoridor }
