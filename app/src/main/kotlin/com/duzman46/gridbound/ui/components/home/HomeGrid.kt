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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.theme.AccentPalette
import com.duzman46.gridbound.theme.Dimens
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
 */
val HomeTileHeight = 116.dp

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
@Composable
fun RowScope.HomeTile(
    title: String,
    subtitle: String,
    glyph: GlyphKind,
    tone: AccentPalette.Tone,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val travel by animateDpAsState(
        targetValue = if (pressed) Dimens.PressTravel else 0.dp,
        animationSpec = tween(durationMillis = if (pressed) 40 else 90, easing = LinearEasing),
        label = "tileTravel",
    )
    val shape = RoundedCornerShape(Dimens.RadiusMd)
    val spoken = "$title. $subtitle"

    Box(Modifier.weight(1f)) {
        Column(
            Modifier
                .offset { IntOffset(x = 0, y = travel.roundToPx()) }
                .padding(bottom = Dimens.PressTravel)
                .fillMaxWidth()
                .heightIn(min = HomeTileHeight - Dimens.PressTravel)
                .clip(shape)
                .background(if (pressed) colors.surfaceVariant else colors.surface)
                .border(BorderStroke(Dimens.Hairline, colors.outlineVariant), shape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics(mergeDescendants = true) { contentDescription = spoken }
                .padding(Dimens.SpaceLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            AccentTile(glyph, tone)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The line under the wordmark.
 *
 * Three verbs in the order the game is actually played: you plan a route, you block theirs, and
 * one of you gets home first. It is the only sentence on the home screen, and it is there
 * because the name alone does not say what the app is to somebody who has just installed it.
 */
@Composable
fun HomeTagline(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.home_tagline),
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
