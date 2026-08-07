package com.duzman46.gridbound.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * The app mark: a pawn standing in a corridor.
 *
 * The same shape as the launcher icon, on the same 108-unit canvas and to the same
 * coordinates — see res/drawable/ic_launcher_foreground.xml. One drawing for one app: a player
 * who meets a different mark on the splash screen from the one they tapped to get there
 * remembers neither.
 *
 * Flat where the launcher is shaded, and that is not a divergence. The icon's gradients are
 * lit for one fixed jade plate; this mark takes its two colours from the caller and lands on a
 * near-black page, a pale one or a dark well, and highlights mixed for one of those are wrong
 * on the other two. The silhouette is what makes it the same mark, so the silhouette is what
 * is shared.
 *
 * Drawn rather than shipped as an asset so it can follow the theme at all, and so it costs
 * nothing at five densities.
 */
@Composable
fun KoridorMark(
    modifier: Modifier = Modifier,
    // Taken as parameters rather than read inside, so the mark can be placed on a surface
    // that is not the theme's — on a dark well, theme-primary walls would disappear.
    wall: Color = MaterialTheme.colorScheme.primary,
    pawn: Color = MaterialTheme.colorScheme.onBackground,
) {
    Canvas(modifier) {
        val side = size.minDimension
        val unit = side / CANVAS
        val origin = Offset((size.width - side) / 2f, (size.height - side) / 2f)

        fun at(x: Float, y: Float) = origin + Offset(x * unit, y * unit)
        fun span(value: Float) = value * unit

        /** A capsule between two corners, the way the vector's arc pairs describe one. */
        fun capsule(left: Float, top: Float, right: Float, bottom: Float, color: Color) {
            val width = span(right - left)
            val height = span(bottom - top)
            drawRoundRect(
                color = color,
                topLeft = at(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(minOf(width, height) / 2f),
            )
        }

        // The two corridor walls.
        capsule(27f, 26f, 35f, 82f, wall)
        capsule(73f, 26f, 81f, 82f, wall)

        // The pawn: head, collar, narrow neck, flared base.
        drawCircle(pawn, span(11f), at(54f, 42f))
        capsule(48f, 54f, 60f, 60f, pawn)
        capsule(49.5f, 57f, 58.5f, 72f, pawn)
        capsule(36f, 67f, 72f, 81f, pawn)
    }
}

/** The adaptive-icon canvas the mark is authored on, shared with the launcher vectors. */
private const val CANVAS = 108f

/**
 * A pawn reduced to what still reads at 24 dp: a domed head over a flared base.
 *
 * Shared with the home screen's glyph family and the profile crest, so the pawn on the
 * "quick play" button, the tutorial icon and the crest are all the same drawing. Kept
 * separate from [KoridorMark] because that one is authored on the launcher's 108-unit
 * canvas, and a glyph is placed by its centre at whatever size the caller has.
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPawnMark(
    center: Offset,
    unit: Float,
    color: Color,
) {
    val headRadius = unit * 0.17f
    drawCircle(color, headRadius, Offset(center.x, center.y - unit * 0.11f))

    val baseWidth = unit * 0.5f
    val baseHeight = unit * 0.14f
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - baseWidth / 2f, center.y + unit * 0.1f),
        size = Size(baseWidth, baseHeight),
        cornerRadius = CornerRadius(baseHeight / 2f),
    )
    // The taper between the two, which is what separates a pawn from a lollipop.
    val neck = unit * 0.2f
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - neck / 2f, center.y - unit * 0.02f),
        size = Size(neck, unit * 0.14f),
        cornerRadius = CornerRadius(neck / 3f),
    )
}
