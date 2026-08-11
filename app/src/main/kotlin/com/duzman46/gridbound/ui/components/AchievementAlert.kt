package com.duzman46.gridbound.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.R
import com.duzman46.gridbound.presentation.achievements.AchievementAlertViewModel
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold
import com.duzman46.gridbound.ui.components.home.PremiumGlyph
import com.duzman46.gridbound.ui.components.home.emblem
import kotlinx.coroutines.delay

/**
 * A badge just earned, said once, over whatever the player is looking at.
 *
 * Hung above the navigation graph for the same reason the request bar is: the moment a badge is
 * won is the moment a match ends, and the app is mid-navigation between the board and the winner
 * screen right then. A notice owned by either of those two screens would be composed and thrown
 * away inside the same second.
 *
 * [enabled] is false on the board. A badge can be earned by the move that ends a match, and the
 * board is the one screen where a card sliding in from the top is in the way of something.
 *
 * It goes away by itself. A notice that has to be dismissed is a notice that interrupts twice.
 */
@Composable
fun AchievementAlert(
    enabled: Boolean,
    onOpen: () -> Unit,
    viewModel: AchievementAlertViewModel = hiltViewModel(),
) {
    val fresh by viewModel.fresh.collectAsStateWithLifecycle()
    val badge = fresh.firstOrNull()

    LaunchedEffect(badge, enabled) {
        if (badge != null && enabled) {
            delay(ALERT_MILLIS)
            viewModel.dismiss()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = badge != null && enabled,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            // Read after the animation starts, so the card keeps its words on the way out.
            val shown = remember(badge) { badge }
            if (shown != null) {
                val title = stringResource(shown.titleRes)
                val headline = stringResource(R.string.achievement_unlocked)
                val shape = RoundedCornerShape(18.dp)
                Row(
                    Modifier
                        .statusBarsPadding()
                        .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceSm)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF221C10), Color(0xFF141A20)),
                            ),
                        )
                        .border(BorderStroke(1.dp, KoridorGold.copy(alpha = 0.6f)), shape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = {
                                viewModel.dismiss()
                                onOpen()
                            },
                        )
                        // A tap on the card is not a tap on whatever it is covering.
                        .pointerInput(Unit) { detectTapGestures { } }
                        .semantics(mergeDescendants = true) {
                            liveRegion = LiveRegionMode.Polite
                        }
                        .heightIn(min = 66.dp)
                        .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(KoridorGold.copy(alpha = 0.14f))
                            .border(BorderStroke(1.dp, KoridorGold.copy(alpha = 0.7f)), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        PremiumGlyph(shown.emblem(), Modifier.size(21.dp), tint = KoridorGold)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.labelMedium,
                            color = KoridorGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // More than one at a time happens: finishing a fiftieth match can complete
                    // two ladders at once. The rest are counted rather than queued — a stack of
                    // cards one after another is a celebration nobody asked for.
                    if (fresh.size > 1) {
                        Text(
                            text = stringResource(
                                R.string.achievement_unlocked_more,
                                fresh.size - 1,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = KoridorGold,
                            maxLines = 1,
                        )
                    }
                    Canvas(Modifier.size(16.dp)) { drawAlertChevron() }
                }
            }
        }
    }
}

/** Long enough to read two lines, short enough that nobody waits for it. */
private const val ALERT_MILLIS = 4_000L

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAlertChevron() {
    val s = size.minDimension
    drawPath(
        androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.38f, s * 0.20f)
            lineTo(s * 0.68f, s * 0.50f)
            lineTo(s * 0.38f, s * 0.80f)
        },
        KoridorGold.copy(alpha = 0.75f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = s * 0.13f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        ),
    )
}
