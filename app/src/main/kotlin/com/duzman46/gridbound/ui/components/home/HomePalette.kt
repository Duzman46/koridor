package com.duzman46.gridbound.ui.components.home

import androidx.compose.ui.graphics.Color
import com.duzman46.gridbound.game.board.BoardPalette

/**
 * The colours of the board well on the home screen — fixed, and deliberately outside the
 * theme.
 *
 * Everything else in the app follows [androidx.compose.material3.MaterialTheme], including
 * the player's dynamic-colour choice. This one surface does not: it is the app's signature,
 * the thing that should look the same in every screenshot on every phone. A home screen that
 * takes its identity from the wallpaper has no identity.
 *
 * Amber is the wall colour, and it is allowed to appear in exactly four places, all of them
 * on this dark ground: walls on the board, the wall rack, the route, and the streak arc on
 * the profile crest. Nowhere below the well.
 */
object HomePalette {

    /** The well itself. Identical in light and dark. */
    val Well = Color(0xFF0C2119)

    /**
     * Dark theme only. Against the dark background the well is barely 1.06:1 and would read
     * as no container at all, so it needs an edge; in light theme the contrast does that job
     * and a border would just look drawn on.
     */
    val WellBorder = Color(0xFF2A4438)

    /** The wall colour, and therefore the colour of what a wall costs you. */
    val Amber = Color(0xFFE8A33D)

    val OnWell = Color(0xFFECF5EF)

    /** Fill and edge for the controls that sit on the well. */
    val ChipFill = Color(0xFF17332A)
    val ChipStroke = Color(0x2EFFFFFF)

    /** The dashed ring around a guest's avatar: an account that is not real yet. */
    val GuestRing = Color(0xFF8FA79A)

    /**
     * The board as it appears in the well. Deeper and warmer than any of the playable board
     * themes, because here it is scenery rather than something being read at arm's length.
     */
    val Board = BoardPalette(
        background = Color(0xFF143026),
        tile = Color(0xFF1B4032),
        tileAlternate = Color(0xFF17392C),
        goalOne = Color(0xFF3F82FF),
        goalTwo = Color(0xFFFF9D3F),
        valid = Color(0xFF32D583),
        invalid = Color(0xFFE5484D),
        wall = Amber,
        playerOne = Color(0xFF4C93FF),
        playerTwo = Color(0xFFFF9A45),
        selection = Color(0xFF54D6A0),
    )
}
