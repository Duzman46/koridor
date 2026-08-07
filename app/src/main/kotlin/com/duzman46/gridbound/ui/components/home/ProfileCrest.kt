package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.profile.domain.UserProfile
import com.duzman46.gridbound.session.SessionState
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.LocalKoridorColors
import com.duzman46.gridbound.ui.components.PlayerAvatar
import com.duzman46.gridbound.ui.components.drawPawnMark
import java.util.Locale

/** A streak this long fills the ring. Short enough to be reachable, long enough to mean something. */
private const val STREAK_TARGET = 5

/**
 * Who is playing, at the trailing end of the home screen's utility row.
 *
 * The ring around the avatar carries the state without a word of translated text: dashed
 * means there is no real account behind this yet, a solid track means there is, and the arc
 * fills as a win streak grows.
 *
 * A null profile is not a loading blip — [SessionState.profile] is only ever populated for a
 * signed-in player, so a guest playing offline has none and never will. That state gets a
 * pawn in a dashed ring rather than an empty circle, and it stays tappable: it is the way in
 * to linking an account.
 *
 * The signed-in form brings its own dark ground with it, so the rating and the streak arc keep
 * the contrast they were drawn for. The two provisional forms have no ground of their own and
 * are read straight off the page, so their marks come from the theme instead.
 */
@Composable
fun ProfileCrest(
    session: SessionState,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = session.profile
    when {
        profile == null -> AnonymousCrest(onProfile, modifier)
        session.isGuest -> GuestCrest(profile, onProfile, modifier)
        else -> SignedInCrest(profile, onProfile, modifier)
    }
}

@Composable
private fun SignedInCrest(profile: UserProfile, onProfile: () -> Unit, modifier: Modifier) {
    val ratingLabel = stringResource(R.string.profile_rating)
    Surface(
        onClick = onProfile,
        modifier = modifier
            .heightIn(min = Dimens.CrestHeight)
            .semantics(mergeDescendants = true) {
                contentDescription = "${profile.username}, $ratingLabel ${profile.rating}"
            },
        shape = RoundedCornerShape(percent = 50),
        color = HomePalette.ChipFill.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, HomePalette.ChipStroke),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = profile.rating.toString(),
                modifier = Modifier.clearAndSetSemantics { },
                // Tabular figures: without them the capsule changes width between 998 and
                // 1284 and the whole crest twitches when a match ends.
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = HomePalette.OnWell,
                maxLines = 1,
                softWrap = false,
            )
            Box(
                Modifier
                    .size(40.dp)
                    .streakRing(profile.currentWinStreak),
                contentAlignment = Alignment.Center,
            ) {
                PlayerAvatar(profile.avatarId, profile.username, size = 34.dp)
            }
        }
    }
}

@Composable
private fun GuestCrest(profile: UserProfile, onProfile: () -> Unit, modifier: Modifier) {
    // No rating: a guest's starting 1000 has not been earned, and showing it would be noise.
    val label = stringResource(R.string.profile_title)
    val badge = stringResource(R.string.auth_guest_badge)
    Box(
        modifier = modifier
            // Surface reserves the 48 dp minimum for the other two forms; a bare Box has to
            // ask, or the guest is the one player whose crest holds less room than a finger.
            .minimumInteractiveComponentSize()
            .size(Dimens.CrestHeight)
            .clip(CircleShape)
            .clickable(onClick = onProfile)
            .semantics(mergeDescendants = true) { contentDescription = "$label, $badge" },
        contentAlignment = Alignment.Center,
    ) {
        PlayerAvatar(profile.avatarId, profile.username, size = 34.dp)
        DashedRing()
    }
}

/**
 * Nobody signed in yet — a guest playing locally, or the first moments of a cold start.
 *
 * A plain pawn rather than an avatar, because there is no name to take an initial from, and
 * no spinner, because a spinner beside the language switch says "wait" when there is nothing
 * to wait for. Still tappable: this is where linking an account starts.
 *
 * The pawn is theme ink. The near-white it used to be was mixed to sit on the well, and a
 * near-white pawn on the light theme's pale ground is a 1.0:1 mark — a control that is simply
 * not there.
 */
@Composable
private fun AnonymousCrest(onProfile: () -> Unit, modifier: Modifier) {
    val label = stringResource(R.string.profile_title)
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(Dimens.CrestHeight)
            .clip(CircleShape)
            .clickable(onClick = onProfile)
            .semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(Dimens.CrestHeight)) {
            drawPawnMark(
                center = Offset(size.width / 2f, size.height / 2f),
                unit = size.minDimension * 0.62f,
                color = ink,
            )
        }
        DashedRing()
    }
}

/**
 * The "no real account yet" ring.
 *
 * `provisionalRing` rather than a fixed grey-green: the dark theme's value is the same colour
 * this ring has always been, while the light theme's is darkened to clear 4.5:1 on pale
 * ground, where the original managed 2.3:1 and read as a smudge.
 */
@Composable
private fun DashedRing() {
    val ring = LocalKoridorColors.current.provisionalRing
    Canvas(Modifier.size(Dimens.CrestHeight)) {
        drawCircle(
            color = ring,
            radius = 20.dp.toPx(),
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(5.dp.toPx(), 4.dp.toPx()),
                ),
            ),
        )
    }
}

/**
 * The win-streak arc.
 *
 * `drawWithContent` rather than `drawBehind`: the avatar is a filled circle and would cover
 * anything drawn underneath it. A streak of zero leaves the bare track, which reads as
 * "nothing going yet" rather than as a broken control.
 */
private fun Modifier.streakRing(streak: Int): Modifier = drawWithContent {
    drawContent()
    val width = 3.dp.toPx()
    drawCircle(
        color = Color.White.copy(alpha = 0.14f),
        radius = (size.minDimension - width) / 2f,
        style = Stroke(width),
    )
    val progress = streak.coerceIn(0, STREAK_TARGET) / STREAK_TARGET.toFloat()
    if (progress > 0f) {
        drawArc(
            color = HomePalette.Accent,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = Offset(width / 2f, width / 2f),
            size = Size(size.width - width, size.height - width),
            style = Stroke(width, cap = StrokeCap.Round),
        )
    }
}

/**
 * A round glyph button, matching the language chip and the crest in the same row.
 *
 * Cut from the well's material rather than the theme's: these read as small pieces of the
 * board panel they sit above, which is what holds the top of the screen together, and a fixed
 * dark ground is what lets the jade in the crest keep the contrast it was mixed for on a pale
 * page as well as a near-black one.
 */
@Composable
fun HomeIconButton(
    glyph: GlyphKind,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(Dimens.CrestHeight)
            .semantics(mergeDescendants = true) { contentDescription = label },
        shape = CircleShape,
        color = HomePalette.ChipFill.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, HomePalette.ChipStroke),
    ) {
        Box(contentAlignment = Alignment.Center) {
            KoridorGlyph(glyph, Modifier.size(20.dp), tint = HomePalette.OnWell)
        }
    }
}

/**
 * The language switch, as the two-letter code of the language you are currently reading.
 *
 * A globe says "language settings exist"; "TR" says which one you are on, needs no
 * translation, and cannot overflow in any of the ten locales.
 */
@Composable
fun LanguageChip(
    language: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalResources.current.configuration
    val effective = AppLanguage.resolve(language, configuration.locales[0].language)
    val code = effective.tag.take(2).uppercase(Locale.ROOT)
    val label = stringResource(R.string.settings_language)
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(Dimens.CrestHeight)
            .semantics(mergeDescendants = true) { contentDescription = label },
        shape = CircleShape,
        color = HomePalette.ChipFill.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, HomePalette.ChipStroke),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = code,
                modifier = Modifier.clearAndSetSemantics { },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = HomePalette.OnWell,
            )
        }
    }
}
