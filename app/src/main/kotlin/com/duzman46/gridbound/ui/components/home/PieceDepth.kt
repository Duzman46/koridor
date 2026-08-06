package com.duzman46.gridbound.ui.components.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Gives a surface the thickness of a game piece: catching the light along its top edge and
 * dropping into shadow along its bottom.
 *
 * Drawn rather than elevated. `shadowElevation` renders a real shadow every frame and, on a
 * screen whose largest element is a 175 dp panel, that is the only thing here that would
 * actually cost battery — and Material's ambient shadow has no light direction, so a column
 * of elevated surfaces looks inflated rather than solid. Two hairlines and one light source
 * do the job.
 *
 * Apply to the content **inside** a Surface, never to the Surface's own modifier: the
 * Surface's clip is what rounds these lips into its corners.
 */
fun Modifier.pieceDepth(): Modifier = drawWithContent {
    drawContent()
    val thickness = 2.dp.toPx()
    drawRect(
        color = Color.White.copy(alpha = 0.10f),
        size = Size(size.width, thickness),
    )
    drawRect(
        color = Color.Black.copy(alpha = 0.16f),
        topLeft = Offset(0f, size.height - thickness),
        size = Size(size.width, thickness),
    )
}
