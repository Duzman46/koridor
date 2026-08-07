package com.duzman46.gridbound.ui.components.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.game.board.BoardGeometry
import com.duzman46.gridbound.game.board.CanvasRenderer
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.WallOrientation
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.ui.util.rememberMotionEnabled

/**
 * Where the board artwork sits inside the well, as fractions of the well's height.
 *
 * Shared by both layers deliberately. They used to carry their own numbers — the board at
 * side 1.06 / top 0.20, the route at side 1.20 / top 0.06 — so the dashed route was drawn
 * against a board that was not where it was, and the line wandered off the squares it was
 * supposed to be threading. One source of truth is the only thing that keeps two Canvases
 * agreeing about the same drawing.
 *
 * [ART_SIDE] is below 1 because the board has to fit the well rather than run out of it. When
 * it was 1.06 the board was taller than the panel that framed it, so its last two ranks fell
 * off the bottom edge — and one of them is a goal row, the single most important row on the
 * board. What is left over is split evenly: [ART_TOP] above, the same again below.
 */
private const val ART_SIDE = 0.90f
private const val ART_TOP = 0.05f

/**
 * The artwork's smallest inset from the panel's leading edge, as a fraction of the width.
 *
 * A floor rather than a fixed position. At [HERO_ASPECT] the board and its rack come out
 * exactly this far from both edges, so the constant is what the composition is actually built
 * on; on a short window the panel is wider than the artwork needs and the pair is centred
 * instead. Pinned to the leading edge there, a fifth of the well would sit empty on one side
 * and the board would read as a picture hung crooked.
 */
private const val ART_START = 0.045f

/**
 * The wall rack, as fractions of the board's side: bar width, bar height, gap to the board.
 *
 * Measured off the board and not off the panel, so the rack cannot drift away from the thing
 * it is counting when the panel changes shape.
 */
private const val RACK_WIDTH = 0.14f
private const val RACK_BAR = 0.030f
private const val RACK_GAP = 0.073f

/**
 * The panel's shape, near enough to square to hold a square board.
 *
 * A 1.6:1 panel could only ever show a board 0.62 of its own width, which is why the artwork
 * was scaled past the frame and cropped instead. Sizing the panel to its contents — a square
 * board, a slim rack beside it, an even margin round both — is what removes the crop rather
 * than moving it somewhere else. The short-screen variant is wider only to keep the hero from
 * swallowing a landscape window whole; the artwork stays the same shape inside it.
 */
private const val HERO_ASPECT = 1.2f
private const val HERO_ASPECT_SHORT = 1.45f
private const val SHORT_SCREEN_DP = 620

/** How far the well's inner top shadow reaches down, as a fraction of its height. */
private const val WELL_SHADOW = 0.12f

/**
 * The home screen's hero: a real Koridor position, drawn by the same renderer that draws a
 * live match, sunk into a dark well.
 *
 * This is the answer to a home screen that could have belonged to any app. The board is the
 * product, so the board is what you see — mid-game, walls placed, one pawn boxed in and a
 * dashed amber line crawling along the long way round it has been forced to take.
 *
 * Nothing but the artwork lives in here, and that is what makes the artwork visible. The
 * wordmark needed two scrim gradients under it heavy enough to bury blue's pawn, and the four
 * controls needed the board pushed down far enough to clear them, which ran its last two ranks
 * — one of them a goal row — off the bottom edge. A hero image that shows one pawn and eight
 * ninths of a board is not showing the game. Both now sit above the panel instead; see
 * [HomeTopBar] and [HomeWordmark].
 *
 * Two stacked Canvases on purpose: the board layer reads no animated state and is recorded
 * once, while only the three-operation route layer re-records per frame.
 */
@Composable
fun BoardShowcase(modifier: Modifier = Modifier) {
    val renderer = remember { CanvasRenderer() }
    // The window, not the screen: in split-screen the display is still tall while the app has
    // half of it, and the well has to leave the menu below room either way.
    val density = LocalDensity.current
    val windowHeightDp = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val aspect = if (windowHeightDp < SHORT_SCREEN_DP.dp) HERO_ASPECT_SHORT else HERO_ASPECT
    // Never isSystemInDarkTheme(): Settings can force light or dark independently of the OS.
    val onDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shape = RoundedCornerShape(Dimens.RadiusXl)

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(shape)
            .then(
                // Against the dark background the well is barely 1.06:1 and would dissolve;
                // in light theme the contrast carries it and a border would look drawn on.
                if (onDarkTheme) Modifier.border(1.dp, HomePalette.WellBorder, shape) else Modifier,
            ),
    ) {
        StaticLayer(renderer, rtl)
        RouteLayer(rtl)
    }
}

/**
 * The board's leading edge inside a well [w] wide, for a board of [side].
 *
 * The board and its wall rack move as one group: centring the board alone would push the rack
 * against the panel's trailing edge on a wide well and leave the two looking unrelated.
 */
private fun artLeft(w: Float, side: Float, rtl: Boolean): Float {
    val group = side * (1f + RACK_GAP + RACK_WIDTH)
    val inset = maxOf(w * ART_START, (w - group) / 2f)
    return if (rtl) w - inset - side else inset
}

@Composable
private fun BoxScope.StaticLayer(renderer: CanvasRenderer, rtl: Boolean) {
    Canvas(
        Modifier
            .matchParentSize()
            .clearAndSetSemantics { },
    ) {
        val w = size.width
        val h = size.height

        drawRect(HomePalette.Well)

        // The inner top shadow, and it is the only thing drawn over the artwork now: this is
        // what makes the panel read as sunk into a case rather than pasted onto one. It used
        // to double as the bed the corner controls were read against and was pitched dark
        // enough for that job; describing an edge needs far less, and anything heavier greys
        // out the rank of tiles it spills onto.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.30f),
                1f to Color.Transparent,
                startY = 0f,
                endY = h * WELL_SHADOW,
            ),
            size = Size(w, h * WELL_SHADOW),
        )

        val side = h * ART_SIDE
        val top = h * ART_TOP
        val left = artLeft(w, side, rtl)
        translate(left, top) {
            renderer.draw(
                scope = this,
                geometry = BoardGeometry(side),
                state = HomePosition.STATE,
                palette = HomePalette.Board,
                validMoves = emptySet(),
                validWalls = emptySet(),
                wallOrientation = WallOrientation.HORIZONTAL,
                pendingWall = null,
                invalidWall = null,
                recentWall = null,
                recentWallProgress = 1f,
                playerOneRow = 5f,
                playerOneColumn = 3f,
                playerTwoRow = 3f,
                playerTwoColumn = 4f,
                selected = false,
            )
        }

        drawWallRack(left, top, side, rtl)
    }
}

/**
 * Ten wall slots beside the board, filled to match what blue has actually spent.
 *
 * Spread over the board's exact height, which lands the bars within a percent of one board row
 * apart — the rack reads as belonging to the grid next to it rather than as a gauge parked in
 * the margin. HomePositionTest keeps the count honest.
 */
private fun DrawScope.drawWallRack(left: Float, top: Float, side: Float, rtl: Boolean) {
    val spent = 10 - HomePosition.STATE.player(PlayerId.PLAYER_ONE).wallsRemaining
    val barW = side * RACK_WIDTH
    val barH = side * RACK_BAR
    val gutterX = if (rtl) {
        left - side * RACK_GAP - barW
    } else {
        left + side * (1f + RACK_GAP)
    }
    val pitch = (side - barH) / 9f
    repeat(10) { index ->
        drawRoundRect(
            color = if (index < spent) HomePalette.Amber else Color.White.copy(alpha = 0.07f),
            topLeft = Offset(gutterX, top + index * pitch),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(barH / 2f),
        )
    }
}

@Composable
private fun BoxScope.RouteLayer(rtl: Boolean) {
    val motionEnabled = rememberMotionEnabled()
    val animated by rememberInfiniteTransition(label = "route").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "routePhase",
    )
    val phase = if (motionEnabled) animated else 0f

    Canvas(
        Modifier
            .matchParentSize()
            .clearAndSetSemantics { },
    ) {
        val side = size.height * ART_SIDE
        val top = size.height * ART_TOP
        val left = artLeft(size.width, side, rtl)

        translate(left, top) {
            val geometry = BoardGeometry(side)
            val cell = geometry.tileSize
            val path = Path()
            HomePosition.ROUTE.forEachIndexed { index, position ->
                val point = geometry.pawnCenter(position.row.toFloat(), position.column.toFloat())
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(
                path = path,
                color = HomePalette.Amber.copy(alpha = 0.88f),
                style = Stroke(
                    width = cell * 0.11f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    // Negative phase walks the dashes forward along the route.
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(cell * 0.30f, cell * 0.26f),
                        phase = -phase * cell * 0.56f,
                    ),
                ),
            )
            val goal = geometry.pawnCenter(
                HomePosition.ROUTE.last().row.toFloat(),
                HomePosition.ROUTE.last().column.toFloat(),
            )
            drawCircle(HomePalette.Amber.copy(alpha = 0.22f), cell * 0.42f, goal)
            drawCircle(HomePalette.Amber, cell * 0.13f, goal)
        }
    }
}
