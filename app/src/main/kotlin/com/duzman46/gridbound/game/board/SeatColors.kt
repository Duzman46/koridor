package com.duzman46.gridbound.game.board

import androidx.compose.ui.graphics.Color
import com.duzman46.gridbound.game.models.PlayerId

/**
 * The two players' colours.
 *
 * Fixed rather than themed, and defined once here rather than repeated as a hex literal in
 * five files: they have to stay distinguishable from each other in both themes, they are
 * named in the tutorial's own text, and the room browser lets a host pick between them by
 * name. A second copy of a colour that carries a name is a copy that will drift.
 *
 * Red and not an orange: walls are drawn in the theme's jade and a pawn has to be as far from
 * that as the wheel allows, while a warm value close to the wall colour puts the piece and the
 * obstacle in the same part of the spectrum and makes a crowded board unreadable.
 *
 * The hues belong to the seats and never trade places: seat one is blue, seat two is red.
 * That is the whole point of the binding — seat one opens, so "blue" is the name a player
 * reads for "moves first", and picking a colour picks a seat rather than a coat of paint.
 * A pawn that could be either colour would make the choice meaningless.
 *
 * Both are pitched against the palest square either theme can put under them, which is the
 * light board's alternate tile at #DBECE3 — a brighter blue or a brighter red is a pawn that
 * dissolves into every other square of the checkerboard.
 */
object SeatColors {
    val Blue = Color(0xFF3A7CFA)
    val Red = Color(0xFFEC4238)

    /**
     * The hues the two goal rows are tinted with, washed over the tile beneath them at 28 %.
     *
     * Blue survives that dilution as it is, so its goal row and its pawn are one value. Red
     * does not: a 28 % wash of the pawn's red lands close enough to the dark board's own tile
     * to read as shadow rather than as the row that ends the game, so the goal takes a lighter
     * red while the piece keeps the deeper one.
     */
    val BlueGoal = Color(0xFF3A7CFA)
    val RedGoal = Color(0xFFFF6455)

    fun pawn(seat: PlayerId): Color = if (seat == PlayerId.PLAYER_ONE) Blue else Red

    fun goal(seat: PlayerId): Color = if (seat == PlayerId.PLAYER_ONE) BlueGoal else RedGoal
}
