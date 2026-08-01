package com.duzman46.gridbound.game.rules

import com.duzman46.gridbound.game.board.BoardGraph
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.Direction
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.Position
import javax.inject.Inject

class MoveValidator @Inject constructor(
    private val boardGraph: BoardGraph,
) {
    fun validMoves(state: BoardState, playerId: PlayerId = state.currentPlayer): Set<Position> {
        val playerPosition = state.player(playerId).position
        val opponentPosition = state.player(playerId.opponent).position
        val valid = LinkedHashSet<Position>()

        Direction.entries.forEach { direction ->
            val adjacent = playerPosition.offsetOrNull(direction.rowDelta, direction.columnDelta)
                ?: return@forEach
            if (!boardGraph.canTraverse(playerPosition, adjacent, state.walls)) return@forEach

            if (adjacent != opponentPosition) {
                valid += adjacent
                return@forEach
            }

            val behind = opponentPosition.offsetOrNull(direction.rowDelta, direction.columnDelta)
            if (behind != null && boardGraph.canTraverse(opponentPosition, behind, state.walls)) {
                valid += behind
            } else {
                perpendicularDirections(direction).forEach { side ->
                    val diagonal = opponentPosition.offsetOrNull(side.rowDelta, side.columnDelta)
                    if (diagonal != null && boardGraph.canTraverse(opponentPosition, diagonal, state.walls)) {
                        valid += diagonal
                    }
                }
            }
        }
        return valid
    }

    fun isValid(state: BoardState, target: Position): Boolean = target in validMoves(state)

    private fun perpendicularDirections(direction: Direction): List<Direction> = when (direction) {
        Direction.UP, Direction.DOWN -> listOf(Direction.LEFT, Direction.RIGHT)
        Direction.LEFT, Direction.RIGHT -> listOf(Direction.UP, Direction.DOWN)
    }
}

