package com.duzman46.gridbound.game

import com.duzman46.gridbound.game.ai.AIActionGenerator
import com.duzman46.gridbound.game.ai.EasyAI
import com.duzman46.gridbound.game.ai.MediumAI
import com.duzman46.gridbound.game.ai.SearchAI
import com.duzman46.gridbound.game.ai.search.SearchClock
import com.duzman46.gridbound.game.ai.search.SearchConfig
import com.duzman46.gridbound.game.models.ActionResult
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.Position
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AIEngineTest {
    private val generator = AIActionGenerator(TestFixtures.moves, TestFixtures.walls, TestFixtures.aStar)

    @Test
    fun easyAlwaysReturnsActionAcceptedByEngine() {
        val state = TestFixtures.state(
            playerOne = Position(8, 4),
            playerTwo = Position(0, 4),
            current = PlayerId.PLAYER_TWO,
        )
        val ai = EasyAI(generator, Random(7))

        val action = ai.chooseAction(state, PlayerId.PLAYER_TWO)

        assertTrue(TestFixtures.engine.perform(state, action) is ActionResult.Success)
    }

    @Test
    fun mediumTakesImmediateWinningMove() {
        val state = TestFixtures.state(
            playerOne = Position(1, 1),
            playerTwo = Position(7, 4),
            current = PlayerId.PLAYER_TWO,
        )
        val ai = MediumAI(generator, TestFixtures.engine, TestFixtures.aStar)

        val action = ai.chooseAction(state, PlayerId.PLAYER_TWO)

        assertEquals(GameAction.MovePawn(Position(8, 4)), action)
    }

    @Test
    fun hardTakesImmediateWinningMove() {
        val state = TestFixtures.state(
            playerOne = Position(1, 1),
            playerTwo = Position(7, 4),
            current = PlayerId.PLAYER_TWO,
        )

        val action = searchAI(SearchConfig.HARD, SearchClock.SYSTEM)
            .chooseAction(state, PlayerId.PLAYER_TWO)

        assertEquals(GameAction.MovePawn(Position(8, 4)), action)
    }

    @Test
    fun hardReturnsValidActionOnOrdinaryBoard() {
        val state = TestFixtures.state(
            playerOne = Position(8, 4),
            playerTwo = Position(0, 4),
            current = PlayerId.PLAYER_TWO,
        )

        val action = searchAI(SearchConfig.HARD, SearchClock.SYSTEM)
            .chooseAction(state, PlayerId.PLAYER_TWO)

        assertTrue(TestFixtures.engine.perform(state, action) is ActionResult.Success)
    }

    @Test
    fun expertTakesImmediateWinningMove() {
        val state = TestFixtures.state(
            playerOne = Position(1, 1),
            playerTwo = Position(7, 4),
            current = PlayerId.PLAYER_TWO,
        )

        val action = searchAI(boundedExpert(), STOPPED_CLOCK)
            .chooseAction(state, PlayerId.PLAYER_TWO)

        assertEquals(GameAction.MovePawn(Position(8, 4)), action)
    }

    @Test
    fun expertReturnsValidActionOnOrdinaryBoard() {
        val state = TestFixtures.state(
            playerOne = Position(8, 4),
            playerTwo = Position(0, 4),
            current = PlayerId.PLAYER_TWO,
        )

        val action = searchAI(boundedExpert(), STOPPED_CLOCK)
            .chooseAction(state, PlayerId.PLAYER_TWO)

        assertTrue(TestFixtures.engine.perform(state, action) is ActionResult.Success)
    }

    private fun searchAI(config: SearchConfig, clock: SearchClock): SearchAI =
        SearchAI(generator, TestFixtures.engine, TestFixtures.aStar, config, clock)

    /**
     * EXPERT driven by nodes rather than by the wall clock. A clock that never advances leaves the
     * node cap and `maxDepth` as the only stopping conditions, so the answer is the same on a busy
     * machine as on an idle one.
     */
    private fun boundedExpert(): SearchConfig =
        SearchConfig.EXPERT.copy(maxNodes = EXPERT_TEST_NODES)

    private companion object {
        const val EXPERT_TEST_NODES = 3_000L
        val STOPPED_CLOCK = SearchClock { 0L }
    }
}
