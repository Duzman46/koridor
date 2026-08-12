package com.duzman46.gridbound.game.board

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
    /**
     * The empty wall slot.
     *
     * Its own colour rather than [valid]'s, and gold rather than green. The two were the same
     * for as long as both meant "you may put something here", but they are not the same thing
     * to look at: a move target is one mark on one square, and the slots are forty bars laid
     * across the whole board at once. In green, over a board of rosewood and brass, forty of
     * them read as pinstripes drawn on top of the table rather than as slots cut into it.
     *
     * The wording survives the change. "Tap the green square" is about the move targets, which
     * are still green circles; a wall slot was never what that sentence pointed at.
     */
    val slot: Color,
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
        /**
         * The whole board as one rendered surface — frame, grid, channels and both goal rows —
         * or null to fall back to drawing them.
         *
         * A tile was tried here first, repeated across the grid, and it was worse than the
         * drawing it replaced. A tile render carries its own frame, its own centre inlay and
         * its own baked highlight, and eighty-one copies carry eighty-one of each: the frames
         * read as eighty-one objects instead of one board, and every highlight falls the same
         * way, which is the one thing light on a real surface never does. A board is lit once.
         * So this is the whole board, lit once, and the only things drawn over it are the ones
         * that move.
         *
         * [BoardGeometry] and this picture have to agree about where the grid is, and they do
         * by construction: `docs/store/board.py` measures the render's own lattice and prints
         * the two ratios `Constants.Board` carries.
         */
        surface: ImageBitmap? = null,
        /**
         * A wall as a piece — rosewood between two brushed gold caps — or null to fall back to
         * the drawn bar.
         *
         * One sprite serves both orientations. It is turned a quarter anticlockwise for a
         * vertical wall rather than clockwise, so the lit edge it carries ends up on the piece's
         * left: the board is lit from the top left, and a bar whose bright edge faces right
         * would be the only object on the table disagreeing about where the lamp is.
         */
        wallPiece: ImageBitmap? = null,
    ) = with(scope) {
        if (surface != null) {
            val side = geometry.boardSize.roundToInt()
            drawImage(
                image = surface,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(side, side),
                filterQuality = FilterQuality.High,
            )
        } else {
            drawFrame(geometry, palette)
            drawTiles(geometry, palette)
            drawEmptyChannels(geometry, palette)
        }
        drawMoveTargets(geometry, palette, validMoves)

        // Slots the current orientation could legally take, so wall mode shows where a piece
        // may go before the player starts hunting for it.
        validWalls.asSequence()
            .filter { it.orientation == wallOrientation }
            .forEach { wall -> drawWallHint(geometry, wall, palette.slot) }

        state.walls.forEach { wall ->
            val progress = if (wall == recentWall) recentWallProgress else 1f
            drawWall(geometry, wall, palette.wall, progress, wallPiece, null)
        }
        pendingWall?.let { wall ->
            drawWallHighlight(geometry, wall, palette.selection)
            drawWall(geometry, wall, palette.valid, 1f, wallPiece, palette.valid)
        }
        invalidWall?.let { wall ->
            drawWall(geometry, wall, palette.invalid.copy(alpha = 0.9f), 1f, wallPiece, palette.invalid)
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

        // Only over the drawing. The render brought its own falloff, and a second one on top of
        // it would darken the corners twice.
        if (surface == null) drawVignette(geometry)
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
     *
     * This is the fallback now — the shipped board is a render. It is kept because previews and
     * tests draw with no resources loaded, and because a board that only exists as a picture is
     * a board nobody can change the colours of.
     */
    private fun DrawScope.drawTiles(geometry: BoardGeometry, palette: BoardPalette) {
        val radius = CornerRadius(geometry.tileSize * Constants.Board.TILE_CORNER_RADIUS_RATIO)
        val bevel = geometry.tileSize * 0.10f
        for (row in 0 until Constants.Board.SIZE) {
            for (column in 0 until Constants.Board.SIZE) {
                val rect = geometry.tileRect(Position(row, column))
                val base = if ((row + column) % 2 == 0) palette.tile else palette.tileAlternate
                val color = when (row) {
                    PlayerId.PLAYER_ONE.goalRow -> blend(base, palette.goalOne, 0.52f)
                    PlayerId.PLAYER_TWO.goalRow -> blend(base, palette.goalTwo, 0.52f)
                    else -> base
                }

                // Behind: the lit face. What is left of it after the next rect is the top edge.
                drawRoundRect(blend(color, Color.White, 0.34f), rect.topLeft, rect.size, radius)
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
            }
        }
    }

    /**
     * Where the piece may go next.
     *
     * Its own pass rather than a branch inside the grid loop, because the grid is a picture now
     * and these are not — they are the one thing on the board that answers to the game state
     * rather than to the artwork.
     */
    private fun DrawScope.drawMoveTargets(
        geometry: BoardGeometry,
        palette: BoardPalette,
        validMoves: Set<Position>,
    ) {
        validMoves.forEach { position ->
            val center = geometry.tileRect(position).center
            drawCircle(palette.valid.copy(alpha = 0.30f), geometry.tileSize * 0.30f, center)
            drawCircle(palette.valid, geometry.tileSize * 0.15f, center)
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
     * flat bar, and it is allowed to be nothing else.
     *
     * Thinner than it was, because the wall it stands in for is thicker than it was: at a third
     * of a wall's thickness the bar was ten pixels of the sixteen a real piece fills, which is
     * close enough to a wall to be mistaken for one. A quarter reads as a slot rather than a
     * piece, and there is no confusing the two — a placed wall is four times this thick and made
     * of rosewood.
     *
     * Nearly opaque, which is not what it was when it was green. Gold on a board of brass and
     * dark wood has almost no contrast of its own to spend, so the first gold version at 30 %
     * disappeared into the surface entirely: prettier than the green pinstripes it replaced and
     * useless, because the one thing this mark has to do is tell a player where a wall may go.
     */
    private fun DrawScope.drawWallHint(geometry: BoardGeometry, wall: Wall, color: Color) {
        val rect = geometry.wallRect(wall)
        val slim = geometry.wallThickness * 0.24f
        val horizontal = rect.width >= rect.height
        val size = if (horizontal) Size(rect.width * 0.82f, slim) else Size(slim, rect.height * 0.82f)
        drawRoundRect(
            color = color.copy(alpha = 0.85f),
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
    private fun DrawScope.drawWall(
        geometry: BoardGeometry,
        wall: Wall,
        color: Color,
        progress: Float,
        sprite: ImageBitmap?,
        tint: Color?,
    ) {
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

        // The shadow the piece casts into the channel, before the piece itself. Drawn for the
        // sprite too: it belongs to the board rather than to the piece, and baking it into the
        // picture would put the same shadow under a wall lying along either axis.
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.45f * clamped * color.alpha),
            topLeft = Offset(topLeft.x, topLeft.y + drop),
            size = scaledSize,
            cornerRadius = radius,
        )

        if (sprite != null) {
            drawWallSprite(sprite, rect, scaledSize, topLeft, clamped, tint)
            return
        }
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

    /**
     * The wall as the render made it: rosewood, a lit top edge, a brushed gold cap at each end.
     *
     * The sprite is horizontal, and a vertical wall gets it turned rather than getting a second
     * asset — a quarter turn anticlockwise, so the lit edge lands on the piece's left and agrees
     * with the light everything else on the board is under.
     *
     * [tint] is how a wall that is not yet a wall is said. A pending piece and a refused piece
     * are the same object in a different state, so they are the same picture with the state
     * washed over it, rather than a differently-shaped thing drawn from scratch.
     */
    private fun DrawScope.drawWallSprite(
        sprite: ImageBitmap,
        rect: Rect,
        size: Size,
        topLeft: Offset,
        progress: Float,
        tint: Color?,
    ) {
        val horizontal = rect.width >= rect.height
        // Turned about the piece's own centre, so the long side of the sprite lands on the long
        // side of the slot whichever way the slot runs.
        val long = if (horizontal) size.width else size.height
        val short = if (horizontal) size.height else size.width
        rotate(if (horizontal) 0f else -90f, rect.center) {
            drawImage(
                image = sprite,
                dstOffset = IntOffset(
                    (rect.center.x - long / 2f).roundToInt(),
                    (rect.center.y - short / 2f).roundToInt(),
                ),
                dstSize = IntSize(long.roundToInt(), short.roundToInt()),
                filterQuality = FilterQuality.High,
            )
        }
        if (tint != null) {
            drawRoundRect(
                color = tint.copy(alpha = 0.55f * progress * tint.alpha),
                topLeft = topLeft,
                size = size,
                cornerRadius = CornerRadius(size.minDimension * 0.34f),
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
     * The sprite is CENTRED on its square. The board is drawn straight down and has no
     * perspective in it at all, so there is no direction for a piece to lean into; standing one
     * up from its base put its head in the square above and left its own square empty
     * underneath, which is exactly what a player reads as a piece that is off centre.
     * [Constants.Board.PAWN_BASE_ANCHOR] is still needed, but only to find where the base is so
     * the shadow can fall under it rather than under the middle of the picture.
     */
    private fun DrawScope.drawPawnSprite(
        geometry: BoardGeometry,
        center: Offset,
        sprite: ImageBitmap,
        selected: Boolean,
        selectionColor: Color,
    ) {
        val unit = geometry.tileSize

        // Width first, height from the bitmap's own proportions. What decides whether a piece
        // suits a square is how much of the square its base covers; the rest of the piece
        // follows from that, and never the other way round. Sizing by height was right for the
        // tall ivory piece these replaced and wrong for them — it left a base crowding its own
        // tile and a piece towering over the two beside it.
        val width = unit * Constants.Board.PAWN_WIDTH_RATIO
        val height = width * sprite.height / sprite.width
        val base = width * 0.5f

        if (selected) {
            drawCircle(selectionColor.copy(alpha = 0.28f), base * 1.24f, center)
            drawCircle(
                color = selectionColor,
                radius = base * 1.24f,
                center = center,
                style = Stroke(width = unit * 0.045f),
            )
        }

        val top = center.y - height / 2f
        // Where the base actually meets the board, which is not the middle of the picture.
        val foot = top + height * Constants.Board.PAWN_BASE_ANCHOR

        // Under the base, and still vector: the shadow belongs to the board, so it has to fall
        // on whichever tile the piece is standing on rather than be baked into the sprite.
        drawOval(
            brush = Brush.radialGradient(
                0f to Color.Black.copy(alpha = 0.52f),
                1f to Color.Transparent,
                center = Offset(center.x + base * 0.14f, foot + base * 0.06f),
                radius = base * 1.15f,
            ),
            topLeft = Offset(center.x - base * 1.20f, foot - base * 0.36f),
            size = Size(base * 2.40f, base * 0.78f),
        )

        drawImage(
            image = sprite,
            dstOffset = IntOffset((center.x - width / 2f).roundToInt(), top.roundToInt()),
            dstSize = IntSize(width.roundToInt(), height.roundToInt()),
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
