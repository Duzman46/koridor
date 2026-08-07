package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.game.board.SeatColors
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.components.ScreenTopBar
import com.duzman46.gridbound.ui.components.home.GlyphKind
import com.duzman46.gridbound.ui.components.home.HomeChoice
import com.duzman46.gridbound.ui.components.home.PlaySlab
import kotlin.random.Random

/**
 * How you want to play.
 *
 * The single question behind "Play". Playing a stranger, playing a bot and playing the person
 * next to you are the three real answers; everything else — difficulty, room codes, opponents
 * — belongs to whichever of them you picked, not to this screen.
 *
 * Online leads and is the one loud target. It is the mode the game is built around and the
 * only one that needs another person waiting, so it should not be the third thing read.
 */
@Composable
fun PlayModeScreen(
    onBack: () -> Unit,
    onVsBot: () -> Unit,
    onLocal: () -> Unit,
    onOnline: () -> Unit,
) {
    ModeColumn(stringResource(R.string.menu_play), onBack) {
        PlaySlab(stringResource(R.string.menu_online), onClick = onOnline, glyph = GlyphKind.ONLINE)
        HomeChoice(stringResource(R.string.play_vs_bot), GlyphKind.VS_BOT, onClick = onVsBot)
        HomeChoice(stringResource(R.string.play_local), GlyphKind.FRIENDS, onClick = onLocal)
    }
}

/**
 * Which bot.
 *
 * Its own step rather than a row of chips inside a sheet: it is the only decision a player
 * makes before a solo match, so it gets the whole screen.
 *
 * All three carry equal weight. Difficulty is a preference, not a recommendation — filling
 * one of them made the screen look like it was steering the player towards the easy bot.
 */
@Composable
fun DifficultyScreen(onBack: () -> Unit, onSelected: (Difficulty, PlayerId) -> Unit) {
    // Random by default, and settled once when the screen opens rather than on every
    // recomposition — a colour that flickered while you were reading the difficulties would
    // be worse than no choice at all.
    var seat by rememberSaveable {
        mutableStateOf(if (Random.nextBoolean()) PlayerId.PLAYER_ONE else PlayerId.PLAYER_TWO)
    }
    ModeColumn(stringResource(R.string.difficulty_title), onBack) {
        SeatPicker(selected = seat, onSelect = { seat = it })
        HomeChoice(stringResource(R.string.difficulty_easy), GlyphKind.QUICK_PLAY, onClick = { onSelected(Difficulty.EASY, seat) })
        HomeChoice(stringResource(R.string.difficulty_medium), GlyphKind.VS_BOT, onClick = { onSelected(Difficulty.MEDIUM, seat) })
        HomeChoice(stringResource(R.string.difficulty_hard), GlyphKind.LEADERBOARD, onClick = { onSelected(Difficulty.HARD, seat) })
    }
}

/**
 * Which colour you play, which is to say which seat: blue is seat one and opens.
 *
 * Two swatches rather than a labelled dropdown: the choice *is* a colour, so showing the
 * colour is both the label and the value. Each carries its name as well, because a control
 * that can only be read by hue is one a colour-blind player cannot use.
 */
@Composable
fun SeatPicker(selected: PlayerId, onSelect: (PlayerId) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        Text(
            stringResource(R.string.paint_label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            SeatSwatch(PlayerId.PLAYER_ONE, selected, onSelect, Modifier.weight(1f))
            SeatSwatch(PlayerId.PLAYER_TWO, selected, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SeatSwatch(
    seat: PlayerId,
    selected: PlayerId,
    onSelect: (PlayerId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chosen = seat == selected
    val swatch = SeatColors.pawn(seat)
    val label = stringResource(
        if (seat == PlayerId.PLAYER_ONE) R.string.game_player_blue else R.string.game_player_red,
    )
    Surface(
        onClick = { onSelect(seat) },
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(Dimens.RadiusSm),
        color = if (chosen) swatch.copy(alpha = 0.18f) else Color.Transparent,
        border = BorderStroke(
            width = if (chosen) Dimens.BorderStrong else 1.dp,
            // An unchosen option carries no fill, so this hairline is the whole control: it
            // has to be the outline proper rather than the divider token, which is mixed to
            // be ignored.
            color = if (chosen) swatch else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(swatch))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/** The shared frame: a title bar and one column of full-width choices. */
@Composable
private fun ModeColumn(
    title: String,
    onBack: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Scaffold(topBar = { ScreenTopBar(title, onBack) }) { padding ->
        ScreenBackground {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = Dimens.MenuMaxWidth)
                        .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceXl),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                    content = content,
                )
            }
        }
    }
}
