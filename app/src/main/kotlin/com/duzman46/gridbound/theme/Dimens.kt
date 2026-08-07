package com.duzman46.gridbound.theme

import androidx.compose.ui.unit.dp

/**
 * The one place spacing, corner radius and control height are decided.
 *
 * Every screen pulls from here rather than typing a number, so the app reads as one design
 * instead of a per-screen collection of guesses. Values step in fours; anything between two
 * steps is a sign the layout, not the token, needs fixing.
 */
object Dimens {
    /** Between tightly related items — an icon and its label. */
    val SpaceXs = 4.dp

    /** Inside a control. */
    val SpaceSm = 8.dp

    /** Between controls in a group. */
    val SpaceMd = 12.dp

    /** Between groups. */
    val SpaceLg = 16.dp

    /** Between sections. */
    val SpaceXl = 24.dp

    /** Screen edge inset. */
    val ScreenPadding = 20.dp

    /**
     * How far below the top of the window anything overlaying a whole screen has to begin.
     *
     * Material's small top app bar is 64 dp tall and keeps the back button inside it, so this
     * is the height of the strip an overlay has to leave alone: covering it would take away
     * the one control that gets a player out of the screen they are on.
     */
    val TopBarClearance = 64.dp

    /**
     * Corner radius, as a hierarchy rather than one value everywhere.
     *
     * The home screen reads as a set of physical pieces because the radius tells you what
     * kind of thing you are looking at: a tight 10 for something you press, a full round for
     * something you wear, a soft 28 for the tray they all sit in.
     */
    val RadiusXs = 10.dp

    /** Small chips and inline surfaces. */
    val RadiusSm = 12.dp

    /** Buttons, tiles, list rows — the default. */
    val RadiusMd = 16.dp

    /** Panels and sheets. */
    val RadiusLg = 24.dp

    /** The home screen's board well. */
    val RadiusXl = 28.dp

    /** Home screen control heights. Minimums, never fixed — long labels must be able to grow. */
    val RoomHeight = 56.dp
    val ChipHeight = 44.dp
    val CrestHeight = 44.dp

    /** How far a block sinks when pressed. The travel is the feedback; there is no ripple. */
    val PressTravel = 2.dp
    val BorderStrong = 1.5.dp
    val Hairline = 1.dp

    /** Hand-drawn glyph sizes, by how loud the control is. */
    val GlyphMd = 24.dp
    val GlyphLg = 26.dp

    /** Icon inside a button or a menu tile. */
    val IconSm = 20.dp

    /** Menu content never stretches past this, so tablets do not get a 900 dp button. */
    val MenuMaxWidth = 460.dp
}
