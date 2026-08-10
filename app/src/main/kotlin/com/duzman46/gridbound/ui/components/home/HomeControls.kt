package com.duzman46.gridbound.ui.components.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.session.SessionState
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorJade
import com.duzman46.gridbound.ui.components.BlockHeight
import com.duzman46.gridbound.ui.components.BlockRank
import com.duzman46.gridbound.ui.components.KoridorBlock

/**
 * The home screen's utility row: other people on one side, the player's own things on the
 * other, above the board panel rather than floating in its corners.
 *
 * These four are not menu entries and never were — three things nobody opens often should not
 * take three of the four choices on the home screen. But sitting on the artwork they landed on
 * the board's top rank of tiles, and a settings mark on a pawn reads as a mistake rather than
 * as a layer. A row of their own costs one line and collides with nothing.
 *
 * Friends is alone at the leading edge and the isolation is the point: it is the one mark here
 * that leads to other people rather than to the player's own settings, so grouping it with
 * them would have buried it.
 *
 * The trailing group is weighted rather than free, so when a doubled font scale widens the
 * crest it grows into what is left of the row instead of sliding under the friends button.
 */
@Composable
fun HomeTopBar(
    session: SessionState,
    language: AppLanguage,
    onFriends: () -> Unit,
    onMore: () -> Unit,
    onLanguage: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeIconButton(GlyphKind.FRIENDS, stringResource(R.string.friends_title), onFriends)
        Row(
            Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // "More" rather than "Settings". Settings is now a tile in the grid below, and this
            // file's own rule is that one destination reachable twice from one screen teaches
            // the player that neither route is real. More has no tile — it is the drawer of
            // things nobody looks for by name — so this is its only way in.
            HomeIconButton(GlyphKind.MORE, stringResource(R.string.menu_more), onMore)
            LanguageChip(language, onLanguage)
            ProfileCrest(session, onProfile)
        }
    }
}

/**
 * The name of the game, on its own line between the utility row and the board panel.
 *
 * It used to sit inside the panel, and carrying it there cost two scrim gradients that dimmed
 * the bottom half of the artwork so that seven letters could be read against tiles. The
 * picture is the point of the panel, so the letters had to leave rather than the picture.
 *
 * Not folded into [HomeTopBar] either: the two control groups leave roughly 130 dp between
 * them on a 360 dp phone, and the wordmark needs half again that at the size it has to be to
 * still be a wordmark. Squeezed between chips it becomes a caption. Given a line it keeps its
 * full weight, it is the first thing read on the screen, and it still labels the picture
 * directly beneath it — from above instead of from on top.
 */
/**
 * The one loud action on a screen — now the block system's PRIMARY at loud size.
 *
 * Kept as its own name so the screens read as what they are rather than as a rank enum,
 * and so the press behaviour is defined in exactly one place.
 */
@Composable
fun PlaySlab(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: GlyphKind = GlyphKind.QUICK_PLAY,
    subtitle: String? = null,
) {
    if (subtitle == null) {
        KoridorBlock(
            label = label,
            onClick = onClick,
            modifier = modifier,
            rank = BlockRank.PRIMARY,
            glyph = glyph,
            loud = true,
        )
        return
    }
    GradientSlab(label = label, subtitle = subtitle, glyph = glyph, onClick = onClick, modifier = modifier)
}

/**
 * The one control on the home screen that is painted rather than filled.
 *
 * Every other surface in this app is a flat colour, and that is deliberate — a gradient on a
 * card is decoration, and decoration on six cards is noise. Exactly one control gets one, and
 * it is the way into the game: the sweep from jade to violet is what makes it read as the
 * loudest thing on the screen without being the biggest.
 *
 * The two ends are the app's own two accents — the jade a wall is drawn in, and the violet the
 * bot wears — so the button is not carrying a third palette. It sweeps along the reading
 * direction, which mirrors to the left in an RTL layout for the same reason the glyphs do: the
 * gradient points at the label, and in Arabic the label is at the other end.
 */
@Composable
private fun GradientSlab(
    label: String,
    subtitle: String,
    glyph: GlyphKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val travel by animateDpAsState(
        targetValue = if (pressed) Dimens.PressTravel else 0.dp,
        animationSpec = tween(durationMillis = if (pressed) 40 else 90, easing = LinearEasing),
        label = "slabTravel",
    )
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ends = if (rtl) listOf(SlabViolet, KoridorJade) else listOf(KoridorJade, SlabViolet)
    // Dimmed as one brush rather than by swapping two more hexes in: a pressed state that
    // re-specifies the gradient is a second gradient to keep in step with the first.
    val brush = Brush.horizontalGradient(ends.map { if (pressed) it.copy(alpha = 0.86f) else it })
    val shape = RoundedCornerShape(Dimens.RadiusMd)
    val ink = MaterialTheme.colorScheme.onPrimary

    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .offset { IntOffset(x = 0, y = travel.roundToPx()) }
                .padding(bottom = Dimens.PressTravel)
                .fillMaxWidth()
                .clip(shape)
                .background(brush)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics(mergeDescendants = true) { contentDescription = "$label. $subtitle" }
                .heightIn(min = BlockHeight.Loud)
                .padding(horizontal = 20.dp, vertical = Dimens.SpaceMd),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The glyph rides in a disc of the ink it is drawn against, which is what stops a
            // jade-on-jade mark from disappearing into the left end of the sweep.
            Box(
                Modifier
                    .size(Dimens.CrestHeight)
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                KoridorGlyph(glyph, Modifier.size(Dimens.GlyphLg), tint = ink)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ink.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The far end of the play button's sweep — the same violet the bot's accent tone is built on. */
private val SlabViolet = Color(0xFF7C5CE6)

/** A real alternative to the loud action: outlined, never a tinted pill. */
@Composable
fun HomeChoice(
    label: String,
    glyph: GlyphKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KoridorBlock(
        label = label,
        onClick = onClick,
        modifier = modifier,
        rank = BlockRank.SECONDARY,
        glyph = glyph,
    )
}
