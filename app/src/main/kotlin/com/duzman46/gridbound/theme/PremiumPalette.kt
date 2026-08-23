package com.duzman46.gridbound.theme

import androidx.compose.ui.graphics.Color

/**
 * The one place a colour is decided, as [Dimens] is the one place a spacing is.
 *
 * It exists because the absence of it was measurable: sixty-five distinct near-neutral values
 * across two hundred and fifty uses, including pairs one integer apart — `#161B21` against
 * `#171C22` is 1.011:1, which is to say it is the same colour typed twice in two files. A design
 * system with a spacing token file and no colour token file does not stay consistent; it drifts,
 * and the drift is what reads as an interface nobody designed.
 *
 * **Every value carries the contrast ratio it was chosen for. Editing one without re-checking
 * that number is a regression, not a preference.** Ratios are sRGB WCAG 2.x against the ground
 * each token actually lands on.
 *
 * Two colours are deliberately NOT reachable from here. [KoridorJade] is what a wall is drawn in
 * and `SeatColors` is what a pawn is drawn in: they are game objects, not interface, and putting
 * them in this object is how one of them ends up as a button tint and stops meaning anything.
 */
object Palette {

    // ---- the three grounds -------------------------------------------------------------
    // The theme's own KDoc already said "the three grounds step apart in clear increments".
    // The code had eight. These three are that intent, made real.

    /** The page. Nothing else is ever this dark. */
    val Ground = Color(0xFF070A0D)

    /**
     * Anything that is an *object* sitting on the page — a card, a panel, a sheet, a dialog, a
     * field. 1.09:1 from [Ground]: enough to read as lifted, not enough to read as a second page.
     */
    val Card = Color(0xFF12161B)

    /**
     * Anything *inside* a card that needs its own footprint: an inset chip, a progress track, an
     * avatar well, a dead control's fill, the rule between two rows. 1.19:1 from [Card].
     */
    val Inset = Color(0xFF20262D)

    // ---- the three inks ----------------------------------------------------------------
    // Six greys became three. The rule that separates them is mechanical, so nobody has to
    // decide: `color =` on a Text is InkMuted; `tint =` on a mark, or a Color handed to a
    // DrawScope, is InkGlyph; a control that cannot be pressed is InkDisabled.

    /** Every piece of secondary **text**. 5.79:1 on [Inset], the worst ground it lands on. */
    val InkMuted = Color(0xFF9AA0A8)

    /**
     * Every muted **non-text mark** — a canvas glyph, a chevron, a presence dot — and the 1dp
     * boundary of a control whose border is its only affordance. 3.78:1 on [Inset].
     */
    val InkGlyph = Color(0xFF7A7F86)

    /**
     * The ink and edge of a control that **cannot be pressed**, and nothing else. 2.45:1, which
     * is below every floor on purpose: WCAG exempts inactive components, and a disabled control
     * that reads as pressable is the worse defect.
     */
    val InkDisabled = Color(0xFF5C6169)

    // ---- edges and metal ---------------------------------------------------------------

    /**
     * A **decorative** boundary — a card that already has a fill distinct from the page. It
     * identifies nothing on its own, so it is exempt from 3:1. A border that IS the affordance
     * uses [InkGlyph] instead.
     */
    val Edge = Color(0xFF2A3038)

    /** The interface accent, re-exported so one import serves. 8.01:1 on [Card]. */
    val Gold = KoridorGold

    /** The near-black that goes **on** gold. 6.4:1 under it. */
    val GoldInk = Color(0xFF1A1206)

    /**
     * The warm end of the header band. It was drawn five times in five files as two values one
     * unit of red apart — the same failure as the greys, at a larger grain.
     */
    val GoldBandEnd = Color(0xFF1A1710)

    // A three-stop gold gradient used to live here, lifted out of two buttons that had drawn it
    // byte-for-byte. It is gone rather than kept, and the reason is the mission's own: the app
    // carried TWO gold gradients, a three-stop and a two-stop, that contradicted each other — and
    // neither resolves as a gradient across a 48dp button. What they actually produced was two
    // buttons that did not match. One flat gold is a decision; two gradients nobody can see is an
    // accident with a longer definition.
}
