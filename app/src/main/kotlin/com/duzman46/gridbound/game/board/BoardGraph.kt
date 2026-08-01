package com.duzman46.gridbound.game.board

import com.duzman46.gridbound.game.models.Direction
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation
import javax.inject.Inject

class BoardGraph @Inject constructor() {
    fun neighbors(position: Position, walls: Set<Wall>): List<Position> =
        Direction.entries.mapNotNull { direction ->
            position.offsetOrNull(direction.rowDelta, direction.columnDelta)
                ?.takeIf { target -> canTraverse(position, target, walls) }
        }

    fun canTraverse(from: Position, to: Position, walls: Set<Wall>): Boolean {
        val rowDelta = to.row - from.row
        val columnDelta = to.column - from.column
        if (kotlin.math.abs(rowDelta) + kotlin.math.abs(columnDelta) != 1) return false

        return when {
            rowDelta != 0 -> !isVerticalTransitionBlocked(from, to, walls)
            else -> !isHorizontalTransitionBlocked(from, to, walls)
        }
    }

    private fun isVerticalTransitionBlocked(from: Position, to: Position, walls: Set<Wall>): Boolean {
        val boundaryRow = minOf(from.row, to.row)
        val column = from.column
        return walls.any { wall ->
            wall.orientation == WallOrientation.HORIZONTAL &&
                wall.row == boundaryRow &&
                (wall.column == column || wall.column + 1 == column)
        }
    }

    private fun isHorizontalTransitionBlocked(from: Position, to: Position, walls: Set<Wall>): Boolean {
        val boundaryColumn = minOf(from.column, to.column)
        val row = from.row
        return walls.any { wall ->
            wall.orientation == WallOrientation.VERTICAL &&
                wall.column == boundaryColumn &&
                (wall.row == row || wall.row + 1 == row)
        }
    }
}

