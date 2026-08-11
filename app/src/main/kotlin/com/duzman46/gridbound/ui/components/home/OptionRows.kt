package com.duzman46.gridbound.ui.components.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold
import kotlin.math.roundToInt

/**
 * The card-of-rows that Settings and More are both made of.
 *
 * One component rather than two, because they are the same object: a mark, a name, a line saying
 * what it is for, and one of three things at the trailing end — a chevron if it opens something,
 * a fact if it only states something, a switch if it is a choice with two answers. Two hand-built
 * versions of that is how two screens in one app end up with rows of different heights.
 *
 * The rules between rows are drawn by the group, not by the rows, so a row cannot be added
 * without one and the last row can never carry a rule down into the card's own edge.
 */
data class OptionEntry(
    val icon: PremiumIcon,
    val title: String,
    val subtitle: String? = null,
    /** Shown at the trailing edge instead of a chevron. A row with one of these does nothing. */
    val value: String? = null,
    /** Non-null makes this a switch, and the whole row toggles it. */
    val checked: Boolean? = null,
    val onCheckedChange: ((Boolean) -> Unit)? = null,
    /** Non-null makes this a level from 0 to 100, shown as a slider with its reading beside it. */
    val level: Int? = null,
    val onLevelChange: ((Int) -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
)

@Composable
fun OptionGroup(entries: List<OptionEntry>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF12161B))
            .border(BorderStroke(1.dp, FieldEdge), shape),
    ) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        // Indented to where the text starts, so the rule separates the rows
                        // rather than cutting the column of marks in half.
                        .padding(start = 60.dp)
                        .height(Dimens.Hairline)
                        .background(Color(0xFF20262D)),
                )
            }
            OptionRow(entry)
        }
    }
}

@Composable
private fun OptionRow(entry: OptionEntry) {
    val toggle = entry.checked
    val onToggle = entry.onCheckedChange
    // A switch row is tappable across its whole width. Aiming for a 52 dp pill at the far edge
    // of a phone is a worse control than the row it sits in, and the row is right there.
    val press: (() -> Unit)? = when {
        toggle != null && onToggle != null -> ({ onToggle(!toggle) })
        else -> entry.onClick
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (press != null) {
                    Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = if (toggle != null) Role.Switch else Role.Button,
                            onClick = press,
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription =
                                listOfNotNull(entry.title, entry.subtitle).joinToString(". ")
                        }
                } else {
                    Modifier
                },
            )
            .heightIn(min = 66.dp)
            .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // In a tile rather than bare on the row. A mark alone on a wide row has nothing to sit
        // against and drifts; the tile gives the column of them a common edge, and the faint
        // gold ground under each is what makes a list of settings read as a set of objects
        // rather than as a page of text with pictures in the margin.
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(KoridorGold.copy(alpha = 0.10f))
                .border(BorderStroke(1.dp, KoridorGold.copy(alpha = 0.30f)), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            PremiumGlyph(entry.icon, Modifier.size(21.dp), tint = KoridorGold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7A7F86),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val level = entry.level
        val onLevel = entry.onLevelChange
        when {
            level != null && onLevel != null -> LevelSlider(level, onLevel)
            toggle != null -> GoldSwitch(toggle)
            entry.value != null -> Text(
                text = entry.value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF7A7F86),
                maxLines = 1,
            )

            entry.onClick != null -> OptionChevron()
        }
    }
}

/**
 * A level from nought to a hundred, and its reading.
 *
 * Material's slider brings a tick row, a value label that pops up over your thumb, and a ripple
 * the width of the track — three things to switch off before it looks like anything else on this
 * screen. What is left is a rail, a filled part, and a knob.
 *
 * Silence is nought rather than a separate switch. One control cannot disagree with itself, and
 * a slider at zero beside a toggle that says "on" is a screen lying to somebody.
 */
@Composable
private fun RowScope.LevelSlider(level: Int, onChange: (Int) -> Unit) {
    val reading = "%$level"
    var width by remember { mutableIntStateOf(1) }
    val knob = with(LocalDensity.current) { KNOB.toPx() }

    // The knob's centre travels between the two ends *inset by its own radius*, so at nought and
    // at a hundred it sits inside the rail rather than half off it. The reading therefore has to
    // be worked out over that shorter run, or the last few per cent are unreachable.
    val set: (Float) -> Unit = { x ->
        val travel = (width - knob).coerceAtLeast(1f)
        onChange((((x - knob / 2f) / travel) * 100f).roundToInt().coerceIn(0, 100))
    }

    Box(
        // A shade under the text's share: the name and the line under it are what the row is
        // for, and a slider that takes half the width leaves "Hamle, duvar ve sonuç sesleri"
        // wrapping to three lines.
        Modifier
            .weight(0.85f)
            .height(40.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            // Two gestures, because a slider has to answer both: a tap anywhere on the rail
            // jumps to that value, and a drag follows the finger. One detector cannot do both —
            // detectTapGestures consumes the down event a drag would have needed.
            .pointerInput(Unit) { detectTapGestures { set(it.x) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> set(change.position.x) }
            }
            .semantics { contentDescription = reading },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(KNOB)) {
            val radius = knob / 2f
            val travel = (size.width - knob).coerceAtLeast(1f)
            val centre = radius + travel * (level / 100f).coerceIn(0f, 1f)
            val y = size.height / 2f
            val rail = RAIL.toPx()
            drawLine(
                Color(0xFF2A3038),
                Offset(radius, y),
                Offset(size.width - radius, y),
                strokeWidth = rail,
                cap = StrokeCap.Round,
            )
            if (centre > radius) {
                drawLine(
                    KoridorGold,
                    Offset(radius, y),
                    Offset(centre, y),
                    strokeWidth = rail,
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(KoridorGold, radius, Offset(centre, y))
        }
    }
    Text(
        text = reading,
        modifier = Modifier.width(46.dp),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = KoridorGold,
        textAlign = TextAlign.End,
        maxLines = 1,
    )
}

/** The knob, and the rail it runs on. */
private val KNOB = 18.dp
private val RAIL = 4.dp

/**
 * The switch, drawn rather than themed.
 *
 * Material's has a thumb that grows when it is on, an outline when it is off, and a state layer
 * around it — three behaviours from a different design language, on a screen whose whole idiom is
 * a hairline on near-black. What is left when those go is a pill and a disc, which is all a
 * switch has ever been.
 *
 * It is not clickable itself: the row owns the press. A switch that is also its own touch target
 * gives the row two, and the smaller one always wins the ones aimed near it.
 */
@Composable
private fun GoldSwitch(checked: Boolean) {
    val track by animateColorAsState(
        targetValue = if (checked) KoridorGold else Color(0xFF262C34),
        animationSpec = tween(160),
        label = "switchTrack",
    )
    val offset by animateDpAsState(
        targetValue = if (checked) 22.dp else 0.dp,
        animationSpec = tween(160),
        label = "switchThumb",
    )
    Box(
        Modifier
            .size(width = 50.dp, height = 28.dp)
            .clip(CircleShape)
            .background(track)
            .border(
                BorderStroke(1.dp, if (checked) Color.Transparent else Color(0xFF3A424C)),
                CircleShape,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .offset(x = offset)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) Color(0xFF14181D) else Color(0xFF6F757D)),
        )
    }
}

/** The mark on a row that opens something. Mirrored in a right-to-left layout. */
@Composable
internal fun OptionChevron(tint: Color = Color(0xFF5C6169)) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        Modifier
            .size(17.dp)
            .scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f),
    ) {
        val s = size.minDimension
        drawPath(
            Path().apply {
                moveTo(s * 0.38f, s * 0.20f)
                lineTo(s * 0.68f, s * 0.50f)
                lineTo(s * 0.38f, s * 0.80f)
            },
            tint,
            style = Stroke(width = s * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * The small grey heading over a group.
 *
 * One of these rather than the three near-identical ones the badge shelf, the friends list and
 * this file each grew: same words, same weight, same colour, same job.
 */
@Composable
fun SectionLabel(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
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
            color = Color(0xFF9AA0A8),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF6F747B),
                maxLines = 1,
            )
        }
    }
}

/** A hairline the width of the group, for a note that belongs under one. */
@Composable
internal fun GroupNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceXs),
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF8B9098),
    )
}

