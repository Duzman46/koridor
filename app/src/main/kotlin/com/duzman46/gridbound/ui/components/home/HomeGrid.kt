package com.duzman46.gridbound.ui.components.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.theme.AccentPalette
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorJade
import com.duzman46.gridbound.ui.components.AccentTile

/**
 * The home screen's two-by-two block of destinations.
 *
 * A stacked column of four full-width rows reads as a ranking, and these four are not ranked —
 * whichever the player wants, they want it as much as the other three. Side by side they are
 * peers, and the pair of rows costs roughly half the height four rows did, which is the room
 * the second line of each tile is paid for with.
 *
 * The tile is laid out vertically rather than as a small [com.duzman46.gridbound.ui.components.KoridorCard]:
 * at half the screen's width a horizontal card gives the text about 120 dp, and "Liderlik
 * Tablosu" alone wraps to three lines in that. Stacked, the glyph gets its own line and the
 * label gets the full width of the tile.
 */

/**
 * What a tile measures at default font scale, and therefore what the home screen has to budget
 * for two of them.
 *
 * Public for the same reason [com.duzman46.gridbound.ui.components.BlockHeight] is: the home
 * screen divides its height to the dp and cannot do that against a number typed in two files.
 *
 * It is a **measurement, not a preference**, and the first version of it was a guess that cost
 * the budget. `heightIn` sits outside the tile's padding, so it constrains the padded total and
 * never binds below the content's own height; what the tile actually measures is
 * `44 tile + 8 gap + 20 title + 2 + 16 subtitle + 32 padding + 2 travel`. Written out as that
 * sum so the next person to change the padding or the glyph size sees the number move with it.
 *
 * The single subtitle line in that sum is why [HomeTile] caps the subtitle at one line and why
 * the strings are short: a second line is 16 dp, twice, and the whole screen has 6 dp of slack.
 */
/**
 * The tinted square inside a tile.
 *
 * Smaller than the [com.duzman46.gridbound.ui.components.TileSize] a full-width card carries,
 * and that is the whole difference between the two shapes: at half the screen's width the
 * glyph is competing with the label for the same line, so it gives way. Two stacked text lines
 * measure 20 + 2 + 16 = 38, so a 38 dp box is also exactly as tall as the text beside it —
 * the tile has one content height, not two.
 *
 * Declared before [HomeTileHeight] because a file's properties initialise in the order they are
 * written; below it, the height would be computed against a zero.
 */
internal val TileGlyphBox = 38.dp

val HomeTileHeight = TileGlyphBox + (Dimens.SpaceMd * 2) + Dimens.PressTravel

/** Two tiles that share a row and always measure the same height. */
@Composable
fun HomeGridRow(
    left: @Composable RowScope.() -> Unit,
    right: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        left()
        right()
    }
}

/**
 * One tile of the grid.
 *
 * Presses exactly the way every other control in the app does — down two pixels quickly, back
 * up slowly — so a grid and a button on the same screen do not feel like two apps.
 */
