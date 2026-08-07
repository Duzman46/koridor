package com.duzman46.gridbound.presentation

import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.GameStatus
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.presentation.game.GameUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameUiStateTest {

    // --- Who won ----------------------------------------------------------------------

    @Test
    fun `a board that decided itself carries the result`() {
        val state = GameUiState(boardState = won(GameStatus.PLAYER_TWO_WON))
        assertEquals(PlayerId.PLAYER_TWO, state.winner)
    }

    @Test
    fun `a match the room ended still has a winner`() {
        // Resignations, walk-outs and clocks running down leave the position untouched, so
        // without this the screen would sit on a board nobody is going to move again.
        val state = online(onlineWinner = PlayerId.PLAYER_ONE)
        assertEquals(PlayerId.PLAYER_ONE, state.winner)
    }

    @Test
    fun `a match still being played has none`() {
        assertNull(online().winner)
    }

    // --- What leaving costs -----------------------------------------------------------

    @Test
    fun `walking out of a live online match costs it`() {
        assertTrue(online().leavingForfeits)
    }

    @Test
    fun `walking out of a bot or hot-seat game costs nothing`() {
        assertFalse(GameUiState(mode = GameMode.VS_AI).leavingForfeits)
        assertFalse(GameUiState(mode = GameMode.LOCAL_TWO_PLAYER).leavingForfeits)
    }

    @Test
    fun `a room still waiting for an opponent is nobody's match to lose`() {
        // Nothing has started, so there is no rival to award it to and the room is simply
        // released. isOnlineConnected is what tells the two apart.
        assertFalse(online(connected = false).leavingForfeits)
    }

    @Test
    fun `a match that is already over cannot be forfeited again`() {
        assertFalse(online(onlineWinner = PlayerId.PLAYER_TWO).leavingForfeits)
        assertFalse(online().copy(boardState = won(GameStatus.PLAYER_ONE_WON)).leavingForfeits)
    }

    private fun online(connected: Boolean = true, onlineWinner: PlayerId? = null) = GameUiState(
        mode = GameMode.ONLINE,
        isOnlineConnected = connected,
        onlineWinner = onlineWinner,
    )

    private fun won(status: GameStatus) = BoardState.initial().copy(status = status)
}
