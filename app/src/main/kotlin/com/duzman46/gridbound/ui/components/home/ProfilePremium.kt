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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.match.domain.MatchOutcome
import com.duzman46.gridbound.match.domain.RecentMatch
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold
import com.duzman46.gridbound.ui.components.PlayerAvatar
import java.text.DateFormat
import java.util.Date

/**
 * The profile tab, in the same language as every other premium screen.
 *
 * The page it replaces was Material: a `Card` of statistics and four stacked buttons, on the one
 * tab of three that had not been redrawn. Nothing here is new information — the rating, the
 * record, the streak and the recent matches were all on the old page — it is the same record
 * arranged so the numbers lead and the navigation stops being a list.
 */

/** The three card accents. Local to this screen on purpose — see [ProfileFeatureCard]. */
internal val PuzzleAccent = Color(0xFF8B7BE8)
internal val BadgeAccent = Color(0xFF2FBF9B)

private val CardFill = Color(0xFF12161B)
private val Muted = Color(0xFF8B9098)


/**
 * Who somebody is: the disc, the name, and whether the name is theirs to keep.
 *
 * The pencils are the change of substance on the owner's page. Editing used to be a button below
 * the record, one navigation hop from the two things it edits; putting the affordance on the
 * avatar and on the name means the control is where the thing it changes is.
 *
 * **[onEdit] is null on a stranger's page, and that is the whole difference between the two.**
 * Not a flag that hides a pencil while leaving the tap target — with no lambda there is nothing
 * to call, so no later edit can put the owner's controls on somebody else's page by flipping a
 * boolean the wrong way.
 */
@Composable
fun ProfileIdentity(
    username: String,
    avatarId: String,
    isGuest: Boolean,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
) {
    val editLabel = stringResource(R.string.profile_edit_title)
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .then(
                    if (onEdit != null) {
                        Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = onEdit,
                            )
                            .semantics { contentDescription = editLabel }
                    } else {
                        Modifier
                    },
                ),
        ) {
            PlayerAvatar(
                avatarId = avatarId,
                name = username,
                modifier = Modifier
                    .size(76.dp)
                    .align(Alignment.TopStart)
                    .border(2.dp, KoridorGold.copy(alpha = 0.75f), CircleShape),
                size = 76.dp,
            )
            if (onEdit != null) PencilBadge(Modifier.align(Alignment.BottomEnd), size = 26.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (onEdit != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onEdit,
                    )
                } else {
                    Modifier
                },
            ) {
                Text(
                    text = username,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (onEdit != null) PencilBadge(size = 26.dp)
            }
            if (isGuest) {
                Text(
                    text = stringResource(R.string.auth_guest_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF20262D))
                        .padding(horizontal = Dimens.SpaceMd, vertical = 5.dp),
                )
            }
        }
    }
}

/**
 * The rest of a player's record, in pairs, under the three headline figures.
 *
 * There is no draw row. The game cannot end in one, and a row of permanent zeroes is a rule the
 * reader has to work out is not a rule.
 */
@Composable
fun ProfileDetailCard(
    entries: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    footer: String? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(CardFill)
            .border(1.dp, FieldEdge, RoundedCornerShape(Dimens.RadiusMd))
            .padding(Dimens.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        // Two to a row, so five entries fill three rows with the last spanning rather than
        // leaving a hole beside it.
        entries.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
            ) {
                pair.forEach { (label, value) ->
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyLarge
                                .copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        footer?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
    }
}

/**
 * A round control for the one thing a premium header offers.
 *
 * [HomeIconButton] already exists and is the wrong one here: it draws a `GlyphKind` in
 * `HomePalette.OnWell` on a jade-edged chip, because it lives on the home screen's photograph
 * and belongs to that surface. This takes a [PremiumIcon] in gold, which is what the header's
 * own title is set in.
 */
@Composable
fun PremiumIconButton(
    icon: PremiumIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(Dimens.CrestHeight)
            .clip(CircleShape)
            .background(Color(0xFF12161B).copy(alpha = 0.86f))
            .border(1.dp, KoridorGold.copy(alpha = 0.45f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        PremiumGlyph(icon, Modifier.size(Dimens.IconSm), KoridorGold)
    }
}

/** The gold disc with a pencil in it, on the avatar and beside the name. */
@Composable
private fun PencilBadge(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 30.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF171C22))
            .border(1.dp, KoridorGold.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.46f)) {
            val edge = this.size.minDimension
            val nib = edge * 0.22f
            // A pencil is a bar on the diagonal with a point at one end. Drawn rather than
            // imported so it inherits nothing and needs no density set of its own.
            drawLine(
                color = KoridorGold,
                start = Offset(edge * 0.14f, edge * 0.86f),
                end = Offset(edge * 0.82f, edge * 0.18f),
                strokeWidth = nib,
            )
            drawLine(
                color = KoridorGold,
                start = Offset(edge * 0.08f, edge * 0.92f),
                end = Offset(edge * 0.30f, edge * 0.86f),
                strokeWidth = nib * 0.7f,
            )
        }
    }
}

/**
 * Rating, matches, wins, losses — the four numbers the game keeps about a player.
 *
 * Deliberately quiet. The first version set the values at `headlineSmall` with a 20dp mark
 * beside each and 16dp of air above and below, and on the handset the strip was the loudest
 * thing on a page whose subject is the person, not their rating. The numbers are the same size
 * as the name above them now, and the marks sit over them rather than beside them, which is
 * also the only arrangement in which four cells fit across a phone without the labels eliding.
 */
@Composable
fun ProfileStatStrip(
    rating: Int,
    games: Int,
    wins: Int,
    losses: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(CardFill)
            .border(1.dp, FieldEdge, RoundedCornerShape(Dimens.RadiusMd))
            .padding(vertical = Dimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCell(PremiumIcon.STAR, rating.toString(), stringResource(R.string.profile_rating))
        StripDivider()
        StatCell(PremiumIcon.GAMEPAD, games.toString(), stringResource(R.string.profile_games))
        StripDivider()
        StatCell(PremiumIcon.TROPHY, wins.toString(), stringResource(R.string.profile_wins))
        StripDivider()
        StatCell(PremiumIcon.SHIELD_STAR, losses.toString(), stringResource(R.string.profile_losses))
    }
}

@Composable
private fun RowScope.StatCell(icon: PremiumIcon, value: String, label: String) {
    Column(
        Modifier.weight(1f).padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        PremiumGlyph(icon, Modifier.size(15.dp), KoridorGold)
        Text(
            text = value,
            // Tabular figures, so a rating that changes by one digit does not shuffle the cells
            // sideways. It belongs to the style rather than to Text, which has no parameter.
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StripDivider() {
    Box(
        Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(Color(0xFF20262D)),
    )
}

/**
 * One of the three cards under the record.
 *
 * The accent is a parameter and the three callers pass three different colours, which is a
 * deliberate exception to the home screen's rule that every mark is gold. That rule is about a
 * *menu* — a row of destinations reads as one list when the marks match. These are three
 * unrelated things a player collects, and the colour is how the eye tells them apart before it
 * reads the word.
 */
@Composable
fun RowScope.ProfileFeatureCard(
    icon: PremiumIcon,
    accent: Color,
    title: String,
    headline: String?,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    detail: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .weight(1f)
            // A fixed height rather than IntrinsicSize.Max on the row. The three cards have to
            // agree on where their buttons sit, and an intrinsic pass over a column that also
            // uses weight is both the expensive way and the fragile way to get that.
            .height(FEATURE_CARD_HEIGHT)
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(CardFill)
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(Dimens.RadiusMd))
            .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceMd),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The badge sits in its own row above the mark rather than on top of it. Aligned to the
        // card's trailing edge it landed across the glyph — a card this narrow has no corner
        // free — and a label overlapping the icon it labels reads as a rendering fault.
        Box(Modifier.fillMaxWidth().height(BADGE_ROW), contentAlignment = Alignment.CenterEnd) {
            badge?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF0B0E11),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            PremiumGlyph(icon, Modifier.size(21.dp), accent)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        headline?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
            )
        }
        detail?.invoke()
        // The sentence of explanation each card carried is gone. Three of them stacked twelve
        // lines of grey text into the middle of the screen and pushed the recent matches under
        // the docked bar — the card's own title and number say the same thing in two words.
        Spacer(Modifier.weight(1f))
        Text(
            text = action,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.16f))
                .clickable(role = Role.Button, onClick = onAction)
                .padding(vertical = 8.dp),
        )
    }
}

/**
 * All three cards, so their buttons line up without measuring anything.
 *
 * Sized for the worst case rather than the average, because the consequence of getting it wrong
 * is invisible in code and obvious on a handset: at 158dp the two-line titles pushed "Görüntüle"
 * and "Detaylar" past the bottom edge and both buttons shipped sliced in half. The worst case is
 * badge row + mark + a title that wraps to two lines + a headline + a detail + the button, and
 * every gap between them.
 */
private val FEATURE_CARD_HEIGHT = 200.dp

/** Kept even on the cards with no badge, so all three marks sit on the same line. */
private val BADGE_ROW = 18.dp

/** A bar that fills left to right, for "badges earned out of all of them". */
@Composable
fun ProfileProgressBar(fraction: Float, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF20262D)),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(accent),
            )
        }
    }
}

/** A run of pips, filled up to [filled]. The streak card's readout. */
@Composable
fun ProfilePips(filled: Int, total: Int, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(total) { index ->
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (index < filled) accent else Color(0xFF262C33)),
            )
        }
    }
}

/**
 * The matches behind you: who, when, and what it cost or paid.
 *
 * The outcome disc carries the letter as well as the colour, because the three states differ by
 * hue alone otherwise and a red-green pair is the one distinction a large minority cannot make.
 */
@Composable
fun RecentGamesPremium(
    matches: List<RecentMatch>,
    rating: Int,
    onOpenPlayer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Saveable, so an expanded list survives a rotation and the trip to a rival's page.
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) matches else matches.take(COLLAPSED_ROWS)
    // What the rating stood at after each match, walked back from what it stands at now. The
    // list is newest first and every ranked match carries its own delta, so subtracting the
    // deltas of the matches *above* a row gives that row's number exactly — no second field
    // and no server change. It holds because nothing but a match moves the rating; if that
    // ever stops being true, this column becomes an estimate and should go.
    val after = remember(matches, rating) {
        var running = rating
        matches.map { match ->
            val value = running
            running -= match.ratingChange ?: 0
            value
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(CardFill)
            .border(1.dp, FieldEdge, RoundedCornerShape(Dimens.RadiusMd)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceMd),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PremiumGlyph(PremiumIcon.CLOCK, Modifier.size(18.dp), Muted)
            Text(
                text = stringResource(R.string.profile_recent_games),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (matches.size > COLLAPSED_ROWS) {
                Text(
                    text = if (expanded) {
                        stringResource(R.string.profile_recent_show_fewer)
                    } else {
                        stringResource(R.string.profile_recent_show_all, matches.size)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = KoridorGold,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(role = Role.Button) { expanded = !expanded }
                        .padding(horizontal = Dimens.SpaceSm, vertical = 4.dp),
                )
            }
        }
        shown.forEachIndexed { index, match ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = if (index == 0) 0.dp else 60.dp)
                    .height(1.dp)
                    .background(Color(0xFF20262D)),
            )
            RecentGameRow(match, after.getOrNull(index), onOpenPlayer)
        }
    }
}

/** How many matches the card shows before it has to be asked for the rest. */
private const val COLLAPSED_ROWS = 4

@Composable
private fun RecentGameRow(match: RecentMatch, after: Int?, onOpenPlayer: (String) -> Unit) {
    val tone = when (match.outcome) {
        MatchOutcome.WIN -> Color(0xFF4CC38A)
        MatchOutcome.LOSS -> Color(0xFFE2776C)
        MatchOutcome.DRAW -> Muted
    }
    val letter = stringResource(
        when (match.outcome) {
            MatchOutcome.WIN -> R.string.profile_recent_letter_win
            MatchOutcome.LOSS -> R.string.profile_recent_letter_loss
            MatchOutcome.DRAW -> R.string.profile_recent_letter_draw
        },
    )
    val name = match.opponentName.ifBlank {
        stringResource(R.string.profile_recent_unknown_opponent)
    }
    val row = Modifier
        .fillMaxWidth()
        .then(
            if (match.hasOpponentProfile) {
                Modifier.clickable(role = Role.Button) { onOpenPlayer(match.opponentUserId) }
            } else {
                Modifier
            },
        )
        .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceMd)
    Row(row, verticalAlignment = Alignment.CenterVertically) {
        // The seam of colour down the leading edge, which is what makes a run of wins legible
        // as a run rather than as four rows that each have to be read.
        Box(
            Modifier
                .width(3.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(50))
                .background(tone),
        )
        Spacer(Modifier.width(Dimens.SpaceMd))
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .border(1.5.dp, tone.copy(alpha = 0.75f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(letter, style = MaterialTheme.typography.labelLarge, color = tone, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(Dimens.SpaceMd))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = remember(match.playedAt) {
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(match.playedAt))
                },
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 1,
            )
        }
        val change = match.ratingChange
        if (change != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PremiumGlyph(PremiumIcon.TROPHY, Modifier.size(15.dp), KoridorGold)
                Text(
                    text = if (change >= 0) "+$change" else change.toString(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = if (change >= 0) Color(0xFF4CC38A) else Color(0xFFE2776C),
                    maxLines = 1,
                )
            }
            after?.let {
                Spacer(Modifier.width(Dimens.SpaceMd))
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                    color = Muted,
                    maxLines = 1,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.profile_recent_unranked),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 1,
            )
        }
        if (match.hasOpponentProfile) {
            Spacer(Modifier.width(Dimens.SpaceXs))
            OptionChevron()
        }
    }
}

/**
 * What stands in for the match list before there is one.
 *
 * It was one grey sentence in a bordered box, which is the shape of an error rather than of a
 * beginning — the first thing a new player sees on their own page should not look like something
 * went wrong. So: the same clock the list header carries, ringed and dimmed to say *empty* rather
 * than *broken*, a line naming what will appear here, and a line saying what fills it.
 *
 * The heading is inside this card too, so an empty list and a full one have the same top edge.
 */
@Composable
fun ProfileEmptyGames(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(CardFill)
            .border(BorderStroke(1.dp, FieldEdge), RoundedCornerShape(Dimens.RadiusMd)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceMd),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PremiumGlyph(PremiumIcon.CLOCK, Modifier.size(18.dp), Muted)
            Text(
                text = stringResource(R.string.profile_recent_games),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF20262D)))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceLg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF171C22))
                    .border(1.dp, Color(0xFF262C33), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PremiumGlyph(PremiumIcon.GLOBE, Modifier.size(22.dp), Color(0xFF5C6169))
            }
            Text(
                text = stringResource(R.string.profile_recent_empty_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.profile_recent_empty),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
