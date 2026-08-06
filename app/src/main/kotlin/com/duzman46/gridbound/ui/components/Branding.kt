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
 * The app mark: a three-by-three board with two walls dropped into the channels and a pawn
 * that has to find its way past them.
 *
 * Deliberately the same composition as the launcher icon, so the thing on the home screen and
 * the thing at the top of the menu are recognisably one object. Drawn rather than shipped as
 * an asset: it costs nothing in the APK and it follows the theme into dark mode, which a
 * baked PNG of a dark tile could not do on a light menu.
 */
@Composable
fun KoridorMark(
    modifier: Modifier = Modifier,
    // Taken as parameters rather than read inside, so the mark can be placed on a surface
    // that is not the theme's — on a dark well, theme-primary walls would disappear.
    tile: Color = MaterialTheme.colorScheme.surfaceVariant,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier) {
        val side = size.minDimension
        val origin = Offset((size.width - side) / 2f, (size.height - side) / 2f)

        // Same 0.24 channel-to-tile ratio the real board uses.
        val cell = side / (3f + 0.24f * 2f)
        val gap = cell * 0.24f
        val step = cell + gap
        val radius = CornerRadius(cell * 0.18f)

        repeat(3) { row ->
            repeat(3) { column ->
                drawRoundRect(
                    color = tile,
                    topLeft = origin + Offset(column * step, row * step),
                    size = Size(cell, cell),
                    cornerRadius = radius,
                )
            }
        }

        val thickness = gap * 0.74f
        val wallSpan = cell * 2f + gap
        val wallRadius = CornerRadius(thickness / 2f)

        // Horizontal wall under the top-left pair — the one blocking the pawn's way forward.
        drawRoundRect(
            color = accent,
            topLeft = origin + Offset(0f, cell + (gap - thickness) / 2f),
            size = Size(wallSpan, thickness),
            cornerRadius = wallRadius,
        )
        // Vertical wall beside the bottom-right pair, so both orientations are in the mark.
        drawRoundRect(
            color = accent,
            topLeft = origin + Offset(cell + (gap - thickness) / 2f, step),
            size = Size(thickness, wallSpan),
            cornerRadius = wallRadius,
        )

        drawPawnMark(
            center = origin + Offset(cell / 2f, cell / 2f),
            unit = cell,
            color = accent,
        )
    }
}

/**
 * A pawn reduced to what still reads at 24 dp: a domed head over a flared base.
 *
 * Shared with the home screen's glyph family, so the pawn in the app mark, the pawn on the
 * "quick play" button and the pawn on the tutorial icon are all the same drawing.
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
