package com.duzman46.gridbound.ui.components.home

import androidx.compose.ui.graphics.Color
import com.duzman46.gridbound.game.board.BoardPalette
import com.duzman46.gridbound.game.board.SeatColors
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.theme.KoridorJade

/**
 * The colours of the board well on the home screen — fixed, and deliberately outside the
 * theme.
 *
 * Everything else in the app follows [androidx.compose.material3.MaterialTheme], including
 * the player's light-or-dark choice. This one surface does not: it is the app's signature,
 * the thing that should look the same in every screenshot on every phone. A home screen that
 * changes colour with a setting has no identity.
 *
 * The small round controls above the well are made of the same material, so the well's ink
 * and chip colours are used off it as well as on it. That is a decision about what those
 * controls *are* — pieces of the panel, not entries in the page — and it is what keeps a dark
 * ground under every mark in here on a light phone as well as a dark one.
 *
 * **What each colour means, which is the whole reason there are three of them.** The panel is
 * a picture of a real position, so it has to obey the same rules the board obeys or it teaches
 * the player something false before the first tap:
 *
 * - [Accent] is a *wall*. A live match draws walls in the theme's jade, so the walls on this
 *   board and the rack counting the ones already spent are drawn in the same jade. A wall
 *   belongs to nobody; painting it in a seat colour would say a player owned it.
 * - A pawn carries its *seat's* colour, straight from [SeatColors] — blue opens, red answers.
 *   Nothing else on the panel may borrow those two hues.
 * - [Route] is blue's shortest way home, so it is blue: the dashed line starts under blue's
 *   pawn and ends on blue's goal row, and drawing one player's escape in the other player's
 *   colour would be a lie told in paint. It is lifted off the seat hue because its last
 *   segment and the disc that ends it cross blue's own blue-tinted goal row, where the seat
 *   value at full strength is the one place on the board it cannot be seen.
 */
object HomePalette {

    /** The well itself. Identical in light and dark. */
    val Well = Color(0xFF111C18)

    /**
     * Dark theme only. Against the near-black page the well is barely 1.13:1 and would read
     * as no container at all, so it needs an edge; in light theme the contrast does that job
     * and a border would just look drawn on.
     */
    val WellBorder = Color(0xFF4A7565)

    /**
     * The wall colour, and therefore the colour of what a wall costs you.
     *
     * The crest's win-streak arc is the one place this is used away from a board. Nothing in a
     * crest is a wall, so there is no meaning to collide with — there it is simply the app's
     * own colour marking progress.
     */
    val Accent = KoridorJade

    /** Blue's route out, and the disc marking where it ends. */
    val Route = Color(0xFF74A3FB)

    /** Ink for the well's material, whether that is the panel or a chip cut from it. */
    val OnWell = Color(0xFFE7F1EC)

    /**
     * Fill and edge for the small round controls above the well.
     *
     * The fill is the well's own material, and it was never what made these read as objects —
     * on the near-black page it manages 1.1:1. The edge is the whole boundary, so it is
     * pitched to clear 3:1 on both pages at once: 3.6:1 on the dark one and 4.2:1 on the pale
     * one, which is the narrow band a single white alpha can hit against two opposite grounds.
     */
    val ChipFill = Well
    val ChipStroke = Color(0x59FFFFFF)

    /**
     * The board as it appears in the well: a shade above the well it sits in, so it reads as a
     * plate laid into a case rather than a drawing printed on one.
     *
     * The goal rows are the exception to "fixed": they name the two seats, so they come from
     * [SeatColors] like every other board does. A player who sees a colour on the home screen
     * has to meet the same colour when the match opens.
     */
    val Board = BoardPalette(
        background = Color(0xFF1C2A25),
        tile = Color(0xFF18241F),
        tileAlternate = Color(0xFF1F2D27),
        goalOne = SeatColors.goal(PlayerId.PLAYER_ONE),
        goalTwo = SeatColors.goal(PlayerId.PLAYER_TWO),
        valid = Color(0xFF32D583),
        invalid = Color(0xFFE5484D),
        wall = Accent,
        slot = Accent,
        playerOne = SeatColors.pawn(PlayerId.PLAYER_ONE),
        playerTwo = SeatColors.pawn(PlayerId.PLAYER_TWO),
        selection = Accent,
    )
}
