package com.duzman46.gridbound.game.rules

import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameStatus
import com.duzman46.gridbound.game.models.PlayerId
import javax.inject.Inject

class VictoryChecker @Inject constructor() {
    fun statusAfterMove(state: BoardState, playerId: PlayerId): GameStatus =
        if (state.player(playerId).position.row == playerId.goalRow) {
            if (playerId == PlayerId.PLAYER_ONE) GameStatus.PLAYER_ONE_WON else GameStatus.PLAYER_TWO_WON
        } else {
            GameStatus.IN_PROGRESS
        }
}

