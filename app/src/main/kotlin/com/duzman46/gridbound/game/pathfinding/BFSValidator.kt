package com.duzman46.gridbound.game.pathfinding

import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.board.BoardGraph
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import java.util.ArrayDeque
import javax.inject.Inject

class BFSValidator @Inject constructor(
    private val boardGraph: BoardGraph,
) {
    fun hasPath(start: Position, goalRow: Int, walls: Set<Wall>): Boolean {
        require(goalRow in 0 until Constants.Board.SIZE)
        val visited = Array(Constants.Board.SIZE) { BooleanArray(Constants.Board.SIZE) }
        val queue = ArrayDeque<Position>()
        visited[start.row][start.column] = true
        queue.addLast(start)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current.row == goalRow) return true
            boardGraph.neighbors(current, walls).forEach { neighbor ->
                if (!visited[neighbor.row][neighbor.column]) {
                    visited[neighbor.row][neighbor.column] = true
                    queue.addLast(neighbor)
                }
            }
        }
        return false
    }
}

