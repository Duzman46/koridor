package com.duzman46.gridbound.game.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation

/**
 * Maps board coordinates to pixels inside a square of [boardSize].
 *
 * The single source of truth for where anything is: the renderer draws through it and the
 * touch controller reads through it, so a tap always lands on the tile that was drawn under
 * the finger. Nothing here scales with the screen except [boardSize] — the proportions are
 * fixed, so the board looks the same on a small phone and a tablet.
 */
data class BoardGeometry(val boardSize: Float) {
    /** The frame the grid sits inside, as a physical board sits in its tray. */
    val padding: Float = boardSize * Constants.Board.FRAME_RATIO

    private val gridSize: Float = boardSize - padding * 2f

    val tileSize: Float = gridSize /
        (Constants.Board.SIZE + Constants.Board.GAP_RATIO * (Constants.Board.SIZE - 1))

    /** The channel between two tiles — the only place a wall can go. */
    val gap: Float = tileSize * Constants.Board.GAP_RATIO
    val step: Float = tileSize + gap
    val wallThickness: Float = gap * Constants.Board.WALL_THICKNESS_RATIO

    fun tileRect(position: Position): Rect {
        val left = padding + position.column * step
        val top = padding + position.row * step
        return Rect(left, top, left + tileSize, top + tileSize)
    }

    fun pawnCenter(row: Float, column: Float): Offset = Offset(
        x = padding + column * step + tileSize / 2f,
        y = padding + row * step + tileSize / 2f,
    )

    /** Where a wall's two-tile span sits, centred in the channel it drops into. */
    fun wallRect(wall: Wall): Rect = when (wall.orientation) {
        WallOrientation.HORIZONTAL -> {
            val left = padding + wall.column * step
            val top = padding + wall.row * step + tileSize + (gap - wallThickness) / 2f
            Rect(left, top, left + tileSize * 2f + gap, top + wallThickness)
        }

        WallOrientation.VERTICAL -> {
            val left = padding + wall.column * step + tileSize + (gap - wallThickness) / 2f
            val top = padding + wall.row * step
            Rect(left, top, left + wallThickness, top + tileSize * 2f + gap)
        }
    }
}
