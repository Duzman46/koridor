package com.duzman46.gridbound.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colour roles Material3 has no slot for.
 *
 * One invariant lives here, and it exists because breaking it produces something that looks
 * fine in the IDE preview and fails on a real screen:
 *
 * - `live` is the ember, and it is budgeted: at most one live control on a screen, and no
 *   PERSISTENT live control on a screen that draws a board. Warm already belongs to a seat
 *   wherever there are pawns, and two standing warm accents in one frame make neither of them
 *   mean anything.
 *
 *   The move clock in its last seconds is the one exemption, and it is worth writing down why
 *   rather than leaving the next reader to think the rule simply broke. What that clock used to
 *   paint was `errorContainer` — a role this app never set, so it resolved to Material's own
 *   baseline crimson `#8C1D18`, a filled slab of a hue that appears nowhere else in Koridor,
 *   eight dp under a gold-edged card. So the choice was never warm against not-warm; it was the
 *   app's own ember against a framework's default. The ember is also transient and it is
 *   *earned*: it appears only while a turn is genuinely running out, which is the one moment the
 *   screen should be shouting. And it costs less warm area than what it replaced — a border, four
 *   digits and a near-black wash, where there used to be a filled bar.
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
 * glyph on it, which has to clear 3:1 on that square. The inks stay close to the source hue;
 * the fills are that hue taken down until a near-black page can still be seen behind it.
 */
@Immutable
data class AccentPalette(
    val online: Tone,
    val bot: Tone,
    val local: Tone,
    val learn: Tone,
    val social: Tone,
    val reward: Tone,
    /**
     * The one tone that names no destination.
     *
     * Settings and More are not places you go to play, and giving them a mode's colour was the
     * exact mistake the paragraph above warns about: Settings took [local] and More took [bot],
     * so on the play screen — where those two tones mean "same device" and "vs computer" — the
     * same blue and the same violet would have meant something else one screen earlier. A
     * neutral keeps the five destination tones honest.
     */
    val system: Tone,
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
    system = AccentPalette.Tone(fill = Color(0xFF232B28), ink = Color(0xFFB9CCC4)),
)

/**
 * The one set of roles. There used to be a light sibling of this and of [DarkAccents]; both
 * went with light mode, and `GridboundTheme`'s KDoc records the three measurements that
 * decided it.
 */
val DarkKoridor = KoridorColors(
    primaryPressed = Color(0xFF08CB86),
    live = Color(0xFFFF6A2B),
    onLive = Color(0xFF1C0500),
    livePressed = Color(0xFFE45A1F),
    liveInk = Color(0xFFFF6A2B),
    // The provisional mark reads the same everywhere it appears on a dark ground — the crest's
    // dashed ring and a provisional rating are one idea, so they are one colour.
    provisionalRing = Color(0xFF8FA79A),
    // WCAG exempts inactive controls, and this pair still clears 3:1 anyway. The fill sits one
    // step off the near-black ground so a dead control is still a control, and "unavailable" is
    // carried by that flat fill and the missing border — it does not also need a label nobody
    // with low vision can read.
    disabledFill = Color(0xFF1A211E),
    disabledInk = Color(0xFF6E7A75),
    accents = DarkAccents,
)

/**
 * The roles Material has no slot for, read through a composition local.
 *
 * **The default matters and it used to be wrong.** It was `LightKoridor` — so anything composed
 * outside `GridboundTheme`, which is every `@Preview` and every test harness, silently got the
 * light palette while the running app got the dark one. The provider in `GridboundTheme` hid it
 * from the only place anybody looked.
 *
 * With one palette left this local now has exactly one possible value, and the indirection could
 * in principle be dropped for a plain `val`. It is kept for now because the read sites live in
 * files being reworked in parallel; collapsing it is a mechanical follow-up, not a decision.
 */
val LocalKoridorColors = staticCompositionLocalOf { DarkKoridor }
