package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Undo
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.core.Constants
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
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Koridor", fontWeight = FontWeight.Black)
                        Text("Tur ${state.boardState.turnNumber}", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onHome) { Icon(Icons.Rounded.Home, contentDescription = "Ana menü") }
                },
                actions = {
                    IconButton(
                        onClick = onUndo,
                        enabled = state.mode != GameMode.ONLINE && state.canUndo && !state.isAiThinking,
                    ) {
                        Icon(Icons.Rounded.Undo, contentDescription = "Geri al")
                    }
                    IconButton(onClick = onRestart, enabled = state.mode != GameMode.ONLINE) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Yeniden başlat")
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Ayarlar") }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val wide = maxWidth >= Constants.Ui.TABLET_BREAKPOINT_DP.dp
            if (wide) {
                Row(
                    Modifier.fillMaxWidth().widthIn(max = Constants.Ui.CONTENT_MAX_WIDTH_DP.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GameBoard(
                        state,
                        onTileTap,
                        onWallTap,
                        Modifier.weight(1.25f).widthIn(max = Constants.Ui.BOARD_MAX_SIZE_DP.dp),
                    )
                    GameControlPanel(
                        state,
                        onToggleWall,
                        onOrientation,
                        onConfirmWall,
                        onCancelWall,
                        Modifier.weight(0.75f).widthIn(max = 420.dp),
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TurnSummary(state)
                    GameBoard(state, onTileTap, onWallTap, Modifier.widthIn(max = Constants.Ui.BOARD_MAX_SIZE_DP.dp))
                    GameControlPanel(
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
private fun GameControlPanel(
    state: GameUiState,
    onToggleWall: () -> Unit,
    onOrientation: (WallOrientation) -> Unit,
    onConfirmWall: () -> Unit,
    onCancelWall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TurnSummary(state)
        state.onlineMessage?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(message, Modifier.fillMaxWidth().padding(12.dp), fontWeight = FontWeight.SemiBold)
            }
        }
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 3.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Hamle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        state.pendingWall != null -> "Seçilen duvar tahtada parlıyor. Konumu kontrol edip onayla."
                        state.wallMode -> "Tahtada istediğin bölgeye dokun; en yakın duvar yuvası seçilir."
                        else -> "Piyonuna dokun ve yeşil kareyi seç."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.wallMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WallOrientation.entries.forEach { orientation ->
                            FilterChip(
                                selected = state.wallOrientation == orientation,
                                onClick = { onOrientation(orientation) },
                                label = { Text(if (orientation == WallOrientation.HORIZONTAL) "Yatay" else "Dikey") },
                                leadingIcon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null) },
                            )
                        }
                    }
                }
                if (state.wallMode) {
                    if (state.pendingWall != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("Bu duvarı yerleştirmek istiyor musun?", fontWeight = FontWeight.Bold)
                                Text(
                                    if (state.pendingWall.orientation == WallOrientation.HORIZONTAL) {
                                        "Yatay duvar"
                                    } else {
                                        "Dikey duvar"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(onClick = onCancelWall, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Rounded.Close, contentDescription = null)
                                        Text("İptal")
                                    }
                                    Button(onClick = onConfirmWall, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Rounded.Check, contentDescription = null)
                                        Text("Onayla")
                                    }
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = onToggleWall, modifier = Modifier.fillMaxWidth()) {
                        Text("Piyon Moduna Dön")
                    }
                } else {
                    OutlinedButton(
                        onClick = onToggleWall,
                        enabled = state.acceptsHumanInput,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Duvar Yerleştir")
                    }
                }
            }
        }
        HistoryCard(state.boardState.history.takeLast(6).reversed())
    }
}

@Composable
private fun TurnSummary(state: GameUiState) {
    val current = state.boardState.currentPlayer
    val turnTitle = when {
        state.mode == GameMode.ONLINE && state.isOnlineSyncing -> "Hamle gönderiliyor…"
        state.mode == GameMode.ONLINE && !state.isOnlineConnected -> "Odaya bağlanılıyor…"
        state.mode == GameMode.ONLINE && current == state.localPlayer ->
            "Senin sıran · ${if (current == PlayerId.PLAYER_ONE) "Mavi" else "Turuncu"}"
        state.mode == GameMode.ONLINE -> "Rakibin sırası"
        state.isAiThinking -> "AI düşünüyor…"
        current == PlayerId.PLAYER_ONE -> "Mavi oyuncunun sırası"
        else -> "Turuncu oyuncunun sırası"
    }
    Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        turnTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when (state.mode) {
                            GameMode.VS_AI -> "${state.difficulty.label()} yapay zekâ"
                            GameMode.LOCAL_TWO_PLAYER -> "Aynı cihazda iki oyuncu"
                            GameMode.ONLINE -> "Çevrimiçi oda · anlık eşzamanlama"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.isAiThinking || state.isOnlineSyncing || (state.mode == GameMode.ONLINE && !state.isOnlineConnected)) {
                    CircularProgressIndicator()
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PlayerWalls("Mavi", state.boardState.player(PlayerId.PLAYER_ONE).wallsRemaining)
                PlayerWalls("Turuncu", state.boardState.player(PlayerId.PLAYER_TWO).wallsRemaining)
            }
        }
    }
}

@Composable
private fun PlayerWalls(name: String, count: Int) {
    Column {
        Text(name, style = MaterialTheme.typography.labelMedium)
        Text("$count duvar", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HistoryCard(history: List<TurnRecord>) {
    Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Hamle geçmişi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (history.isEmpty()) {
                Text("Henüz hamle yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                history.forEach { record ->
                    Text(
                        "${record.turnNumber}. ${if (record.player == PlayerId.PLAYER_ONE) "Mavi" else "Turuncu"} · ${record.action.label()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun GameAction.label(): String = when (this) {
    is GameAction.MovePawn -> "Piyon ${target.row + 1},${target.column + 1}"
    is GameAction.PlaceWall -> if (wall.orientation == WallOrientation.HORIZONTAL) "Yatay duvar" else "Dikey duvar"
}

private fun com.duzman46.gridbound.game.models.Difficulty.label(): String = when (this) {
    com.duzman46.gridbound.game.models.Difficulty.EASY -> "Kolay"
    com.duzman46.gridbound.game.models.Difficulty.MEDIUM -> "Orta"
    com.duzman46.gridbound.game.models.Difficulty.HARD -> "Zor"
}
