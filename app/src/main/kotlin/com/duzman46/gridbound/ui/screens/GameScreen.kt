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
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Gridbound", fontWeight = FontWeight.Black)
                        Text("Tur ${state.boardState.turnNumber}", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onHome) { Icon(Icons.Rounded.Home, contentDescription = "Ana menü") }
                },
                actions = {
                    IconButton(onClick = onUndo, enabled = state.canUndo && !state.isAiThinking) {
                        Icon(Icons.Rounded.Undo, contentDescription = "Geri al")
                    }
                    IconButton(onClick = onRestart) { Icon(Icons.Rounded.Refresh, contentDescription = "Yeniden başlat") }
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
                    GameControlPanel(state, onToggleWall, onOrientation, Modifier.fillMaxWidth())
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
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TurnSummary(state)
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 3.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Hamle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (state.wallMode) "Tahtada duvar konumuna dokun." else "Piyonuna dokun ve yeşil kareyi seç.",
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
                    Button(onClick = onToggleWall, modifier = Modifier.fillMaxWidth()) { Text("Piyon Moduna Dön") }
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
    Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        if (state.isAiThinking) "AI düşünüyor…" else if (current == PlayerId.PLAYER_ONE) "Mavi oyuncunun sırası" else "Turuncu oyuncunun sırası",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (state.mode == com.duzman46.gridbound.game.models.GameMode.VS_AI) "${state.difficulty.label()} yapay zekâ" else "Aynı cihazda iki oyuncu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.isAiThinking) CircularProgressIndicator()
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
