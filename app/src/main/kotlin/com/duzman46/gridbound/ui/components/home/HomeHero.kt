package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The scene the home screen opens on: a Koridor board under one controlled light.
 *
 * It is a *photograph* of the game rather than a diagram of it — a low camera, a board that
 * runs off the top of the frame, two pieces caught mid-race and three walls standing between
 * them. The point is that somebody who has never heard of Quoridor understands the game in the
 * second before they read a word: two pieces want to get past each other, and those slabs are
 * in the way.
 *
 * Everything here is drawn rather than photographed, and that is what makes it survivable: a
 * bitmap of a board is a bitmap that is wrong on the next screen size, in the next locale's
 * mirrored layout, and on the day the board's colours change. A drawn scene follows all three.
 *
 * **The rules this obeys, because they are what separate premium from template.**
 * - One light. It falls from the top right and everything is lit or shaded by that single
 *   decision — tile faces, the pieces' turned shoulders, the walls' top edges, the shadows on
 *   the floor. Two light sources is how a rendered scene starts to look synthetic.
 * - Nothing glows. There is no bloom, no neon, no emissive line. Contrast comes from light and
 *   shadow, which is what a real object on a real table has.
 * - Nothing moves. The scene is still. A looping shimmer is the single loudest signal that an
 *   interface came out of a template.
 * - It ends in the page. The bottom fifth fades into the app's own background so the picture
 *   has no edge — a hard border under it would make it a card, and it is meant to be the room
 *   the interface is standing in.
 */

/** How tall the scene may be, as a fraction of the width it is given. */
private const val SCENE_SHORTEST_RATIO = 2.2f
private const val SCENE_TALLEST_RATIO = 1.42f

/** The floor, lit and unlit. Fixed in both themes — this is the app's signature image. */
private val FloorLit = Color(0xFF4A5462)
private val FloorShade = Color(0xFF0B0E12)
private val SeamInk = Color(0xFF05070A)

/** The two pieces: one near-black, one bone. Neither is a seat colour — this is a still life. */
private val PieceDark = Color(0xFF0C0E11)
private val PieceDarkLit = Color(0xFF5A626D)
private val PieceLight = Color(0xFFD8D2C4)
private val PieceLightLit = Color(0xFFF1EBDD)

/** The walls: brushed graphite, catching the light along their top edge. */
private val WallFace = Color(0xFF2B3138)
private val WallTop = Color(0xFF59616C)
private val WallSide = Color(0xFF121519)

/** Where the page takes over from the picture. Matches the dark scheme's background. */
private val PageInk = Color(0xFF070A0D)

/**
 * The scene fills whatever box it is given.
 *
 * It used to be told how many device-independent pixels to take, and that was the wrong
 * contract: the caller had to compute the number by adding up every other row on the screen,
 * which is arithmetic that cannot be right on every device and font scale at once — it was
 * wrong twice, and both times the last card ended up under the navigation bar. The home screen
 * now weights this box instead, so the picture is exactly the room that is left over and the
 * screen cannot overflow.
 */
@Composable
fun HomeHero(modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier
            // One node, no description. It is scene-setting, and a screen reader announcing
            // "two pawns and three walls" hands the player nothing they can act on.
            .clearAndSetSemantics { },
    ) {
        Canvas(Modifier.fillMaxSize()) { drawScene(rtl) }
    }
}

/**
 * The scene, in one oblique projection.
 *
 * Rows are drawn back to front so a piece standing on a near row overlaps the row behind it.
 * The projection is a plain shear rather than a true perspective: at this camera height the
 * difference is invisible and a shear cannot produce the warped, wrong-looking tiles that a
 * hand-tuned perspective does at the edges of a wide phone.
 */
private fun DrawScope.drawScene(rtl: Boolean) {
    val w = size.width
    val h = size.height
    fun mirror(x: Float) = if (rtl) w - x else x

    drawRect(Brush.verticalGradient(listOf(FloorShade, Color(0xFF0A0D11))))

    // The floor. Five ranks visible, the nearest widest — enough board to read as a board, and
    // the top of it runs out of the frame rather than stopping, so the room continues.
    val rows = 5
    val horizon = h * 0.06f
    val front = h * 0.86f
    val depth = front - horizon
    for (row in 0 until rows) {
        val nearT = (row + 1f) / rows
        val farT = row / rows.toFloat()
        val yFar = horizon + depth * farT * farT
        val yNear = horizon + depth * nearT * nearT
        // The nearer the rank, the wider it spreads past the frame.
        val spreadFar = 0.16f + farT * 0.5f
        val spreadNear = 0.16f + nearT * 0.5f
        val columns = 6
        for (column in 0 until columns) {
            val leftFar = w * (0.5f + (column - columns / 2f) * spreadFar / 2f)
            val rightFar = w * (0.5f + (column + 1 - columns / 2f) * spreadFar / 2f)
            val leftNear = w * (0.5f + (column - columns / 2f) * spreadNear / 2f)
            val rightNear = w * (0.5f + (column + 1 - columns / 2f) * spreadNear / 2f)
            // One light, from the top right: a tile's face brightens as it nears that corner.
            val lit = ((column + 1f) / columns) * 0.55f + (1f - nearT) * 0.45f
            val face = lerpColor(FloorShade, FloorLit, lit.coerceIn(0f, 1f))
            val tile = Path().apply {
                moveTo(mirror(leftFar), yFar)
                lineTo(mirror(rightFar), yFar)
                lineTo(mirror(rightNear), yNear)
                lineTo(mirror(leftNear), yNear)
                close()
            }
            drawPath(tile, face)
            // A sheen on the tiles nearest the lamp. Stone under a light has a
            // specular; without one the floor reads as paper.
            if (column >= columns - 3) {
                drawPath(tile, Color.White.copy(alpha = 0.05f * (column - columns + 3)))
            }
            drawPath(tile, SeamInk, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
        }
    }

    // Three walls, standing in the grooves between ranks. Placed so they read as a barrier the
    // light piece has had to go around — the picture is a position, not an arrangement.
    wall(mirror(w * 0.30f), h * 0.46f, w * 0.27f, h * 0.175f, rtl)
    wall(mirror(w * 0.64f), h * 0.37f, w * 0.23f, h * 0.150f, rtl)
    wall(mirror(w * 0.52f), h * 0.70f, w * 0.31f, h * 0.195f, rtl)

    // The two pieces. Dark stands near and left, bone stands far and right, which is the
    // diagonal the eye reads first and the one the walls interrupt.
    piece(mirror(w * 0.21f), h * 0.80f, h * 0.42f, PieceDark, PieceDarkLit)
    piece(mirror(w * 0.76f), h * 0.46f, h * 0.34f, PieceLight, PieceLightLit)

    // The light itself: a soft wash down from the top right, never a visible source.
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color(0x4DFFF3DC), Color(0x14FFF0D2), Color.Transparent),
            center = Offset(mirror(w * 0.86f), -h * 0.04f),
            radius = h * 1.05f,
        ),
    )

    drawRect(
        Brush.horizontalGradient(
            colors = listOf(PageInk.copy(alpha = 0.85f), Color.Transparent, Color.Transparent),
            startX = 0f,
            endX = w * 0.42f,
        ),
    )

    // And the picture ends in the page rather than at an edge.
    drawRect(
        Brush.verticalGradient(
            colors = listOf(Color.Transparent, PageInk.copy(alpha = 0.55f), PageInk),
            startY = h * 0.44f,
            endY = h * 1.02f,
        ),
    )
}

/**
 * A wall: a slab with a lit top edge and a shaded side.
 *
 * Three faces and no more. A slab drawn as one rectangle is a bar; the top edge is what makes
 * it an object with a thickness, and the thickness is the whole reason a wall reads as
 * something placed on the board rather than painted on it.
 */
private fun DrawScope.wall(centreX: Float, baseY: Float, width: Float, tall: Float, rtl: Boolean) {
    val half = width / 2f
    val lean = width * 0.16f * if (rtl) -1f else 1f
    val top = baseY - tall

    val face = Path().apply {
        moveTo(centreX - half, top)
        lineTo(centreX + half, top - tall * 0.16f)
        lineTo(centreX + half, baseY - tall * 0.16f)
        lineTo(centreX - half, baseY)
        close()
    }
    drawPath(face, WallFace)

    val cap = Path().apply {
        moveTo(centreX - half, top)
        lineTo(centreX + half, top - tall * 0.16f)
        lineTo(centreX + half + lean, top - tall * 0.16f - tall * 0.10f)
        lineTo(centreX - half + lean, top - tall * 0.10f)
        close()
    }
    drawPath(cap, WallTop)

    val side = Path().apply {
        moveTo(centreX + half, top - tall * 0.16f)
        lineTo(centreX + half + lean, top - tall * 0.26f)
        lineTo(centreX + half + lean, baseY - tall * 0.26f)
        lineTo(centreX + half, baseY - tall * 0.16f)
        close()
    }
    drawPath(side, WallSide)

    // The shadow it casts, thrown away from the light rather than straight down.
    drawOval(
        color = Color(0x66000000),
        topLeft = Offset(centreX - half - lean, baseY - tall * 0.05f),
        size = Size(width * 1.15f, tall * 0.16f),
    )
}

/**
 * A piece: base, waist, collar, head — the silhouette of a Koridor pawn, not a chess pawn.
 *
 * Built from stacked ellipses so it turns its lit side to the same corner every other object
 * does. The two-tone fill is the whole modelling; a flat disc reads as a counter, and a counter
 * on a board reads as draughts.
 */
private fun DrawScope.piece(centreX: Float, baseY: Float, tall: Float, body: Color, lit: Color) {
    val wide = tall * 0.52f
    val brush = Brush.linearGradient(
        colors = listOf(lit, body),
        start = Offset(centreX - wide * 0.5f, baseY - tall),
        end = Offset(centreX + wide * 0.6f, baseY),
    )

    // Contact shadow first, so the piece stands on the floor rather than floats over it.
    drawOval(
        color = Color(0x73000000),
        topLeft = Offset(centreX - wide * 0.72f, baseY - tall * 0.07f),
        size = Size(wide * 1.44f, tall * 0.15f),
    )

    // Base
    drawOval(
        brush = brush,
        topLeft = Offset(centreX - wide * 0.58f, baseY - tall * 0.24f),
        size = Size(wide * 1.16f, tall * 0.22f),
    )
    // Body: a tapered column from base to collar.
    val column = Path().apply {
        moveTo(centreX - wide * 0.44f, baseY - tall * 0.16f)
        cubicTo(
            centreX - wide * 0.34f, baseY - tall * 0.46f,
            centreX - wide * 0.30f, baseY - tall * 0.52f,
            centreX - wide * 0.26f, baseY - tall * 0.62f,
        )
        lineTo(centreX + wide * 0.26f, baseY - tall * 0.62f)
        cubicTo(
            centreX + wide * 0.30f, baseY - tall * 0.52f,
            centreX + wide * 0.34f, baseY - tall * 0.46f,
            centreX + wide * 0.44f, baseY - tall * 0.16f,
        )
        close()
    }
    drawPath(column, brush)
    // Collar
    drawOval(
        brush = brush,
        topLeft = Offset(centreX - wide * 0.40f, baseY - tall * 0.70f),
        size = Size(wide * 0.80f, tall * 0.13f),
    )
    // Head
    drawOval(
        brush = brush,
        topLeft = Offset(centreX - wide * 0.33f, baseY - tall),
        size = Size(wide * 0.66f, tall * 0.32f),
    )
    // The rim: a crescent of the lamp along the shoulder the light reaches. It is the
    // one mark that lifts a near-black piece off a near-black floor, and without it the
    // dark player was a silhouette nobody could find.
    drawArc(
        color = lit,
        startAngle = -70f,
        sweepAngle = 120f,
        useCenter = false,
        topLeft = Offset(centreX - wide * 0.33f, baseY - tall),
        size = Size(wide * 0.66f, tall * 0.32f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = tall * 0.022f),
    )
    drawArc(
        color = lit.copy(alpha = 0.75f),
        startAngle = -60f,
        sweepAngle = 95f,
        useCenter = false,
        topLeft = Offset(centreX - wide * 0.40f, baseY - tall * 0.70f),
        size = Size(wide * 0.80f, tall * 0.13f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = tall * 0.018f),
    )
}

/** Straight-line blend between two colours; enough for lighting a flat face. */
private fun lerpColor(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = 1f,
)
