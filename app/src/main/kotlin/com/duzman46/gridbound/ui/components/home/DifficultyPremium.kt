package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.Palette

/**
 * How hard the bot plays, as four things you can tell apart at a glance.
 *
 * Four rows of the same weight would be four rows nobody reads to the end, so each carries three
 * separate signals: a mark whose subject climbs — a piece, a balance, a tower, a crown — a
 * sentence saying what the match will feel like, and a row of pips that fills up. The pips are
 * the only part that is strictly ordinal, and they are what a player actually scans.
 *
 * No "recommended" or "standard" badge. The owner cut both, and they were right to: a label
 * telling somebody which difficulty to want is the screen answering a question it just asked.
 *
 * `selectable` rather than `clickable(role = Role.RadioButton)`. The role alone names the control
 * without ever saying which of the four is chosen, and the only other signal here is a gold
 * border — so to a screen reader the four rows were identical, and the answer to "which bot am I
 * about to play" was carried by colour and nothing else. `selectable` puts the choice in the
 * semantics tree, which is the same fix [com.duzman46.gridbound.ui.components.LanguagePicker]
 * already uses.
 */
@Composable
fun BotLevelRow(
    title: String,
    subtitle: String,
    /** Where this sits in the four, from 1. Fills that many pips. */
    rank: Int,
    chosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .selectable(
                selected = chosen,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = "$title. $subtitle" }
            .clip(shape)
            .background(if (chosen) Palette.Gold.copy(alpha = 0.07f) else Palette.Card)
            .border(BorderStroke(if (chosen) 1.5.dp else 1.dp, if (chosen) Palette.Gold else Palette.Edge), shape)
            // A floor, not a height. The screen no longer scrolls, so four of these share what
            // the scene and the colour cards leave — and on a short phone that is less than the
            // ninety-two a fixed row would have insisted on.
            .heightIn(min = 74.dp)
            .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(46.dp)) { drawBotMark(rank) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (chosen) Palette.Gold else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(BOT_LEVELS) { index ->
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (index < rank) Palette.Gold else Palette.InkGlyph),
                    )
                }
            }
            if (chosen) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Palette.Gold),
                    contentAlignment = Alignment.Center,
                ) { Canvas(Modifier.size(15.dp)) { drawTick(Palette.GoldInk) } }
            }
        }
    }
}

/** How many rungs the ladder has. The pip row and the marks both count to this. */
const val BOT_LEVELS = 4

/**
 * One mark per rung, and the subject climbs with it.
 *
 * A piece, a balance, a tower, a crown: a beginner's opponent, a fair one, a fortified one, and
 * one that outranks you. Drawn rather than imported, at the same weight as the rest of the app.
 */
private fun DrawScope.drawBotMark(rank: Int) {
    when (rank) {
        1 -> drawPawn(Palette.Gold.copy(alpha = 0.85f))
        2 -> drawScales()
        3 -> drawTower()
        else -> drawBigCrown()
    }
}

/** A balance, level. The bot that plays you fairly. */
private fun DrawScope.drawScales() {
    val s = size.minDimension
    val line = s * 0.055f
    val tint = Palette.Gold
    // The column and its foot.
    drawLine(tint, Offset(s * 0.5f, s * 0.16f), Offset(s * 0.5f, s * 0.80f), line, StrokeCap.Round)
    drawRoundRect(tint, Offset(s * 0.30f, s * 0.80f), Size(s * 0.40f, s * 0.07f), CornerRadius(s * 0.035f))
    drawCircle(tint, s * 0.055f, Offset(s * 0.5f, s * 0.14f))
    // The beam.
    drawLine(tint, Offset(s * 0.16f, s * 0.30f), Offset(s * 0.84f, s * 0.30f), line, StrokeCap.Round)
    // Two pans hanging level, which is the whole point of the mark.
    listOf(0.16f, 0.84f).forEach { x ->
        drawLine(
            tint.copy(alpha = 0.7f),
            Offset(s * x, s * 0.30f),
            Offset(s * x, s * 0.44f),
            strokeWidth = line * 0.6f,
        )
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(s * (x - 0.13f), s * 0.38f),
            size = Size(s * 0.26f, s * 0.20f),
            style = Stroke(width = line * 0.9f, cap = StrokeCap.Round),
        )
    }
}

/** A tower with battlements. The bot that has dug in. */
private fun DrawScope.drawTower() {
    val s = size.minDimension
    val tint = Palette.Gold
    drawPath(
        Path().apply {
            moveTo(s * 0.24f, s * 0.30f)
            lineTo(s * 0.24f, s * 0.18f)
            lineTo(s * 0.36f, s * 0.18f)
            lineTo(s * 0.36f, s * 0.26f)
            lineTo(s * 0.44f, s * 0.26f)
            lineTo(s * 0.44f, s * 0.18f)
            lineTo(s * 0.56f, s * 0.18f)
            lineTo(s * 0.56f, s * 0.26f)
            lineTo(s * 0.64f, s * 0.26f)
            lineTo(s * 0.64f, s * 0.18f)
            lineTo(s * 0.76f, s * 0.18f)
            lineTo(s * 0.76f, s * 0.30f)
            lineTo(s * 0.66f, s * 0.40f)
            lineTo(s * 0.66f, s * 0.72f)
            lineTo(s * 0.34f, s * 0.72f)
            lineTo(s * 0.34f, s * 0.40f)
            close()
        },
        tint,
    )
    drawRoundRect(tint, Offset(s * 0.22f, s * 0.74f), Size(s * 0.56f, s * 0.10f), CornerRadius(s * 0.05f))
}

/** A crown, filled. The bot that outranks you. */
private fun DrawScope.drawBigCrown() {
    val s = size.minDimension
    val tint = Palette.Gold
    drawPath(
        Path().apply {
            moveTo(s * 0.16f, s * 0.68f)
            lineTo(s * 0.10f, s * 0.26f)
            lineTo(s * 0.32f, s * 0.46f)
            lineTo(s * 0.50f, s * 0.16f)
            lineTo(s * 0.68f, s * 0.46f)
            lineTo(s * 0.90f, s * 0.26f)
            lineTo(s * 0.84f, s * 0.68f)
            close()
        },
        tint,
    )
    drawRoundRect(tint, Offset(s * 0.16f, s * 0.72f), Size(s * 0.68f, s * 0.10f), CornerRadius(s * 0.05f))
    // Three stones set along it, because a flat crown reads as a bar chart.
    listOf(0.28f, 0.5f, 0.72f).forEach { x ->
        drawCircle(Palette.Card, s * 0.035f, Offset(s * x, s * 0.58f))
    }
}
