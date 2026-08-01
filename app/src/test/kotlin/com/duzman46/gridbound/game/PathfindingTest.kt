package com.duzman46.gridbound.game

import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation
import com.duzman46.gridbound.game.pathfinding.PathResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathfindingTest {
    @Test
    fun findsStraightShortestPathOnOpenBoard() {
        val result = TestFixtures.aStar.findPath(
            PlayerId.PLAYER_ONE.startPosition,
            PlayerId.PLAYER_ONE.goalRow,
            emptySet(),
        )

        assertTrue(result is PathResult.Found)
        assertEquals(8, (result as PathResult.Found).distance)
    }

    @Test
    fun routesAroundWallAndMatchesExpectedDistance() {
        val walls = setOf(Wall(6, 3, WallOrientation.HORIZONTAL))

        val result = TestFixtures.aStar.findPath(Position(8, 4), 0, walls)

        assertTrue(result is PathResult.Found)
        assertEquals(9, (result as PathResult.Found).distance)
        assertTrue(TestFixtures.bfs.hasPath(Position(8, 4), 0, walls))
    }

    @Test
    fun bfsRejectsCompletelyClosedBoard() {
        val walls = setOf(
            Wall(1, 0, WallOrientation.HORIZONTAL),
            Wall(1, 2, WallOrientation.HORIZONTAL),
            Wall(1, 4, WallOrientation.HORIZONTAL),
            Wall(1, 6, WallOrientation.HORIZONTAL),
            Wall(0, 7, WallOrientation.VERTICAL),
        )

        assertTrue(!TestFixtures.bfs.hasPath(Position(0, 4), 8, walls))
    }
}
