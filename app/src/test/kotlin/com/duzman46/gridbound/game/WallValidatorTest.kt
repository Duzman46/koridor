package com.duzman46.gridbound.game

import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallValidatorTest {
    @Test
    fun acceptsOrdinaryWallOnOpenBoard() {
        assertTrue(
            TestFixtures.walls.isValid(
                BoardState.initial(),
                Wall(4, 4, WallOrientation.HORIZONTAL),
            ),
        )
    }

    @Test
    fun rejectsDuplicateOverlapAndCrossing() {
        val existing = setOf(Wall(4, 4, WallOrientation.HORIZONTAL))

        assertFalse(TestFixtures.walls.isStructurallyValid(existing, Wall(4, 4, WallOrientation.HORIZONTAL)))
        assertFalse(TestFixtures.walls.isStructurallyValid(existing, Wall(4, 3, WallOrientation.HORIZONTAL)))
        assertFalse(TestFixtures.walls.isStructurallyValid(existing, Wall(4, 5, WallOrientation.HORIZONTAL)))
        assertFalse(TestFixtures.walls.isStructurallyValid(existing, Wall(4, 4, WallOrientation.VERTICAL)))
    }

    @Test
    fun acceptsWallsThatMeetEndToEnd() {
        val existing = setOf(Wall(4, 2, WallOrientation.HORIZONTAL))

        assertTrue(TestFixtures.walls.isStructurallyValid(existing, Wall(4, 4, WallOrientation.HORIZONTAL)))
    }

    @Test
    fun rejectsWallThatClosesFinalRouteAcrossBoard() {
        val state = BoardState.initial().copy(
            walls = setOf(
                Wall(1, 0, WallOrientation.HORIZONTAL),
                Wall(1, 2, WallOrientation.HORIZONTAL),
                Wall(1, 4, WallOrientation.HORIZONTAL),
                Wall(0, 7, WallOrientation.VERTICAL),
            ),
        )

        assertFalse(
            TestFixtures.walls.isValid(
                state,
                Wall(1, 6, WallOrientation.HORIZONTAL),
            ),
        )
    }
}
