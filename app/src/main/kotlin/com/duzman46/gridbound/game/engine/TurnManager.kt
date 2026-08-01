package com.duzman46.gridbound.game.engine

import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameStatus
import javax.inject.Inject

class TurnManager @Inject constructor() {
    fun completeTurn(state: BoardState): BoardState =
        if (state.status == GameStatus.IN_PROGRESS) {
            state.copy(
                currentPlayer = state.currentPlayer.opponent,
                turnNumber = state.turnNumber + 1,
            )
        } else {
            state
        }
}

