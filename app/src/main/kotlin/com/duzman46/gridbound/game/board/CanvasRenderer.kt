package com.duzman46.gridbound.game.board

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
        /**
         * Counter-turn applied to each piece about its own centre, so that a board drawn
         * upside down still shows pawns standing on their bases. Zero for an unturned board.
         */
        pieceRotation: Float = 0f,
        /**
         * The two pieces as shaded sprites, or null to fall back to the drawn ones.
         *
         * A pawn is a turned solid and a canvas cannot draw one: a circle with a lighter circle
         * on it reads as a symbol of a pawn however the gradient is tuned, because the highlight
         * does not travel round the form and the terminator does not curve. The sprites are
         * computed from a profile with a real per-pixel normal — see docs/store/pieces.py — and
         * this is the one place in the app where a picture beats a drawing.
         *
         * Nullable so nothing is forced to have them: the fallback below is the old drawing, and
         * it is still what a preview with no resources loaded gets.
         */
        pawnOne: ImageBitmap? = null,
        pawnTwo: ImageBitmap? = null,
    ) = with(scope) {
        drawFrame(geometry, palette)
        drawTiles(geometry, palette, validMoves)
        drawEmptyChannels(geometry, palette)

        // Slots the current orientation could legally take, so wall mode shows where a piece
        // may go before the player starts hunting for it.
        validWalls.asSequence()
            .filter { it.orientation == wallOrientation }
            .forEach { wall -> drawWallHint(geometry, wall, palette.valid) }

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
            uprightBy = pieceRotation,
            sprite = pawnOne,
        )
        drawPawn(
            geometry = geometry,
            row = playerTwoRow,
            column = playerTwoColumn,
            color = palette.playerTwo,
            crowned = true,
            selected = selected && state.currentPlayer == PlayerId.PLAYER_TWO,
            selectionColor = palette.selection,
            uprightBy = pieceRotation,
            sprite = pawnTwo,
        )

        drawVignette(geometry)
    }

    /**
     * The tray the tiles sit in: a dark bed, a lit rim along its top edge and a shadow inside it.
     *
     * Two rects used to do this and the board had no edge — it read as a slightly different
     * black on black. What makes a frame is the rim: a thin lighter line where the light would
     * catch the top of a raised tray, and the well below it darker than anything in the grid.
     */
    private fun DrawScope.drawFrame(geometry: BoardGeometry, palette: BoardPalette) {
        val side = geometry.boardSize
        val radius = CornerRadius(geometry.tileSize * Constants.Board.TILE_CORNER_RADIUS_RATIO * 1.6f)
        val rim = geometry.padding * 0.22f

        drawRoundRect(
            color = blend(palette.background, Color.White, 0.10f),
            size = Size(side, side),
            cornerRadius = radius,
        )
        drawRoundRect(
            color = blend(palette.background, Color.Black, 0.55f),
            topLeft = Offset(0f, rim),
            size = Size(side, side - rim),
            cornerRadius = radius,
        )
        // The well: darker than any tile, so the grid reads as sitting *in* something.
        drawRoundRect(
            color = blend(palette.background, Color.Black, 0.72f),
            topLeft = Offset(geometry.padding * 0.42f, geometry.padding * 0.42f),
            size = Size(side - geometry.padding * 0.84f, side - geometry.padding * 0.84f),
            cornerRadius = radius,
        )
    }

    /**
     * The falloff towards the edges, drawn last over everything.
     *
     * One brush a frame, which is the whole reason the tiles do their shading with solid rects:
     * the board is lit from the middle and a corner tile is not as bright as a central one, and
     * saying that once here costs a fraction of saying it eighty-one times.
     */
    private fun DrawScope.drawVignette(geometry: BoardGeometry) {
        val side = geometry.boardSize
        drawRect(
            brush = Brush.radialGradient(
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.38f),
                center = Offset(side / 2f, side * 0.46f),
                radius = side * 0.78f,
            ),
            size = Size(side, side),
        )
    }

    /**
     * The grid, as raised blocks rather than coloured squares.
     *
     * Three solid rounded rects each, and no brush: a lit one behind, the base inset from the
     * top so the lit one shows as an edge, and a shade along the bottom. That is the whole
     * bevel, and it costs the same as the flat fill it replaces.
     *
     * The goal rows are tinted harder than they were. Which end you are running for is the
     * single most important fact on the board, and at 0.28 against a near-black tile the blue
     * and the red were a shade of grey each.
     */
    private fun DrawScope.drawTiles(
        geometry: BoardGeometry,
        palette: BoardPalette,
        validMoves: Set<Position>,
    ) {
        val radius = CornerRadius(geometry.tileSize * Constants.Board.TILE_CORNER_RADIUS_RATIO)
        val bevel = geometry.tileSize * 0.085f
        for (row in 0 until Constants.Board.SIZE) {
            for (column in 0 until Constants.Board.SIZE) {
                val position = Position(row, column)
                val rect = geometry.tileRect(position)
                val base = if ((row + column) % 2 == 0) palette.tile else palette.tileAlternate
                val color = when (row) {
                    PlayerId.PLAYER_ONE.goalRow -> blend(base, palette.goalOne, 0.52f)
                    PlayerId.PLAYER_TWO.goalRow -> blend(base, palette.goalTwo, 0.52f)
                    else -> base
                }

                // Behind: the lit face. What is left of it after the next rect is the top edge.
                drawRoundRect(blend(color, Color.White, 0.26f), rect.topLeft, rect.size, radius)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(rect.left, rect.top + bevel),
                    size = Size(rect.width, rect.height - bevel),
                    cornerRadius = radius,
                )
                // And the shade the block casts on itself at its foot.
                drawRoundRect(
                    color = blend(color, Color.Black, 0.45f),
                    topLeft = Offset(rect.left + bevel, rect.bottom - bevel * 1.4f),
                    size = Size(rect.width - bevel * 2f, bevel * 1.4f),
                    cornerRadius = CornerRadius(bevel * 0.7f),
                )

                if (position in validMoves) {
                    drawCircle(
                        color = palette.valid.copy(alpha = 0.30f),
                        radius = geometry.tileSize * 0.30f,
                        center = rect.center,
                    )
                    drawCircle(
                        color = palette.valid,
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

    /**
     * Where a wall *could* go: a slot, not a piece.
     *
     * These went through [drawWall] and came out looking more solid than the walls actually on
     * the board — a lit top face and a specular are drawn at fixed alphas, so passing a colour
     * at 22% did nothing to them and forty green columns stood over the game. A hint is a thin
     * flat bar, half the thickness of the real thing, and it is allowed to be nothing else.
     */
    private fun DrawScope.drawWallHint(geometry: BoardGeometry, wall: Wall, color: Color) {
        val rect = geometry.wallRect(wall)
        val slim = geometry.wallThickness * 0.34f
        val horizontal = rect.width >= rect.height
        val size = if (horizontal) Size(rect.width * 0.86f, slim) else Size(slim, rect.height * 0.86f)
        drawRoundRect(
            color = color.copy(alpha = 0.16f),
            topLeft = Offset(rect.center.x - size.width / 2f, rect.center.y - size.height / 2f),
            size = size,
            cornerRadius = CornerRadius(slim / 2f),
        )
    }

    /**
     * A wall, as a piece with a top face and a front face.
     *
     * It was a bar with a pale strip along its top, which is a drawing of a wall rather than a
     * wall. What gives it thickness is having two faces that disagree about the light: a bright
     * top the eye reads as facing up, a body that falls away below it, and a shadow underneath
     * that separates the piece from the board it is standing on.
     *
     * A brush per wall is affordable — there are at most twenty on a board, against eighty-one
     * tiles a frame.
     */
    private fun DrawScope.drawWall(geometry: BoardGeometry, wall: Wall, color: Color, progress: Float) {
        val rect = geometry.wallRect(wall)
        val clamped = progress.coerceIn(0f, 1f)
        val scaledSize = Size(rect.width * clamped, rect.height * clamped)
        val topLeft = Offset(
            rect.center.x - scaledSize.width / 2f,
            rect.center.y - scaledSize.height / 2f,
        )
        if (scaledSize.width <= 0f || scaledSize.height <= 0f) return
        val radius = CornerRadius(geometry.wallThickness * 0.34f)
        val alpha = color.alpha * clamped
        val drop = geometry.wallThickness * 0.34f

        // The shadow the piece casts into the channel, before the piece itself.
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.45f * clamped * color.alpha),
            topLeft = Offset(topLeft.x, topLeft.y + drop),
            size = scaledSize,
            cornerRadius = radius,
        )
        // The body, falling away from the light.
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to blend(color, Color.White, 0.28f).copy(alpha = alpha),
                0.42f to color.copy(alpha = alpha),
                1f to blend(color, Color.Black, 0.42f).copy(alpha = alpha),
                startY = topLeft.y,
                endY = topLeft.y + scaledSize.height,
            ),
            topLeft = topLeft,
            size = scaledSize,
            cornerRadius = radius,
        )
        // The top face, and the specular line along its leading edge.
        if (clamped > 0.35f) {
            val face = scaledSize.height * 0.34f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to blend(color, Color.White, 0.55f).copy(alpha = alpha),
                    1f to blend(color, Color.White, 0.10f).copy(alpha = alpha * 0.7f),
                    startY = topLeft.y,
                    endY = topLeft.y + face,
                ),
                topLeft = topLeft,
                size = Size(scaledSize.width, face),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.55f * clamped * color.alpha),
                topLeft = Offset(topLeft.x + radius.x, topLeft.y),
                size = Size(
                    (scaledSize.width - radius.x * 2f).coerceAtLeast(0f),
                    (scaledSize.height * 0.10f).coerceAtLeast(1f),
                ),
                cornerRadius = CornerRadius(scaledSize.height * 0.05f),
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
     * from red.
     */
    private fun DrawScope.drawPawn(
        geometry: BoardGeometry,
        row: Float,
        column: Float,
        color: Color,
        crowned: Boolean,
        selected: Boolean,
        selectionColor: Color,
        uprightBy: Float,
        sprite: ImageBitmap?,
    ) {
        val center = geometry.pawnCenter(row, column)
        rotate(uprightBy, center) {
            if (sprite != null) {
                drawPawnSprite(geometry, center, sprite, selected, selectionColor)
            } else {
                drawPawnBody(geometry, center, color, crowned, selected, selectionColor)
            }
        }
    }

    /**
     * The sprite, with the selection ring and the contact shadow still drawn.
     *
     * Those two stay vector because they belong to the board rather than to the piece: the ring
     * is the app's selection colour and has to follow the theme, and the shadow has to fall on
     * whatever tile the piece is standing on rather than being baked into a picture that also
     * gets drawn over the goal rows.
     *
     * The sprite is placed by its FOOT, not its middle. A piece stands on a square; centring the
     * image would sit it half a head too high and it would read as hovering.
     */
    private fun DrawScope.drawPawnSprite(
        geometry: BoardGeometry,
        center: Offset,
        sprite: ImageBitmap,
        selected: Boolean,
        selectionColor: Color,
    ) {
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

        val foot = center.y + radius * 0.78f
        drawOval(
            brush = Brush.radialGradient(
                0f to Color.Black.copy(alpha = 0.52f),
                1f to Color.Transparent,
                center = Offset(center.x + radius * 0.16f, foot - radius * 0.06f),
                radius = radius * 1.10f,
            ),
            topLeft = Offset(center.x - radius * 1.15f, foot - radius * 0.34f),
            size = Size(radius * 2.30f, radius * 0.62f),
        )

        val side = radius * 2.36f
        drawImage(
            image = sprite,
            dstOffset = IntOffset(
                (center.x - side / 2f).roundToInt(),
                (foot - side).roundToInt(),
            ),
            dstSize = IntSize(side.roundToInt(), side.roundToInt()),
            filterQuality = FilterQuality.High,
        )
    }

    /**
     * A turned piece rather than a stack of shapes.
     *
     * What it was: a flared rounded rect, a narrow one, a pale one and a filled circle with a
     * lighter circle dropped on it. Every part opaque and every part the same value, so the
     * pawn read as a symbol printed on the tile.
     *
     * What makes it an object is that the light disagrees with itself across it. The dome is a
     * radial gradient whose bright pole sits up and to the left, with a hard specular inside it
     * and a bounce along the lower right where a real sphere picks the tile back up. The body
     * falls from lit shoulder to dark foot. The base is an ellipse, not a rectangle, because it
     * is a disc seen at an angle — and the cast shadow is offset down and to the right, away
     * from the same light, which is what sets the piece on the board rather than in it.
     */
    private fun DrawScope.drawPawnBody(
        geometry: BoardGeometry,
        center: Offset,
        color: Color,
        crowned: Boolean,
        selected: Boolean,
        selectionColor: Color,
    ) {
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

        val lit = blend(color, Color.White, 0.42f)
        val mid = color
        val dark = blend(color, Color.Black, 0.42f)
        val deep = blend(color, Color.Black, 0.62f)

        // Cast shadow: down and to the right, soft, and gone by its own edge.
        val shadowCentre = Offset(center.x + radius * 0.20f, center.y + radius * 0.72f)
        drawOval(
            brush = Brush.radialGradient(
                0f to Color.Black.copy(alpha = 0.50f),
                1f to Color.Transparent,
                center = shadowCentre,
                radius = radius * 1.15f,
            ),
            topLeft = Offset(shadowCentre.x - radius * 1.15f, shadowCentre.y - radius * 0.42f),
            size = Size(radius * 2.30f, radius * 0.84f),
        )

        // Base: a disc seen at an angle, lit on its front face.
        val baseWidth = radius * 1.72f
        val baseHeight = radius * 0.52f
        val baseTop = center.y + radius * 0.30f
        drawOval(
            brush = Brush.verticalGradient(
                0f to lit,
                0.35f to mid,
                1f to deep,
                startY = baseTop,
                endY = baseTop + baseHeight,
            ),
            topLeft = Offset(center.x - baseWidth / 2f, baseTop),
            size = Size(baseWidth, baseHeight),
        )

        // Body: a waist that flares into the base.
        val bodyTop = center.y - radius * 0.26f
        val bodyBottom = baseTop + baseHeight * 0.34f
        val waist = radius * 0.34f
        val flare = radius * 0.74f
        drawPath(
            path = Path().apply {
                moveTo(center.x - waist, bodyTop)
                cubicTo(
                    center.x - waist * 1.05f, bodyTop + (bodyBottom - bodyTop) * 0.45f,
                    center.x - flare * 0.86f, bodyBottom - (bodyBottom - bodyTop) * 0.18f,
                    center.x - flare, bodyBottom,
                )
                lineTo(center.x + flare, bodyBottom)
                cubicTo(
                    center.x + flare * 0.86f, bodyBottom - (bodyBottom - bodyTop) * 0.18f,
                    center.x + waist * 1.05f, bodyTop + (bodyBottom - bodyTop) * 0.45f,
                    center.x + waist, bodyTop,
                )
                close()
            },
            brush = Brush.horizontalGradient(
                0f to blend(color, Color.Black, 0.10f),
                0.30f to lit,
                0.62f to mid,
                1f to dark,
                startX = center.x - flare,
                endX = center.x + flare,
            ),
        )

        // Collar: the ring the dome sits on.
        val collarWidth = radius * 1.02f
        val collarHeight = radius * 0.26f
        val collarTop = center.y - radius * 0.34f
        drawOval(
            brush = Brush.verticalGradient(
                0f to blend(color, Color.White, 0.55f),
                1f to dark,
                startY = collarTop,
                endY = collarTop + collarHeight,
            ),
            topLeft = Offset(center.x - collarWidth / 2f, collarTop),
            size = Size(collarWidth, collarHeight),
        )

        // The dome.
        val headRadius = radius * 0.56f
        val headCenter = Offset(center.x, center.y - radius * 0.60f)
        drawCircle(
            brush = Brush.radialGradient(
                0f to blend(color, Color.White, 0.62f),
                0.45f to mid,
                1f to deep,
                center = Offset(
                    headCenter.x - headRadius * 0.34f,
                    headCenter.y - headRadius * 0.38f,
                ),
                radius = headRadius * 1.72f,
            ),
            radius = headRadius,
            center = headCenter,
        )
        // The bounce: a real sphere on a lit board picks the board back up along its underside.
        drawArc(
            color = blend(color, Color.White, 0.30f).copy(alpha = 0.5f),
            startAngle = 35f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(headCenter.x - headRadius * 0.90f, headCenter.y - headRadius * 0.90f),
            size = Size(headRadius * 1.80f, headRadius * 1.80f),
            style = Stroke(width = headRadius * 0.16f),
        )
        // And the specular, small and hard, where the light actually is.
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.92f),
                1f to Color.White.copy(alpha = 0f),
                center = Offset(
                    headCenter.x - headRadius * 0.34f,
                    headCenter.y - headRadius * 0.40f,
                ),
                radius = headRadius * 0.46f,
            ),
            radius = headRadius * 0.46f,
            center = Offset(headCenter.x - headRadius * 0.34f, headCenter.y - headRadius * 0.40f),
        )
        if (crowned) {
            drawCircle(
                color = deep,
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
