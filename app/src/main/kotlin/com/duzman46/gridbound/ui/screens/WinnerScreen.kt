package com.duzman46.gridbound.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.duzman46.gridbound.ui.components.FormMessage
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.components.SubmitButton

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
    val lost = mode != GameMode.LOCAL_TWO_PLAYER && winner != localPlayer
    ScreenBackground {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Confetti(progress)
            Card(
                Modifier.fillMaxWidth().widthIn(max = 500.dp).padding(20.dp),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(
                    Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.size(92.dp).scale(glow),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        when {
                            !lost -> stringResource(R.string.winner_victory)
                            mode == GameMode.ONLINE -> stringResource(R.string.winner_rival_won)
                            else -> stringResource(R.string.winner_ai_won)
                        },
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        when {
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
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (canRematch) {
                        RematchControls(rematch, onRematch, onReplay)
                    } else {
                        Button(onClick = onReplay, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Replay, contentDescription = null)
                            Text(stringResource(R.string.winner_play_again), Modifier.padding(start = 8.dp))
                        }
                    }
                    OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Home, contentDescription = null)
                        Text(stringResource(R.string.winner_main_menu), Modifier.padding(start = 8.dp))
                    }
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
 */
@Composable
private fun ColumnScope.RematchControls(
    state: RematchUiState,
    onRematch: () -> Unit,
    onReplay: () -> Unit,
) {
    when (state.stage) {
        RematchStage.IDLE -> SubmitButton(
            text = stringResource(R.string.winner_rematch),
            onClick = onRematch,
            isSubmitting = state.isBusy,
            leadingIcon = Icons.Rounded.Replay,
        )

        RematchStage.WAITING -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                Modifier.size(18.dp).clearAndSetSemantics { },
                strokeWidth = 2.dp,
            )
            Text(
                stringResource(R.string.winner_rematch_waiting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        RematchStage.DECLINED -> Text(
            stringResource(R.string.winner_rematch_declined),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    state.message?.let { FormMessage(it) }
    OutlinedButton(onClick = onReplay, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.SportsEsports, contentDescription = null)
        Text(stringResource(R.string.winner_another_game), Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Confetti(progress: Float) {
    val colors = listOf(
        Color(0xFF32D583),
        Color(0xFFFFC857),
        Color(0xFF4D8DFF),
        Color(0xFFFF6B6B),
        Color(0xFFB56CFF),
    )
    Canvas(Modifier.fillMaxSize()) {
        repeat(Constants.Animation.CONFETTI_PARTICLE_COUNT) { index ->
            val xFraction = ((index * 37) % 101) / 100f
            val phase = ((index * 19) % Constants.Animation.CONFETTI_PARTICLE_COUNT).toFloat() /
                Constants.Animation.CONFETTI_PARTICLE_COUNT
            val yFraction = (progress + phase) % 1f
            val drift = kotlin.math.sin((progress + phase) * Math.PI * 2).toFloat() * 18f
            drawCircle(
                color = colors[index % colors.size].copy(alpha = 0.78f),
                radius = 3f + (index % 4),
                center = androidx.compose.ui.geometry.Offset(xFraction * size.width + drift, yFraction * size.height),
            )
        }
    }
}
