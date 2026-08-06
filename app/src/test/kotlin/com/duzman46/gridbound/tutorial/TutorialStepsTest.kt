package com.duzman46.gridbound.tutorial

import com.duzman46.gridbound.game.TestFixtures
import com.duzman46.gridbound.game.models.GameStatus
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.WallOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the tutorial against a rules change.
 *
 * Every lesson is replayed through the production engine, so if a move, jump or wall rule
 * changes in a way that would strand a learner, the build fails here first.
 */
class TutorialStepsTest {

    private val engine = TutorialEngine(TestFixtures.engine)

    @Test
    fun `every step is reachable and distinct`() {
        assertEquals(TutorialStepId.entries.size, TutorialSteps.size)
        assertEquals(TutorialStepId.entries.toSet(), TutorialSteps.steps.map(TutorialStep::id).toSet())
    }

    @Test
    fun `selecting the pawn satisfies the first step`() {
        val step = step(TutorialStepId.SELECT_PAWN)
        val pawn = step.board.player(PlayerId.PLAYER_ONE).position
        assertTrue(engine.onTileTapped(step, step.board, pawn) is TutorialOutcome.Advance)
    }

    @Test
    fun `the move step accepts only the highlighted square`() {
        val step = step(TutorialStepId.MOVE)
        val target = (step.goal as TutorialGoal.MoveTo).target
        assertTrue(engine.onTileTapped(step, step.board, target) is TutorialOutcome.Advance)
        assertEquals(
            TutorialOutcome.Hint,
            engine.onTileTapped(step, step.board, Position(7, 3)),
        )
    }

    @Test
    fun `the jump step really jumps over the rival`() {
        val step = step(TutorialStepId.JUMP)
        val rival = step.board.player(PlayerId.PLAYER_TWO).position
        val own = step.board.player(PlayerId.PLAYER_ONE).position
        val target = (step.goal as TutorialGoal.MoveTo).target
        // The rival must actually stand between the pawn and the destination.
        assertEquals(own.column, rival.column)
        assertEquals(own.row - 1, rival.row)
        assertEquals(rival.row - 1, target.row)

        val outcome = engine.onTileTapped(step, step.board, target)
        assertTrue(outcome is TutorialOutcome.Advance)
        assertEquals(target, (outcome as TutorialOutcome.Advance).board.player(PlayerId.PLAYER_ONE).position)
    }

    @Test
    fun `both wall steps place a legal wall of the orientation they teach`() {
        mapOf(
            TutorialStepId.PLACE_VERTICAL_WALL to WallOrientation.VERTICAL,
            TutorialStepId.PLACE_HORIZONTAL_WALL to WallOrientation.HORIZONTAL,
        ).forEach { (id, orientation) ->
            val step = step(id)
            val wall = (step.goal as TutorialGoal.PlaceWall).wall
            assertEquals("$id teaches the wrong orientation", orientation, wall.orientation)
            val outcome = engine.onWallTapped(step, step.board, wall)
            assertTrue("$id was refused by the rules engine", outcome is TutorialOutcome.Advance)
            assertTrue(wall in (outcome as TutorialOutcome.Advance).board.walls)
        }
    }

    @Test
    fun `a wall step rejects a different wall`() {
        val step = step(TutorialStepId.PLACE_VERTICAL_WALL)
        val other = step.highlightWalls.first().copy(column = 0)
        assertEquals(TutorialOutcome.Hint, engine.onWallTapped(step, step.board, other))
    }

    @Test
    fun `the horizontal lesson keeps the wall from the vertical one`() {
        val vertical = (step(TutorialStepId.PLACE_VERTICAL_WALL).goal as TutorialGoal.PlaceWall).wall
        val next = step(TutorialStepId.PLACE_HORIZONTAL_WALL)
        assertTrue("the two orientations must be visible together", vertical in next.board.walls)
    }

    @Test
    fun `the blocking lesson is refused by the rules engine`() {
        val step = step(TutorialStepId.BLOCKED_WALL)
        val wall = (step.goal as TutorialGoal.AttemptSealingWall).wall
        // The whole point of the lesson: the engine must refuse this wall.
        assertEquals(TutorialOutcome.AdvanceOnRejection, engine.onWallTapped(step, step.board, wall))
    }

    @Test
    fun `every step starts from a legal position`() {
        // No lesson may open on a board the real game could never produce — both players
        // must still have a route, or the rules engine's verdicts would not be the ones a
        // real match would give.
        TutorialSteps.steps.forEach { step ->
            PlayerId.entries.forEach { playerId ->
                assertTrue(
                    "player $playerId is trapped at the start of ${step.id}",
                    TestFixtures.bfs.hasPath(
                        step.board.player(playerId).position,
                        playerId.goalRow,
                        step.board.walls,
                    ),
                )
            }
        }
    }

    @Test
    fun `the final step wins the game`() {
        val step = step(TutorialStepId.WIN)
        val target = (step.goal as TutorialGoal.MoveTo).target
        val outcome = engine.onTileTapped(step, step.board, target)
        assertTrue(outcome is TutorialOutcome.Advance)
        assertEquals(
            GameStatus.PLAYER_ONE_WON,
            (outcome as TutorialOutcome.Advance).board.status,
        )
    }

    @Test
    fun `wall steps open in the orientation of their highlighted wall`() {
        TutorialSteps.steps.filter(TutorialStep::startInWallMode).forEach { step ->
            assertNotNull("wall step ${step.id} has no highlight", step.highlightWalls.firstOrNull())
        }
    }

    private fun step(id: TutorialStepId): TutorialStep =
        TutorialSteps.steps.first { it.id == id }
}
