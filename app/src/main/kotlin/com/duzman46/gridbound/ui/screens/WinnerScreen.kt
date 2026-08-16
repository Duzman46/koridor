package com.duzman46.gridbound.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.online.model.RoomEndReason
import com.duzman46.gridbound.presentation.game.RematchStage
import com.duzman46.gridbound.presentation.game.RematchUiState
import com.duzman46.gridbound.presentation.game.RematchViewModel
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.ui.components.FormMessage
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.components.home.PremiumActionButton
import com.duzman46.gridbound.ui.components.home.PremiumIcon
import com.duzman46.gridbound.ui.components.home.WinnerConfetti
import com.duzman46.gridbound.ui.components.home.WinnerHeadline
import com.duzman46.gridbound.ui.components.home.WinnerMark
import com.duzman46.gridbound.ui.components.home.WinnerPanel
import com.duzman46.gridbound.ui.components.home.WinnerStatusStrip

/**
 * The end of a match, and what can follow it.
 *
 * @param playedRoomCode the room the match was played in, empty off line. Together with
 *   [opponentUserId] it is what makes a rematch possible: a rematch is a question put to a
 *   named person about a specific finished game, and without either of them there is nobody
 *   to ask and nothing to prove we ever played them.
 */
@Composable
fun WinnerRoute(
    winner: PlayerId,
    mode: GameMode,
    localPlayer: PlayerId,
    endReason: RoomEndReason,
    playedRoomCode: String,
    opponentUserId: String,
    onReplay: () -> Unit,
    onHome: () -> Unit,
    onRematchAccepted: (OnlineSession) -> Unit,
    viewModel: RematchViewModel = hiltViewModel(),
) {
    val rematch by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.accepted.collect(onRematchAccepted) }
    // Back here means "I am done with this match", which is what Home means, so it goes the same
    // way. Left to the navigator it popped the stack on its own and landed the player back on the
    // difficulty screen — behind the match, past the ad, and on a screen they had already left.
    // Every way out of a finished match now runs through one of the three the screen offers.
    BackHandler(onBack = onHome)
    WinnerScreen(
        winner = winner,
        mode = mode,
        localPlayer = localPlayer,
        endReason = endReason,
        rematch = rematch,
        // Against a bot, on a shared handset, or where the rival never had an account there is
        // nobody on the far end of a request, so the button keeps its old meaning and its old
        // name and simply starts the next game.
        canRematch = mode == GameMode.ONLINE && rematch.canAsk &&
            playedRoomCode.isNotBlank() && opponentUserId.isNotBlank(),
        onRematch = { viewModel.ask(playedRoomCode, opponentUserId, localPlayer) },
        onReplay = onReplay,
        onHome = onHome,
    )
}

/**
 * One card in the middle of an empty screen, and no chrome around it.
 *
 * This is pushed and modal rather than one of the places the docked bar switches between, so it
 * takes neither the bar's clearance nor a header: there is nothing here to come back from, only
 * the three ways on, and they are in the card. The card is held to the width the app gives its
 * modal family rather than the wider one its scrolling lists get — a verdict read across five
 * hundred device-independent pixels on a tablet is a verdict nobody's eye can hold in one line.
 */
@Composable
private fun WinnerScreen(
    winner: PlayerId,
    mode: GameMode,
    /** Carried from the match, because against a bot the player is not always seat one. */
    localPlayer: PlayerId,
    /**
     * How an online match ended. A player who was left, or whose clock ran out, is owed the
     * reason: without it the screen claims someone crossed the board when nobody did.
     */
    endReason: RoomEndReason,
    rematch: RematchUiState,
    canRematch: Boolean,
    onRematch: () -> Unit,
    onReplay: () -> Unit,
    onHome: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "winner")
    val glow by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(760), RepeatMode.Reverse),
        label = "glow",
    )
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(Constants.Animation.CONFETTI_CYCLE_MILLIS)),
        label = "confetti",
    )
    // On a shared handset both players are here and one of them has won. Anywhere else the
    // person holding the phone is one of the seats, so the screen has a side to take.
    val lost = didLose(mode, winner, localPlayer)
    ScreenBackground {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // The celebration is for the player who won it. Confetti and a gold trophy over
            // "the move clock ran out" congratulated the loser in the same frame as the
            // sentence telling them they had lost.
            if (!lost) WinnerConfetti(progress)
            WinnerPanel(
                lost = lost,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
                    .padding(Dimens.ScreenPadding),
            ) {
                WinnerMark(
                    lost = lost,
                    // Still, on a defeat: a trophy pulsing above a loss is the artwork arguing
                    // with the words underneath it.
                    pulse = if (lost) 1f else glow,
                )
                WinnerHeadline(
                    title = when {
                        !lost -> stringResource(R.string.winner_victory)
                        mode == GameMode.ONLINE -> stringResource(R.string.winner_rival_won)
                        else -> stringResource(R.string.winner_ai_won)
                    },
                    subtitle = when {
                        endReason == RoomEndReason.TIMEOUT ->
                            stringResource(R.string.winner_turn_clock_ran_out)

                        // Told only to the player who was left. The one who walked out
                        // already knows, and does not need it said back to them.
                        endReason == RoomEndReason.RESIGNATION && !lost ->
                            stringResource(R.string.winner_rival_left)

                        lost -> stringResource(R.string.winner_retry_hint)
                        else -> stringResource(
                            R.string.winner_reached_goal,
                            stringResource(
                                if (winner == PlayerId.PLAYER_ONE) {
                                    R.string.game_player_blue
                                } else {
                                    R.string.game_player_red
                                },
                            ),
                        )
                    },
                    lost = lost,
                )
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                ) {
                    if (canRematch) {
                        RematchControls(rematch, onRematch, onReplay)
                    } else {
                        PremiumActionButton(
                            label = stringResource(R.string.winner_play_again),
                            onClick = onReplay,
                            modifier = Modifier.fillMaxWidth(),
                            filled = true,
                            icon = PremiumIcon.REPLAY,
                        )
                    }
                    // The way out is always the last thing on the card, whatever happened above
                    // it, and never the loudest: leaving is what a player does when the two
                    // offers over it are not what they wanted.
                    PremiumActionButton(
                        label = stringResource(R.string.winner_main_menu),
                        onClick = onHome,
                        modifier = Modifier.fillMaxWidth(),
                        icon = PremiumIcon.HOUSE,
                    )
                }
            }
        }
    }
}

/**
 * The rematch offer and whatever became of it.
 *
 * A rematch is a question put to somebody else, so it can go unanswered — and the screen never
 * makes the player sit through that. Another game stays one tap away from the moment the offer
 * goes out and remains there if the answer turns out to be no, which is also why the offer is
 * not made twice: a rival who has said no has said it.
 *
 * The offer carries the card's only filled control while it is still an offer. Once it has been
 * sent it stops being one, and what is left in its place is a line of text — see
 * [WinnerStatusStrip] for why that line keeps a button's shape.
 */
@Composable
private fun ColumnScope.RematchControls(
    state: RematchUiState,
    onRematch: () -> Unit,
    onReplay: () -> Unit,
) {
    when (state.stage) {
        RematchStage.IDLE -> PremiumActionButton(
            label = stringResource(R.string.winner_rematch),
            onClick = onRematch,
            modifier = Modifier.fillMaxWidth(),
            filled = true,
            busy = state.isBusy,
            icon = PremiumIcon.REPLAY,
        )

        RematchStage.WAITING -> WinnerStatusStrip(
            label = stringResource(R.string.winner_rematch_waiting),
            busy = true,
            modifier = Modifier.fillMaxWidth(),
        )

        RematchStage.DECLINED -> WinnerStatusStrip(
            label = stringResource(R.string.winner_rematch_declined),
            busy = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    state.message?.let { FormMessage(it) }
    PremiumActionButton(
        label = stringResource(R.string.winner_another_game),
        onClick = onReplay,
        modifier = Modifier.fillMaxWidth(),
        icon = PremiumIcon.GAMEPAD,
    )
}

/**
 * Whether the player holding the phone is the one who was beaten.
 *
 * On a shared handset both players are looking at the same screen and one of them has won, so
 * there is no side to take. Everywhere else there is, and it decides the artwork as well as
 * the words: this screen is reached by the winner and the loser alike.
 */
internal fun didLose(mode: GameMode, winner: PlayerId, localPlayer: PlayerId): Boolean =
    mode != GameMode.LOCAL_TWO_PLAYER && winner != localPlayer
