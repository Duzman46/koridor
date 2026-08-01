package com.duzman46.gridbound.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.TurnRecord
import com.duzman46.gridbound.game.models.WallOrientation
import com.duzman46.gridbound.presentation.game.GameEvent
import com.duzman46.gridbound.presentation.game.GameUiState
import com.duzman46.gridbound.presentation.game.GameViewModel
import com.duzman46.gridbound.ui.game.GameBoard
import com.duzman46.gridbound.ui.localization.localized
import com.duzman46.gridbound.ui.localization.localized as localizedMessage

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
        onHome = onHome,
        onSettings = onSettings,
        onRestart = viewModel::restart,
        onUndo = viewModel::undo,
        onTileTap = viewModel::onTileTapped,
        onWallTap = viewModel::onWallTapped,
        onToggleWall = viewModel::toggleWallMode,
        onOrientation = viewModel::setWallOrientation,
        onConfirmWall = viewModel::confirmPendingWall,
        onCancelWall = viewModel::cancelPendingWall,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameScreen(
    state: GameUiState,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    onRestart: () -> Unit,
    onUndo: () -> Unit,
    onTileTap: (com.duzman46.gridbound.game.models.Position) -> Unit,
    onWallTap: (com.duzman46.gridbound.game.models.Wall) -> Unit,
    onToggleWall: () -> Unit,
    onOrientation: (WallOrientation) -> Unit,
    onConfirmWall: () -> Unit,
    onCancelWall: () -> Unit,
) {
    var showHistory by remember { mutableStateOf(false) }
    if (showHistory) {
        HistoryDialog(state.boardState.history, onDismiss = { showHistory = false })
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Koridor", fontWeight = FontWeight.Black)
                        Text(
                            localized("Tur ${state.boardState.turnNumber}", "Turn ${state.boardState.turnNumber}"),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onHome) {
                        Icon(Icons.Rounded.Home, contentDescription = localized("Ana menü", "Home"))
                    }
                },
                actions = {
                    if (state.mode != GameMode.ONLINE) {
                        IconButton(onClick = onUndo, enabled = state.canUndo && !state.isAiThinking) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = localized("Geri al", "Undo"))
                        }
                        IconButton(onClick = onRestart) {
                            Icon(Icons.Rounded.Refresh, contentDescription = localized("Yeniden başlat", "Restart"))
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = localized("Ayarlar", "Settings"))
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
                Text(message.localizedMessage(), Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    state.pendingWall != null -> {
                        Text(
                            localized("Duvar konumunu kontrol edip onayla.", "Check the wall position and confirm."),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onCancelWall, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Close, contentDescription = null)
                                Text(localized("İptal", "Cancel"))
                            }
                            Button(onClick = onConfirmWall, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Check, contentDescription = null)
                                Text(localized("Onayla", "Confirm"))
                            }
                        }
                    }

                    state.wallMode -> {
                        Text(
                            localized("Tahtaya dokunarak en yakın duvar yuvasını seç.", "Tap the board to select the nearest wall slot."),
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
                                                localized("Yatay", "Horizontal")
                                            } else {
                                                localized("Dikey", "Vertical")
                                            },
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null) },
                                )
                            }
                            OutlinedButton(onClick = onToggleWall, modifier = Modifier.weight(1f)) {
                                Text(localized("Piyon", "Pawn"))
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
                                localized("Piyonu seçip yeşil kareye dokun.", "Select the pawn, then tap a green tile."),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = onToggleWall, enabled = state.acceptsHumanInput) {
                                Text(localized("Duvar Yerleştir", "Place Wall"))
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
        state.mode == GameMode.ONLINE && state.isOnlineSyncing -> localized("Hamle gönderiliyor…", "Sending move…")
        state.mode == GameMode.ONLINE && !state.isOnlineConnected -> localized("Odaya bağlanılıyor…", "Connecting to room…")
        state.mode == GameMode.ONLINE && current == state.localPlayer -> localized(
            "Senin sıran · ${playerName(current)}",
            "Your turn · ${playerName(current)}",
        )
        state.mode == GameMode.ONLINE -> localized("Rakibin sırası", "Opponent's turn")
        state.isAiThinking -> localized("Yapay zekâ düşünüyor…", "AI is thinking…")
        current == PlayerId.PLAYER_ONE -> localized("Mavi oyuncunun sırası", "Blue player's turn")
        else -> localized("Turuncu oyuncunun sırası", "Orange player's turn")
    }
    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(turnTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        when (state.mode) {
                            GameMode.VS_AI -> localized("${state.difficulty.label()} yapay zekâ", "${state.difficulty.label()} AI")
                            GameMode.LOCAL_TWO_PLAYER -> localized("Aynı cihazda iki oyuncu", "Two players on one device")
                            GameMode.ONLINE -> localized("Çevrimiçi oyun", "Online game")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.isAiThinking || state.isOnlineSyncing || (state.mode == GameMode.ONLINE && !state.isOnlineConnected)) {
                    CircularProgressIndicator()
                }
                IconButton(onClick = onHistory) {
                    Icon(Icons.Rounded.Info, contentDescription = localized("Hamle geçmişi", "Move history"))
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
    PlayerId.PLAYER_ONE -> localized("Mavi", "Blue")
    PlayerId.PLAYER_TWO -> localized("Turuncu", "Orange")
}

@Composable
private fun PlayerWalls(name: String, count: Int) {
    Text(
        localized("$name · $count duvar", "$name · $count walls"),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun HistoryDialog(history: List<TurnRecord>, onDismiss: () -> Unit) {
    val recent = history.takeLast(12).reversed()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localized("Hamle geçmişi", "Move history")) },
        text = {
            if (recent.isEmpty()) {
                Text(localized("Henüz hamle yok.", "No moves yet."))
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
            TextButton(onClick = onDismiss) { Text(localized("Kapat", "Close")) }
        },
    )
}

@Composable
private fun GameAction.label(): String = when (this) {
    is GameAction.MovePawn -> localized("Piyon ${target.row + 1},${target.column + 1}", "Pawn ${target.row + 1},${target.column + 1}")
    is GameAction.PlaceWall -> if (wall.orientation == WallOrientation.HORIZONTAL) {
        localized("Yatay duvar", "Horizontal wall")
    } else {
        localized("Dikey duvar", "Vertical wall")
    }
}

@Composable
private fun Difficulty.label(): String = when (this) {
    Difficulty.EASY -> localized("Kolay", "Easy")
    Difficulty.MEDIUM -> localized("Orta", "Medium")
    Difficulty.HARD -> localized("Zor", "Hard")
}
