package com.duzman46.gridbound.ui.components

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.LocalKoridorColors
import com.duzman46.gridbound.ui.components.home.GlyphKind
import com.duzman46.gridbound.ui.components.home.KoridorGlyph

/**
 * How tall a block's content is allowed to get down to.
 *
 * Public because the home screen has one flexible element and four fixed ones, and it can only
 * work out how much room the flexible one gets by adding these up. A button height typed twice
 * is a button height that will drift. Each block also reserves [Dimens.PressTravel] underneath
 * these, which is the strip it sinks into.
 */
object BlockHeight {
    /** The one way forward on a screen. */
    val Loud = 72.dp

    /** Anything else that spans the column. */
    val Wide = 56.dp

    /** Text only. */
    val Text = 48.dp
}

/**
 * How loud a control is. At most one [PRIMARY] on a screen — when everything is emphasised
 * the player has to read all of it to find the way in.
 */
enum class BlockRank {
    /** The way forward. Filled with the accent. */
    PRIMARY,

    /** A real alternative. Outlined, not tinted — a tinted pill is the generic shape. */
    SECONDARY,

    /** A quiet option. Text only. */
    TERTIARY,
}

/**
 * The app's button.
 *
 * It sinks two device-independent pixels when pressed and comes back up more slowly than it
 * went down, which is what reads as pressing a physical thing rather than watching an
 * animation. There is no ripple: the travel *is* the acknowledgement, and layering a ripple
 * on top gives the same event two different, weaker signals.
 *
 * The outer box keeps a constant height — the travel moves placement, not measurement — so a
 * column of these cannot reflow while one of them is held down.
 */
@Composable
fun KoridorBlock(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rank: BlockRank = BlockRank.PRIMARY,
    glyph: GlyphKind? = null,
    enabled: Boolean = true,
    loud: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalKoridorColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sinks = pressed && enabled && rank != BlockRank.TERTIARY

    val travel by animateDpAsState(
        targetValue = if (sinks) Dimens.PressTravel else 0.dp,
        // Down fast, up slow. The asymmetry is the whole effect.
        animationSpec = tween(durationMillis = if (pressed) 40 else 90, easing = LinearEasing),
        label = "blockTravel",
    )

    val fill = when {
        !enabled -> extra.disabledFill
        rank == BlockRank.PRIMARY -> if (pressed) extra.primaryPressed else colors.primary
        rank == BlockRank.SECONDARY -> if (pressed) colors.surfaceVariant else Color.Transparent
        else -> if (pressed) colors.surfaceVariant else Color.Transparent
    }
    val ink = when {
        !enabled -> extra.disabledInk
        rank == BlockRank.PRIMARY -> colors.onPrimary
        rank == BlockRank.SECONDARY -> colors.onSurface
        else -> colors.secondary
    }
    val border = when {
        rank != BlockRank.SECONDARY -> null
        enabled -> BorderStroke(Dimens.BorderStrong, colors.outline)
        else -> BorderStroke(Dimens.BorderStrong, colors.outline.copy(alpha = 0.38f))
    }
    val wide = rank != BlockRank.TERTIARY
    val shape = RoundedCornerShape(if (wide) Dimens.RadiusSm else Dimens.RadiusXs)
    val minHeight = when {
        loud -> BlockHeight.Loud
        wide -> BlockHeight.Wide
        else -> BlockHeight.Text
    }

    Box(if (wide) modifier.fillMaxWidth() else modifier) {
        Box(
            Modifier
                // Read in the layout phase, not in composition: the travel animates every
                // frame a block is held, and the dp overload would recompose the whole block
                // on each of them to move it two pixels.
                .offset { IntOffset(x = 0, y = travel.roundToPx()) }
                // The room the press travels into, so the row below never moves.
                .padding(bottom = Dimens.PressTravel)
                .then(if (wide) Modifier.fillMaxWidth() else Modifier)
                .clip(shape)
                .background(fill)
                .then(if (border != null) Modifier.border(border, shape) else Modifier)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
        ) {
            Row(
                Modifier
                    .heightIn(min = minHeight)
                    .padding(horizontal = if (loud) 20.dp else 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                glyph?.let {
                    KoridorGlyph(
                        it,
                        Modifier.size(if (loud) Dimens.GlyphLg else Dimens.GlyphMd),
                        tint = ink,
                    )
                }
                Text(
                    text = label,
                    modifier = if (wide) Modifier.weight(1f) else Modifier,
                    style = if (loud) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
