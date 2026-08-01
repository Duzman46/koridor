package com.duzman46.gridbound.game.ai

import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.PlayerId
import kotlin.random.Random

class EasyAI(
    private val actionGenerator: AIActionGenerator,
    private val random: Random,
) : AIEngine {
    override fun chooseAction(state: BoardState, playerId: PlayerId): GameAction {
        require(state.currentPlayer == playerId)
        val pawnActions = actionGenerator.pawnActions(state)
        pawnActions.firstOrNull { action ->
            action.target.row == playerId.goalRow
        }?.let { return it }

        val useWall = state.player(playerId).wallsRemaining > 0 &&
            random.nextDouble() < Constants.Ai.EASY_WALL_PROBABILITY
        if (useWall) {
            val wallActions = actionGenerator.allValidActions(state).filterIsInstance<GameAction.PlaceWall>()
            if (wallActions.isNotEmpty()) return wallActions.random(random)
        }
        return pawnActions.ifEmpty {
            actionGenerator.allValidActions(state)
        }.random(random)
    }
}
