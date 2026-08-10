package com.duzman46.gridbound.ui.components.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold

/**
 * The panel a player looks at while nothing is happening.
 *
 * It replaces a spinner and a sentence, and the difference is not decoration. Waiting for a
 * stranger is the one moment the app asks for patience without being able to say how much, and
 * the old panel gave a player no way to tell "still looking" from "this broke four minutes ago".
 * Everything here exists to answer that: a clock that is counting, a connection light that is
 * read from the database rather than assumed, and a wait to measure the clock against.
 *
 * The ring turns because a still panel and a stalled panel look the same. It is the only
 * perpetual animation in the app, it runs on one graphics layer, and it stops the moment the
 * panel leaves — which is the moment a match is found.
 */
@Composable
fun SearchingPanel(
    modifier: Modifier = Modifier,
    badge: String,
    title: String,
    subtitle: String,
    connectionLabel: String,
    stageLabel: String,
    elapsedLabel: String,
    elapsedValue: String,
    estimateLabel: String,
    estimateValue: String,
    /**
     * Null on a hosted room.
     *
     * A player waiting in the queue has nothing to do and something to read is a kindness. A
     * player hosting a room has a code to send somebody, and the tip is sixty-eight device-
     * independent pixels standing between them and it.
     */
    tip: String?,
    connected: Boolean,
    /**
     * What this particular wait has that the other one does not.
     *
     * A hosted room has a code to read out and friends to send it to; matchmaking has neither —
     * nobody is meant to join it. Rather than give the panel two shapes and a nullable list for
     * each, the caller passes the rows its own case actually has.
     */
    extra: @Composable () -> Unit = {},
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PanelFill)
            .border(BorderStroke(1.dp, PanelEdge), shape)
            .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        RankedBadge(badge)
        SearchRing()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF2F3F5),
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8B9098),
                textAlign = TextAlign.Center,
            )
        }
        ConnectionRow(connectionLabel, stageLabel, connected)
        DiamondRule()
        ElapsedBox(elapsedLabel, elapsedValue, estimateLabel, estimateValue)
        tip?.let { TipRow(it) }
        extra()
    }
}

/** The chip that says which kind of match this is. A crown, a word, a hairline ring. */
@Composable
private fun RankedBadge(label: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(BorderStroke(1.dp, KoridorGold.copy(alpha = 0.45f)), RoundedCornerShape(50))
            .padding(horizontal = Dimens.SpaceLg, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(16.dp)) { drawSmallCrown() }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = KoridorGold,
            maxLines = 1,
        )
    }
}

/**
 * The turning ring, with a pawn standing still inside it.
 *
 * The arc is a fixed sweep on a rotating layer rather than an animated sweep angle: rotation is
 * a property of the layer the GPU already has, so the whole thing costs one transform a frame
 * and never redraws its own path. The pawn is outside that layer, because a piece that spun
 * would look like it was falling over.
 */
@Composable
private fun SearchRing() {
    val turn by rememberInfiniteTransition(label = "queueRing").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "queueRingTurn",
    )
    // 124, not 168. The panel has to reach its own cancel button on the shortest phone the app
    // supports, and the ring is the one part of it that can give ground without losing meaning.
    Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(124.dp)) { drawRingTrack() }
        Canvas(Modifier.size(124.dp).rotate(turn)) { drawRingSweep() }
        Canvas(Modifier.size(42.dp)) { drawPawn(KoridorGold) }
    }
}

/** How things stand: the connection, and what the app is doing about it. */
@Composable
private fun ConnectionRow(connectionLabel: String, stageLabel: String, connected: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The one green in the app, and it earns it: this is a light, and a light that is
            // gold like everything else says nothing.
            Canvas(Modifier.size(14.dp)) {
                drawSignalBars(if (connected) SignalGood else SignalLost)
            }
            Text(
                text = connectionLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = if (connected) Color(0xFFB6BCC3) else SignalLost,
                maxLines = 1,
            )
        }
        Box(
            Modifier
                .height(16.dp)
                .width(Dimens.Hairline)
                .background(PanelEdge),
        )
        Text(
            text = stageLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8B9098),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A rule with a diamond in the middle of it, which is the panel's only ornament. */
@Composable
private fun DiamondRule() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(Dimens.Hairline)
                .background(PanelEdge),
        )
        Canvas(Modifier.size(9.dp)) { drawDiamond(KoridorGold.copy(alpha = 0.8f)) }
        Box(
            Modifier
                .weight(1f)
                .height(Dimens.Hairline)
                .background(PanelEdge),
        )
    }
}

/** The clock, and something to measure it against. */
@Composable
private fun ElapsedBox(
    elapsedLabel: String,
    elapsedValue: String,
    estimateLabel: String,
    estimateValue: String,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF0D1116))
            .border(BorderStroke(1.dp, PanelEdge), shape)
            .padding(vertical = Dimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCell(Modifier.weight(1f), elapsedLabel, elapsedValue) { drawClock() }
        Box(
            Modifier
                .height(38.dp)
                .width(Dimens.Hairline)
                .background(PanelEdge),
        )
        StatCell(Modifier.weight(1f), estimateLabel, estimateValue) { drawHourglass() }
    }
}

@Composable
private fun StatCell(
    modifier: Modifier,
    label: String,
    value: String,
    mark: DrawScope.() -> Unit,
) {
    Row(
        modifier.padding(horizontal = Dimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(22.dp)) { mark() }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.6.sp,
                color = Color(0xFF7A7F86),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = KoridorGold,
                maxLines = 1,
            )
        }
    }
}

/** One thing worth knowing, while there is nothing to do. */
@Composable
private fun TipRow(tip: String) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF10141A))
            .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        verticalAlignment = Alignment.Top,
    ) {
        Canvas(Modifier.size(18.dp)) { drawBulb() }
        Text(
            text = tip,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9AA0A8),
        )
    }
}

/** The closing line, under the way out. A shield, because it is about consequence. */
@Composable
fun PanelFootnote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(15.dp)) { drawShieldTick(Color(0xFF6F747B)) }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6F747B),
            textAlign = TextAlign.Center,
        )
    }
}

private val PanelFill = Brush.verticalGradient(listOf(Color(0xFF14181D), Color(0xFF0B0E12)))
private val PanelEdge = Color(0xFF2A3038)
private val SignalGood = Color(0xFF5FBF7A)
private val SignalLost = Color(0xFFC96A5E)

// ---------------------------------------------------------------------------------------------
// Marks. All drawn to the same weight as the rest of the app rather than imported from a set.
// ---------------------------------------------------------------------------------------------

private fun DrawScope.drawRingTrack() {
    val s = size.minDimension
    drawCircle(
        color = KoridorGold.copy(alpha = 0.22f),
        radius = s * 0.42f,
        style = Stroke(width = s * 0.016f),
    )
    // The faint lattice behind the piece: three lines each way, so the ring reads as a board
    // seen through a window rather than as a loading spinner.
    val inner = s * 0.30f
    listOf(-1f, 0f, 1f).forEach { step ->
        val offset = inner * 0.5f * step
        drawLine(
            KoridorGold.copy(alpha = 0.10f),
            Offset(s * 0.5f - inner + offset, s * 0.5f - inner - offset),
            Offset(s * 0.5f + inner + offset, s * 0.5f + inner - offset),
            strokeWidth = s * 0.006f,
        )
        drawLine(
            KoridorGold.copy(alpha = 0.10f),
            Offset(s * 0.5f - inner + offset, s * 0.5f + inner + offset),
            Offset(s * 0.5f + inner + offset, s * 0.5f - inner + offset),
            strokeWidth = s * 0.006f,
        )
    }
}

private fun DrawScope.drawRingSweep() {
    val s = size.minDimension
    val inset = s * 0.08f
    drawArc(
        brush = Brush.sweepGradient(
            0f to Color.Transparent,
            0.16f to KoridorGold,
            0.22f to Color(0xFFF3DFA8),
            0.30f to Color.Transparent,
            1f to Color.Transparent,
        ),
        startAngle = -90f,
        sweepAngle = 110f,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(s - inset * 2, s - inset * 2),
        style = Stroke(width = s * 0.022f, cap = StrokeCap.Round),
    )
    // The bead at the leading end, which is what the eye actually follows.
    val angle = Math.toRadians(20.0)
    val r = s * 0.42f
    drawDiamondAt(
        Offset(
            s * 0.5f + (r * Math.cos(angle)).toFloat(),
            s * 0.5f + (r * Math.sin(angle)).toFloat(),
        ),
        s * 0.030f,
        KoridorGold,
    )
}

private fun DrawScope.drawDiamond(tint: Color) =
    drawDiamondAt(Offset(size.minDimension * 0.5f, size.minDimension * 0.5f), size.minDimension * 0.5f, tint)

private fun DrawScope.drawDiamondAt(centre: Offset, radius: Float, tint: Color) {
    drawPath(
        Path().apply {
            moveTo(centre.x, centre.y - radius)
            lineTo(centre.x + radius, centre.y)
            lineTo(centre.x, centre.y + radius)
            lineTo(centre.x - radius, centre.y)
            close()
        },
        tint,
    )
}

/** The game's own piece, at whatever size it is asked for. */
internal fun DrawScope.drawPawn(tint: Color) {
    val s = size.minDimension
    drawCircle(tint, s * 0.155f, Offset(s * 0.5f, s * 0.26f))
    drawRoundRect(
        tint,
        Offset(s * 0.335f, s * 0.415f),
        Size(s * 0.33f, s * 0.07f),
        androidx.compose.ui.geometry.CornerRadius(s * 0.035f),
    )
    drawPath(
        Path().apply {
            moveTo(s * 0.375f, s * 0.50f)
            cubicTo(s * 0.355f, s * 0.64f, s * 0.315f, s * 0.70f, s * 0.275f, s * 0.755f)
            lineTo(s * 0.725f, s * 0.755f)
            cubicTo(s * 0.685f, s * 0.70f, s * 0.645f, s * 0.64f, s * 0.625f, s * 0.50f)
            close()
        },
        tint,
    )
    drawRoundRect(
        tint,
        Offset(s * 0.235f, s * 0.775f),
        Size(s * 0.53f, s * 0.085f),
        androidx.compose.ui.geometry.CornerRadius(s * 0.042f),
    )
}

private fun DrawScope.drawSmallCrown() {
    val s = size.minDimension
    drawPath(
        Path().apply {
            moveTo(s * 0.10f, s * 0.76f)
            lineTo(s * 0.04f, s * 0.24f)
            lineTo(s * 0.30f, s * 0.50f)
            lineTo(s * 0.50f, s * 0.14f)
            lineTo(s * 0.70f, s * 0.50f)
            lineTo(s * 0.96f, s * 0.24f)
            lineTo(s * 0.90f, s * 0.76f)
            close()
        },
        KoridorGold,
    )
}

private fun DrawScope.drawSignalBars(tint: Color) {
    val s = size.minDimension
    listOf(0.34f, 0.62f, 0.92f).forEachIndexed { index, height ->
        drawRoundRect(
            color = if (index == 2) tint.copy(alpha = 0.55f) else tint,
            topLeft = Offset(s * (0.08f + index * 0.33f), s * (1f - height)),
            size = Size(s * 0.20f, s * height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.05f),
        )
    }
}

private fun DrawScope.drawClock() {
    val s = size.minDimension
    val line = s * 0.085f
    drawCircle(KoridorGold, s * 0.42f, style = Stroke(line))
    drawLine(
        KoridorGold,
        Offset(s * 0.5f, s * 0.5f),
        Offset(s * 0.5f, s * 0.26f),
        strokeWidth = line,
        cap = StrokeCap.Round,
    )
    drawLine(
        KoridorGold,
        Offset(s * 0.5f, s * 0.5f),
        Offset(s * 0.70f, s * 0.58f),
        strokeWidth = line,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawHourglass() {
    val s = size.minDimension
    val line = s * 0.085f
    drawLine(KoridorGold, Offset(s * 0.22f, s * 0.12f), Offset(s * 0.78f, s * 0.12f), line, StrokeCap.Round)
    drawLine(KoridorGold, Offset(s * 0.22f, s * 0.88f), Offset(s * 0.78f, s * 0.88f), line, StrokeCap.Round)
    drawPath(
        Path().apply {
            moveTo(s * 0.28f, s * 0.14f)
            lineTo(s * 0.72f, s * 0.14f)
            lineTo(s * 0.52f, s * 0.50f)
            lineTo(s * 0.72f, s * 0.86f)
            lineTo(s * 0.28f, s * 0.86f)
            lineTo(s * 0.48f, s * 0.50f)
            close()
        },
        KoridorGold,
        style = Stroke(width = line * 0.85f),
    )
    // The sand that has already fallen.
    drawPath(
        Path().apply {
            moveTo(s * 0.34f, s * 0.80f)
            lineTo(s * 0.66f, s * 0.80f)
            lineTo(s * 0.50f, s * 0.58f)
            close()
        },
        KoridorGold.copy(alpha = 0.55f),
    )
}

private fun DrawScope.drawBulb() {
    val s = size.minDimension
    val line = s * 0.09f
    drawCircle(KoridorGold, s * 0.31f, Offset(s * 0.5f, s * 0.40f), style = Stroke(line))
    drawLine(
        KoridorGold,
        Offset(s * 0.38f, s * 0.74f),
        Offset(s * 0.62f, s * 0.74f),
        strokeWidth = line,
        cap = StrokeCap.Round,
    )
    drawLine(
        KoridorGold,
        Offset(s * 0.42f, s * 0.90f),
        Offset(s * 0.58f, s * 0.90f),
        strokeWidth = line,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawShieldTick(tint: Color) {
    val s = size.minDimension
    val line = s * 0.10f
    drawPath(
        Path().apply {
            moveTo(s * 0.5f, s * 0.06f)
            lineTo(s * 0.90f, s * 0.24f)
            lineTo(s * 0.90f, s * 0.54f)
            cubicTo(s * 0.90f, s * 0.78f, s * 0.71f, s * 0.90f, s * 0.5f, s * 0.96f)
            cubicTo(s * 0.29f, s * 0.90f, s * 0.10f, s * 0.78f, s * 0.10f, s * 0.54f)
            lineTo(s * 0.10f, s * 0.24f)
            close()
        },
        tint,
        style = Stroke(width = line),
    )
    drawPath(
        Path().apply {
            moveTo(s * 0.32f, s * 0.50f)
            lineTo(s * 0.45f, s * 0.64f)
            lineTo(s * 0.70f, s * 0.36f)
        },
        tint,
        style = Stroke(width = line, cap = StrokeCap.Round),
    )
}
