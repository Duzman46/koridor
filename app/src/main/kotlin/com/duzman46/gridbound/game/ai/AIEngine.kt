package com.duzman46.gridbound.game.ai

import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.PlayerId

interface AIEngine {
    fun chooseAction(state: BoardState, playerId: PlayerId = state.currentPlayer): GameAction

    /**
     * Tells a search that is already running that nobody wants its answer.
     *
     * [chooseAction] is called from `Dispatchers.Default` and cancelling that coroutine does not
     * stop it: cancellation is cooperative and a search is a CPU loop that never suspends, so it
     * runs to its own deadline whatever the caller does. A player who restarts a match against a
     * thinking bot would otherwise wait out the abandoned search before the new one could even
     * begin, and could stack several by pressing again.
     *
     * Safe to call at any time, from any thread, including when nothing is searching. The default
     * does nothing, which is correct for the engines that answer in microseconds.
     */
    fun abandonSearch() = Unit
}

