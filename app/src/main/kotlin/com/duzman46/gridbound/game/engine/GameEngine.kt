package com.duzman46.gridbound.game.engine

import com.duzman46.gridbound.game.models.ActionResult
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.GameStatus
import com.duzman46.gridbound.game.models.InvalidActionReason
import com.duzman46.gridbound.game.models.TurnRecord
import com.duzman46.gridbound.game.rules.RuleEngine
import com.duzman46.gridbound.game.rules.VictoryChecker
import javax.inject.Inject

class GameEngine @Inject constructor(
    private val ruleEngine: RuleEngine,
    private val victoryChecker: VictoryChecker,
    private val turnManager: TurnManager,
) {
    fun perform(state: BoardState, action: GameAction): ActionResult {
        if (state.status != GameStatus.IN_PROGRESS) {
            return ActionResult.Invalid(InvalidActionReason.GAME_FINISHED)
        }
        ruleEngine.validate(state, action)?.let { reason -> return ActionResult.Invalid(reason) }

        val actingPlayer = state.currentPlayer
        val updatedPlayers = state.players.toMutableMap()
        val updatedWalls = state.walls.toMutableSet()
        when (action) {
            is GameAction.MovePawn -> {
                updatedPlayers[actingPlayer] = state.player(actingPlayer).moveTo(action.target)
            }

            is GameAction.PlaceWall -> {
                updatedPlayers[actingPlayer] = state.player(actingPlayer).useWall()
                updatedWalls += action.wall
            }
        }

        val record = TurnRecord(state.turnNumber, actingPlayer, action)
        var next = state.copy(
            players = updatedPlayers,
            walls = updatedWalls,
            history = state.history + record,
        )
        if (action is GameAction.MovePawn) {
            next = next.copy(status = victoryChecker.statusAfterMove(next, actingPlayer))
        }
        return ActionResult.Success(turnManager.completeTurn(next))
    }
}

