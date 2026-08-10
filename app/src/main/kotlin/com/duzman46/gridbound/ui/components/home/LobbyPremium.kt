package com.duzman46.gridbound.ui.components.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
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
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold

/**
 * The online lobby's surfaces.
 *
 * The lobby has three jobs and the old screen gave them equal weight: a tonal card for the
 * queue, two tonal buttons for the forms, and a list. But they are not equal. Joining the queue
 * is what nine players in ten came here to do; opening a room by code or creating one is what
 * the tenth does; and the list is where you look when the first two did not appeal.
 *
 * So the hierarchy is written into the material. One card carries a crest, a sentence and a
 * filled gold control. Two carry a mark and a word. The list carries neither.
 */

private const val PRESSED_SCALE = 0.98f


/**
 * The press acknowledgement, and the rule that comes with it.
 *
 * **The scale must sit inside the click, never outside it.** Every surface here puts `clickable`
 * before `scale`, which reads backwards and is exactly the point: an earlier modifier wraps the
 * later ones, so the touch target stays the control at rest and only the picture inside it moves.
 *
 * The other way round, the control being pressed is also the control whose target is shrinking
 * under the finger in that same frame — a touch near an edge lands outside what it just hit, and
 * Compose cancels the tap. Nothing happens, the player taps again, and the second one works.
 */
@Composable
private fun pressScale(pressed: Boolean): Float {
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = tween(durationMillis = if (pressed) 70 else 130, easing = LinearEasing),
        label = "lobbyPress",
    )
    return scale
}

/** The press scale and the rounded corner in one graphics layer rather than two. */
private fun Modifier.pressLayer(scale: Float, shape: Shape): Modifier = graphicsLayer {
    scaleX = scale
    scaleY = scale
    this.shape = shape
    clip = true
}

/** The queue card's ground and the gold control's two fills. Built once; they never change. */
private val QueueCardFill = Brush.verticalGradient(listOf(Color(0xFF12151A), Color(0xFF0C0F13)))
private val GoldResting =
    Brush.horizontalGradient(listOf(Color(0xFFCFA455), Color(0xFFEBCB84), Color(0xFFC79B47)))
private val GoldPressed =
    Brush.horizontalGradient(listOf(Color(0xFFB98F3F), Color(0xFFCEA75B)))

/**
 * The queue, and the only filled gold control in the app.
 *
 * Everything else that is gold is an edge, a mark or a word; this is a slab of it, and that is
 * deliberate — it is the single action the whole screen exists to offer, and the two cards
 * under it are alternatives to it rather than peers of it.
 *
 * The crest is a pawn wearing a crown inside a laurel. It is the only heraldic thing in the app
 * and it is here because this is the only ranked surface: a match from this queue moves a
 * number that no other mode touches, and the crest is what says so before the sentence does.
 */
@Composable
fun RankedQueueCard(
    title: String,
    hint: String,
    action: String,
    onClick: () -> Unit,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(QueueCardFill)
            .border(BorderStroke(1.dp, Color(0xFF2A3038)), shape)
            .padding(Dimens.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(66.dp)) { drawRankedCrest() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8B9098),
                )
            }
        }
        GoldButton(label = action, onClick = onClick, busy = busy)
    }
}

/** The one filled gold control. Bolt, rule, label — the same three parts the mockup asks for. */
@Composable
private fun GoldButton(label: String, onClick: () -> Unit, busy: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(14.dp)
    val ink = Color(0xFF1A1206)
    Row(
        Modifier
            .fillMaxWidth()
            // Click outside the scale. See pressScale.
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !busy,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = label }
            .pressLayer(pressScale(pressed), shape)
            .background(if (pressed) GoldPressed else GoldResting)
            .heightIn(min = 56.dp)
            .padding(horizontal = Dimens.SpaceLg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(22.dp), color = ink, strokeWidth = 2.dp)
        } else {
            Canvas(Modifier.size(22.dp)) { drawBolt(ink) }
            Box(
                Modifier
                    .padding(horizontal = Dimens.SpaceLg)
                    .height(26.dp)
                    .width(Dimens.Hairline)
                    .background(ink.copy(alpha = 0.35f)),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One of the two secondary ways in: a mark and a word, at half the width.
 *
 * No chevron. There is room for one and it was there first, and it cost the label the width it
 * needed — "Oda oluştur" broke over two lines to make space for an arrow that says nothing the
 * card does not already say by being tappable. On a card this narrow the label is the interface;
 * everything else is a claim on its width.
 */
@Composable
fun RowScope.LobbyActionCard(
    label: String,
    icon: PremiumIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .weight(1f)
            // Click outside the scale. See pressScale.
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = label }
            .pressLayer(pressScale(pressed), shape)
            .background(if (pressed) colors.surfaceVariant else colors.surface)
            .border(BorderStroke(1.dp, colors.outlineVariant), shape)
            .heightIn(min = 70.dp)
            .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF161B21))
                .border(BorderStroke(Dimens.Hairline, Color(0xFF2A3038)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PremiumGlyph(icon, Modifier.size(18.dp), tint = KoridorGold)
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A section title with the one control that belongs to it. */
@Composable
fun LobbySectionHeader(title: String, refreshLabel: String, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        Box(
            Modifier
                .size(44.dp)
                // Click outside the scale. See pressScale.
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onRefresh,
                )
                .semantics { contentDescription = refreshLabel }
                .pressLayer(pressScale(pressed), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(24.dp)) { drawRefresh() }
        }
    }
}

/**
 * Nothing open yet.
 *
 * A drawn scene rather than a line of grey text, because this is the state most first-time
 * players meet: a new game has no rooms in it, and "Şu anda açık oda yok" on its own reads as a
 * failure. A lit doorway with a piece waiting beside it says the same thing as an invitation.
 *
 * The corner brackets are the frame of an empty slot rather than a border — a full outline would
 * make the emptiness a card, which is the one thing it is not.
 */
@Composable
fun EmptyRoomsPanel(title: String, hint: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // The brackets frame whatever room the list was going to have, rather than a height of
        // their own. The panel is the empty slot, so it is exactly the size of the slot.
        Canvas(Modifier.fillMaxSize()) { drawCornerBrackets() }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        ) {
            Canvas(Modifier.size(84.dp)) { drawEmptyDoorway() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6F747B),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * A pawn inside a laurel, under a crown. The app's only heraldry.
 *
 * The crown is held clear of the ring rather than sitting on the piece, and that separation is
 * the whole point: a crown resting on a rounded head is the silhouette of a chess queen, which
 * is precisely the reading this game cannot afford. Apart, they are a pawn — the piece Koridor
 * actually has — and a mark of rank above it.
 *
 * The laurel is drawn as leaves on a stem rather than a ring of dots. Dots at this size read as
 * a gear or a sun; a leaf has a long axis, and it is the angle of that axis that says wreath.
 */
private fun DrawScope.drawRankedCrest() {
    val s = size.minDimension
    val centre = Offset(s * 0.5f, s * 0.56f)
    val ring = s * 0.30f
    drawCircle(KoridorGold.copy(alpha = 0.45f), ring, centre, style = Stroke(s * 0.016f))

    // The crown, clear above the ring with air under it.
    drawPath(
        Path().apply {
            moveTo(s * 0.37f, s * 0.19f)
            lineTo(s * 0.40f, s * 0.06f)
            lineTo(s * 0.45f, s * 0.145f)
            lineTo(s * 0.50f, s * 0.03f)
            lineTo(s * 0.55f, s * 0.145f)
            lineTo(s * 0.60f, s * 0.06f)
            lineTo(s * 0.63f, s * 0.19f)
            close()
        },
        KoridorGold,
    )

    // Pawn: head, collar, body, base — the same silhouette the board uses.
    drawCircle(KoridorGold, s * 0.058f, Offset(s * 0.5f, s * 0.455f))
    drawRoundRect(
        KoridorGold,
        Offset(s * 0.437f, s * 0.515f),
        Size(s * 0.126f, s * 0.026f),
        CornerRadius(s * 0.013f),
    )
    drawPath(
        Path().apply {
            moveTo(s * 0.452f, s * 0.545f)
            cubicTo(s * 0.443f, s * 0.60f, s * 0.428f, s * 0.625f, s * 0.414f, s * 0.645f)
            lineTo(s * 0.586f, s * 0.645f)
            cubicTo(s * 0.572f, s * 0.625f, s * 0.557f, s * 0.60f, s * 0.548f, s * 0.545f)
            close()
        },
        KoridorGold,
    )
    drawRoundRect(
        KoridorGold,
        Offset(s * 0.395f, s * 0.65f),
        Size(s * 0.21f, s * 0.032f),
        CornerRadius(s * 0.016f),
    )

    // Laurel: a stem down each flank, four leaves along it.
    //
    // Both flanks are drawn from the same maths and mirrored by `side`, so the wreath is
    // symmetrical by construction rather than by two lists of numbers kept in agreement.
    // Angles run clockwise from the top of the ring, in degrees, because that is how the shape
    // is read — a stem that starts beside the crown and sweeps down to meet its twin.
    val stemRadius = ring + s * 0.085f
    listOf(-1f, 1f).forEach { side ->
        fun pointAt(degrees: Double) = Offset(
            centre.x + side * (stemRadius * Math.sin(Math.toRadians(degrees))).toFloat(),
            centre.y - (stemRadius * Math.cos(Math.toRadians(degrees))).toFloat(),
        )

        val stem = Path()
        for (step in 0..14) {
            val point = pointAt(38.0 + step * 8.0)
            if (step == 0) stem.moveTo(point.x, point.y) else stem.lineTo(point.x, point.y)
        }
        drawPath(
            stem,
            KoridorGold.copy(alpha = 0.45f),
            style = Stroke(width = s * 0.013f, cap = StrokeCap.Round),
        )

        for (index in 0 until 4) {
            val degrees = 50.0 + index * 25.0
            val root = pointAt(degrees)
            // Each leaf lies along the stem and tilts outward from it, which is what reads as a
            // wreath. A leaf drawn axis-aligned is a bead, and four beads are a gear.
            withTransform({
                translate(root.x, root.y)
                rotate(degrees = side * (degrees.toFloat() + 38f), pivot = Offset.Zero)
            }) {
                drawOval(
                    color = KoridorGold.copy(alpha = 0.85f - index * 0.09f),
                    topLeft = Offset(-s * 0.05f, -s * 0.021f),
                    size = Size(s * 0.10f, s * 0.042f),
                )
            }
        }
    }
}

/** A lightning bolt, for the control that puts you in the queue. */
private fun DrawScope.drawBolt(tint: Color) {
    val s = size.minDimension
    drawPath(
        Path().apply {
            moveTo(s * 0.58f, s * 0.04f)
            lineTo(s * 0.20f, s * 0.56f)
            lineTo(s * 0.45f, s * 0.56f)
            lineTo(s * 0.38f, s * 0.96f)
            lineTo(s * 0.80f, s * 0.42f)
            lineTo(s * 0.53f, s * 0.42f)
            close()
        },
        tint,
    )
}

/** Two arrows chasing each other round a circle. */
private fun DrawScope.drawRefresh() {
    val s = size.minDimension
    val stroke = s * 0.10f
    listOf(0f, 180f).forEach { start ->
        drawArc(
            color = KoridorGold,
            startAngle = start + 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(s * 0.12f, s * 0.12f),
            size = Size(s * 0.76f, s * 0.76f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
    // The two heads, one at each arc's end.
    drawPath(
        Path().apply {
            moveTo(s * 0.86f, s * 0.30f)
            lineTo(s * 0.88f, s * 0.06f)
            lineTo(s * 0.64f, s * 0.16f)
            close()
        },
        KoridorGold,
    )
    drawPath(
        Path().apply {
            moveTo(s * 0.14f, s * 0.70f)
            lineTo(s * 0.12f, s * 0.94f)
            lineTo(s * 0.36f, s * 0.84f)
            close()
        },
        KoridorGold,
    )
}

/** The frame of an empty slot: four corners, no sides. */
private fun DrawScope.drawCornerBrackets() {
    val w = size.width
    val h = size.height
    val arm = 22.dp.toPx()
    val stroke = 1.dp.toPx()
    val ink = Color(0xFF2A3038)
    val corners = listOf(
        Offset(0f, 0f) to Pair(1f, 1f),
        Offset(w, 0f) to Pair(-1f, 1f),
        Offset(0f, h) to Pair(1f, -1f),
        Offset(w, h) to Pair(-1f, -1f),
    )
    corners.forEach { (origin, dir) ->
        drawLine(
            ink,
            origin,
            Offset(origin.x + arm * dir.first, origin.y),
            strokeWidth = stroke,
        )
        drawLine(
            ink,
            origin,
            Offset(origin.x, origin.y + arm * dir.second),
            strokeWidth = stroke,
        )
    }
}

/**
 * A lit doorway with a piece beside it, drawn in a single gold hairline.
 *
 * Line art rather than a filled shape: this sits in the largest empty area on the screen, and a
 * solid mark that size becomes the loudest thing on a page whose whole message is that there is
 * nothing here yet.
 */
private fun DrawScope.drawEmptyDoorway() {
    val s = size.minDimension
    val stroke = s * 0.018f
    val ink = KoridorGold.copy(alpha = 0.75f)

    // The arc the scene sits in, open at the bottom.
    drawArc(
        color = KoridorGold.copy(alpha = 0.35f),
        startAngle = 155f,
        sweepAngle = 230f,
        useCenter = false,
        topLeft = Offset(s * 0.06f, s * 0.06f),
        size = Size(s * 0.88f, s * 0.88f),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )

    // The door: a leaf standing open, seen at an angle.
    drawPath(
        Path().apply {
            moveTo(s * 0.40f, s * 0.26f)
            lineTo(s * 0.62f, s * 0.34f)
            lineTo(s * 0.62f, s * 0.80f)
            lineTo(s * 0.40f, s * 0.74f)
            close()
        },
        ink,
        style = Stroke(width = stroke, join = StrokeJoin.Round),
    )
    drawLine(
        ink,
        Offset(s * 0.40f, s * 0.74f),
        Offset(s * 0.40f, s * 0.26f),
        strokeWidth = stroke,
    )
    // Its handle.
    drawCircle(ink, s * 0.016f, Offset(s * 0.455f, s * 0.55f))

    // The floor it stands on.
    drawLine(
        KoridorGold.copy(alpha = 0.45f),
        Offset(s * 0.24f, s * 0.80f),
        Offset(s * 0.80f, s * 0.80f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )

    // The piece waiting beside it.
    drawCircle(ink, s * 0.045f, Offset(s * 0.70f, s * 0.60f))
    drawPath(
        Path().apply {
            moveTo(s * 0.655f, s * 0.66f)
            cubicTo(s * 0.645f, s * 0.72f, s * 0.63f, s * 0.75f, s * 0.62f, s * 0.775f)
            lineTo(s * 0.78f, s * 0.775f)
            cubicTo(s * 0.77f, s * 0.75f, s * 0.755f, s * 0.72f, s * 0.745f, s * 0.66f)
            close()
        },
        ink,
        style = Stroke(width = stroke, join = StrokeJoin.Round),
    )

    // A few sparks, because an empty room should look like it is waiting rather than shut.
    listOf(
        Triple(0.26f, 0.40f, 0.016f),
        Triple(0.31f, 0.62f, 0.011f),
        Triple(0.78f, 0.34f, 0.013f),
        Triple(0.84f, 0.52f, 0.009f),
    ).forEach { (x, y, r) ->
        drawCircle(KoridorGold.copy(alpha = 0.6f), s * r, Offset(s * x, s * y))
    }

    // And a dashed suggestion of the threshold.
    drawLine(
        KoridorGold.copy(alpha = 0.30f),
        Offset(s * 0.62f, s * 0.80f),
        Offset(s * 0.86f, s * 0.80f),
        strokeWidth = stroke * 0.8f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.03f, s * 0.03f)),
    )
}
