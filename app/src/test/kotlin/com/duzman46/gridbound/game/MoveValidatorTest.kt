package com.duzman46.gridbound.game

import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveValidatorTest {
    @Test
    fun startingPlayerHasThreeMoves() {
        val moves = TestFixtures.moves.validMoves(BoardState.initial())

        assertEquals(
            setOf(Position(7, 4), Position(8, 3), Position(8, 5)),
            moves,
        )
    }

    @Test
    fun jumpsStraightOverAdjacentOpponentWhenBehindIsOpen() {
        val state = TestFixtures.state(Position(4, 4), Position(3, 4))

        val moves = TestFixtures.moves.validMoves(state)

        assertTrue(Position(2, 4) in moves)
        assertFalse(Position(3, 3) in moves)
        assertFalse(Position(3, 5) in moves)
    }

    @Test
    fun allowsBothDiagonalsWhenWallBlocksStraightJump() {
        val state = TestFixtures.state(
            playerOne = Position(4, 4),
            playerTwo = Position(3, 4),
            walls = setOf(Wall(2, 3, WallOrientation.HORIZONTAL)),
        )

        val moves = TestFixtures.moves.validMoves(state)

        assertFalse(Position(2, 4) in moves)
        assertTrue(Position(3, 3) in moves)
        assertTrue(Position(3, 5) in moves)
    }

    @Test
    fun allowsDiagonalsWhenOpponentIsAgainstBoardEdge() {
        val state = TestFixtures.state(Position(1, 4), Position(0, 4))

        val moves = TestFixtures.moves.validMoves(state)

        assertTrue(Position(0, 3) in moves)
        assertTrue(Position(0, 5) in moves)
    }

    @Test
    fun sideWallBlocksOnlyItsDiagonal() {
        val state = TestFixtures.state(
            playerOne = Position(4, 4),
            playerTwo = Position(3, 4),
            walls = setOf(
                Wall(2, 3, WallOrientation.HORIZONTAL),
                Wall(2, 3, WallOrientation.VERTICAL),
            ),
        )

        val moves = TestFixtures.moves.validMoves(state)

        assertFalse(Position(3, 3) in moves)
        assertTrue(Position(3, 5) in moves)
    }
}

