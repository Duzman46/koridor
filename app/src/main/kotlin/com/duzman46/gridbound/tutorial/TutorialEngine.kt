package com.duzman46.gridbound.tutorial

import com.duzman46.gridbound.game.engine.GameEngine
import com.duzman46.gridbound.game.models.ActionResult
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.InvalidActionReason
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import javax.inject.Inject

/** How the tutorial reacts to something the player just did. */
sealed interface TutorialOutcome {
    /** Correct. [board] is the position after the real rules engine applied the action. */
    data class Advance(val board: BoardState) : TutorialOutcome

    /**
     * Correct, and the point was that the rules engine refused the action — used by the
     * lesson showing that a wall can never seal a player in.
     */
    data object AdvanceOnRejection : TutorialOutcome

    /** The action was not what the step asked for; nothing changes and a hint is shown. */
    data object Hint : TutorialOutcome

    /** A pawn selection that reveals the hint, without completing the step. */
    data class Select(val selected: Boolean) : TutorialOutcome
}

/**
 * Decides whether a tutorial step has been satisfied.
 *
 * Actions are put through the production [GameEngine] rather than a simplified copy, so what
 * the player learns here is exactly what the game will do in a real match.
 */
class TutorialEngine @Inject constructor(
    private val gameEngine: GameEngine,
) {
    fun onTileTapped(step: TutorialStep, board: BoardState, position: Position): TutorialOutcome {
        val pawn = board.player(board.currentPlayer).position
        if (position == pawn) {
            return if (step.goal is TutorialGoal.SelectPawn) {
                TutorialOutcome.Advance(board)
            } else {
                TutorialOutcome.Select(true)
            }
        }
        val goal = step.goal
        if (goal !is TutorialGoal.MoveTo || goal.target != position) return TutorialOutcome.Hint
        return when (val result = gameEngine.perform(board, GameAction.MovePawn(position))) {
            is ActionResult.Success -> TutorialOutcome.Advance(result.state)
            is ActionResult.Invalid -> TutorialOutcome.Hint
        }
    }

    fun onWallTapped(step: TutorialStep, board: BoardState, wall: Wall): TutorialOutcome {
        return when (val goal = step.goal) {
            is TutorialGoal.PlaceWall -> {
                if (goal.wall != wall) return TutorialOutcome.Hint
                when (val result = gameEngine.perform(board, GameAction.PlaceWall(wall))) {
                    is ActionResult.Success -> TutorialOutcome.Advance(result.state)
                    is ActionResult.Invalid -> TutorialOutcome.Hint
                }
            }

            is TutorialGoal.AttemptSealingWall -> {
                if (goal.wall != wall) return TutorialOutcome.Hint
                val result = gameEngine.perform(board, GameAction.PlaceWall(wall))
                // The lesson only lands if the engine refuses it for the right reason.
                val sealed = result is ActionResult.Invalid &&
                    result.reason == InvalidActionReason.INVALID_WALL
                if (sealed) TutorialOutcome.AdvanceOnRejection else TutorialOutcome.Hint
            }

            else -> TutorialOutcome.Hint
        }
    }
}
