package com.duzman46.gridbound.ui.components.home

import androidx.compose.ui.graphics.Color
import com.duzman46.gridbound.game.board.BoardPalette
import com.duzman46.gridbound.game.board.SeatColors
import com.duzman46.gridbound.game.models.PlayerId

/**
 * The colours of the board well on the home screen — fixed, and deliberately outside the
 * theme.
 *
 * Everything else in the app follows [androidx.compose.material3.MaterialTheme], including
 * the player's dynamic-colour choice. This one surface does not: it is the app's signature,
 * the thing that should look the same in every screenshot on every phone. A home screen that
 * takes its identity from the wallpaper has no identity.
 *
 * The small round controls above the well are made of the same material, so the well's ink
 * and chip colours are used off it as well as on it. That is a decision about what those
 * controls *are* — pieces of the panel, not entries in the page — and it is also what keeps
 * a dark ground under the one amber mark that lives outside the board.
 *
 * Amber is the wall colour, and it is allowed to appear in exactly four places, every one of
 * them on this dark ground: walls on the board, the wall rack, the route, and the streak arc
 * on the profile crest. Never on the page itself.
 */
object HomePalette {

    /** The well itself. Identical in light and dark. */
    val Well = Color(0xFF0C2119)

    /**
     * Dark theme only. Against the dark background the well is barely 1.06:1 and would read
     * as no container at all, so it needs an edge; in light theme the contrast does that job
     * and a border would just look drawn on.
     */
    val WellBorder = Color(0xFF46705E)

    /** The wall colour, and therefore the colour of what a wall costs you. */
    val Amber = Color(0xFFE8A33D)

    /** Ink for the well's material, whether that is the panel or a chip cut from it. */
    val OnWell = Color(0xFFECF5EF)

    /**
     * Fill and edge for the small round controls above the well.
     *
     * The fill was never what made these read as objects — it is within a shade of the dark
     * theme's background and managed 1.2:1 even against the well. The edge is the whole
     * boundary, so it is pitched to clear 3:1 on the dark theme's ground; the 18% hairline it
     * inherited came to 2.4:1 there, which is a chip you have to look for. On the light theme's
     * pale ground the fill carries it at 9.7:1 and the edge simply softens the rim.
     */
    val ChipFill = Color(0xFF17332A)
    val ChipStroke = Color(0x52FFFFFF)

    /**
     * The board as it appears in the well. Deeper and warmer than any of the playable board
     * themes, because here it is scenery rather than something being read at arm's length.
     *
     * The goal rows are the exception: they name the two seats, so they come from
     * [SeatColors] like every other board does. A player who picked a colour on the home
     * screen has to see the same colour when the match opens.
     */
    val Board = BoardPalette(
        background = Color(0xFF143026),
        tile = Color(0xFF1B4032),
        tileAlternate = Color(0xFF17392C),
        goalOne = SeatColors.goal(PlayerId.PLAYER_ONE),
        goalTwo = SeatColors.goal(PlayerId.PLAYER_TWO),
        valid = Color(0xFF32D583),
        invalid = Color(0xFFE5484D),
        wall = Amber,
        playerOne = Color(0xFF4C93FF),
        playerTwo = Color(0xFFFF9A45),
        selection = Color(0xFF54D6A0),
    )
}
