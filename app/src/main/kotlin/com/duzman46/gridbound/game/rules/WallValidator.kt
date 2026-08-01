package com.duzman46.gridbound.game.rules

import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation
import com.duzman46.gridbound.game.pathfinding.BFSValidator
import javax.inject.Inject
import kotlin.math.abs

class WallValidator @Inject constructor(
    private val bfsValidator: BFSValidator,
) {
    fun isValid(state: BoardState, wall: Wall): Boolean {
        if (state.player(state.currentPlayer).wallsRemaining <= 0) return false
        if (!isStructurallyValid(state.walls, wall)) return false
        val proposedWalls = state.walls + wall
        return PlayerId.entries.all { playerId ->
            val player = state.player(playerId)
            bfsValidator.hasPath(player.position, playerId.goalRow, proposedWalls)
        }
    }

    fun validWalls(state: BoardState): Set<Wall> {
        if (state.player(state.currentPlayer).wallsRemaining <= 0) return emptySet()
        return buildSet {
            for (row in 0 until Constants.Board.WALL_GRID_SIZE) {
                for (column in 0 until Constants.Board.WALL_GRID_SIZE) {
                    WallOrientation.entries.forEach { orientation ->
                        val wall = Wall(row, column, orientation)
                        if (isValid(state, wall)) add(wall)
                    }
                }
            }
        }
    }

    fun isStructurallyValid(existing: Set<Wall>, candidate: Wall): Boolean = existing.none { wall ->
        when {
            wall.orientation != candidate.orientation ->
                wall.row == candidate.row && wall.column == candidate.column

            wall.orientation == WallOrientation.HORIZONTAL ->
                wall.row == candidate.row && abs(wall.column - candidate.column) <= 1

            else ->
                wall.column == candidate.column && abs(wall.row - candidate.row) <= 1
        }
    }
}

