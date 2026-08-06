package com.duzman46.gridbound.ui

import com.duzman46.gridbound.game.TestFixtures
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.ui.components.home.HomePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Guards the board on the home screen.
 *
 * It is drawn by the production renderer from a hand-authored position, which means it can
 * drift into something the rules would never allow — a wall crossing another, a player with
 * no route home, a wall count that does not match what the two players have spent. Any of
 * those is a picture of a game that cannot exist, on the first screen anyone sees.
 */
class HomePositionTest {

    private val graph = TestFixtures.graph
    private val bfs = TestFixtures.bfs
    private val aStar = TestFixtures.aStar
    private val validator = TestFixtures.walls

    @Test
    fun `every wall could legally have been placed`() {
        // Replayed one at a time: a wall that overlaps or crosses an earlier one would be
        // refused in a real match, so it must be refused here too.
        val placed = mutableSetOf<Wall>()
        HomePosition.STATE.walls.forEach { wall ->
            assertTrue(
                "$wall cannot legally sit alongside $placed",
                validator.isStructurallyValid(placed, wall),
            )
            placed += wall
        }
    }

    @Test
    fun `neither player is trapped`() {
        PlayerId.entries.forEach { playerId ->
            assertTrue(
                "player $playerId has no route home on the home screen board",
                bfs.hasPath(
                    HomePosition.STATE.player(playerId).position,
                    playerId.goalRow,
                    HomePosition.STATE.walls,
                ),
            )
        }
    }

    @Test
    fun `the walls on the board match what the players have spent`() {
        val spent = HomePosition.STATE.players.values.sumOf { 10 - it.wallsRemaining }
        assertEquals(
            "the wall rack would be lying about how many walls are in play",
            HomePosition.wallsPlaced,
            spent,
        )
    }

    @Test
    fun `the drawn route is the shortest way home and every step of it is legal`() {
        val walls = HomePosition.STATE.walls
        val start = HomePosition.STATE.player(PlayerId.PLAYER_ONE).position
        assertEquals("the route must start under the pawn", start, HomePosition.ROUTE.first())
        assertEquals("the route must end on the goal row", PlayerId.PLAYER_ONE.goalRow, HomePosition.ROUTE.last().row)

        val steps = expandCorners(HomePosition.ROUTE)
        steps.zipWithNext { from, to ->
            assertTrue(
                "the drawn route walks through a wall between $from and $to",
                graph.canTraverse(from, to, walls),
            )
        }

        val length = steps.size - 1
        assertEquals(
            "the drawn route is not the shortest one, so the picture would be teaching a bad move",
            aStar.distance(start, PlayerId.PLAYER_ONE.goalRow, walls),
            length,
        )
        // The point of the picture: the walls force a detour, so the route is longer than the
        // straight run down the board would be.
        assertTrue(
            "the walls do not actually cost blue anything, so the board says nothing",
            length > abs(start.row - PlayerId.PLAYER_ONE.goalRow),
        )
    }

    /** Turns the corner list into the single steps it implies. */
    private fun expandCorners(corners: List<Position>): List<Position> {
        val steps = mutableListOf(corners.first())
        corners.zipWithNext { from, to ->
            assertTrue(
                "route segment $from -> $to is diagonal; corners must be axis-aligned",
                from.row == to.row || from.column == to.column,
            )
            val rowStep = to.row.compareTo(from.row)
            val columnStep = to.column.compareTo(from.column)
            var current = from
            while (current != to) {
                current = Position(current.row + rowStep, current.column + columnStep)
                steps += current
            }
        }
        return steps
    }
}
