package com.duzman46.gridbound.game

import com.duzman46.gridbound.game.ai.AIActionGenerator
import com.duzman46.gridbound.game.ai.EasyAI
import com.duzman46.gridbound.game.ai.HardAI
import com.duzman46.gridbound.game.ai.MediumAI
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
        val ai = HardAI(generator, TestFixtures.engine, TestFixtures.aStar)

        val action = ai.chooseAction(state, PlayerId.PLAYER_TWO)

        assertEquals(GameAction.MovePawn(Position(8, 4)), action)
    }

    @Test
    fun hardReturnsValidActionOnOrdinaryBoard() {
        val state = TestFixtures.state(
            playerOne = Position(8, 4),
            playerTwo = Position(0, 4),
            current = PlayerId.PLAYER_TWO,
        )
        val ai = HardAI(generator, TestFixtures.engine, TestFixtures.aStar)

        val action = ai.chooseAction(state, PlayerId.PLAYER_TWO)

        assertTrue(TestFixtures.engine.perform(state, action) is ActionResult.Success)
    }
}
