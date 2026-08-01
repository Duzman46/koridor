package com.duzman46.gridbound.game.rules

import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.InvalidActionReason
import javax.inject.Inject

class RuleEngine @Inject constructor(
    private val moveValidator: MoveValidator,
    private val wallValidator: WallValidator,
) {
    fun validate(state: BoardState, action: GameAction): InvalidActionReason? = when (action) {
        is GameAction.MovePawn ->
            if (moveValidator.isValid(state, action.target)) null else InvalidActionReason.INVALID_MOVE

        is GameAction.PlaceWall -> when {
            state.player(state.currentPlayer).wallsRemaining <= 0 -> InvalidActionReason.NO_WALLS_REMAINING
            wallValidator.isValid(state, action.wall) -> null
            else -> InvalidActionReason.INVALID_WALL
        }
    }
}

