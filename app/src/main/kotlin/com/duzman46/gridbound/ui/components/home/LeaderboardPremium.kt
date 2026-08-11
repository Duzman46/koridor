package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold

/**
 * The standings, as a table with a stage in front of it.
 *
 * Three places get a card and everyone else gets a row, which is the whole argument of the
 * screen: a leaderboard that renders its first three the same way as its fortieth is a list, and
 * a list is not something anybody wants to be on. The metals do the ranking before the numbers
 * do — gold raised and crowned, silver and bronze flanking it a step lower.
 */

/** Gold, silver, bronze — and the ring, the rule and the number all take their colour from here. */
private fun placeMetal(place: Int): Color = when (place) {
    1 -> KoridorGold
    2 -> Color(0xFFB9C0C9)
    else -> Color(0xFFC08552)
}

/**
 * One of the three on the stage.
 *
 * @param place 1, 2 or 3. First is raised, wears a crown and carries the only filled ring.
 */
@Composable
fun RowScope.PodiumCard(
    place: Int,
    name: String,
    initial: String,
    rating: String,
    wins: String,
    winsLabel: String,
    losses: String,
    lossesLabel: String,
    onClick: () -> Unit,
) {
    val metal = placeMetal(place)
    val first = place == 1
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .weight(1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = "$place. $name. $rating" }
            // The winner stands a step higher. Everything else about the three is the same, so
            // this alone has to carry the ranking before a number is read.
            .padding(top = if (first) 0.dp else 26.dp)
            .clip(shape)
            .background(
                if (first) {
                    Brush.verticalGradient(listOf(Color(0xFF1E1a12), Color(0xFF0D1015)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF141920), Color(0xFF0D1015)))
                },
            )
            .border(BorderStroke(if (first) 1.5.dp else 1.dp, metal.copy(alpha = if (first) 0.75f else 0.35f)), shape)
            .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceMd),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        // The crest, and the head that sits into the bottom of it.
        //
        // Overlapped rather than stacked, which is what keeps the card short: the wreath and the
        // avatar share about a third of their height instead of each taking their own. Every
        // place is crowned in the reference, not only the winner — the metal does the ranking.
        val crest = if (first) 76.dp else 66.dp
        val head = if (first) 32.dp else 28.dp
        Box(
            // Tall enough for the two to overlap by a fraction rather than sit on top of each
            // other. At the first attempt the head was more than half the wreath and the pair
            // read as a figure of eight.
            Modifier.height(crest + head - 8.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.size(crest), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxWidth().height(crest)) { drawLaurelRing(metal) }
                Text(
                    text = "$place",
                    style = if (first) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.Bold,
                    color = metal,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .size(head)
                    .clip(CircleShape)
                    .background(Color(0xFF10151B))
                    .border(BorderStroke(2.dp, metal), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = metal,
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(13.dp)) { drawStar(metal) }
            Text(
                text = rating,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = metal,
                maxLines = 1,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(Dimens.Hairline)
                .background(Color(0xFF2A3038)),
        )
        // Two columns, an initial over a number, the way the reference sets them. Its third
        // column has no data behind it in this app, so it is not invented here.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PodiumFact(wins, winsLabel, WinGreen) { drawTinyTrophy(WinGreen) }
            PodiumFact(losses, lossesLabel, LossRed) { drawSwords(LossRed) }
        }
    }
}

@Composable
private fun PodiumFact(value: String, label: String, tint: Color, mark: DrawScope.() -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(11.dp)) { mark() }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF6F747B),
                maxLines = 1,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = tint,
            maxLines = 1,
        )
    }
}

/** The three ways to read the table, as a segmented control rather than Material's tab rule. */
@Composable
fun ScopeTabs(
    labels: List<String>,
    icons: List<PremiumIcon>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF0D1116))
            .border(BorderStroke(1.dp, Color(0xFF2A3038)), shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Row(
                Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    )
                    .semantics(mergeDescendants = true) { contentDescription = label }
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (active) KoridorGold.copy(alpha = 0.14f) else Color.Transparent)
                    .then(
                        if (active) {
                            Modifier.border(
                                BorderStroke(1.dp, KoridorGold.copy(alpha = 0.6f)),
                                RoundedCornerShape(13.dp),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .heightIn(min = 46.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PremiumGlyph(
                    icons[index],
                    Modifier.size(17.dp),
                    tint = if (active) KoridorGold else Color(0xFF7A7F86),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) KoridorGold else Color(0xFF7A7F86),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The table's column heads. Marks rather than words, for the same reason the rooms list uses them. */
@Composable
fun StandingsHeader(rankLabel: String, playerLabel: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rankLabel,
            modifier = Modifier.width(RANK_WIDTH),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF6F747B),
            maxLines = 1,
        )
        Text(
            text = playerLabel,
            modifier = Modifier
                .weight(1f)
                .padding(start = AVATAR_SIZE + Dimens.SpaceSm),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF6F747B),
            maxLines = 1,
        )
        // The three heads sit over their own columns, at exactly the widths the rows use.
        HeaderMark(WinGreen) { drawTinyTrophy(WinGreen) }
        HeaderMark(LossRed) { drawSwords(LossRed) }
        HeaderMark(KoridorGold) { drawStar(KoridorGold) }
    }
}

@Composable
private fun HeaderMark(tint: Color, mark: DrawScope.() -> Unit) {
    Box(Modifier.width(STAT_WIDTH), contentAlignment = Alignment.CenterStart) {
        Canvas(Modifier.size(13.dp)) { mark() }
    }
}

/**
 * The table's column widths, shared by the head and every row under it.
 *
 * They were three separate sets of numbers and the columns did not line up: the head's marks
 * sat over nothing in particular, and the three fixed stat columns between them left the name
 * about fifty pixels, so "suculent" printed as "suc…" on a screen with room to spare.
 */
private val RANK_WIDTH = 28.dp
private val AVATAR_SIZE = 32.dp
private val STAT_WIDTH = 52.dp

/** One standing. Same three columns as the head above it, in the same order. */
@Composable
fun StandingRow(
    rank: String,
    initial: String,
    name: String,
    wins: String,
    losses: String,
    rating: String,
    highlighted: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$rank. $name. $rating"
                        }
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(if (highlighted) KoridorGold.copy(alpha = 0.09f) else Color(0xFF12161B))
            .then(
                if (highlighted) {
                    Modifier.border(BorderStroke(1.dp, KoridorGold.copy(alpha = 0.55f)), shape)
                } else {
                    Modifier
                },
            )
            .heightIn(min = 60.dp)
            .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rank,
            modifier = Modifier.width(RANK_WIDTH),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF8B9098),
            maxLines = 1,
        )
        Box(
            Modifier
                .size(AVATAR_SIZE)
                .clip(CircleShape)
                .background(Color(0xFF2F6BE8)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Text(
            text = name,
            modifier = Modifier
                .weight(1f)
                .padding(start = Dimens.SpaceSm, end = Dimens.SpaceXs),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StatCell(wins, WinGreen) { drawTinyTrophy(WinGreen) }
        StatCell(losses, LossRed) { drawSwords(LossRed) }
        StatCell(rating, KoridorGold) { drawStar(KoridorGold) }
        // No chevron. The whole row is tappable and it was spending the width the names needed.
    }
}

@Composable
private fun StatCell(value: String, tint: Color, mark: DrawScope.() -> Unit) {
    Row(
        Modifier.width(STAT_WIDTH),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(12.dp)) { mark() }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The line at the foot of the table that says what the table is for. */
@Composable
fun ClimbBanner(
    title: String,
    hint: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    // Stacked, because the two of them were competing for one line and both lost.
    //
    // A crest, a title and a sentence beside a button is the shape this wants to be, and there
    // is not a phone narrow enough to make it fit: "SIRALAMADA YÜKS…" next to "Hesabını b…" is
    // a banner that explains why you are not on the table, unreadable, beside a button that
    // does not say what it does. The button takes the width it needs on a line of its own.
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF1A1710), Color(0xFF12161B))))
            .border(BorderStroke(1.dp, KoridorGold.copy(alpha = 0.35f)), shape)
            .padding(Dimens.SpaceMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(30.dp)) { drawCrestedTrophy() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = KoridorGold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8B9098),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        GoldSubmit(label = action, onClick = onAction)
    }
}

internal val WinGreen = Color(0xFF5FBF7A)
internal val LossRed = Color(0xFFD26A5E)

// ---------------------------------------------------------------------------------------------
// Marks
// ---------------------------------------------------------------------------------------------

/** A laurel wreath around a ring, crowned. Every place wears one; the metal does the ranking. */
private fun DrawScope.drawLaurelRing(metal: Color) {
    val s = size.minDimension
    val centre = Offset(s * 0.5f, s * 0.58f)
    val ring = s * 0.27f
    drawCircle(metal.copy(alpha = 0.9f), ring, centre, style = Stroke(s * 0.035f))
    drawCircle(metal.copy(alpha = 0.10f), ring - s * 0.018f, centre)

    drawPath(
        Path().apply {
            moveTo(s * 0.34f, s * 0.19f)
            lineTo(s * 0.30f, s * 0.03f)
            lineTo(s * 0.42f, s * 0.14f)
            lineTo(s * 0.50f, s * 0.00f)
            lineTo(s * 0.58f, s * 0.14f)
            lineTo(s * 0.70f, s * 0.03f)
            lineTo(s * 0.66f, s * 0.19f)
            close()
        },
        metal,
    )

    // Two arcs of leaves, mirrored, sweeping down from beside the ring to meet under it.
    val stemRadius = ring + s * 0.10f
    listOf(-1f, 1f).forEach { side ->
        for (index in 0 until 5) {
            val degrees = 40.0 + index * 25.0
            val x = centre.x + side * (stemRadius * Math.sin(Math.toRadians(degrees))).toFloat()
            val y = centre.y - (stemRadius * Math.cos(Math.toRadians(degrees))).toFloat()
            rotateLeaf(side, degrees, x, y, s, metal, index)
        }
    }
}

private fun DrawScope.rotateLeaf(
    side: Float,
    degrees: Double,
    x: Float,
    y: Float,
    s: Float,
    metal: Color,
    index: Int,
) {
    withTransform({
        translate(x, y)
        rotate(degrees = side * (degrees.toFloat() + 38f), pivot = Offset.Zero)
    }) {
        drawOval(
            color = metal.copy(alpha = 0.95f - index * 0.07f),
            topLeft = Offset(-s * 0.055f, -s * 0.024f),
            size = Size(s * 0.11f, s * 0.048f),
        )
    }
}

private fun DrawScope.drawStar(tint: Color) {
    val s = size.minDimension
    val path = Path()
    for (index in 0 until 10) {
        val radius = if (index % 2 == 0) s * 0.5f else s * 0.21f
        val angle = Math.toRadians((-90 + index * 36).toDouble())
        val x = s * 0.5f + (radius * Math.cos(angle)).toFloat()
        val y = s * 0.5f + (radius * Math.sin(angle)).toFloat()
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, tint)
}

private fun DrawScope.drawTinyTrophy(tint: Color) {
    val s = size.minDimension
    drawPath(
        Path().apply {
            moveTo(s * 0.26f, s * 0.10f)
            lineTo(s * 0.74f, s * 0.10f)
            lineTo(s * 0.74f, s * 0.40f)
            cubicTo(s * 0.74f, s * 0.60f, s * 0.63f, s * 0.68f, s * 0.50f, s * 0.68f)
            cubicTo(s * 0.37f, s * 0.68f, s * 0.26f, s * 0.60f, s * 0.26f, s * 0.40f)
            close()
        },
        tint,
    )
    drawRect(tint, Offset(s * 0.44f, s * 0.68f), Size(s * 0.12f, s * 0.14f))
    drawRoundRect(
        tint,
        Offset(s * 0.28f, s * 0.82f),
        Size(s * 0.44f, s * 0.10f),
        androidx.compose.ui.geometry.CornerRadius(s * 0.05f),
    )
}

private fun DrawScope.drawSwords(tint: Color) {
    val s = size.minDimension
    val line = s * 0.13f
    drawLine(tint, Offset(s * 0.14f, s * 0.86f), Offset(s * 0.82f, s * 0.14f), line, StrokeCap.Round)
    drawLine(tint, Offset(s * 0.86f, s * 0.86f), Offset(s * 0.18f, s * 0.14f), line, StrokeCap.Round)
}

private fun DrawScope.drawRowChevron() {
    val s = size.minDimension
    drawPath(
        Path().apply {
            moveTo(s * 0.36f, s * 0.16f)
            lineTo(s * 0.68f, s * 0.50f)
            lineTo(s * 0.36f, s * 0.84f)
        },
        KoridorGold.copy(alpha = 0.7f),
        style = Stroke(width = s * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawCalendar(tint: Color) {
    val s = size.minDimension
    val line = s * 0.09f
    drawRoundRect(
        tint,
        Offset(s * 0.10f, s * 0.20f),
        Size(s * 0.80f, s * 0.70f),
        androidx.compose.ui.geometry.CornerRadius(s * 0.12f),
        style = Stroke(line),
    )
    drawLine(tint, Offset(s * 0.10f, s * 0.42f), Offset(s * 0.90f, s * 0.42f), line * 0.9f)
    listOf(0.30f, 0.70f).forEach { x ->
        drawLine(tint, Offset(s * x, s * 0.08f), Offset(s * x, s * 0.26f), line, StrokeCap.Round)
    }
}

/** A trophy on a shield: the mark for climbing rather than for having arrived. */
private fun DrawScope.drawCrestedTrophy() {
    val s = size.minDimension
    drawPath(
        Path().apply {
            moveTo(s * 0.5f, s * 0.04f)
            lineTo(s * 0.92f, s * 0.23f)
            lineTo(s * 0.92f, s * 0.55f)
            cubicTo(s * 0.92f, s * 0.80f, s * 0.72f, s * 0.92f, s * 0.5f, s * 0.98f)
            cubicTo(s * 0.28f, s * 0.92f, s * 0.08f, s * 0.80f, s * 0.08f, s * 0.55f)
            lineTo(s * 0.08f, s * 0.23f)
            close()
        },
        KoridorGold,
        style = Stroke(width = s * 0.075f, join = StrokeJoin.Round),
    )
    drawPath(
        Path().apply {
            moveTo(s * 0.34f, s * 0.28f)
            lineTo(s * 0.66f, s * 0.28f)
            lineTo(s * 0.66f, s * 0.48f)
            cubicTo(s * 0.66f, s * 0.62f, s * 0.58f, s * 0.68f, s * 0.50f, s * 0.68f)
            cubicTo(s * 0.42f, s * 0.68f, s * 0.34f, s * 0.62f, s * 0.34f, s * 0.48f)
            close()
        },
        KoridorGold,
    )
    drawRoundRect(
        KoridorGold,
        Offset(s * 0.36f, s * 0.74f),
        Size(s * 0.28f, s * 0.07f),
        androidx.compose.ui.geometry.CornerRadius(s * 0.035f),
    )
}

/** Keeps the file's column helper usable from a Column scope without importing the scope. */
@Composable
fun ColumnScope.PodiumSpacer(height: Int) {
    Spacer(Modifier.height(height.dp))
}
