package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.session.SessionState
import com.duzman46.gridbound.theme.Dimens
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
    onSettings: () -> Unit,
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
            HomeIconButton(GlyphKind.SETTINGS, stringResource(R.string.game_settings), onSettings)
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
@Composable
fun HomeWordmark(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    // Sized in dp then converted, so the wordmark keeps a fixed cap height and grows by at
    // most a quarter at large font scales. Seven Black glyphs cannot clip at 320 dp.
    val wordmarkSize = with(density) { 30.dp.toSp() } * density.fontScale.coerceAtMost(1.25f)
    Text(
        text = stringResource(R.string.app_name).uppercase(),
        modifier = modifier,
        fontSize = wordmarkSize,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

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
) {
    KoridorBlock(
        label = label,
        onClick = onClick,
        modifier = modifier,
        rank = BlockRank.PRIMARY,
        glyph = glyph,
        loud = true,
    )
}

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
