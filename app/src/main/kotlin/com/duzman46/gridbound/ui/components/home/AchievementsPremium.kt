package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.achievements.domain.Achievement
import com.duzman46.gridbound.achievements.domain.AchievementTier
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.Palette

/**
 * The badge shelf.
 *
 * A locked badge is drawn, not hidden. Half of what an achievement list is for is telling a
 * player what there is to go and do, and a screen that only shows what has already been earned
 * says nothing at all to the player who has earned nothing. So every badge is on the shelf from
 * the first launch — dimmed, with its bar at zero and its target spelled out.
 *
 * Metal carries the tier, exactly as it does on the podium: bronze, silver, gold. That is the
 * whole visual grammar, and it is why the ladders share a mark — three robots in three metals
 * read as one thing to get better at, where three different drawings read as three errands.
 */

private fun tierMetal(tier: AchievementTier): Color = when (tier) {
    AchievementTier.GOLD -> Palette.Gold
    AchievementTier.SILVER -> Color(0xFFB9C0C9)
    AchievementTier.BRONZE -> Color(0xFFC08552)
}

/** The mark a badge wears. Shared inside a ladder, on purpose — see the file comment. */
internal fun Achievement.emblem(): PremiumIcon = when (this) {
    Achievement.FIRST_STEP, Achievement.REGULAR, Achievement.VETERAN -> PremiumIcon.GAMEPAD
    Achievement.FIRST_WIN, Achievement.TEN_WINS, Achievement.FIFTY_WINS -> PremiumIcon.TROPHY
    Achievement.SCHOLAR -> PremiumIcon.MORTARBOARD
    Achievement.FACE_TO_FACE -> PremiumIcon.PEOPLE
    Achievement.BOT_MEDIUM, Achievement.BOT_HARD, Achievement.BOT_EXPERT -> PremiumIcon.ROBOT
    Achievement.ONLINE_DEBUT, Achievement.ONLINE_WIN, Achievement.ONLINE_TEN -> PremiumIcon.GLOBE
    Achievement.STREAK_THREE, Achievement.STREAK_FIVE -> PremiumIcon.SHIELD_STAR
    Achievement.SWIFT -> PremiumIcon.BOLT
    Achievement.MARATHON -> PremiumIcon.BARS
}

/**
 * How far through the whole shelf this player is.
 *
 * The ring is the one piece of ornament on the screen and it earns its place: it answers the
 * question the player opened the screen with before they have read a single row.
 */
@Composable
fun AchievementSummary(
    unlocked: Int,
    total: Int,
    fraction: Float,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Palette.GoldBandEnd, Palette.Card)))
            .border(BorderStroke(1.dp, Palette.Gold.copy(alpha = 0.35f)), shape)
            .semantics(mergeDescendants = true) { contentDescription = "$title. $unlocked / $total" }
            .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawProgressRing(fraction) }
            Text(
                text = "$unlocked",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Palette.Gold,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A section of the shelf: what these badges have in common, and how many are earned. */
@Composable
fun AchievementGroupHeader(
    title: String,
    earned: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = Dimens.SpaceSm, bottom = Dimens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = localeUpper(title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Palette.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = earned,
            style = MaterialTheme.typography.labelMedium,
            color = Palette.InkMuted,
            maxLines = 1,
        )
    }
}

/**
 * One badge.
 *
 * The bar only appears on a badge that can show progress — a target of one is a door, not a
 * road, and a bar under it would be either empty or full and never anything else.
 */
@Composable
fun AchievementRow(
    title: String,
    description: String,
    tier: AchievementTier,
    emblem: PremiumIcon,
    unlocked: Boolean,
    progress: Int,
    target: Int,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val metal = tierMetal(tier)
    val shape = RoundedCornerShape(16.dp)
    val ladder = target > 1
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (unlocked) metal.copy(alpha = 0.07f) else Palette.Card)
            .border(
                BorderStroke(1.dp, if (unlocked) metal.copy(alpha = 0.5f) else Palette.Edge),
                shape,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $description. $progress / $target"
            }
            .heightIn(min = 74.dp)
            .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Medallion(metal, emblem, unlocked)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                // Dimmed rather than hidden. A locked badge still has to be readable, because
                // reading it is how the player finds out what to go and do.
                color = if (unlocked) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    Palette.InkMuted
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (ladder && !unlocked) {
                ProgressBar(fraction, metal)
            }
        }
        if (ladder && !unlocked) {
            Text(
                text = "$progress/$target",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.InkMuted,
                maxLines = 1,
            )
        }
    }
}

/** The disc a badge sits on: the mark inside a ring of its own metal, struck through when locked. */
@Composable
private fun Medallion(metal: Color, emblem: PremiumIcon, unlocked: Boolean) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(if (unlocked) metal.copy(alpha = 0.13f) else Palette.Inset)
            .border(
                BorderStroke(
                    if (unlocked) 1.5.dp else 1.dp,
                    if (unlocked) metal.copy(alpha = 0.8f) else Palette.Edge,
                ),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        PremiumGlyph(
            emblem,
            Modifier.size(22.dp),
            tint = if (unlocked) metal else Palette.InkGlyph,
        )
        if (unlocked) {
            // The tick hangs off the disc rather than replacing the mark: which badge this is
            // matters as much after it is earned as before.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(metal)
                    .border(BorderStroke(1.5.dp, Palette.Ground), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Canvas(Modifier.size(9.dp)) { drawTick(Palette.GoldInk) } }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float, metal: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(50))
            .background(Palette.Inset),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(metal.copy(alpha = 0.85f)),
        )
    }
}

/** The ring on the summary card: a full track, and the earned part of it swept in gold. */
private fun DrawScope.drawProgressRing(fraction: Float) {
    val s = size.minDimension
    val stroke = s * 0.09f
    val inset = stroke / 2f
    drawCircle(Palette.Edge, s / 2f - inset, style = Stroke(stroke))
    if (fraction > 0f) {
        drawArc(
            color = Palette.Gold,
            startAngle = -90f,
            sweepAngle = 360f * fraction.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(s - stroke, s - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
