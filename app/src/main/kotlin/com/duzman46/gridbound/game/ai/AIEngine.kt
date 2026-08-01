package com.duzman46.gridbound.game.ai

import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.PlayerId

interface AIEngine {
    fun chooseAction(state: BoardState, playerId: PlayerId = state.currentPlayer): GameAction
}

