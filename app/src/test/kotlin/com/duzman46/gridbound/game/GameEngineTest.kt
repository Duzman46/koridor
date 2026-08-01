package com.duzman46.gridbound.game

import com.duzman46.gridbound.game.models.ActionResult
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.GameStatus
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GameEngineTest {
    @Test
    fun successfulMoveUpdatesHistoryAndTurn() {
        val manager = TestFixtures.manager()

        val result = manager.perform(GameAction.MovePawn(Position(7, 4)))

        val state = (result as ActionResult.Success).state
        assertEquals(PlayerId.PLAYER_TWO, state.currentPlayer)
        assertEquals(2, state.turnNumber)
        assertEquals(1, state.history.size)
    }

    @Test
    fun invalidMoveLeavesStateUnchanged() {
        val manager = TestFixtures.manager()
        val before = manager.state

        manager.perform(GameAction.MovePawn(Position(0, 0)))

        assertEquals(before, manager.state)
        assertNull(manager.undo())
    }

    @Test
    fun winningMoveStopsTurnSwitch() {
        val state = TestFixtures.state(Position(1, 4), Position(8, 4))

        val result = TestFixtures.engine.perform(state, GameAction.MovePawn(Position(0, 4)))

        val finalState = (result as ActionResult.Success).state
        assertEquals(GameStatus.PLAYER_ONE_WON, finalState.status)
        assertEquals(PlayerId.PLAYER_ONE, finalState.currentPlayer)
    }

    @Test
    fun undoRestoresPreviousImmutableState() {
        val manager = TestFixtures.manager()
        val initial = manager.state
        manager.perform(GameAction.MovePawn(Position(7, 4)))

        val restored = manager.undo()

        assertNotNull(restored)
        assertEquals(initial, restored)
    }
}
