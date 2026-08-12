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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.game.board.BoardGeometry
import com.duzman46.gridbound.game.board.CanvasRenderer
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.WallOrientation
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.ui.util.rememberMotionEnabled
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import com.duzman46.gridbound.R

/**
 * Where the board artwork sits inside the well, as fractions of the well's height.
 *
 * Shared by both layers deliberately: the board and the dashed route are drawn by two separate
 * Canvases, and the moment they carry their own numbers the line is threading squares that are
 * not where it thinks they are. One source of truth is the only thing that keeps two drawings
 * agreeing about the same board.
 *
 * [ART_SIDE] is below 1 because the board has to fit the well rather than run out of it. A
 * board scaled past the frame loses its bottom ranks, and one of those is a goal row — the
 * single most important row on it. What is left over is split evenly, [ART_TOP] above and the
 * same again below: enough for the board to read as set into the well rather than butted
 * against it, and no more than that, because on a short panel every percent of the height
 * given to margin is a percent taken off the board.
 */
private const val ART_SIDE = 0.94f
private const val ART_TOP = 0.03f

/**
 * How far the artwork keeps off the panel's side edges at its tallest, as a fraction of the
 * width.
 *
 * A margin the composition is built on rather than a number any drawing reads: [HERO_TALLEST]
 * is derived from it, so a panel of that shape is the one where the board and its rack come
 * exactly this far in from both edges. Every shorter panel is wider than the artwork needs and
 * simply has more well on each side of it.
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

/** Board, gap and rack together, as a multiple of the board's side: the artwork's real width. */
private const val ART_SPAN = 1f + RACK_GAP + RACK_WIDTH

/**
 * The panel's shape limits, as width ÷ height.
 *
 * The panel is the one thing on the home screen with no fixed size: the utility row, the
 * wordmark, the five controls and the ad banner all have to be on screen at once, and the
 * panel takes what is left of the window. These two are the ends of that.
 *
 * [HERO_TALLEST] is not a matter of taste — it is the shape at which the artwork exactly fills
 * the panel out to its [ART_START] margins, and anything taller is a panel the board and its
 * rack cannot both fit across. [HERO_SHORTEST] is the other end: below it the board is too
 * small to be a board, and a home screen that scrolls is the better of two bad answers. In
 * between, a wider and shorter panel is only a smaller board with more well around it, which
 * costs nothing the picture needs.
 */
private val HERO_TALLEST = ART_SIDE * ART_SPAN / (1f - 2f * ART_START)
private const val HERO_SHORTEST = 2.4f

/** How far the well's inner top shadow reaches down, as a fraction of its height. */
private const val WELL_SHADOW = 0.12f

/**
 * The height the panel takes when the home screen has [available] left over for it, in a
 * column [width] wide.
 *
 * Pulled out of the composable so the home screen's height budget can be asserted in a unit
 * test rather than discovered on a phone.
 */
internal fun heroHeight(available: Dp, width: Dp): Dp =
    available.coerceIn(width / HERO_SHORTEST, width / HERO_TALLEST)

/**
 * The home screen's hero: a real Koridor position, drawn by the same renderer that draws a
 * live match, sunk into a dark well.
 *
 * This is the answer to a home screen that could have belonged to any app. The board is the
 * product, so the board is what you see — mid-game, walls placed, blue boxed in behind jade
 * walls and a dashed line crawling along the long way round it has been forced to take.
 *
 * Nothing but the artwork lives in here, and that is what makes the artwork visible: a
 * wordmark laid over it needs scrims heavy enough to bury a pawn, and controls in the corners
 * need the board pushed down until its goal row falls off the bottom edge. Both sit above the
 * panel instead; see [HomeTopBar] and [HomeWordmark].
 *
 * [available] is how much room the rest of the screen has left, not a size: the panel is the
 * only element here that can give ground, so it is the only one told how much room there is.
 * What it does with that is [heroHeight]'s business, and the well always spans the full column
 * — a hero narrower than the buttons beneath it reads as a thumbnail.
 *
 * Two stacked Canvases on purpose: the board layer reads no animated state and is recorded
 * once, while only the three-operation route layer re-records per frame.
 */
@Composable
fun BoardShowcase(available: Dp, modifier: Modifier = Modifier) {
    val renderer = remember { CanvasRenderer() }
    // Never isSystemInDarkTheme(): Settings can force light or dark independently of the OS.
    val onDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shape = RoundedCornerShape(Dimens.RadiusXl)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(heroHeight(available, maxWidth))
                .clip(shape)
                .then(
                    // Against the near-black page the well is barely 1.13:1 and would
                    // dissolve; in light theme the contrast carries it and a border would
                    // look drawn on.
                    if (onDarkTheme) {
                        Modifier.border(1.dp, HomePalette.WellBorder, shape)
                    } else {
                        Modifier
                    },
                ),
        ) {
            StaticLayer(renderer, rtl)
            RouteLayer(rtl)
        }
    }
}

/**
 * The board's leading edge inside a well [w] wide, for a board of [side].
 *
 * The board and its wall rack move as one group: centring the board alone would push the rack
 * against the panel's trailing edge on a wide well and leave the two looking unrelated. No
 * panel is ever taller than [HERO_TALLEST], so the group always fits across with a margin to
 * spare and centring is the whole of the job.
 */
private fun artLeft(w: Float, side: Float, rtl: Boolean): Float {
    val inset = (w - side * ART_SPAN) / 2f
    return if (rtl) w - inset - side else inset
}

@Composable
private fun BoxScope.StaticLayer(renderer: CanvasRenderer, rtl: Boolean) {
    val pawnBlue = ImageBitmap.imageResource(R.drawable.pawn_blue)
    val pawnRed = ImageBitmap.imageResource(R.drawable.pawn_red)
    Canvas(
        Modifier
            .matchParentSize()
            .clearAndSetSemantics { },
    ) {
        val w = size.width
        val h = size.height

        drawRect(HomePalette.Well)

        // The inner top shadow, and the only thing allowed over the artwork: this is what
        // makes the panel read as sunk into a case rather than pasted onto one. Pitched as
        // light as describing an edge needs, because it reaches further down the panel than
        // the board's own top margin and anything heavier greys out the rank of tiles it
        // spills onto.
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
                pawnOne = pawnBlue,
                pawnTwo = pawnRed,
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
 *
 * A spent slot is drawn in the wall colour and not in the spender's, because what the rack
 * counts is walls: the bars have to be the same object as the pieces standing on the board
 * beside them, or the gauge is measuring something the picture does not show.
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
            color = if (index < spent) HomePalette.Accent else Color.White.copy(alpha = 0.07f),
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
                color = HomePalette.Route.copy(alpha = 0.88f),
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
            drawCircle(HomePalette.Route.copy(alpha = 0.22f), cell * 0.42f, goal)
            drawCircle(HomePalette.Route, cell * 0.13f, goal)
        }
    }
}
