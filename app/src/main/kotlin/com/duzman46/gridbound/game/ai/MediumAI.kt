package com.duzman46.gridbound.game.ai

import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.engine.GameEngine
import com.duzman46.gridbound.game.models.ActionResult
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.pathfinding.AStarPathFinder
import javax.inject.Inject

class MediumAI @Inject constructor(
    private val actionGenerator: AIActionGenerator,
    private val gameEngine: GameEngine,
    private val pathFinder: AStarPathFinder,
) : AIEngine {
    override fun chooseAction(state: BoardState, playerId: PlayerId): GameAction {
        require(state.currentPlayer == playerId)
        val pawnActions = actionGenerator.pawnActions(state)
        pawnActions.firstOrNull { it.target.row == playerId.goalRow }?.let { return it }

        val ownBefore = distance(state, playerId)
        val opponentBefore = distance(state, playerId.opponent)
        val bestWall = actionGenerator.strategicActions(state, Constants.Ai.MEDIUM_MAX_WALL_CANDIDATES)
            .filterIsInstance<GameAction.PlaceWall>()
            .mapNotNull { action ->
                val next = successfulState(state, action) ?: return@mapNotNull null
                val opponentDelay = distance(next, playerId.opponent) - opponentBefore
                val ownDelay = distance(next, playerId) - ownBefore
                action to (opponentDelay * Constants.Ai.OPPONENT_DISTANCE_WEIGHT -
                    ownDelay * Constants.Ai.OWN_DISTANCE_WEIGHT)
            }
            .maxByOrNull { it.second }

        if (bestWall != null && bestWall.second >= Constants.Ai.MEDIUM_WALL_THRESHOLD) {
            return bestWall.first
        }
        return pawnActions.minWithOrNull(
            compareBy<GameAction.MovePawn> { action ->
                pathFinder.distance(action.target, playerId.goalRow, state.walls)
            }.thenBy { it.target.column },
        ) ?: actionGenerator.allValidActions(state).first()
    }

    private fun distance(state: BoardState, playerId: PlayerId): Int {
        val player = state.player(playerId)
        return pathFinder.distance(player.position, playerId.goalRow, state.walls)
    }

    private fun successfulState(state: BoardState, action: GameAction): BoardState? =
        (gameEngine.perform(state, action) as? ActionResult.Success)?.state
}

