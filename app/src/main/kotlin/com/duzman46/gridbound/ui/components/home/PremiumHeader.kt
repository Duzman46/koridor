package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold

/**
 * The top of a screen the player opened and will come back from.
 *
 * The arrow leads, because on a pushed screen it is true: this place was entered from somewhere,
 * and the way out is the first thing on the line. The three places the docked bar switches
 * between deliberately have no arrow at all — there is no "back" from a place, only the place
 * beside it — which is why this is a component and not a rule applied everywhere.
 *
 * The lobby and the play screen each keep a private arrow of their own. Theirs is drawn on a
 * photograph and sized to it; this one sits on the page.
 */
@Composable
fun PremiumHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PremiumBackArrow(onBack)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = KoridorGold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8B9098),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // One control, at most, and it belongs to the screen rather than to the row: the thing
        // this place exists to do, reachable whether the list under it is full or empty.
        trailing?.invoke()
    }
}

/** The arrow itself, mirrored in a right-to-left layout because it points the way out. */
@Composable
fun PremiumBackArrow(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val label = stringResource(R.string.action_back)
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onBack,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(24.dp)
                .scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f),
        ) {
            val s = size.minDimension
            drawPath(
                Path().apply {
                    moveTo(s * 0.92f, s * 0.5f)
                    lineTo(s * 0.12f, s * 0.5f)
                    moveTo(s * 0.44f, s * 0.18f)
                    lineTo(s * 0.12f, s * 0.5f)
                    lineTo(s * 0.44f, s * 0.82f)
                },
                KoridorGold,
                style = Stroke(width = s * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
