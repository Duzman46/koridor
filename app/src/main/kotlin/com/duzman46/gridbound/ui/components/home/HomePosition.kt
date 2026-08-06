package com.duzman46.gridbound.ui.components.home

import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameStatus
import com.duzman46.gridbound.game.models.Player
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation

/**
 * The position shown on the home screen.
 *
 * Not decoration: it is a real, legal mid-game board drawn by the production renderer, so the
 * first thing a player sees is the actual game rather than an illustration of it. Blue is
 * boxed in by five walls and has to run the long way round — which is the whole idea of
 * Koridor in one picture.
 *
 * HomePositionTest replays it through the real rules engine, so a future rules change cannot
 * quietly leave an impossible board on the home screen.
 */
object HomePosition {

    val STATE: BoardState = BoardState(
        players = mapOf(
            PlayerId.PLAYER_ONE to Player(PlayerId.PLAYER_ONE, Position(5, 3), wallsRemaining = 6),
            PlayerId.PLAYER_TWO to Player(PlayerId.PLAYER_TWO, Position(3, 4), wallsRemaining = 7),
        ),
        walls = setOf(
            Wall(4, 2, WallOrientation.HORIZONTAL),
            Wall(4, 4, WallOrientation.HORIZONTAL),
            Wall(2, 5, WallOrientation.HORIZONTAL),
            Wall(1, 3, WallOrientation.HORIZONTAL),
            Wall(6, 2, WallOrientation.HORIZONTAL),
            Wall(3, 1, WallOrientation.VERTICAL),
            Wall(5, 1, WallOrientation.VERTICAL),
        ),
        currentPlayer = PlayerId.PLAYER_ONE,
        status = GameStatus.IN_PROGRESS,
        turnNumber = 14,
        history = emptyList(),
    )

    /**
     * The corners of blue's shortest way home, drawn as a travelling dashed line.
     *
     * Corner list, not step list: the renderer strokes straight segments between these, and
     * the test expands them back into single steps to prove every one is legal.
     */
    val ROUTE: List<Position> = listOf(
        Position(5, 3),
        Position(5, 6),
        Position(3, 6),
        Position(3, 7),
        Position(0, 7),
    )

    /** Walls already on the board, which must equal what the two players have spent. */
    val wallsPlaced: Int get() = STATE.walls.size
}
