package com.duzman46.gridbound.game.ai

import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation
import com.duzman46.gridbound.game.pathfinding.AStarPathFinder
import com.duzman46.gridbound.game.pathfinding.PathResult
import com.duzman46.gridbound.game.rules.MoveValidator
import com.duzman46.gridbound.game.rules.WallValidator
import javax.inject.Inject

class AIActionGenerator @Inject constructor(
    private val moveValidator: MoveValidator,
    private val wallValidator: WallValidator,
    private val pathFinder: AStarPathFinder,
) {
    fun pawnActions(state: BoardState): List<GameAction.MovePawn> =
        moveValidator.validMoves(state).map(GameAction::MovePawn)

    fun allValidActions(state: BoardState): List<GameAction> = buildList {
        addAll(pawnActions(state))
        if (state.player(state.currentPlayer).wallsRemaining > 0) {
            addAll(wallValidator.validWalls(state).map(GameAction::PlaceWall))
        }
    }

    fun strategicActions(
        state: BoardState,
        maxWallCandidates: Int = Constants.Ai.HARD_MAX_WALL_CANDIDATES,
    ): List<GameAction> = buildList {
        addAll(pawnActions(state))
        if (state.player(state.currentPlayer).wallsRemaining <= 0) return@buildList
        val candidates = LinkedHashSet<Wall>()
        addPathBlockingCandidates(state, state.currentPlayer.opponent, candidates)
        addPathBlockingCandidates(state, state.currentPlayer, candidates)
        addAll(
            candidates.asSequence()
                .filter { wallValidator.isValid(state, it) }
                .take(maxWallCandidates)
                .map(GameAction::PlaceWall),
        )
    }

    private fun addPathBlockingCandidates(
        state: BoardState,
        playerId: com.duzman46.gridbound.game.models.PlayerId,
        destination: MutableSet<Wall>,
    ) {
        val player = state.player(playerId)
        val result = pathFinder.findPath(player.position, playerId.goalRow, state.walls)
        if (result !is PathResult.Found) return
        result.path.zipWithNext().forEach { (from, to) ->
            wallsBlockingEdge(from, to).forEach(destination::add)
        }
    }

    private fun wallsBlockingEdge(from: Position, to: Position): List<Wall> {
        val walls = ArrayList<Wall>(2)
        if (from.row != to.row) {
            val row = minOf(from.row, to.row)
            listOf(from.column - 1, from.column).forEach { column ->
                if (row in 0 until Constants.Board.WALL_GRID_SIZE &&
                    column in 0 until Constants.Board.WALL_GRID_SIZE
                ) {
                    walls += Wall(row, column, WallOrientation.HORIZONTAL)
                }
            }
        } else {
            val column = minOf(from.column, to.column)
            listOf(from.row - 1, from.row).forEach { row ->
                if (row in 0 until Constants.Board.WALL_GRID_SIZE &&
                    column in 0 until Constants.Board.WALL_GRID_SIZE
                ) {
                    walls += Wall(row, column, WallOrientation.VERTICAL)
                }
            }
        }
        return walls
    }
}

