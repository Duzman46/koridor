package com.duzman46.gridbound.game.board

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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

/**
 * Draws the board.
 *
 * Purely presentational — it is handed a finished [BoardState] and never decides anything
 * about the game. The physical board it is imitating has square tiles set into a frame with
 * open channels between them, and walls are pieces that drop into those channels; that is
 * what the layering below reproduces, in this order: frame, tiles, empty channels, wall
 * hints, placed walls, pawns.
 */
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
        drawFrame(geometry, palette)
        drawTiles(geometry, palette, validMoves)
        drawEmptyChannels(geometry, palette)

        // Slots the current orientation could legally take, so wall mode shows where a piece
        // may go before the player starts hunting for it.
        validWalls.asSequence()
            .filter { it.orientation == wallOrientation }
            .forEach { wall -> drawWall(geometry, wall, palette.valid.copy(alpha = 0.22f), 1f) }

        state.walls.forEach { wall ->
            val progress = if (wall == recentWall) recentWallProgress else 1f
            drawWall(geometry, wall, palette.wall, progress)
        }
        pendingWall?.let { wall ->
            drawWallHighlight(geometry, wall, palette.selection)
            drawWall(geometry, wall, palette.valid, 1f)
        }
        invalidWall?.let { wall ->
            drawWall(geometry, wall, palette.invalid.copy(alpha = 0.9f), 1f)
            // Colour alone would say nothing to a player who cannot separate red from the
            // wall colour, so the refusal also carries a shape.
            drawRefusalCross(geometry, wall, palette.invalid)
        }

        drawPawn(
            geometry = geometry,
            row = playerOneRow,
            column = playerOneColumn,
            color = palette.playerOne,
            crowned = false,
            selected = selected && state.currentPlayer == PlayerId.PLAYER_ONE,
            selectionColor = palette.selection,
        )
        drawPawn(
            geometry = geometry,
            row = playerTwoRow,
            column = playerTwoColumn,
            color = palette.playerTwo,
            crowned = true,
            selected = selected && state.currentPlayer == PlayerId.PLAYER_TWO,
            selectionColor = palette.selection,
        )
    }

    /** The board sits inside a frame, the way a physical one sits in its tray. */
    private fun DrawScope.drawFrame(geometry: BoardGeometry, palette: BoardPalette) {
        val side = geometry.boardSize
        val radius = CornerRadius(geometry.tileSize * Constants.Board.TILE_CORNER_RADIUS_RATIO * 1.6f)
        drawRoundRect(
            color = blend(palette.background, Color.Black, 0.2f),
            size = Size(side, side),
            cornerRadius = radius,
        )
        drawRoundRect(
            color = palette.background,
            topLeft = Offset(geometry.padding * 0.4f, geometry.padding * 0.4f),
            size = Size(side - geometry.padding * 0.8f, side - geometry.padding * 0.8f),
            cornerRadius = radius,
        )
    }

    private fun DrawScope.drawTiles(
        geometry: BoardGeometry,
        palette: BoardPalette,
        validMoves: Set<Position>,
    ) {
        val radius = CornerRadius(geometry.tileSize * Constants.Board.TILE_CORNER_RADIUS_RATIO)
        for (row in 0 until Constants.Board.SIZE) {
            for (column in 0 until Constants.Board.SIZE) {
                val position = Position(row, column)
                val rect = geometry.tileRect(position)
                val base = if ((row + column) % 2 == 0) palette.tile else palette.tileAlternate
                // The two goal rows are tinted hard enough to be read at a glance: which end
                // you are running for is the single most important fact on the board.
                val color = when (row) {
                    PlayerId.PLAYER_ONE.goalRow -> blend(base, palette.goalOne, 0.28f)
                    PlayerId.PLAYER_TWO.goalRow -> blend(base, palette.goalTwo, 0.28f)
                    else -> base
                }
                drawRoundRect(color, rect.topLeft, rect.size, radius)

                if (position in validMoves) {
                    drawCircle(
                        color = palette.valid.copy(alpha = 0.85f),
                        radius = geometry.tileSize * 0.15f,
                        center = rect.center,
                    )
                }
            }
        }
    }

    /**
     * The empty wall slots.
     *
     * Without these the space between tiles reads as background, and a player has no way to
     * tell where a wall could ever go. Drawn dark and thin so a real wall placed on top of
     * one is unmistakably a piece rather than a gap.
     */
    private fun DrawScope.drawEmptyChannels(geometry: BoardGeometry, palette: BoardPalette) {
        val color = blend(palette.background, Color.Black, 0.3f).copy(alpha = 0.55f)
        val thickness = geometry.wallThickness * 0.3f
        val span = geometry.tileSize
        for (index in 1 until Constants.Board.SIZE) {
            val center = geometry.padding + index * geometry.step - geometry.gap / 2f
            for (cell in 0 until Constants.Board.SIZE) {
                val start = geometry.padding + cell * geometry.step
                drawRoundRect(
                    color = color,
                    topLeft = Offset(start, center - thickness / 2f),
                    size = Size(span, thickness),
                    cornerRadius = CornerRadius(thickness / 2f),
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(center - thickness / 2f, start),
                    size = Size(thickness, span),
                    cornerRadius = CornerRadius(thickness / 2f),
                )
            }
        }
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
        val radius = CornerRadius(geometry.wallThickness / 2f)
        drawRoundRect(color.copy(alpha = color.alpha * clamped), topLeft, scaledSize, radius)
        // A lighter top edge gives the piece thickness, which is what separates a wall from
        // a coloured line on a flat drawing.
        if (clamped > 0.5f) {
            val lip = geometry.wallThickness * 0.26f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.2f * clamped),
                topLeft = topLeft,
                size = Size(scaledSize.width, minOf(lip, scaledSize.height)),
                cornerRadius = radius,
            )
        }
    }

    /** The cross drawn over a wall the rules refused. */
    private fun DrawScope.drawRefusalCross(geometry: BoardGeometry, wall: Wall, color: Color) {
        val center = geometry.wallRect(wall).center
        val arm = geometry.tileSize * 0.2f
        val stroke = Stroke(width = geometry.tileSize * 0.07f)
        drawCircle(Color.White.copy(alpha = 0.86f), arm * 1.25f, center)
        drawCircle(color, arm * 1.25f, center, style = stroke)
        drawLine(
            color = color,
            start = center - Offset(arm * 0.55f, arm * 0.55f),
            end = center + Offset(arm * 0.55f, arm * 0.55f),
            strokeWidth = stroke.width,
        )
        drawLine(
            color = color,
            start = center - Offset(-arm * 0.55f, arm * 0.55f),
            end = center + Offset(-arm * 0.55f, arm * 0.55f),
            strokeWidth = stroke.width,
        )
    }

    /**
     * A pawn: a shadow, a flared base, a tapered body and a domed head.
     *
     * [crowned] adds a ring to the head of the second pawn, so the two are told apart by
     * shape as well as colour — the board has to work for a player who cannot separate blue
     * from orange.
     */
    private fun DrawScope.drawPawn(
        geometry: BoardGeometry,
        row: Float,
        column: Float,
        color: Color,
        crowned: Boolean,
        selected: Boolean,
        selectionColor: Color,
    ) {
        val center = geometry.pawnCenter(row, column)
        val unit = geometry.tileSize
        val radius = unit * Constants.Board.PAWN_RADIUS_RATIO

        if (selected) {
            drawCircle(selectionColor.copy(alpha = 0.28f), radius * 1.5f, center)
            drawCircle(
                color = selectionColor,
                radius = radius * 1.5f,
                center = center,
                style = Stroke(width = unit * 0.045f),
            )
        }

        // Contact shadow, so the pawn sits on the tile rather than floating over it.
        drawOval(
            color = Color.Black.copy(alpha = 0.18f),
            topLeft = Offset(center.x - radius * 0.92f, center.y + radius * 0.5f),
            size = Size(radius * 1.84f, radius * 0.5f),
        )

        val dark = blend(color, Color.Black, 0.24f)
        val light = blend(color, Color.White, 0.3f)

        val baseWidth = radius * 1.7f
        val baseHeight = radius * 0.44f
        drawRoundRect(
            color = dark,
            topLeft = Offset(center.x - baseWidth / 2f, center.y + radius * 0.34f),
            size = Size(baseWidth, baseHeight),
            cornerRadius = CornerRadius(baseHeight / 2f),
        )

        val bodyWidth = radius * 0.78f
        drawRoundRect(
            color = color,
            topLeft = Offset(center.x - bodyWidth / 2f, center.y - radius * 0.2f),
            size = Size(bodyWidth, radius * 0.72f),
            cornerRadius = CornerRadius(bodyWidth * 0.4f),
        )

        val collarWidth = radius * 1.06f
        val collarHeight = radius * 0.22f
        drawRoundRect(
            color = light,
            topLeft = Offset(center.x - collarWidth / 2f, center.y - radius * 0.3f),
            size = Size(collarWidth, collarHeight),
            cornerRadius = CornerRadius(collarHeight / 2f),
        )

        val headRadius = radius * 0.52f
        val headCenter = Offset(center.x, center.y - radius * 0.52f)
        drawCircle(color, headRadius, headCenter)
        drawCircle(light, headRadius * 0.42f, headCenter - Offset(headRadius * 0.3f, headRadius * 0.32f))
        if (crowned) {
            drawCircle(
                color = dark,
                radius = headRadius * 0.72f,
                center = headCenter,
                style = Stroke(width = unit * 0.035f),
            )
        }
    }

    private fun blend(first: Color, second: Color, fraction: Float): Color = Color(
        red = first.red + (second.red - first.red) * fraction,
        green = first.green + (second.green - first.green) * fraction,
        blue = first.blue + (second.blue - first.blue) * fraction,
        alpha = first.alpha,
    )
}
