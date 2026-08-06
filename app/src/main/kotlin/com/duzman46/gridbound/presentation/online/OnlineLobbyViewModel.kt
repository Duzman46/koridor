package com.duzman46.gridbound.presentation.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.online.data.RoomCredentials
import com.duzman46.gridbound.online.domain.OnlineGameRepository
import com.duzman46.gridbound.online.model.OnlineLobbyResult
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineRoomStatus
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.online.model.RoomBrowserFilter
import com.duzman46.gridbound.online.model.RoomConfiguration
import com.duzman46.gridbound.online.model.RoomTiming
import com.duzman46.gridbound.online.model.RoomVisibility
import com.duzman46.gridbound.session.SessionManager
import com.duzman46.gridbound.social.domain.Friend
import com.duzman46.gridbound.social.domain.FriendshipStatus
import com.duzman46.gridbound.social.domain.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val canPlayRanked: Boolean = false,
    val roomCodeInput: String = "",
    val joinPassword: String = "",
    val configuration: RoomConfiguration = RoomConfiguration(),
    val isBusy: Boolean = false,
    val isSearching: Boolean = false,
    val waitingSession: OnlineSession? = null,
    val resumableSession: OnlineSession? = null,
    val openRooms: List<OnlineRoom> = emptyList(),
    val isLoadingRooms: Boolean = false,
    val filter: RoomBrowserFilter = RoomBrowserFilter(),
    val passwordPromptRoom: OnlineRoom? = null,
    /** Friends who can be invited into the room currently being hosted. */
    val invitableFriends: List<Friend> = emptyList(),
    val invitedUserIds: Set<String> = emptySet(),
    val message: UiText? = null,
) {
    val canJoinByCode: Boolean
        get() = !isBusy && RoomCredentials.isValidCode(roomCodeInput)

    val visibleRooms: List<OnlineRoom>
        get() = openRooms.filter { filter.matches(it, friendIds = emptySet()) }
}

sealed interface OnlineLobbyEvent {
    data class OpenGame(val session: OnlineSession) : OnlineLobbyEvent
}

/**
 * Drives room creation, the room browser, quick match and reconnection.
 *
 * Every entry point funnels through [enterRoom], so the busy flag that stops a double tap
 * from opening two rooms is applied in exactly one place.
 */
@HiltViewModel
class OnlineLobbyViewModel @Inject constructor(
    private val repository: OnlineGameRepository,
    private val socialRepository: SocialRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnlineLobbyUiState(
            isConfigured = repository.isConfigured,
            canPlayRanked = sessionManager.state.value.canUseSocialFeatures,
            configuration = RoomConfiguration(
                ranked = sessionManager.state.value.canUseSocialFeatures,
            ),
        ),
    )
    val uiState: StateFlow<OnlineLobbyUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OnlineLobbyEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<OnlineLobbyEvent> = _events.asSharedFlow()

    private var waitingJob: Job? = null

    init {
        refreshResumableSession()
        refreshOpenRooms()
        observeFriends()
    }

    /**
     * Sends a friend the code of the room being hosted. Only offered while waiting, because
     * that is the only moment there is a room worth joining.
     */
    fun inviteFriend(userId: String) {
        val session = _uiState.value.waitingSession ?: return
        val ownId = sessionManager.state.value.user?.userId ?: return
        if (userId in _uiState.value.invitedUserIds) return
        _uiState.update { it.copy(invitedUserIds = it.invitedUserIds + userId) }
        viewModelScope.launch {
            val result = socialRepository.sendInvite(ownId, userId, session.roomCode)
            if (result is Outcome.Failure) {
                _uiState.update {
                    it.copy(
                        invitedUserIds = it.invitedUserIds - userId,
                        message = result.error.message,
                    )
                }
            }
        }
    }

    private fun observeFriends() {
        val userId = sessionManager.state.value.user
            ?.userId
            ?.takeIf { sessionManager.state.value.canUseSocialFeatures }
            ?: return
        viewModelScope.launch {
            socialRepository.observeFriendships(userId).collect { friends ->
                _uiState.update { state ->
                    state.copy(
                        invitableFriends = friends.filter { it.status == FriendshipStatus.FRIENDS },
                    )
                }
            }
        }
    }

    fun setRoomCode(value: String) = _uiState.update {
        it.copy(roomCodeInput = RoomCredentials.normalizeCode(value), message = null)
    }

    fun setJoinPassword(value: String) = _uiState.update { it.copy(joinPassword = value) }

    fun setRoomName(value: String) = updateConfiguration { it.copy(roomName = value) }

    fun setVisibility(value: RoomVisibility) = updateConfiguration { it.copy(visibility = value) }

    fun setRanked(value: Boolean) = updateConfiguration { it.copy(ranked = value) }

    fun setTurnDuration(seconds: Int) =
        updateConfiguration { it.copy(timing = it.timing.copy(turnDurationSeconds = seconds)) }

    fun setTotalDuration(seconds: Int) =
        updateConfiguration { it.copy(timing = it.timing.copy(totalDurationSeconds = seconds)) }

    fun setRoomPassword(value: String) = updateConfiguration { it.copy(password = value) }

    fun setFilter(filter: RoomBrowserFilter) = _uiState.update { it.copy(filter = filter) }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    fun dismissPasswordPrompt() =
        _uiState.update { it.copy(passwordPromptRoom = null, joinPassword = "") }

    fun refreshOpenRooms() {
        _uiState.update { it.copy(isLoadingRooms = true) }
        viewModelScope.launch {
            when (val result = repository.loadOpenRooms()) {
                is Outcome.Success -> _uiState.update {
                    it.copy(openRooms = result.value, isLoadingRooms = false)
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(isLoadingRooms = false, message = result.error.message)
                }
            }
        }
    }

    fun createRoom() {
        val configuration = _uiState.value.configuration
        // Ranked play needs a durable identity, otherwise a rating could not follow anyone.
        if (configuration.ranked && !_uiState.value.canPlayRanked) {
            _uiState.update { it.copy(message = AppError.RANKED_REQUIRES_ACCOUNT.message) }
            return
        }
        enterRoom(waitForOpponent = true) { repository.createRoom(configuration) }
    }

    fun joinByCode() {
        val state = _uiState.value
        if (!RoomCredentials.isValidCode(state.roomCodeInput)) {
            _uiState.update { it.copy(message = AppError.ROOM_CODE_INVALID.message) }
            return
        }
        enterRoom { repository.joinRoom(state.roomCodeInput, state.joinPassword) }
    }

    /** A protected room asks for its password first rather than failing the tap. */
    fun joinListedRoom(room: OnlineRoom) {
        if (room.requiresPassword) {
            _uiState.update { it.copy(passwordPromptRoom = room, joinPassword = "") }
            return
        }
        enterRoom { repository.joinRoom(room.roomCode) }
    }

    fun confirmPasswordPrompt() {
        val room = _uiState.value.passwordPromptRoom ?: return
        val password = _uiState.value.joinPassword
        _uiState.update { it.copy(passwordPromptRoom = null) }
        enterRoom { repository.joinRoom(room.roomCode, password) }
    }

    fun quickMatch() {
        _uiState.update { it.copy(isSearching = true) }
        enterRoom(
            onFinally = { _uiState.update { it.copy(isSearching = false) } },
        ) { repository.quickMatch(preferRanked = _uiState.value.canPlayRanked) }
    }

    fun resumeMatch() {
        val session = _uiState.value.resumableSession ?: return
        viewModelScope.launch { _events.emit(OnlineLobbyEvent.OpenGame(session)) }
    }

    fun cancelWaiting() {
        val session = _uiState.value.waitingSession ?: return
        waitingJob?.cancel()
        viewModelScope.launch { repository.leaveRoom(session) }
        _uiState.update { it.copy(waitingSession = null, isBusy = false) }
    }

    private fun refreshResumableSession() {
        val userId = sessionManager.state.value.user?.userId ?: return
        viewModelScope.launch {
            val result = repository.findResumableSession(userId)
            if (result is Outcome.Success) {
                _uiState.update { it.copy(resumableSession = result.value) }
            }
        }
    }

    private fun enterRoom(
        waitForOpponent: Boolean = false,
        onFinally: () -> Unit = {},
        action: suspend () -> OnlineLobbyResult,
    ) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            when (val result = action()) {
                is OnlineLobbyResult.Failure -> {
                    _uiState.update { it.copy(isBusy = false, message = result.error.message) }
                    onFinally()
                }

                is OnlineLobbyResult.Success -> {
                    onFinally()
                    if (waitForOpponent) {
                        awaitOpponent(result.session)
                    } else {
                        _uiState.update { it.copy(isBusy = false) }
                        _events.emit(OnlineLobbyEvent.OpenGame(result.session))
                    }
                }
            }
        }
    }

    private fun awaitOpponent(session: OnlineSession) {
        waitingJob?.cancel()
        _uiState.update { it.copy(isBusy = false, waitingSession = session) }
        waitingJob = viewModelScope.launch {
            repository.observeRoom(session.roomCode)
                .catch { _uiState.update { state -> state.copy(waitingSession = null) } }
                .collect { room ->
                    when {
                        room.status.isPlayable && room.playerCount == 2 -> {
                            _events.emit(OnlineLobbyEvent.OpenGame(session))
                            waitingJob?.cancel()
                        }

                        room.status == OnlineRoomStatus.CANCELLED ||
                            room.status == OnlineRoomStatus.EXPIRED ->
                            _uiState.update {
                                it.copy(
                                    waitingSession = null,
                                    message = AppError.ROOM_NOT_FOUND.message,
                                )
                            }

                        else -> Unit
                    }
                }
        }
    }

    private fun updateConfiguration(transform: (RoomConfiguration) -> RoomConfiguration) {
        _uiState.update { it.copy(configuration = transform(it.configuration), message = null) }
    }

    companion object {
        val TURN_OPTIONS = RoomTiming.TURN_OPTIONS
        val TOTAL_OPTIONS = RoomTiming.TOTAL_OPTIONS
    }
}
