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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
 * The friends screen, in the app's own material.
 *
 * There are no tabs across the top. The reference has three — friends, invitations, recent games
 * — and two of them were asked to be dropped; a tab row with one tab in it is a heading that
 * looks like a control, so the row goes with them. Invitations still arrive: they are a section
 * of this list when there are any, and the bar hung over the whole app answers them wherever the
 * player happens to be standing.
 *
 * The empty state is the screen's real design work. A friends list starts empty for everybody,
 * so the first thing every player sees here is the panel below — what this place is for, in three
 * columns, over one button that does the only thing worth doing from an empty list.
 */

/** One of the three reasons to have anybody on this list. */
data class FriendPerk(val icon: PremiumIcon, val title: String, val body: String)

/**
 * What the screen shows before there is anybody on it.
 *
 * [action] is null for a guest: searching for a player needs an account, and a gold button that
 * opens a form nobody can submit is worse than no button. The banner under the panel is what a
 * guest gets instead, and it leads somewhere that works.
 */
@Composable
fun FriendsEmptyPanel(
    title: String,
    body: String,
    perks: List<FriendPerk>,
    action: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF141A20), Color(0xFF0D1116))))
            .border(BorderStroke(1.dp, FieldEdge), shape)
            .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp),
        ) { drawFriendsScene() }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8B9098),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        // Intrinsic height, so the two rules run the full depth of the tallest column rather
        // than a guessed seventy-two pixels. "Skorları karşılaştır" wraps to two lines and
        // "Birlikte oyna" does not, and with a fixed rule the three columns read as three
        // unrelated blocks that happen to be next to each other.
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(top = Dimens.SpaceXs),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
            verticalAlignment = Alignment.Top,
        ) {
            perks.forEachIndexed { index, perk ->
                if (index > 0) {
                    Box(
                        Modifier
                            .width(Dimens.Hairline)
                            .fillMaxHeight()
                            .background(Color(0xFF232A32)),
                    )
                }
                PerkColumn(perk)
            }
        }
        if (action != null) {
            GoldSubmit(
                label = action,
                onClick = onAction,
                modifier = Modifier.padding(top = Dimens.SpaceXs),
                mark = { drawAddPerson(Color(0xFF1A1206)) },
            )
        }
    }
}

@Composable
private fun RowScope.PerkColumn(perk: FriendPerk) {
    Column(
        Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF15191F))
                .border(BorderStroke(1.dp, KoridorGold.copy(alpha = 0.45f)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PremiumGlyph(perk.icon, Modifier.size(20.dp), tint = KoridorGold)
        }
        // Two lines whether it needs them or not, so the sentence under it starts at the same
        // height in all three columns.
        Text(
            text = perk.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = perk.body,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF7A7F86),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One player on the list.
 *
 * The presence dot rides on the avatar rather than sitting beside the word "online", because the
 * word is the caption and the dot is the fact — and on a row this narrow the caption is the first
 * thing to be clipped.
 */
@Composable
fun FriendCard(
    initial: String,
    name: String,
    status: String,
    online: Boolean,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF12161B))
            .border(BorderStroke(1.dp, FieldEdge), shape)
            .heightIn(min = 68.dp)
            .padding(start = Dimens.SpaceMd, end = Dimens.SpaceXs, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0xFF2F6BE8)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            if (online) {
                Box(
                    Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(PresenceGreen)
                        .border(BorderStroke(2.dp, Color(0xFF12161B)), CircleShape),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = if (online) PresenceGreen else Color(0xFF7A7F86),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        actions()
    }
}

/**
 * One thing to do about a player, as a mark in a ring.
 *
 * The label is carried as the accessibility description rather than printed: three of these share
 * the trailing end of a row with a name that has to stay readable, and three words there would
 * leave the name about fifty pixels.
 */
@Composable
fun FriendAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accented: Boolean = false,
    mark: DrawScope.() -> Unit,
) {
    val tint = if (accented) KoridorGold else Color(0xFF8B9098)
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (accented) KoridorGold.copy(alpha = 0.10f) else Color(0xFF161B21))
            .border(
                BorderStroke(1.dp, if (accented) KoridorGold.copy(alpha = 0.5f) else FieldEdge),
                CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Canvas(Modifier.size(17.dp)) { mark() } }
}

/** A section of the list: whose rows these are. */
@Composable
fun FriendsSectionHeader(title: String, count: String, modifier: Modifier = Modifier) {
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
        Text(
            text = count,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF6F747B),
            maxLines = 1,
        )
    }
}

/** Somebody is holding a seat for this player right now. Gold, because it expires. */
@Composable
fun InviteCard(
    text: String,
    joinLabel: String,
    dismissLabel: String,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF1A1710), Color(0xFF12161B))))
            .border(BorderStroke(1.dp, KoridorGold.copy(alpha = 0.45f)), shape)
            .heightIn(min = 66.dp)
            .padding(start = Dimens.SpaceMd, end = Dimens.SpaceXs, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(22.dp)) { drawPairMark(KoridorGold) }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        FriendAction(joinLabel, onJoin, accented = true) { drawTick(KoridorGold) }
        FriendAction(dismissLabel, onDismiss) { drawCross(Color(0xFF8B9098)) }
    }
}

internal val PresenceGreen = Color(0xFF5FBF7A)

// ---------------------------------------------------------------------------------------------
// Marks
// ---------------------------------------------------------------------------------------------

/** A figure with a plus beside it: adding somebody who is not here yet. */
internal fun DrawScope.drawAddPerson(tint: Color) {
    val s = size.minDimension
    drawCircle(tint, s * 0.16f, Offset(s * 0.38f, s * 0.28f))
    drawArc(
        color = tint,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(s * 0.10f, s * 0.50f),
        size = Size(s * 0.56f, s * 0.46f),
    )
    val bar = s * 0.10f
    drawLine(tint, Offset(s * 0.82f, s * 0.24f), Offset(s * 0.82f, s * 0.60f), bar, StrokeCap.Round)
    drawLine(tint, Offset(s * 0.64f, s * 0.42f), Offset(s * 1.00f, s * 0.42f), bar, StrokeCap.Round)
}

/** Two crossed strokes, for dismissing. */
internal fun DrawScope.drawCross(tint: Color) {
    val s = size.minDimension
    val line = s * 0.14f
    drawLine(tint, Offset(s * 0.20f, s * 0.20f), Offset(s * 0.80f, s * 0.80f), line, StrokeCap.Round)
    drawLine(tint, Offset(s * 0.80f, s * 0.20f), Offset(s * 0.20f, s * 0.80f), line, StrokeCap.Round)
}

/** A gamepad outline, for inviting somebody into a match. */
internal fun DrawScope.drawInviteMark(tint: Color) {
    val s = size.minDimension
    drawRoundRect(
        color = tint,
        topLeft = Offset(s * 0.06f, s * 0.30f),
        size = Size(s * 0.88f, s * 0.40f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.18f),
        style = Stroke(width = s * 0.10f),
    )
    drawLine(tint, Offset(s * 0.22f, s * 0.50f), Offset(s * 0.38f, s * 0.50f), s * 0.09f, StrokeCap.Round)
    drawLine(tint, Offset(s * 0.30f, s * 0.42f), Offset(s * 0.30f, s * 0.58f), s * 0.09f, StrokeCap.Round)
    drawCircle(tint, s * 0.06f, Offset(s * 0.68f, s * 0.44f))
    drawCircle(tint, s * 0.06f, Offset(s * 0.78f, s * 0.56f))
}

/** A circle with a bar through it: blocking. */
internal fun DrawScope.drawBlockMark(tint: Color) {
    val s = size.minDimension
    val line = s * 0.11f
    drawCircle(tint, s * 0.42f, Offset(s * 0.5f, s * 0.5f), style = Stroke(line))
    drawLine(tint, Offset(s * 0.22f, s * 0.78f), Offset(s * 0.78f, s * 0.22f), line, StrokeCap.Round)
}

/** A shield with two figures in it, over a board, between two pawns. The empty screen's picture. */
private fun DrawScope.drawFriendsScene() {
    val w = size.width
    val h = size.height
    val centre = w / 2f

    // The board: a square seen in perspective, so a diamond, with its ranks and files ruled
    // across it. Both families of lines run parallel to an edge of the diamond, which is what
    // makes it read as a board rather than as cross-hatching — the first attempt drew chords
    // between opposite edges and produced a scribble.
    val far = Offset(centre, h * 0.58f)
    val east = Offset(centre + w * 0.30f, h * 0.78f)
    val near = Offset(centre, h * 0.98f)
    val west = Offset(centre - w * 0.30f, h * 0.78f)
    val ink = KoridorGold.copy(alpha = 0.16f)
    val rule = KoridorGold.copy(alpha = 0.09f)
    val weight = h * 0.006f

    drawPath(
        Path().apply {
            moveTo(far.x, far.y)
            lineTo(east.x, east.y)
            lineTo(near.x, near.y)
            lineTo(west.x, west.y)
            close()
        },
        ink,
        style = Stroke(width = h * 0.009f, join = StrokeJoin.Round),
    )
    for (index in 1 until BOARD_RANKS) {
        val t = index.toFloat() / BOARD_RANKS
        // Parallel to the far-east edge: from a point on west→far to one on near→east.
        drawLine(rule, lerp(west, far, t), lerp(near, east, t), strokeWidth = weight)
        // Parallel to the far-west edge: from a point on east→far to one on near→west.
        drawLine(rule, lerp(east, far, t), lerp(near, west, t), strokeWidth = weight)
    }

    // The shield, with the pair of figures cut into it.
    val shieldW = h * 0.54f
    val shieldH = h * 0.66f
    val left = centre - shieldW / 2f
    val top = h * 0.04f
    val shield = Path().apply {
        moveTo(centre, top)
        lineTo(left + shieldW, top + shieldH * 0.22f)
        lineTo(left + shieldW, top + shieldH * 0.58f)
        cubicTo(
            left + shieldW, top + shieldH * 0.86f,
            centre + shieldW * 0.22f, top + shieldH * 0.98f,
            centre, top + shieldH,
        )
        cubicTo(
            centre - shieldW * 0.22f, top + shieldH * 0.98f,
            left, top + shieldH * 0.86f,
            left, top + shieldH * 0.58f,
        )
        lineTo(left, top + shieldH * 0.22f)
        close()
    }
    drawPath(shield, Color(0xFF171C22))
    drawPath(
        shield,
        KoridorGold.copy(alpha = 0.65f),
        style = Stroke(width = h * 0.011f, join = StrokeJoin.Round),
    )
    val markSize = shieldH * 0.40f
    val markLeft = centre - markSize / 2f
    val markTop = top + shieldH * 0.28f
    drawCircle(KoridorGold, markSize * 0.16f, Offset(markLeft + markSize * 0.34f, markTop + markSize * 0.24f))
    drawCircle(
        KoridorGold.copy(alpha = 0.85f),
        markSize * 0.14f,
        Offset(markLeft + markSize * 0.70f, markTop + markSize * 0.27f),
    )
    drawArc(
        color = KoridorGold,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(markLeft + markSize * 0.08f, markTop + markSize * 0.48f),
        size = Size(markSize * 0.52f, markSize * 0.44f),
    )
    drawArc(
        color = KoridorGold.copy(alpha = 0.85f),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(markLeft + markSize * 0.46f, markTop + markSize * 0.52f),
        size = Size(markSize * 0.48f, markSize * 0.40f),
    )

    // The two pawns, one of each seat, standing on the board either side of the shield.
    //
    // The dark one carries a rim. Without it, a near-black piece on a near-black page is a hole
    // in the picture rather than a piece — it read as the pale one's shadow.
    drawScenePawn(
        foot = Offset(centre - w * 0.20f, h * 0.76f),
        height = h * 0.30f,
        body = Color(0xFF262E37),
        light = Color(0xFF3E4753),
        rim = Color(0xFF5C6673),
    )
    drawScenePawn(
        foot = Offset(centre + w * 0.20f, h * 0.74f),
        height = h * 0.30f,
        body = Color(0xFFD8C39A),
        light = Color(0xFFF0E2C4),
        rim = null,
    )

    // A few motes of light, at the edges, where nothing else is.
    listOf(
        Offset(w * 0.14f, h * 0.22f) to 0.30f,
        Offset(w * 0.86f, h * 0.30f) to 0.24f,
        Offset(w * 0.24f, h * 0.50f) to 0.18f,
        Offset(w * 0.78f, h * 0.14f) to 0.20f,
    ).forEach { (at, alpha) ->
        drawCircle(KoridorGold.copy(alpha = alpha), h * 0.012f, at)
    }
}

/** How many ranks the board in the picture is ruled into. Four reads; nine is a moiré at 150 dp. */
private const val BOARD_RANKS = 4

private fun lerp(from: Offset, to: Offset, t: Float): Offset =
    Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)

/** One pawn: a head, a waist and a base, lit from above, with an optional rim to lift it off. */
private fun DrawScope.drawScenePawn(
    foot: Offset,
    height: Float,
    body: Color,
    light: Color,
    rim: Color?,
) {
    val width = height * 0.52f
    drawOval(
        color = Color.Black.copy(alpha = 0.35f),
        topLeft = Offset(foot.x - width * 0.60f, foot.y - height * 0.05f),
        size = Size(width * 1.20f, height * 0.13f),
    )
    val silhouette = Path().apply {
        moveTo(foot.x - width * 0.50f, foot.y)
        lineTo(foot.x + width * 0.50f, foot.y)
        lineTo(foot.x + width * 0.34f, foot.y - height * 0.16f)
        cubicTo(
            foot.x + width * 0.20f, foot.y - height * 0.42f,
            foot.x + width * 0.24f, foot.y - height * 0.52f,
            foot.x + width * 0.16f, foot.y - height * 0.58f,
        )
        lineTo(foot.x - width * 0.16f, foot.y - height * 0.58f)
        cubicTo(
            foot.x - width * 0.24f, foot.y - height * 0.52f,
            foot.x - width * 0.20f, foot.y - height * 0.42f,
            foot.x - width * 0.34f, foot.y - height * 0.16f,
        )
        close()
    }
    drawPath(silhouette, body)
    drawCircle(light, width * 0.34f, Offset(foot.x, foot.y - height * 0.74f))
    drawCircle(body, width * 0.30f, Offset(foot.x + width * 0.04f, foot.y - height * 0.72f))
    if (rim != null) {
        drawPath(silhouette, rim, style = Stroke(width = height * 0.018f, join = StrokeJoin.Round))
        drawCircle(
            rim,
            width * 0.34f,
            Offset(foot.x, foot.y - height * 0.74f),
            style = Stroke(width = height * 0.018f),
        )
    }
}
