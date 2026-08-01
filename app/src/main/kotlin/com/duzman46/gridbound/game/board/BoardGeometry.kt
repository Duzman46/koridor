package com.duzman46.gridbound.game.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation

data class BoardGeometry(val boardSize: Float) {
    val tileSize: Float = boardSize /
        (Constants.Board.SIZE + Constants.Board.GAP_RATIO * (Constants.Board.SIZE - 1))
    val gap: Float = tileSize * Constants.Board.GAP_RATIO
    val step: Float = tileSize + gap
    val wallThickness: Float = gap * Constants.Board.WALL_THICKNESS_RATIO

    fun tileRect(position: Position): Rect {
        val left = position.column * step
        val top = position.row * step
        return Rect(left, top, left + tileSize, top + tileSize)
    }

    fun pawnCenter(row: Float, column: Float): Offset = Offset(
        x = column * step + tileSize / 2f,
        y = row * step + tileSize / 2f,
    )

    fun wallRect(wall: Wall): Rect = when (wall.orientation) {
        WallOrientation.HORIZONTAL -> {
            val left = wall.column * step
            val top = wall.row * step + tileSize + (gap - wallThickness) / 2f
            Rect(left, top, left + tileSize * 2f + gap, top + wallThickness)
        }

        WallOrientation.VERTICAL -> {
            val left = wall.column * step + tileSize + (gap - wallThickness) / 2f
            val top = wall.row * step
            Rect(left, top, left + wallThickness, top + tileSize * 2f + gap)
        }
    }
}

