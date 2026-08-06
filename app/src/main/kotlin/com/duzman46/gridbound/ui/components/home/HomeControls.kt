package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duzman46.gridbound.theme.Dimens

/**
 * The one loud action. Everything else on the screen is quieter than this by design — when
 * every control is emphasised the player has to read all of them to find the way in.
 */
@Composable
fun PlaySlab(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = Dimens.SlabHeight),
        shape = RoundedCornerShape(Dimens.RadiusXs),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .pieceDepth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KoridorGlyph(
                GlyphKind.QUICK_PLAY,
                Modifier.size(Dimens.GlyphLg),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The two room actions, side by side.
 *
 * [IntrinsicSize.Min] on the row is what keeps them honest in German and Russian: when one
 * label wraps to two lines both halves grow together, instead of one clipping.
 */
@Composable
fun RoomPair(
    createLabel: String,
    joinLabel: String,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RoomButton(createLabel, GlyphKind.CREATE_ROOM, onCreate, Modifier.weight(1f))
        RoomButton(joinLabel, GlyphKind.JOIN_ROOM, onJoin, Modifier.weight(1f))
    }
}

@Composable
private fun RoomButton(
    label: String,
    glyph: GlyphKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight().heightIn(min = Dimens.RoomHeight),
        shape = RoundedCornerShape(Dimens.RadiusXs),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .pieceDepth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KoridorGlyph(
                glyph,
                Modifier.size(Dimens.GlyphMd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One of the quiet destinations. Outlined, not filled: it must not compete with the slab. */
@Composable
fun DestinationChip(
    label: String,
    glyph: GlyphKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = Dimens.ChipHeight),
        shape = RoundedCornerShape(20.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KoridorGlyph(
                glyph,
                Modifier.size(Dimens.GlyphSm),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                modifier = Modifier.widthIn(max = 160.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One quiet destination: its label, its glyph and where it goes. */
class Destination(
    val label: String,
    val glyph: GlyphKind,
    val onClick: () -> Unit,
)

/**
 * The tail of the menu.
 *
 * A wrapping row rather than a grid, and that is the point: the lines come out uneven,
 * because the labels are different lengths in every one of the ten languages. A fixed grid
 * of equal tiles is the shape that made this screen look generated.
 */
@Composable
fun DestinationChips(destinations: List<Destination>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        destinations.forEach { destination ->
            DestinationChip(destination.label, destination.glyph, destination.onClick)
        }
    }
}
