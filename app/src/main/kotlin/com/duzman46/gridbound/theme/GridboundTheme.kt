package com.duzman46.gridbound.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * The brand's jade: the accent, and the colour a wall is drawn in on a dark board.
 *
 * Public because the home screen's board panel is painted outside the theme and still has to
 * agree with it. A wall on the panel and a wall in a live match are the same object, so they
 * are one value rather than two hexes in two files that will drift apart.
 */
val KoridorJade = Color(0xFF16E9A0)

/**
 * The brand's gold: the accent everywhere the interface is not a board.
 *
 * A jade-accented dark app reads as a neon mobile game, which is the one thing this is not
 * meant to be — it is a strategy game for adults and it should sit next to a premium board
 * game rather than next to a free-to-play template. Gold does that work, and it does it on a
 * strict ration: roughly a tenth of what the eye lands on. Active state, the one way into a
 * match, a brand detail. Everything else is neutral.
 *
 * Muted deliberately. A saturated yellow is a coin in a casino game; this is closer to brass
 * seen under a controlled light, which is what keeps it expensive rather than loud.
 *
 * [KoridorJade] stays exactly where it was — on the board. A wall is jade in a live match and on
 * the feature graphic's board, because that is a rule about a game object and not a decision
 * about the interface's accent. The two never appear in the same role.
 *
 * The launcher icon is the one place that rule is not visible, and it is not an exception to it.
 * Since 2026-08-12 the icon is a supplied render — gold pawn, gold-edged walls — chosen by the
 * owner as artwork. It is a picture *of* the game rather than an instance of the interface, and
 * nothing in the app reads a colour from it, so it constrains nothing here.
 */
val KoridorGold = Color(0xFFD0A653)

/**
 * Body ink: the near-white every screen reads through `colorScheme.onSurface`.
 *
 * It is declared here rather than added to [Palette] for two reasons. [Palette] is a contract
 * shared with work happening in parallel and is additive-only once published, and — more to the
 * point — every call site in the app already reads this value the correct way, through the
 * scheme role three declarations below. A second public name for the same colour is precisely
 * how sixty-five near-neutral literals came to exist in the first place.
 */
private val Ink = Color(0xFFF2F0EB)

/**
 * The one colour scheme. There is no second one, and the [GridboundTheme] KDoc says why.
 *
 * **Every role Material3 has is set here, including the ones nothing in this app appears to
 * use.** That is the whole point of the file and it is not defensive tidiness — it was a
 * measured defect. `darkColorScheme` fills anything left blank from Material's *baseline* dark
 * palette, which is built on a violet primary and a purple-tinted neutral, and four of those
 * defaults were reachable inside a live match:
 *
 * - `secondaryContainer` `#4A4458`, a grey-violet, was the rival's chat bubble on the board.
 * - `tertiaryContainer` `#633B48`, a wine, was the online status strip under the board.
 * - `errorContainer` `#8C1D18`, a crimson from no palette this app owns, was the move clock.
 * - `primaryContainer` `#4F378B`, a violet, was every match invitation, over every screen.
 * - `surfaceContainerHigh` `#2B2930` was the container of every `AlertDialog` in the app, and
 *   `surfaceContainerLow` `#1D1B20` the container of every `ModalBottomSheet`.
 *
 * Measured against [Palette.Card], those four containers sit at 1.95, 1.95, 1.99 and 1.95:1 —
 * the same lightness as one another in four hues that belong to none of this app's screens.
 * That is the mechanical definition of a colour inconsistency, and it was invisible to any
 * audit that counts colour literals, because there is no literal to count. A role added by a
 * future Material release will still fall back, so anything that starts looking foreign after
 * a dependency bump should be looked for here first.
 *
 * `surfaceTint = Color.Transparent` is the other deliberate one. Material blends the tint over
 * a `Surface` in proportion to its `tonalElevation`, and the default tint is `primary` — so any
 * elevated surface quietly washed itself in gold at an opacity nobody chose. Transparent makes
 * `tonalElevation` a no-op app-wide, which means a component cannot separate itself with a
 * colour by accident; it has to use one of the three grounds like everything else.
 *
 * **`by lazy`, and it is not a performance choice — it breaks a class-initialisation cycle that
 * would otherwise fail silently.** [Palette] reads [KoridorGold] out of this file, and this
 * scheme reads its tokens back out of [Palette]. Whichever of the two the JVM initialises first
 * re-enters the other mid-initialisation, and a re-entrant read is allowed rather than
 * deadlocked: it simply returns whatever the field holds at that instant. If [Palette] went
 * first, this list would read `Palette.GoldInk` before that line of [Palette] had run and get
 * the zero value — which for an inline `Color` is not a crash but `Color.Unspecified`, i.e. the
 * ink on every gold button quietly becoming nothing, in a way no test and no build would name.
 * Deferring the whole scheme to first use means both objects are complete before either value is
 * read. Anything added here that reads [Palette] at construction time must stay inside this lazy.
 */
private val DarkColors by lazy {
    darkColorScheme(
        primary = KoridorGold,
        onPrimary = Palette.GoldInk,
        primaryContainer = Palette.Card,
        onPrimaryContainer = Ink,
        inversePrimary = KoridorGold,

        secondary = KoridorGold,
        onSecondary = Palette.GoldInk,
        secondaryContainer = Palette.Inset,
        onSecondaryContainer = Ink,

        // The one hue in the scheme that is neither gold nor neutral: a burnt orange kept for
        // the rare tertiary accent. It is close enough to the gold's family not to read as a
        // second brand, and far enough from it to be told apart when the two are adjacent.
        tertiary = Color(0xFFC98A4B),
        onTertiary = Color(0xFF241202),
        tertiaryContainer = Palette.Inset,
        onTertiaryContainer = Ink,

        background = Palette.Ground,
        onBackground = Ink,
        surface = Palette.Card,
        onSurface = Ink,
        surfaceVariant = Palette.Inset,
        onSurfaceVariant = Palette.InkMuted,
        surfaceTint = Color.Transparent,

        // The container steps collapse onto the three grounds rather than inventing five more.
        // Material intends them as a tonal ladder; this app's ladder is Ground / Card / Inset,
        // and a component that asks for "surfaceContainerHigh" is asking to be a card.
        surfaceBright = Palette.Inset,
        surfaceDim = Palette.Ground,
        surfaceContainerLowest = Palette.Ground,
        surfaceContainerLow = Palette.Card,
        surfaceContainer = Palette.Card,
        surfaceContainerHigh = Palette.Card,
        surfaceContainerHighest = Palette.Inset,

        // Set so a snackbar — the one component that inverts — does not fall back to Material's
        // lavender-tinted pair.
        inverseSurface = Ink,
        inverseOnSurface = Palette.Ground,

        // `outline` is the boundary that *identifies* a control, so it takes the token that
        // clears 3:1 (4.50:1 on Card). `outlineVariant` is the divider and stays decorative.
        // The old outline was #3A424B at 1.77:1 — a border doing an affordance's job, failing it.
        outline = Palette.InkGlyph,
        outlineVariant = Palette.Edge,

        error = Color(0xFFE2776C),
        onError = Color(0xFF2C0704),
        errorContainer = Color(0xFF3A1714),
        onErrorContainer = Color(0xFFF0BDB6),

        scrim = Color(0xFF000000),
    )
}

/**
 * The app's theme. One scheme, unconditionally.
 *
 * Light mode is gone, and it was removed on measurement rather than taste. Three numbers
 * decided it, and they are recorded here because the decision is the kind that gets reversed by
 * somebody who only sees the missing setting:
 *
 * 1. [KoridorGold] on the old light background `#EDF5F0` is **1.89:1**, where large text needs
 *    3:1. That is the title of every screen in the app. There was no light ground in this app
 *    on which the brand's own accent was legible — on white it is 2.27:1, on the light
 *    `surfaceVariant` 1.83:1.
 * 2. The section label `#9AA0A8` on that same background is 2.37:1, against a 4.5:1 floor.
 * 3. Light mode did not actually exist. All two hundred and fifty near-neutral colour literals
 *    in the UI were unconditional — no theme branch anywhere — so "light mode" rendered the
 *    finished dark app with a handful of Material leftovers flipping to white around it. The
 *    board is a fixed dark render besides. There was nothing to port.
 *
 * The setting that chose between them is gone too, rather than reduced to a single-value
 * preference. A control that offers one answer is a control that lies about being a choice.
 *
 * There is also no dynamic-colour branch, and that is a separate and older decision: Material
 * You repainted the game in whatever the wallpaper happened to be, so it had no look of its own
 * and no two screenshots matched.
 */
@Composable
fun GridboundTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKoridorColors provides DarkKoridor) {
        MaterialTheme(colorScheme = DarkColors, content = content)
    }
}
