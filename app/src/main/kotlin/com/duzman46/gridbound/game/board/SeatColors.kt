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
 * Red rather than the old amber. The walls are drawn in the theme's own accent and the wall
 * rack on the home panel is amber; a third warm value on the same board meant the second
 * pawn, a wall and a spent-wall marker were all fighting for the same part of the spectrum.
 *
 * The hues belong to the seats and never trade places: seat one is blue, seat two is red.
 * That is the whole point of the binding — seat one opens, so "blue" is the name a player
 * reads for "moves first", and picking a colour picks a seat rather than a coat of paint.
 * A pawn that could be either colour would make the choice meaningless.
 */
object SeatColors {
    val Blue = Color(0xFF3F82FF)
    val Red = Color(0xFFF0483F)

    /** The goal rows are the same hues, lifted so a tinted tile still reads as a tile. */
    val BlueGoal = Color(0xFF3F82FF)
    val RedGoal = Color(0xFFFF6455)

    fun pawn(seat: PlayerId): Color = if (seat == PlayerId.PLAYER_ONE) Blue else Red

    fun goal(seat: PlayerId): Color = if (seat == PlayerId.PLAYER_ONE) BlueGoal else RedGoal
}
