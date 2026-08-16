package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold

/**
 * The action button the pushed screens share.
 *
 * The app grew two button idioms and neither was wrong: a pill for the home tabs, where a button
 * sits in a list of soft cards, and a square-shouldered one for the board, where it sits on the
 * darkest surface the app draws. This is the second — because the screens that use it are the
 * ones either side of a match, and a control that changes shape between the board and the screen
 * that follows it reads as two apps.
 *
 * It exists as one composable rather than as a shape two screens each re-implement. The tutorial
 * and the winner screen were restyled by different hands at the same time, and the one control
 * they have in common is the one most likely to come out as two near-identical things — which is
 * exactly the seam an eye finds.
 *
 * **Filled means gold with near-black ink, and that is a contrast decision rather than a taste
 * one.** White on this gold is 1.9:1. The ink is the same `onPrimary` the theme already pairs
 * with it.
 */
@Composable
fun PremiumActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    busy: Boolean = false,
    icon: PremiumIcon? = null,
) {
    val shape = RoundedCornerShape(ActionRadius)
    val live = enabled && !busy
    val fill = when {
        !live -> Color(0xFF20262D)
        filled -> KoridorGold
        else -> Color(0xFF161B21)
    }
    val edge = when {
        !live -> Color(0xFF2A3038)
        filled -> KoridorGold
        else -> KoridorGold.copy(alpha = 0.45f)
    }
    val ink = when {
        !live -> Color(0xFF5C6169)
        filled -> Color(0xFF1A1206)
        else -> KoridorGold
    }
    Row(
        modifier
            .height(ActionHeight)
            .clip(shape)
            .background(fill)
            .border(Dimens.Hairline, edge, shape)
            .clickable(enabled = live, role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The spinner replaces the mark rather than joining it, so the row does not change width
        // the moment it is pressed — a button that reflows under the finger reads as a misfire.
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(Dimens.IconSm).clearAndSetSemantics { },
                color = ink,
                strokeWidth = 2.dp,
            )
        } else if (icon != null) {
            Box(Modifier.size(Dimens.IconSm)) {
                PremiumGlyph(icon, Modifier.size(Dimens.IconSm), ink)
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The quiet way out of a screen — "skip", and nothing that looks like a decision.
 *
 * A `TextButton` would have done the job and would have brought Material's ripple, its minimum
 * touch target and its own label metrics onto a screen that has none of those. What it has to be
 * is a word that is clearly pressable and clearly not the main action, which is a colour and a
 * weight, not a container.
 */
@Composable
fun PremiumTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = label,
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.RadiusXs))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            // Kept above the 48dp target the platform asks for, without a container to say so.
            .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceMd),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (enabled) KoridorGold else Color(0xFF5C6169),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

/**
 * The radius the board's controls already use, named here because three screens now depend on it
 * agreeing. A card is [Dimens.RadiusMd]; this is deliberately tighter, so a button reads as a
 * thing you press rather than a small card.
 */
val ActionRadius = 14.dp

/** Tall enough to clear the platform's touch target with the label centred in it. */
val ActionHeight = 48.dp
