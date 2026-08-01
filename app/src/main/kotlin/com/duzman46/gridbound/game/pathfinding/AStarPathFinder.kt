package com.duzman46.gridbound.game.pathfinding

import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.board.BoardGraph
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import java.util.PriorityQueue
import javax.inject.Inject
import kotlin.math.abs

sealed interface PathResult {
    data class Found(val path: List<Position>) : PathResult {
        val distance: Int = path.size - 1
    }

    data object NoPath : PathResult
}

class AStarPathFinder @Inject constructor(
    private val boardGraph: BoardGraph,
) {
    private data class Node(val position: Position, val cost: Int, val heuristic: Int) {
        val total: Int = cost + heuristic
    }

    fun findPath(start: Position, goalRow: Int, walls: Set<Wall>): PathResult {
        require(goalRow in 0 until Constants.Board.SIZE)
        val costs = Array(Constants.Board.SIZE) { IntArray(Constants.Board.SIZE) { Int.MAX_VALUE } }
        val previous = HashMap<Position, Position>()
        val open = PriorityQueue(
            compareBy<Node> { it.total }
                .thenBy { it.heuristic }
                .thenBy { it.position.row }
                .thenBy { it.position.column },
        )
        costs[start.row][start.column] = 0
        open.add(Node(start, 0, heuristic(start, goalRow)))

        while (open.isNotEmpty()) {
            val current = open.remove()
            if (current.cost != costs[current.position.row][current.position.column]) continue
            if (current.position.row == goalRow) {
                return PathResult.Found(reconstruct(current.position, previous))
            }
            boardGraph.neighbors(current.position, walls).forEach { neighbor ->
                val newCost = current.cost + 1
                if (newCost < costs[neighbor.row][neighbor.column]) {
                    costs[neighbor.row][neighbor.column] = newCost
                    previous[neighbor] = current.position
                    open.add(Node(neighbor, newCost, heuristic(neighbor, goalRow)))
                }
            }
        }
        return PathResult.NoPath
    }

    fun distance(start: Position, goalRow: Int, walls: Set<Wall>): Int =
        when (val result = findPath(start, goalRow, walls)) {
            is PathResult.Found -> result.distance
            PathResult.NoPath -> Int.MAX_VALUE
        }

    private fun heuristic(position: Position, goalRow: Int): Int = abs(position.row - goalRow)

    private fun reconstruct(end: Position, previous: Map<Position, Position>): List<Position> {
        val reversed = ArrayList<Position>()
        var current: Position? = end
        while (current != null) {
            reversed += current
            current = previous[current]
        }
        reversed.reverse()
        return reversed
    }
}

