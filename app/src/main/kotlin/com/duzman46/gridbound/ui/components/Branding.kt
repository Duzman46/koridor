package com.duzman46.gridbound.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.theme.KoridorGold

/**
 * The app mark: the launcher tile, on the splash screen.
 *
 * Literally the same pixels. res/drawable-xxxhdpi/app_mark.webp and the icon's background layer
 * are two exports of one crop of one photograph — docs/store/app-icon.py cuts both — so the tile
 * a player taps and the tile that greets them a heartbeat later are the same object, down to the
 * grain on the board. That is the whole reason this is a picture and no longer a drawing: the
 * mark used to be a flat pawn between two bars while the icon was a shaded one, and they were
 * recognisably siblings rather than recognisably the same.
 *
 * The asset is the *masked* square — what a launcher leaves after it throws away the outer
 * sixth of the 108-unit canvas — so rounding it here reproduces the tile rather than re-cropping
 * it. One export at xxxhdpi and nothing below: this is drawn at a single size on a single screen,
 * and 432 pixels covers 104dp on the densest handset with room to spare.
 *
 * The hairline is not decoration. On the splash the ground is near-black and so is most of the
 * board in the picture, and without an edge the tile bleeds into the page and stops reading as
 * an object.
 */
@Composable
fun KoridorMark(modifier: Modifier = Modifier) {
    // A percentage, not a dp: the shape has to stay the launcher's shape at whatever size the
    // caller asks for, and a fixed radius stops being a squircle the moment the size moves.
    val shape = RoundedCornerShape(percent = 26)
    Image(
        painter = painterResource(R.drawable.app_mark),
        contentDescription = null,
        modifier = modifier
            .clip(shape)
            .border(1.dp, KoridorGold.copy(alpha = 0.20f), shape),
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
