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
        // The grid starts inside the frame, so a tap on the frame itself floors to -1 and is
        // rejected by the range check below rather than snapping to the nearest tile.
        val column = floor((offset.x - geometry.padding) / geometry.step).toInt()
        val row = floor((offset.y - geometry.padding) / geometry.step).toInt()
        if (row !in 0 until Constants.Board.SIZE || column !in 0 until Constants.Board.SIZE) return null
        val position = Position(row, column)
        return position.takeIf { geometry.tileRect(it).contains(offset) }
    }

    fun wallAt(offset: Offset, geometry: BoardGeometry, orientation: WallOrientation): Wall? {
        if (offset.x !in 0f..geometry.boardSize || offset.y !in 0f..geometry.boardSize) return null
        return buildList {
            repeat(Constants.Board.WALL_GRID_SIZE) { row ->
                repeat(Constants.Board.WALL_GRID_SIZE) { column ->
                    add(Wall(row, column, orientation))
                }
            }
        }.minByOrNull { wall ->
            val center = geometry.wallRect(wall).center
            val horizontalWeight = if (orientation == WallOrientation.HORIZONTAL) 0.45f else 1f
            val verticalWeight = if (orientation == WallOrientation.VERTICAL) 0.45f else 1f
            val deltaX = (offset.x - center.x) * horizontalWeight
            val deltaY = (offset.y - center.y) * verticalWeight
            deltaX * deltaX + deltaY * deltaY
        }
    }
}
