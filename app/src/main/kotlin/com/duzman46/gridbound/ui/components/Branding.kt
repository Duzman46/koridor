package com.duzman46.gridbound.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.duzman46.gridbound.R

/**
 * The app mark: the launcher tile, on the splash screen.
 *
 * Literally the same pixels. res/drawable-xxxhdpi/app_mark.webp and the icon's background layer
 * are two exports of one crop of one file — docs/store/app-icon.py cuts both — so the tile a
 * player taps and the tile that greets them a heartbeat later are the same object. That is the
 * whole reason this is a picture and no longer a drawing: the mark used to be a flat pawn between
 * two bars while the icon was a shaded one, and they were recognisably siblings rather than
 * recognisably the same.
 *
 * The asset is the *masked* square — what a launcher leaves after it throws away the outer sixth
 * of the 108-unit canvas. One export at xxxhdpi and nothing below: this is drawn at a single size
 * on a single screen, and 432 pixels covers 104dp on the densest handset with room to spare.
 *
 * **No border and no rounding of its own beyond the source's.** The artwork arrives with a gold
 * bezel already drawn on it at a 14.5% corner radius, so the clip below traces that corner rather
 * than imposing a second one — a squircle at 26%, which is what this used when the mark was a
 * photograph, would cut across the metal and leave four gaps in the frame. A hairline on top of
 * the bezel would be a second frame for the same reason.
 */
@Composable
fun KoridorMark(modifier: Modifier = Modifier) {
    // Matches BAKED_RADIUS in docs/store/app-icon.py. A percentage rather than a dp, so the
    // corner stays the source's corner at whatever size the caller asks for.
    Image(
        painter = painterResource(R.drawable.app_mark),
        contentDescription = null,
        modifier = modifier.clip(RoundedCornerShape(percent = 14)),
        contentScale = ContentScale.Crop,
    )
}

/**
 * A pawn reduced to what still reads at 24 dp: a domed head over a flared base.
 *
 * Shared with the home screen's glyph family and the profile crest, so the pawn on the
 * "quick play" button, the tutorial icon and the crest are all the same drawing. Nothing to do
 * with [KoridorMark] any more — that is a photograph now, and this has to be a shape the theme
 * can recolour and the caller can place by its centre at whatever size it has.
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
