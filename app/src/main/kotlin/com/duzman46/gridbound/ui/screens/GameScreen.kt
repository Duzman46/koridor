package com.duzman46.gridbound.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.asString
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.GameStatus
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.TurnRecord
import com.duzman46.gridbound.game.models.WallOrientation
import com.duzman46.gridbound.presentation.game.GameEvent
import com.duzman46.gridbound.presentation.game.GameUiState
import com.duzman46.gridbound.presentation.game.GameViewModel
import com.duzman46.gridbound.ui.game.GameBoard
import kotlinx.coroutines.delay

@Composable
fun GameRoute(
    onHome: () -> Unit,
    onSettings: () -> Unit,
    onWinner: (PlayerId, GameUiState) -> Unit,
    viewModel: GameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is GameEvent.Feedback && event.hapticsEnabled) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }
    val winner = state.boardState.status.winner
    LaunchedEffect(winner) {
        winner?.let { onWinner(it, state) }
    }
    GameScreen(
        state = state,
        onExit = { viewModel.leaveGame(onHome) },
        onSettings = onSettings,
        onRestart = viewModel::restart,
        onUndo = viewModel::undo,
        onTileTap = viewModel::onTileTapped,
        onWallTap = viewModel::onWallTapped,
        onToggleWall = viewModel::toggleWallMode,
        onOrientation = viewModel::setWallOrientation,
        onConfirmWall = viewModel::confirmPendingWall,
        onCancelWall = viewModel::cancelPendingWall,
        onResign = viewModel::resign,
        onClaimTimeout = viewModel::claimTurnTimeout,
    )
}

/**
 * Ticks once a second while an online match has a move clock, so the countdown and the
 * "claim the win" affordance stay live without redrawing the board on every frame.
 */
@Composable
private fun rememberClockTick(enabled: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(enabled) {
        while (enabled) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    return now
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameScreen(
    state: GameUiState,
    onExit: () -> Unit,
    onSettings: () -> Unit,
    onRestart: () -> Unit,
    onUndo: () -> Unit,
    onTileTap: (com.duzman46.gridbound.game.models.Position) -> Unit,
    onWallTap: (com.duzman46.gridbound.game.models.Wall) -> Unit,
    onToggleWall: () -> Unit,
    onOrientation: (WallOrientation) -> Unit,
    onConfirmWall: () -> Unit,
    onCancelWall: () -> Unit,
    onResign: () -> Unit,
    onClaimTimeout: () -> Unit,
) {
    var showHistory by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showResignConfirmation by remember { mutableStateOf(false) }
    BackHandler(enabled = !showExitConfirmation) { showExitConfirmation = true }

    val clockRunning = state.isOnline && state.turnDeadlineAt != null &&
        state.boardState.status == GameStatus.IN_PROGRESS
    val now = rememberClockTick(clockRunning)

    if (showResignConfirmation) {
        AlertDialog(
            onDismissRequest = { showResignConfirmation = false },
            title = { Text(stringResource(R.string.game_resign_confirm_title)) },
            text = { Text(stringResource(R.string.game_resign_confirm_message)) },
            dismissButton = {
                TextButton(onClick = { showResignConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            confirmButton = {
                Button(onClick = {
                    showResignConfirmation = false
                    onResign()
                }) { Text(stringResource(R.string.game_resign)) }
            },
        )
    }
    if (showHistory) {
        HistoryDialog(state.boardState.history, onDismiss = { showHistory = false })
    }
    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text(stringResource(R.string.game_exit_title)) },
            text = {
                Text(stringResource(R.string.game_exit_message))
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text(stringResource(R.string.game_exit_keep_playing))
                }
            },
            confirmButton = {
                Button(onClick = onExit) { Text(stringResource(R.string.game_exit_confirm)) }
            },
        )
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Koridor", fontWeight = FontWeight.Black)
                        Text(
                            stringResource(R.string.game_turn, state.boardState.turnNumber),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showExitConfirmation = true }) {
                        Icon(Icons.Rounded.Home, contentDescription = stringResource(R.string.game_home))
                    }
                },
                actions = {
                    if (state.mode != GameMode.ONLINE) {
                        IconButton(onClick = onUndo, enabled = state.canUndo && !state.isAiThinking) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.game_undo))
                        }
                        IconButton(onClick = onRestart) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.game_restart))
                        }
                    } else if (state.boardState.status == GameStatus.IN_PROGRESS) {
                        IconButton(onClick = { showResignConfirmation = true }) {
                            Icon(
                                Icons.Rounded.Flag,
                                contentDescription = stringResource(R.string.game_resign),
                            )
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.game_settings))
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val wide = maxWidth >= Constants.Ui.TABLET_BREAKPOINT_DP.dp
            Column(
                modifier = Modifier.fillMaxSize().widthIn(max = Constants.Ui.CONTENT_MAX_WIDTH_DP.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TurnSummary(state, onHistory = { showHistory = true })
                OnlineClockBar(state, now, onClaimTimeout)
                if (wide) {
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GameBoard(
                            state,
                            onTileTap,
                            onWallTap,
                            Modifier.weight(1.25f).fillMaxHeight().widthIn(max = Constants.Ui.BOARD_MAX_SIZE_DP.dp),
                        )
                        CompactGameControls(
                            state,
                            onToggleWall,
                            onOrientation,
                            onConfirmWall,
                            onCancelWall,
                            Modifier.weight(0.75f).widthIn(max = 420.dp),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        GameBoard(
                            state,
                            onTileTap,
                            onWallTap,
                            Modifier.fillMaxHeight().widthIn(max = Constants.Ui.BOARD_MAX_SIZE_DP.dp),
                        )
                    }
                    CompactGameControls(
                        state,
                        onToggleWall,
                        onOrientation,
                        onConfirmWall,
                        onCancelWall,
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Move clock for online matches, plus the button that ends a match whose rival has stopped
 * playing. The claim is re-checked against the server clock, so this is an affordance rather
 * than the decision.
 */
@Composable
private fun OnlineClockBar(state: GameUiState, now: Long, onClaimTimeout: () -> Unit) {
    val deadline = state.turnDeadlineAt
    if (!state.isOnline || deadline == null) return
    if (state.boardState.status != GameStatus.IN_PROGRESS) return

    val remaining = (deadline - now).coerceAtLeast(0L)
    val expired = state.canClaimTimeout(now)
    val yourTurn = state.boardState.currentPlayer == state.localPlayer

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (expired) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(
                    if (yourTurn) R.string.game_your_turn else R.string.game_rival_turn,
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(
                    R.string.game_turn_timer,
                    remaining / 60_000L,
                    (remaining / 1_000L) % 60L,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            if (expired) {
                Button(onClick = onClaimTimeout) {
                    Text(stringResource(R.string.game_claim_win))
                }
            }
        }
    }
}

@Composable
private fun CompactGameControls(
    state: GameUiState,
    onToggleWall: () -> Unit,
    onOrientation: (WallOrientation) -> Unit,
    onConfirmWall: () -> Unit,
    onCancelWall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.onlineMessage?.let { message ->
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp)) {
                Text(message.asString(), Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    state.pendingWall != null -> {
                        Text(
                            stringResource(R.string.game_wall_confirm_hint),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onCancelWall, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Close, contentDescription = null)
                                Text(stringResource(R.string.game_wall_cancel))
                            }
                            Button(onClick = onConfirmWall, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Check, contentDescription = null)
                                Text(stringResource(R.string.game_wall_confirm))
                            }
                        }
                    }

                    state.wallMode -> {
                        Text(
                            stringResource(R.string.game_wall_pick_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WallOrientation.entries.forEach { orientation ->
                                FilterChip(
                                    selected = state.wallOrientation == orientation,
                                    onClick = { onOrientation(orientation) },
                                    label = {
                                        Text(
                                            if (orientation == WallOrientation.HORIZONTAL) {
                                                stringResource(R.string.game_wall_horizontal)
                                            } else {
                                                stringResource(R.string.game_wall_vertical)
                                            },
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null) },
                                )
                            }
                            OutlinedButton(onClick = onToggleWall, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.game_history_pawn_label))
                            }
                        }
                    }

                    else -> {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.game_move_hint),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = onToggleWall, enabled = state.acceptsHumanInput) {
                                Text(stringResource(R.string.game_place_wall))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TurnSummary(state: GameUiState, onHistory: () -> Unit) {
    val current = state.boardState.currentPlayer
    val turnTitle = when {
        state.mode == GameMode.ONLINE && state.isOnlineSyncing -> stringResource(R.string.game_sending_move)
        state.mode == GameMode.ONLINE && !state.isOnlineConnected -> stringResource(R.string.game_connecting)
        state.mode == GameMode.ONLINE && current == state.localPlayer ->
            stringResource(R.string.game_turn_yours, playerName(current))
        state.mode == GameMode.ONLINE -> stringResource(R.string.game_turn_opponent)
        state.isAiThinking -> stringResource(R.string.game_ai_thinking)
        current == PlayerId.PLAYER_ONE -> stringResource(R.string.game_turn_blue)
        else -> stringResource(R.string.game_turn_orange)
    }
    val activeColor = if (current == PlayerId.PLAYER_ONE) Color(0xFF3F82FF) else Color(0xFFFF8A34)
    val shouldPulse = state.acceptsHumanInput || state.mode == GameMode.LOCAL_TWO_PLAYER
    val transition = rememberInfiniteTransition(label = "turnBeacon")
    val beaconAlpha by transition.animateFloat(
        initialValue = if (shouldPulse) 0.42f else 0.72f,
        targetValue = if (shouldPulse) 1f else 0.72f,
        animationSpec = infiniteRepeatable(tween(720), RepeatMode.Reverse),
        label = "turnBeaconAlpha",
    )
    Surface(
        modifier = Modifier.border(2.dp, activeColor.copy(alpha = beaconAlpha), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 3.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .alpha(beaconAlpha)
                                .background(activeColor, RoundedCornerShape(50)),
                        )
                        Text(turnTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        when (state.mode) {
                            GameMode.VS_AI ->
                                stringResource(R.string.game_vs_ai, state.difficulty.label())
                            GameMode.LOCAL_TWO_PLAYER -> stringResource(R.string.game_local_two_player)
                            GameMode.ONLINE -> stringResource(R.string.game_online_match)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.isAiThinking || state.isOnlineSyncing || (state.mode == GameMode.ONLINE && !state.isOnlineConnected)) {
                    CircularProgressIndicator()
                }
                IconButton(onClick = onHistory) {
                    Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.game_history))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PlayerWalls(playerName(PlayerId.PLAYER_ONE), state.boardState.player(PlayerId.PLAYER_ONE).wallsRemaining)
                PlayerWalls(playerName(PlayerId.PLAYER_TWO), state.boardState.player(PlayerId.PLAYER_TWO).wallsRemaining)
            }
        }
    }
}

@Composable
private fun playerName(player: PlayerId): String = when (player) {
    PlayerId.PLAYER_ONE -> stringResource(R.string.game_player_blue)
    PlayerId.PLAYER_TWO -> stringResource(R.string.game_player_orange)
}

@Composable
private fun PlayerWalls(name: String, count: Int) {
    Text(
        pluralStringResource(R.plurals.game_player_walls, count, name, count),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun HistoryDialog(history: List<TurnRecord>, onDismiss: () -> Unit) {
    val recent = history.takeLast(12).reversed()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_history)) },
        text = {
            if (recent.isEmpty()) {
                Text(stringResource(R.string.game_history_empty))
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recent) { record ->
                        Text(
                            "${record.turnNumber}. ${playerName(record.player)} · ${record.action.label()}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun GameAction.label(): String = when (this) {
    is GameAction.MovePawn ->
        stringResource(R.string.game_history_pawn, target.row + 1, target.column + 1)
    is GameAction.PlaceWall -> if (wall.orientation == WallOrientation.HORIZONTAL) {
        stringResource(R.string.game_wall_horizontal_full)
    } else {
        stringResource(R.string.game_wall_vertical_full)
    }
}

@Composable
private fun Difficulty.label(): String = when (this) {
    Difficulty.EASY -> stringResource(R.string.difficulty_easy)
    Difficulty.MEDIUM -> stringResource(R.string.difficulty_medium)
    Difficulty.HARD -> stringResource(R.string.difficulty_hard)
}
