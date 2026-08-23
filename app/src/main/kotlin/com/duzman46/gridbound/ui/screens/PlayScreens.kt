package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.duzman46.gridbound.theme.Palette
import com.duzman46.gridbound.ui.components.home.BotLevelRow
import com.duzman46.gridbound.ui.components.home.FieldLabel
import com.duzman46.gridbound.ui.components.home.HomeHero
import com.duzman46.gridbound.ui.components.home.HomeSceneShare
import com.duzman46.gridbound.ui.components.home.PlayModeCard
import com.duzman46.gridbound.ui.components.home.PremiumActionButton
import com.duzman46.gridbound.ui.components.home.PremiumHeader
import com.duzman46.gridbound.ui.components.home.PremiumIcon
import com.duzman46.gridbound.ui.components.home.SeatCard
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.duzman46.gridbound.R
import com.duzman46.gridbound.game.board.SeatColors
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.theme.Dimens

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
    showAdBanner: Boolean,
) {
    // No banner here any more. This screen now opens on the same scene the home screen does, and
    // a strip of advertising under a photograph is what turns a game into a storefront. The
    // interstitial after a match still runs; that is where the app asks.
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                // The share the picture itself declares. Weighted against the cards rather than
                // taking what they leave, so one photograph is one size across every screen a
                // player passes through on the way to a match.
                .weight(HomeSceneShare),
        ) {
            HomeHero(Modifier.fillMaxSize())
            // The app's one header, over the picture rather than instead of it. This was a
            // hand-built row here, another on the difficulty screen and a third in the lobby —
            // three copies of an arrow and a gold word, and the only thing they did differently
            // from PremiumHeader was set the title SemiBold instead of Bold.
            PremiumHeader(
                title = stringResource(R.string.menu_play),
                onBack = onBack,
                modifier = Modifier.statusBarsPadding(),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = Dimens.MenuMaxWidth)
                .align(Alignment.CenterHorizontally)
                .weight(1f - HomeSceneShare)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(top = Dimens.SpaceMd, bottom = Dimens.SpaceSm)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        ) {
            // Online leads and wears the gold. It is the mode the game is built around and the
            // only one that needs another person waiting, so it must not be the third thing read.
            PlayModeCard(
                title = stringResource(R.string.menu_online),
                subtitle = stringResource(R.string.play_online_subtitle),
                icon = PremiumIcon.GLOBE,
                onClick = onOnline,
                leading = true,
            )
            PlayModeCard(
                title = stringResource(R.string.play_vs_bot),
                subtitle = stringResource(R.string.play_bot_subtitle),
                icon = PremiumIcon.ROBOT,
                onClick = onVsBot,
            )
            PlayModeCard(
                title = stringResource(R.string.play_local),
                subtitle = stringResource(R.string.play_local_subtitle),
                icon = PremiumIcon.PEOPLE,
                onClick = onLocal,
            )
        }
    }
}

/**
 * Which bot.
 *
 * Its own step rather than a row of chips inside a sheet: it is the only decision a player
 * makes before a solo match, so it gets the whole screen.
 *
 * All four carry equal weight. Difficulty is a preference, not a recommendation — filling one
 * of them made the screen look like it was steering the player towards the easy bot.
 */
@Composable
fun DifficultyScreen(onBack: () -> Unit, onSelected: (Difficulty, PlayerId) -> Unit) {
    // Blue every time. It was random, which is right for a match against a stranger — nobody
    // gets the first move by choosing it — and wrong here: there is no opponent to be fair to,
    // and a solo player who never touches this control should get the same board every time
    // rather than one that silently changes who opens.
    var seat by rememberSaveable { mutableStateOf(PlayerId.PLAYER_ONE) }

    // Chosen, then started — rather than started by the act of choosing.
    //
    // Tapping a difficulty used to launch the match. That is one tap fewer and it costs the
    // screen its whole purpose: a player who wanted red and hard had to pick the colour first
    // and could never change their mind about it afterwards, because the second choice was
    // already the door out. Two decisions and one door now.
    var level by rememberSaveable { mutableStateOf(Difficulty.MEDIUM) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(DIFFICULTY_SCENE_SHARE),
        ) {
            HomeHero(Modifier.fillMaxSize())
            PremiumHeader(
                title = stringResource(R.string.difficulty_title),
                onBack = onBack,
                modifier = Modifier.statusBarsPadding(),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                // No scroll. Two questions and a button is not a page, and a screen that
                // scrolls to reach its own start button is a screen that has been overfilled.
                // The rows give ground instead: they are weighted, so four of them share
                // whatever is left rather than each insisting on a height of its own.
                .weight(1f - DIFFICULTY_SCENE_SHARE)
                .widthIn(max = Dimens.MenuMaxWidth)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(top = Dimens.SpaceSm, bottom = Dimens.SpaceSm)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            FieldLabel(stringResource(R.string.paint_label))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                PlayerId.entries.forEach { option ->
                    SeatCard(
                        swatch = SeatColors.pawn(option),
                        name = stringResource(
                            if (option == PlayerId.PLAYER_ONE) {
                                R.string.game_player_blue
                            } else {
                                R.string.game_player_red
                            },
                        ),
                        role = stringResource(
                            if (option == seat) R.string.paint_yours else R.string.paint_rivals,
                        ),
                        chosen = option == seat,
                        onClick = { seat = option },
                    )
                }
            }

            FieldLabel(stringResource(R.string.difficulty_level_label))
            Difficulty.entries.forEachIndexed { index, option ->
                BotLevelRow(
                    title = stringResource(option.label),
                    subtitle = stringResource(option.hint),
                    rank = index + 1,
                    chosen = option == level,
                    onClick = { level = option },
                    modifier = Modifier.weight(1f),
                )
            }

            PremiumActionButton(
                label = stringResource(R.string.difficulty_start),
                onClick = { onSelected(level, seat) },
                modifier = Modifier.fillMaxWidth(),
                filled = true,
                mark = { drawStartTriangle(Palette.GoldInk) },
            )
        }
    }
}

/** How much of the window the scene keeps here — the same share the online screen settles on. */
private const val DIFFICULTY_SCENE_SHARE = 0.20f

private val Difficulty.label: Int
    get() = when (this) {
        Difficulty.EASY -> R.string.difficulty_easy
        Difficulty.MEDIUM -> R.string.difficulty_medium
        Difficulty.HARD -> R.string.difficulty_hard
        Difficulty.EXPERT -> R.string.difficulty_expert
    }

private val Difficulty.hint: Int
    get() = when (this) {
        Difficulty.EASY -> R.string.difficulty_easy_hint
        Difficulty.MEDIUM -> R.string.difficulty_medium_hint
        Difficulty.HARD -> R.string.difficulty_hard_hint
        Difficulty.EXPERT -> R.string.difficulty_expert_hint
    }

/** The same triangle the home screen's play card wears, at the size a button label needs. */
private fun DrawScope.drawStartTriangle(tint: Color) {
    val s = size.minDimension
    drawPath(
        Path().apply {
            moveTo(s * 0.20f, s * 0.10f)
            lineTo(s * 0.86f, s * 0.50f)
            lineTo(s * 0.20f, s * 0.90f)
            close()
        },
        tint,
    )
}
