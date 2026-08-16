package com.duzman46.gridbound.ui.components.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold

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
    //
    // `toggleable` rather than `clickable(role = Role.Switch)`, and that is not a tidy-up.
    // `clickable` sets the role and nothing else, so the node carried no ToggleableState: a
    // screen reader read "Sesler, switch" with no on or off in it, and said nothing at all after
    // a tap, because as far as the semantics tree was concerned nothing had changed. `toggleable`
    // supplies the state and the state change announcement for free, and it governs all four of
    // these rows — sounds, haptics, match messages, notifications.
    val action = if (toggle != null && onToggle != null) null else entry.onClick
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                when {
                    toggle != null && onToggle != null ->
                        Modifier
                            .toggleable(
                                value = toggle,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Switch,
                                onValueChange = onToggle,
                            )
                            .semantics(mergeDescendants = true) {
                                contentDescription =
                                    listOfNotNull(entry.title, entry.subtitle).joinToString(". ")
                            }

                    action != null ->
                        Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = action,
                            )
                            .semantics(mergeDescendants = true) {
                                contentDescription =
                                    listOfNotNull(entry.title, entry.subtitle).joinToString(". ")
                            }

                    else -> Modifier
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
        when {
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
 *
 * Marked as a heading, which is what makes TalkBack's navigate-by-heading gesture do anything on
 * a settings screen: it is a long column of near-identical rows, and skipping to the next group
 * is the only way to cross it that is not row by row.
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
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
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

