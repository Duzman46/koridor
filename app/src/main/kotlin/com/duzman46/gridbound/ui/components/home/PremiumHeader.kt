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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.Palette

/**
 * The top of every screen in the app — the one the app had five of.
 *
 * The arrow leads when there is one, because on a pushed screen it is true: this place was
 * entered from somewhere, and the way out is the first thing on the line. The three places the
 * docked bar switches between have no arrow at all — there is no "back" from a place, only the
 * place beside it — and that is what a null [onBack] means. It used to mean a second header
 * hand-built per screen, which is how one gold title ended up drawn at three font weights.
 *
 * [PremiumBackArrow] is the app's only back arrow. The lobby and the play screen each kept a
 * private copy, and each carried a comment explaining why it had to stay private; the two copies
 * were byte-for-byte the same box, the same canvas, the same right-to-left flip and the same path
 * to the third decimal, so neither comment was true of the code under it. They call this one now.
 *
 * The header is a row of slots rather than a fixed shape, so the screens that sit it over a
 * photograph do not have to rebuild it: [overline] takes whatever belongs above the name, and the
 * caller's [modifier] carries the inset — `statusBarsPadding()` over [HomeHero], for instance.
 */
@Composable
fun PremiumHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    overline: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onBack?.let { PremiumBackArrow(it) }
        Column(
            Modifier
                .weight(1f)
                // Without an arrow in front of it the title would start 8dp from the edge, and
                // every other first pixel on these screens starts at ScreenPadding — which is
                // also where the arrow's own glyph lands. The two cases line up rather than
                // merely both looking deliberate.
                .padding(start = if (onBack == null) Dimens.SpaceMd else 0.dp),
        ) {
            overline?.invoke()
            Text(
                text = title,
                // The screen's name, marked as what it is. TalkBack's navigate-by-heading gesture
                // had nothing to land on anywhere in the app, so a player using it was left
                // stepping through every control on a screen to reach the top of the next block.
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Palette.Gold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.InkMuted,
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
                Palette.Gold,
                style = Stroke(width = s * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
