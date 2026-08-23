package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.Palette

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
            // Three stops, and the first one is the picture's own black.
            //
            // The artwork runs edge to edge across the top of this card, and it is darker than
            // any card in the app. Starting the card at the same black is what makes the two
            // one surface: there is no line where the picture stops, because on three sides it
            // does not stop — it meets the card's border. The ground warms to the usual charcoal
            // by the time the text begins, which is where the artwork has already faded out.
            .background(
                Brush.verticalGradient(
                    0f to Palette.Ground,
                    HERO_SHARE to Palette.Inset,
                    1f to Palette.Card,
                ),
            )
            .border(BorderStroke(1.dp, Palette.Edge), shape),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Edge to edge, outside the text's padding, and that is what the feathered border on
        // the asset buys: the picture has no rectangle to see, so it can run right into the
        // card's own corners instead of sitting inside them as a black patch.
        //
        // The drawing that was here is gone. It was a stand-in for exactly this.
        Image(
            painter = painterResource(R.drawable.friends_empty),
            // Decorative: the heading under it says what it is, and a screen reader announcing
            // the picture as well would say the same thing twice.
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(HERO_RATIO),
            contentScale = ContentScale.FillWidth,
        )
        Column(
            Modifier.padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceLg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        ) {
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
            color = Palette.InkMuted,
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
                            .background(Palette.Edge),
                    )
                }
                PerkColumn(perk)
            }
        }
        if (action != null) {
            PremiumActionButton(
                label = action,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpaceXs),
                filled = true,
                mark = { drawAddPerson(Palette.GoldInk) },
            )
        }
        }
    }
}

/** The hero asset's own proportions, so the card reserves exactly its height and no more. */
private const val HERO_RATIO = 1050f / 600f

/**
 * Roughly how much of the card the picture takes, and therefore where its ground has finished
 * warming from the artwork's black to the card's charcoal.
 *
 * Approximate on purpose: the text under it is three sizes at any font scale, so the exact share
 * is not knowable at build time. It only has to land inside the stretch the artwork fades over.
 */
private const val HERO_SHARE = 0.40f

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
                .background(Palette.Inset)
                .border(BorderStroke(1.dp, Palette.Gold.copy(alpha = 0.45f)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PremiumGlyph(perk.icon, Modifier.size(20.dp), tint = Palette.Gold)
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
            color = Palette.InkMuted,
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
            .background(Palette.Card)
            .border(BorderStroke(1.dp, Palette.Edge), shape)
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
                        .border(BorderStroke(2.dp, Palette.Card), CircleShape),
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
                color = if (online) PresenceGreen else Palette.InkMuted,
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
 *
 * The ring stays forty and the touch box is forty-eight, which is the platform's minimum and the
 * arrangement [PremiumBackArrow] uses. Three of these sit side by side on a request row and one
 * of them refuses a friend request — a mis-aimed tap there is not a cosmetic problem, and eight
 * device-independent pixels of invisible margin is what it costs to make them separable.
 */
@Composable
fun FriendAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accented: Boolean = false,
    mark: DrawScope.() -> Unit,
) {
    val tint = if (accented) Palette.Gold else Palette.InkGlyph
    Box(
        modifier
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
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (accented) Palette.Gold.copy(alpha = 0.10f) else Palette.Inset)
                .border(
                    BorderStroke(1.dp, if (accented) Palette.Gold.copy(alpha = 0.5f) else Palette.Edge),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) { Canvas(Modifier.size(17.dp)) { mark() } }
    }
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
            // A heading, so the navigate-by-heading gesture crosses the friend list a section at
            // a time rather than a row at a time. This is the longest list in the app.
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Palette.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = count,
            style = MaterialTheme.typography.labelMedium,
            color = Palette.InkMuted,
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
            .background(Brush.horizontalGradient(listOf(Palette.GoldBandEnd, Palette.Card)))
            // The same hairline the other three gold-band cards wear. It was drawn at three
            // different alphas across four files, which on one card idiom is a difference the
            // eye registers as untidiness without ever being able to name it.
            .border(BorderStroke(1.dp, Palette.Gold.copy(alpha = 0.35f)), shape)
            .heightIn(min = 66.dp)
            .padding(start = Dimens.SpaceMd, end = Dimens.SpaceXs, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(22.dp)) { drawPairMark(Palette.Gold) }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        FriendAction(joinLabel, onJoin, accented = true) { drawTick(Palette.Gold) }
        FriendAction(dismissLabel, onDismiss) { drawCross(Palette.InkGlyph) }
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
