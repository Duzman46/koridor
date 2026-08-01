package com.duzman46.gridbound.presentation.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.domain.models.LocalizedText
import com.duzman46.gridbound.online.domain.OnlineGameRepository
import com.duzman46.gridbound.online.model.OnlineLobbyResult
import com.duzman46.gridbound.online.model.OnlineRoomStatus
import com.duzman46.gridbound.online.model.OnlineSession
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnlineLobbyUiState(
    val isConfigured: Boolean = false,
    val roomCodeInput: String = "",
    val isLoading: Boolean = false,
    val waitingSession: OnlineSession? = null,
    val errorMessage: LocalizedText? = null,
)

sealed interface OnlineLobbyEvent {
    data class OpenGame(val session: OnlineSession) : OnlineLobbyEvent
}

@HiltViewModel
class OnlineLobbyViewModel @Inject constructor(
    private val repository: OnlineGameRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnlineLobbyUiState(isConfigured = repository.isConfigured))
    val uiState: StateFlow<OnlineLobbyUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OnlineLobbyEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<OnlineLobbyEvent> = _events.asSharedFlow()
    private var roomJob: Job? = null

    fun setRoomCode(value: String) {
        val normalized = value.uppercase(Locale.ROOT)
            .filter(Constants.Online.ROOM_CODE_ALPHABET::contains)
            .take(Constants.Online.ROOM_CODE_LENGTH)
        _uiState.update { it.copy(roomCodeInput = normalized, errorMessage = null) }
    }

    fun createRoom() = executeLobbyAction(repository::createRoom, waitForOpponent = true)

    fun joinRoom() {
        val roomCode = _uiState.value.roomCodeInput
        if (roomCode.length != Constants.Online.ROOM_CODE_LENGTH) {
            _uiState.update { it.copy(errorMessage = LocalizedText("6 karakterli oda kodunu gir.", "Enter the 6-character room code.")) }
            return
        }
        executeLobbyAction({ repository.joinRoom(roomCode) }, waitForOpponent = false)
    }

    fun cancelWaiting() {
        val session = _uiState.value.waitingSession ?: return
        roomJob?.cancel()
        viewModelScope.launch { repository.leaveRoom(session) }
        _uiState.update { it.copy(waitingSession = null, isLoading = false, errorMessage = null) }
    }

    private fun executeLobbyAction(
        action: suspend () -> OnlineLobbyResult,
        waitForOpponent: Boolean,
    ) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = action()) {
                is OnlineLobbyResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }

                is OnlineLobbyResult.Success -> {
                    if (waitForOpponent) observeWaitingRoom(result.session) else {
                        _uiState.update { it.copy(isLoading = false) }
                        _events.emit(OnlineLobbyEvent.OpenGame(result.session))
                    }
                }
            }
        }
    }

    private fun observeWaitingRoom(session: OnlineSession) {
        roomJob?.cancel()
        _uiState.update { it.copy(isLoading = false, waitingSession = session) }
        roomJob = viewModelScope.launch {
            repository.observeRoom(session.roomCode)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            waitingSession = null,
                            errorMessage = error.localizedMessage?.let { LocalizedText(it, it) }
                                ?: LocalizedText("Oda bağlantısı kesildi.", "The room connection was lost."),
                        )
                    }
                }
                .collect { room ->
                    when (room.status) {
                        OnlineRoomStatus.ACTIVE -> {
                            _events.emit(OnlineLobbyEvent.OpenGame(session))
                            roomJob?.cancel()
                        }
                        OnlineRoomStatus.ABANDONED -> _uiState.update {
                            it.copy(waitingSession = null, errorMessage = LocalizedText("Oda kapatıldı.", "The room was closed."))
                        }
                        else -> Unit
                    }
                }
        }
    }
}
