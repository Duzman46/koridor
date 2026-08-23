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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.Palette

/**
 * The lobby's forms and dialogs, in the same material as the screen behind them.
 *
 * Material's own text fields and buttons were fine and looked borrowed: a filled field with a
 * floating label and a tonal button belong to a different design than a gold hairline on
 * near-black. These are the same three parts everywhere — a hairline box, a mark inside it at
 * the leading edge, and a label sitting above rather than floating into the border.
 */

/** A form's heading: what this is, what it is for, and a mark to the side. */
@Composable
fun FormHeading(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    mark: (DrawScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.InkMuted,
            )
        }
        mark?.let { Canvas(Modifier.size(64.dp)) { it() } }
    }
}

/** The label above a field. Above, not floating into the border — the border is a hairline. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = Palette.InkMuted,
        )
        trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.InkMuted,
            )
        }
    }
}

/**
 * One field.
 *
 * A `BasicTextField` rather than an `OutlinedTextField`, because everything the Material one
 * adds — the floating label, the filled container, the two-dp focus ring — is something this
 * design removes again. What is left is the box, the mark and the text, which is the whole
 * control.
 *
 * What the Material one also carried, and what had to be put back by hand, is the label. The
 * placeholder here is a sibling `Text` that disappears the moment anything is typed, and
 * [FieldLabel] above is a node of its own that the field is not associated with — so focusing
 * the room-code box announced an edit box and nothing about what it wanted. [label] is the
 * field's own description, and it is required rather than optional so a new call site cannot
 * quietly go back to being unlabelled.
 *
 * @param label what this field is asking for, said in words to a screen reader.
 * @param keyboardActions what the key in the corner of the keyboard does. Declaring
 *   `ImeAction.Done` in [keyboardOptions] draws a tick and nothing more; this is what makes it
 *   submit.
 */
@Composable
fun LobbyField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leading: (DrawScope.() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Card)
            .border(BorderStroke(1.dp, Palette.Edge), shape)
            .heightIn(min = 60.dp)
            .padding(horizontal = Dimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Palette.Inset)
                    .border(BorderStroke(Dimens.Hairline, Palette.Edge), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) { Canvas(Modifier.size(18.dp)) { it() } }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                ),
                cursorBrush = SolidColor(Palette.Gold),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = label },
            )
        }
        trailing?.invoke()
    }
}

/**
 * A control that lives inside a field: the eye on a password, and nothing else so far.
 *
 * The box is forty-eight, which is the platform's minimum for anything a finger has to hit, and
 * the mark inside it is still twenty — the same arrangement [PremiumBackArrow] uses. Nothing is
 * drawn on the box itself, so the extra eight device-independent pixels either side cost the
 * field nothing to look at and are the difference between a control that can be tapped and one
 * that can be aimed at.
 */
@Composable
fun FieldControl(label: String, onClick: () -> Unit, mark: DrawScope.() -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Canvas(Modifier.size(20.dp)) { mark() } }
}

/**
 * The forms' filled control — now only a name in front of [PremiumActionButton].
 *
 * It used to be its own button: a 58dp row with a three-stop gold gradient that a second file
 * drew byte-for-byte the same. That was one of the app's four golds and one of its five button
 * heights, and a form whose submit control does not match the one on the screen before it is how
 * an app comes to read as assembled. What is left forwards, so the two cannot drift again.
 *
 * It survives at all because `ReportDialog` still calls it; when that file moves to
 * [PremiumActionButton] this whole declaration goes with it.
 */
@Composable
fun GoldSubmit(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    mark: (DrawScope.() -> Unit)? = null,
) = PremiumActionButton(
    label = label,
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    filled = true,
    enabled = enabled,
    busy = busy,
    mark = mark,
)

/** The way out of a form or a dialog: an outline, never a fill. */
@Composable
fun OutlineAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .border(BorderStroke(1.dp, Palette.Gold.copy(alpha = 0.55f)), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = label }
            .heightIn(min = 56.dp)
            .padding(horizontal = Dimens.SpaceLg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = Palette.Gold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A choice you make by looking at it: the colour is the label and the value at once.
 *
 * Each still carries its name and its role in words, because a control that can only be read by
 * hue is one a colour-blind player cannot use — and because "blue" alone does not say that blue
 * is the seat that opens.
 */
@Composable
fun RowScope.SeatCard(
    swatch: Color,
    name: String,
    role: String,
    chosen: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .weight(1f)
            .clip(shape)
            .background(if (chosen) swatch.copy(alpha = 0.10f) else Palette.Card)
            .border(
                BorderStroke(if (chosen) Dimens.BorderStrong else 1.dp, if (chosen) swatch else Palette.Edge),
                shape,
            )
            .selectable(
                selected = chosen,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = "$name. $role" }
            .heightIn(min = 74.dp)
            .padding(horizontal = Dimens.SpaceSm + Dimens.SpaceXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The tick rides on the swatch rather than beside it. Two cards share a phone's width
        // between them, and a separate badge took the twenty-two device-independent pixels that
        // "Senin rengin" needed — the label was clipped to "Senin ren…" on the one card whose
        // whole job is to say what that colour means to you.
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(swatch)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)), CircleShape),
            )
            if (chosen) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Palette.Gold)
                        .border(BorderStroke(1.5.dp, Palette.Card), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Canvas(Modifier.size(8.dp)) { drawTick(Palette.GoldInk) } }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = role,
                style = MaterialTheme.typography.labelMedium,
                color = if (chosen) swatch else Palette.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One of the turn-length options: a word, and the mark that says what kind of length it is.
 *
 * `selectable`, not `clickable(role = Role.RadioButton)`. Which of these is chosen was said by a
 * gold border and by nothing else — not by the semantics tree, so a screen reader read four
 * identical chips and a player who cannot see the border had no way to find out how long a turn
 * they were about to agree to.
 */
@Composable
fun RowScope.DurationChip(
    label: String,
    chosen: Boolean,
    unlimited: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .weight(1f)
            .clip(shape)
            .background(if (chosen) Palette.Gold.copy(alpha = 0.10f) else Palette.Card)
            .border(BorderStroke(1.dp, if (chosen) Palette.Gold else Palette.Edge), shape)
            .selectable(
                selected = chosen,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = label }
            .heightIn(min = 68.dp)
            .padding(vertical = Dimens.SpaceSm, horizontal = Dimens.SpaceXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
            color = if (chosen) Palette.Gold else Palette.InkMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Canvas(Modifier.size(15.dp)) {
            val tint = if (chosen) Palette.Gold else Palette.InkGlyph
            if (unlimited) drawInfinity(tint) else drawSmallClock(tint)
        }
    }
}

/** The badge that hangs off the top edge of a dialog and says what kind of dialog it is. */
@Composable
fun DialogCrest(mark: DrawScope.() -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Palette.Inset)
            .border(BorderStroke(1.dp, Palette.Gold.copy(alpha = 0.55f)), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Canvas(Modifier.size(28.dp)) { mark() } }
}

/** One choice in a list of them, with the ring that says whether it is the chosen one. */
@Composable
fun ChoiceRow(
    label: String,
    chosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mark: (DrawScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (chosen) Palette.Gold.copy(alpha = 0.08f) else Palette.Card)
            .border(BorderStroke(1.dp, if (chosen) Palette.Gold else Palette.Edge), shape)
            .selectable(
                selected = chosen,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = label }
            .heightIn(min = 60.dp)
            .padding(horizontal = Dimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mark?.let { Canvas(Modifier.size(20.dp)) { it() } }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (chosen) Palette.Gold else Color.Transparent)
                .border(
                    BorderStroke(1.5.dp, if (chosen) Palette.Gold else Palette.InkGlyph),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (chosen) Canvas(Modifier.size(13.dp)) { drawTick(Palette.GoldInk) }
        }
    }
}

/** The grip at the top of a sheet. Purely a handle for the eye; the sheet drags anywhere. */
@Composable
fun SheetGrip(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(46.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(Palette.Gold.copy(alpha = 0.55f)),
    )
}

/**
 * One room in the browser.
 *
 * The numbered disc carries the host's seat colour, which is the one fact about a listed room
 * that a player can act on before joining it: it says which side they will be given, because
 * they get the other one. It replaced a generic avatar that said nothing at all — every room
 * showed the same grey circle with the same letter in it.
 *
 * The report control is here because this is the only place the app shows a stranger something
 * another player typed, with no route to that player's page to report it from.
 */
@Composable
fun OpenRoomCard(
    index: Int,
    seat: Color,
    title: String,
    host: String,
    rating: Int,
    durationLabel: String,
    players: String,
    locked: Boolean,
    lockedLabel: String,
    reportLabel: String,
    enabled: Boolean,
    onJoin: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Card)
            .border(BorderStroke(1.dp, Palette.Edge), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onJoin,
            )
            .semantics(mergeDescendants = true) { contentDescription = "$title. $host. $players" }
            .heightIn(min = 76.dp)
            .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(seat),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Only when there is one. A room whose host has no name showed a bare middle
                // dot with nothing on its left — punctuation between a word and an absence.
                if (host.isNotBlank()) {
                    Text(
                        text = host,
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text("·", style = MaterialTheme.typography.bodySmall, color = Palette.InkMuted)
                }
                Canvas(Modifier.size(12.dp)) { drawRatingCrown() }
                Text(
                    text = "$rating",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.InkMuted,
                    maxLines = 1,
                )
            }
        }
        // Marks rather than captions, and that is a width decision.
        //
        // "Hamle süresi" under "1 dk" is sixty-two device-independent pixels of label on a row
        // that has a hundred and thirty to spare in total, and it took them from the one column
        // that cannot give ground: the room's name, which was being clipped to "EGJ…". A clock
        // says the same thing in thirteen.
        RoomFact(durationLabel) { drawTinyClock() }
        RoomFact(players) { drawTinyPair() }
        Canvas(Modifier.size(15.dp)) {
            drawLock(if (locked) Palette.Gold else Palette.InkGlyph)
        }
        // Forty-eight for the touch box, fifteen for the flag. The mark stays the size the row
        // can afford — the argument above about width still holds — but the thing a finger has
        // to land on is the platform minimum. Thirty-two was a target smaller than the fingertip
        // aiming at it, sitting immediately beside a row-wide control that joins a game.
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onReport,
                )
                .semantics { contentDescription = reportLabel },
            contentAlignment = Alignment.Center,
        ) { Canvas(Modifier.size(15.dp)) { drawFlag(Palette.InkGlyph) } }
        // No chevron. A row this crowded cannot spend twenty-four pixels saying "tappable" when
        // the whole row is tappable and the arrow is the only thing on it that says nothing.
    }
}

@Composable
private fun RoomFact(value: String, mark: DrawScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(13.dp)) { mark() }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.InkMuted,
            maxLines = 1,
        )
    }
}

private fun DrawScope.drawTinyClock() {
    val s = size.minDimension
    val tint = Palette.InkGlyph
    val line = s * 0.12f
    drawCircle(tint, s * 0.42f, style = Stroke(line))
    drawLine(tint, Offset(s * 0.5f, s * 0.5f), Offset(s * 0.5f, s * 0.28f), line, StrokeCap.Round)
    drawLine(tint, Offset(s * 0.5f, s * 0.5f), Offset(s * 0.70f, s * 0.58f), line, StrokeCap.Round)
}

private fun DrawScope.drawTinyPair() {
    val s = size.minDimension
    val tint = Palette.InkGlyph
    drawCircle(tint.copy(alpha = 0.6f), s * 0.16f, Offset(s * 0.72f, s * 0.34f))
    drawArc(
        color = tint.copy(alpha = 0.6f),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(s * 0.52f, s * 0.54f),
        size = Size(s * 0.42f, s * 0.36f),
    )
    drawCircle(tint, s * 0.19f, Offset(s * 0.36f, s * 0.32f))
    drawArc(
        color = tint,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(s * 0.10f, s * 0.52f),
        size = Size(s * 0.52f, s * 0.42f),
    )
}

private fun DrawScope.drawRatingCrown() {
    val s = size.minDimension
    drawPath(
        Path().apply {
            moveTo(s * 0.08f, s * 0.80f)
            lineTo(s * 0.02f, s * 0.22f)
            lineTo(s * 0.30f, s * 0.52f)
            lineTo(s * 0.50f, s * 0.12f)
            lineTo(s * 0.70f, s * 0.52f)
            lineTo(s * 0.98f, s * 0.22f)
            lineTo(s * 0.92f, s * 0.80f)
            close()
        },
        Palette.Gold,
    )
}

private fun DrawScope.drawSmallChevron() {
    val s = size.minDimension
    drawPath(
        Path().apply {
            moveTo(s * 0.36f, s * 0.16f)
            lineTo(s * 0.68f, s * 0.50f)
            lineTo(s * 0.36f, s * 0.84f)
        },
        Palette.Gold.copy(alpha = 0.75f),
        style = Stroke(width = s * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}


// ---------------------------------------------------------------------------------------------
// Marks used by the forms, drawn to the same weight as the rest of the app.
// ---------------------------------------------------------------------------------------------

internal fun DrawScope.drawTick(tint: Color) {
    val s = size.minDimension
    drawPath(
        Path().apply {
            moveTo(s * 0.16f, s * 0.52f)
            lineTo(s * 0.40f, s * 0.78f)
            lineTo(s * 0.86f, s * 0.22f)
        },
        tint,
        style = Stroke(width = s * 0.17f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** A hash, for the room code. It is what a code looks like before you know what it says. */
internal fun DrawScope.drawHash(tint: Color = Palette.Gold) {
    val s = size.minDimension
    val line = s * 0.10f
    listOf(0.34f, 0.66f).forEach { at ->
        drawLine(tint, Offset(s * at, s * 0.10f), Offset(s * (at - 0.08f), s * 0.90f), line, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.10f, s * at), Offset(s * 0.90f, s * at), line, StrokeCap.Round)
    }
}

/** A padlock, closed. */
internal fun DrawScope.drawLock(tint: Color = Palette.Gold) {
    val s = size.minDimension
    val line = s * 0.11f
    drawArc(
        color = tint,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(s * 0.27f, s * 0.10f),
        size = Size(s * 0.46f, s * 0.46f),
        style = Stroke(width = line, cap = StrokeCap.Round),
    )
    drawRoundRect(
        tint,
        Offset(s * 0.17f, s * 0.42f),
        Size(s * 0.66f, s * 0.46f),
        CornerRadius(s * 0.12f),
    )
    drawCircle(Palette.Card, s * 0.075f, Offset(s * 0.5f, s * 0.63f))
}

/** An eye, and the same eye struck through. */
internal fun DrawScope.drawEye(tint: Color, open: Boolean) {
    val s = size.minDimension
    val line = s * 0.09f
    drawPath(
        Path().apply {
            moveTo(s * 0.06f, s * 0.5f)
            cubicTo(s * 0.28f, s * 0.18f, s * 0.72f, s * 0.18f, s * 0.94f, s * 0.5f)
            cubicTo(s * 0.72f, s * 0.82f, s * 0.28f, s * 0.82f, s * 0.06f, s * 0.5f)
            close()
        },
        tint,
        style = Stroke(width = line, join = StrokeJoin.Round),
    )
    drawCircle(tint, s * 0.14f, Offset(s * 0.5f, s * 0.5f))
    if (!open) {
        drawLine(
            tint,
            Offset(s * 0.14f, s * 0.86f),
            Offset(s * 0.86f, s * 0.14f),
            strokeWidth = line,
            cap = StrokeCap.Round,
        )
    }
}

/** A tower on a shield: the mark for a room, which is a place with a name. */
internal fun DrawScope.drawRoomCrest(tint: Color = Palette.Gold) {
    val s = size.minDimension
    drawPath(
        Path().apply {
            moveTo(s * 0.5f, s * 0.06f)
            lineTo(s * 0.90f, s * 0.26f)
            lineTo(s * 0.90f, s * 0.56f)
            cubicTo(s * 0.90f, s * 0.80f, s * 0.71f, s * 0.90f, s * 0.5f, s * 0.96f)
            cubicTo(s * 0.29f, s * 0.90f, s * 0.10f, s * 0.80f, s * 0.10f, s * 0.56f)
            lineTo(s * 0.10f, s * 0.26f)
            close()
        },
        tint,
        style = Stroke(width = s * 0.085f, join = StrokeJoin.Round),
    )
    // The tower: three merlons on a body, which is a room seen as a keep.
    drawPath(
        Path().apply {
            moveTo(s * 0.32f, s * 0.36f)
            lineTo(s * 0.32f, s * 0.30f)
            lineTo(s * 0.41f, s * 0.30f)
            lineTo(s * 0.41f, s * 0.36f)
            lineTo(s * 0.59f, s * 0.36f)
            lineTo(s * 0.59f, s * 0.30f)
            lineTo(s * 0.68f, s * 0.30f)
            lineTo(s * 0.68f, s * 0.36f)
            lineTo(s * 0.63f, s * 0.44f)
            lineTo(s * 0.63f, s * 0.70f)
            lineTo(s * 0.37f, s * 0.70f)
            lineTo(s * 0.37f, s * 0.44f)
            close()
        },
        tint,
    )
}

/** A plus, at the weight the rest of the set uses. */
internal fun DrawScope.drawPlusMark(tint: Color) {
    val s = size.minDimension
    val bar = s * 0.13f
    drawLine(tint, Offset(s * 0.5f, s * 0.14f), Offset(s * 0.5f, s * 0.86f), bar, StrokeCap.Round)
    drawLine(tint, Offset(s * 0.14f, s * 0.5f), Offset(s * 0.86f, s * 0.5f), bar, StrokeCap.Round)
}

/** Two figures, for joining somebody else's room. */
internal fun DrawScope.drawPairMark(tint: Color) {
    val s = size.minDimension
    drawCircle(tint.copy(alpha = 0.55f), s * 0.15f, Offset(s * 0.70f, s * 0.33f))
    drawArc(
        color = tint.copy(alpha = 0.55f),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(s * 0.50f, s * 0.52f),
        size = Size(s * 0.44f, s * 0.38f),
    )
    drawCircle(tint, s * 0.175f, Offset(s * 0.38f, s * 0.31f))
    drawArc(
        color = tint,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(s * 0.10f, s * 0.50f),
        size = Size(s * 0.56f, s * 0.44f),
    )
}

private fun DrawScope.drawSmallClock(tint: Color) {
    val s = size.minDimension
    val line = s * 0.11f
    drawCircle(tint, s * 0.40f, style = Stroke(line))
    drawLine(tint, Offset(s * 0.5f, s * 0.5f), Offset(s * 0.5f, s * 0.28f), line, StrokeCap.Round)
    drawLine(tint, Offset(s * 0.5f, s * 0.5f), Offset(s * 0.68f, s * 0.58f), line, StrokeCap.Round)
}

private fun DrawScope.drawInfinity(tint: Color) {
    val s = size.minDimension
    val line = s * 0.11f
    drawCircle(tint, s * 0.21f, Offset(s * 0.28f, s * 0.5f), style = Stroke(line))
    drawCircle(tint, s * 0.21f, Offset(s * 0.72f, s * 0.5f), style = Stroke(line))
}

/** A flag, for reporting. Small, because it costs the row nothing when nobody needs it. */
internal fun DrawScope.drawFlag(tint: Color) {
    val s = size.minDimension
    val line = s * 0.11f
    drawLine(tint, Offset(s * 0.24f, s * 0.08f), Offset(s * 0.24f, s * 0.94f), line, StrokeCap.Round)
    drawPath(
        Path().apply {
            moveTo(s * 0.30f, s * 0.14f)
            lineTo(s * 0.86f, s * 0.30f)
            lineTo(s * 0.30f, s * 0.50f)
            close()
        },
        tint,
    )
}

/** A single figure, for a name that belongs to a person. */
internal fun DrawScope.drawPersonMark(tint: Color) {
    val s = size.minDimension
    drawCircle(tint, s * 0.185f, Offset(s * 0.50f, s * 0.28f))
    drawArc(
        color = tint,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(s * 0.16f, s * 0.52f),
        size = Size(s * 0.68f, s * 0.56f),
    )
}

/** A knight, for cheating. The one piece Koridor does not have, which is rather the point. */
internal fun DrawScope.drawFoulMark(tint: Color) {
    val s = size.minDimension
    drawPath(
        Path().apply {
            moveTo(s * 0.30f, s * 0.88f)
            lineTo(s * 0.30f, s * 0.62f)
            cubicTo(s * 0.30f, s * 0.40f, s * 0.40f, s * 0.30f, s * 0.52f, s * 0.22f)
            lineTo(s * 0.44f, s * 0.12f)
            lineTo(s * 0.62f, s * 0.14f)
            cubicTo(s * 0.80f, s * 0.22f, s * 0.82f, s * 0.46f, s * 0.78f, s * 0.62f)
            lineTo(s * 0.78f, s * 0.88f)
            close()
        },
        tint,
    )
    drawCircle(Palette.Card, s * 0.045f, Offset(s * 0.60f, s * 0.28f))
}

/** A shield with a tick, for the dialog that files a report. */
internal fun DrawScope.drawShieldBadge(tint: Color = Palette.Gold) {
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
        tint,
    )
    drawPath(
        Path().apply {
            moveTo(s * 0.34f, s * 0.50f)
            lineTo(s * 0.46f, s * 0.63f)
            lineTo(s * 0.68f, s * 0.38f)
        },
        Palette.GoldInk,
        style = Stroke(width = s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** Three dots, for anything else. */
internal fun DrawScope.drawEllipsisMark(tint: Color) {
    val s = size.minDimension
    listOf(0.22f, 0.5f, 0.78f).forEach { x ->
        drawCircle(tint, s * 0.09f, Offset(s * x, s * 0.5f))
    }
}
