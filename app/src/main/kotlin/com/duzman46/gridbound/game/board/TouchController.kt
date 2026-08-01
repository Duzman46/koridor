package com.duzman46.gridbound.game.board

import androidx.compose.ui.geometry.Offset
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation
import javax.inject.Inject
import kotlin.math.floor

class TouchController @Inject constructor() {
    fun tileAt(offset: Offset, geometry: BoardGeometry): Position? {
        if (offset.x !in 0f..geometry.boardSize || offset.y !in 0f..geometry.boardSize) return null
        val column = floor(offset.x / geometry.step).toInt()
        val row = floor(offset.y / geometry.step).toInt()
        if (row !in 0 until Constants.Board.SIZE || column !in 0 until Constants.Board.SIZE) return null
        val position = Position(row, column)
        return position.takeIf { geometry.tileRect(it).contains(offset) }
    }

    fun wallAt(offset: Offset, geometry: BoardGeometry, orientation: WallOrientation): Wall? {
        if (offset.x !in 0f..geometry.boardSize || offset.y !in 0f..geometry.boardSize) return null
        val row = floor(offset.y / geometry.step).toInt().coerceIn(0, Constants.Board.WALL_GRID_SIZE - 1)
        val column = floor(offset.x / geometry.step).toInt().coerceIn(0, Constants.Board.WALL_GRID_SIZE - 1)
        return Wall(row, column, orientation)
    }
}

