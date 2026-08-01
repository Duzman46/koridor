package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.presentation.online.OnlineLobbyEvent
import com.duzman46.gridbound.presentation.online.OnlineLobbyUiState
import com.duzman46.gridbound.presentation.online.OnlineLobbyViewModel
import com.duzman46.gridbound.ui.components.CenteredContent
import com.duzman46.gridbound.ui.components.GradientBackground
import com.duzman46.gridbound.ui.components.ScreenTopBar
import com.duzman46.gridbound.ui.localization.localized
import com.duzman46.gridbound.ui.localization.localized as localizedMessage

@Composable
fun OnlineLobbyRoute(
    onBack: () -> Unit,
    onOpenGame: (OnlineSession) -> Unit,
    viewModel: OnlineLobbyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is OnlineLobbyEvent.OpenGame) onOpenGame(event.session)
        }
    }
    OnlineLobbyScreen(
        state = state,
        onBack = onBack,
        onRoomCode = viewModel::setRoomCode,
        onCreate = viewModel::createRoom,
        onJoin = viewModel::joinRoom,
        onCancelWaiting = viewModel::cancelWaiting,
    )
}

@Composable
private fun OnlineLobbyScreen(
    state: OnlineLobbyUiState,
    onBack: () -> Unit,
    onRoomCode: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onCancelWaiting: () -> Unit,
) {
    Scaffold(topBar = { ScreenTopBar(localized("Çevrimiçi Oyun", "Online Game"), onBack) }) { padding ->
        GradientBackground {
            CenteredContent(Modifier.padding(padding)) {
                Column(
                    Modifier.fillMaxWidth().widthIn(max = 560.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(localized("İnternetten rakibinle oyna", "Play your opponent online"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    when {
                        !state.isConfigured -> ConfigurationNotice()
                        state.waitingSession != null -> WaitingRoom(state.waitingSession.roomCode, onCancelWaiting)
                        else -> LobbyActions(state, onRoomCode, onCreate, onJoin)
                    }
                    state.errorMessage?.let {
                        Text(it.localizedMessage(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    }
                    if (state.isLoading) CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun LobbyActions(
    state: OnlineLobbyUiState,
    onRoomCode: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(localized("Yeni oda", "New room"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(localized("Bir oda oluştur, kodu arkadaşına gönder ve onun katılmasını bekle.", "Create a room, send the code to your friend, and wait for them to join."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onCreate, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.Icon(Icons.Rounded.AddCircle, contentDescription = null)
                Text(localized("Oda Oluştur", "Create Room"))
            }
        }
    }
    Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(localized("Odaya katıl", "Join a room"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = state.roomCodeInput,
                onValueChange = onRoomCode,
                label = { Text(localized("6 karakterli oda kodu", "6-character room code")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onJoin,
                enabled = !state.isLoading && state.roomCodeInput.length == 6,
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.material3.Icon(Icons.Rounded.Groups, contentDescription = null)
                Text(localized("Odaya Katıl", "Join Room"))
            }
        }
    }
}

@Composable
private fun WaitingRoom(roomCode: String, onCancel: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 4.dp) {
        Column(
            Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator()
            Text(localized("Rakip bekleniyor", "Waiting for opponent"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(localized("Bu kodu arkadaşına gönder:", "Send this code to your friend:"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(roomCode, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
            }
            Text(localized("Kodun üzerine basılı tutarak kopyalayabilirsin.", "Touch and hold the code to copy it."), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(localized("Odayı Kapat", "Close Room")) }
        }
    }
}

@Composable
private fun ConfigurationNotice() {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(localized("Çevrimiçi servis hazırlanıyor", "Online service is being prepared"), fontWeight = FontWeight.Bold)
            Text(localized("Firebase bağlantısı tamamlandığında oda oluşturma ve katılma burada açılacak.", "Room creation and joining will appear here when the Firebase connection is ready."))
        }
    }
}
