package com.duzman46.gridbound.game.board

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation
import javax.inject.Inject

data class BoardPalette(
    val background: Color,
    val tile: Color,
    val tileAlternate: Color,
    val goalOne: Color,
    val goalTwo: Color,
    val valid: Color,
    val invalid: Color,
    val wall: Color,
    val playerOne: Color,
    val playerTwo: Color,
    val selection: Color,
)

class CanvasRenderer @Inject constructor() {
    fun draw(
        scope: DrawScope,
        geometry: BoardGeometry,
        state: BoardState,
        palette: BoardPalette,
        validMoves: Set<Position>,
        validWalls: Set<Wall>,
        wallOrientation: WallOrientation,
        pendingWall: Wall?,
        invalidWall: Wall?,
        recentWall: Wall?,
        recentWallProgress: Float,
        playerOneRow: Float,
        playerOneColumn: Float,
        playerTwoRow: Float,
        playerTwoColumn: Float,
        selected: Boolean,
    ) = with(scope) {
        drawRoundRect(
            color = palette.background,
            cornerRadius = CornerRadius(geometry.tileSize * Constants.Board.TILE_CORNER_RADIUS_RATIO),
        )
        for (row in 0 until Constants.Board.SIZE) {
            for (column in 0 until Constants.Board.SIZE) {
                val position = Position(row, column)
                val rect = geometry.tileRect(position)
                val base = if ((row + column) % 2 == 0) palette.tile else palette.tileAlternate
                val color = when (row) {
                    PlayerId.PLAYER_ONE.goalRow -> blend(base, palette.goalOne, 0.2f)
                    PlayerId.PLAYER_TWO.goalRow -> blend(base, palette.goalTwo, 0.2f)
                    else -> base
                }
                drawRoundRect(
                    color = color,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = CornerRadius(geometry.tileSize * Constants.Board.TILE_CORNER_RADIUS_RATIO),
                )
                if (position in validMoves) {
                    drawCircle(
                        color = palette.valid.copy(alpha = 0.78f),
                        radius = geometry.tileSize * 0.16f,
                        center = rect.center,
                    )
                }
            }
        }

        validWalls.asSequence()
            .filter { it.orientation == wallOrientation }
            .forEach { wall -> drawWall(geometry, wall, palette.valid.copy(alpha = 0.24f), 1f) }

        state.walls.forEach { wall ->
            val progress = if (wall == recentWall) recentWallProgress else 1f
            drawWall(geometry, wall, palette.wall, progress)
        }
        pendingWall?.let { wall ->
            drawWallHighlight(geometry, wall, palette.selection)
            drawWall(geometry, wall, palette.valid, 1f)
        }
        invalidWall?.let { drawWall(geometry, it, palette.invalid.copy(alpha = 0.9f), 1f) }

        drawPawn(
            geometry = geometry,
            row = playerOneRow,
            column = playerOneColumn,
            color = palette.playerOne,
            selected = selected && state.currentPlayer == PlayerId.PLAYER_ONE,
            selectionColor = palette.selection,
        )
        drawPawn(
            geometry = geometry,
            row = playerTwoRow,
            column = playerTwoColumn,
            color = palette.playerTwo,
            selected = selected && state.currentPlayer == PlayerId.PLAYER_TWO,
            selectionColor = palette.selection,
        )
    }

    private fun DrawScope.drawWallHighlight(geometry: BoardGeometry, wall: Wall, color: Color) {
        val rect = geometry.wallRect(wall)
        val padding = geometry.wallThickness * 0.72f
        drawRoundRect(
            color = color.copy(alpha = 0.32f),
            topLeft = Offset(rect.left - padding, rect.top - padding),
            size = Size(rect.width + padding * 2f, rect.height + padding * 2f),
            cornerRadius = CornerRadius(geometry.wallThickness),
        )
    }

    private fun DrawScope.drawWall(geometry: BoardGeometry, wall: Wall, color: Color, progress: Float) {
        val rect = geometry.wallRect(wall)
        val clamped = progress.coerceIn(0f, 1f)
        val scaledSize = Size(rect.width * clamped, rect.height * clamped)
        val topLeft = Offset(
            rect.center.x - scaledSize.width / 2f,
            rect.center.y - scaledSize.height / 2f,
        )
        drawRoundRect(
            color = color.copy(alpha = color.alpha * clamped),
            topLeft = topLeft,
            size = scaledSize,
            cornerRadius = CornerRadius(geometry.wallThickness / 2f),
        )
    }

    private fun DrawScope.drawPawn(
        geometry: BoardGeometry,
        row: Float,
        column: Float,
        color: Color,
        selected: Boolean,
        selectionColor: Color,
    ) {
        val center = geometry.pawnCenter(row, column)
        val radius = geometry.tileSize * Constants.Board.PAWN_RADIUS_RATIO
        if (selected) {
            drawCircle(selectionColor.copy(alpha = 0.7f), radius * 1.34f, center)
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.8f), color, color.copy(alpha = 0.74f)),
                center = center - Offset(radius * 0.28f, radius * 0.28f),
                radius = radius * 1.4f,
            ),
            radius = radius,
            center = center,
        )
        drawCircle(Color.White.copy(alpha = 0.72f), radius * 0.17f, center - Offset(radius * 0.28f, radius * 0.28f))
    }

    private fun blend(first: Color, second: Color, fraction: Float): Color = Color(
        red = first.red + (second.red - first.red) * fraction,
        green = first.green + (second.green - first.green) * fraction,
        blue = first.blue + (second.blue - first.blue) * fraction,
        alpha = first.alpha,
    )
}
