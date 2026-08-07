package com.duzman46.gridbound.presentation.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.online.data.RoomCredentials
import com.duzman46.gridbound.online.domain.OnlineGameRepository
import com.duzman46.gridbound.online.model.MatchmakingState
import com.duzman46.gridbound.online.model.OnlineLobbyResult
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineRoomStatus
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.online.model.RoomBrowserFilter
import com.duzman46.gridbound.online.model.RoomConfiguration
import com.duzman46.gridbound.online.model.RoomTiming
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
    /** True while this player is in the matchmaking list, which is not a room. */
    val isQueued: Boolean = false,
    val waitingSession: OnlineSession? = null,
    val openRooms: List<OnlineRoom> = emptyList(),
    val isLoadingRooms: Boolean = false,
    val filter: RoomBrowserFilter = RoomBrowserFilter(),
    /** The room code we are asking a password for. Set from either join path. */
    val passwordPromptCode: String? = null,
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
 * Drives room creation, the room browser, matchmaking and reconnection.
 *
 * Every path that opens or joins a room funnels through [enterRoom], so the busy flag that
 * stops a double tap from opening two rooms is applied in exactly one place. Matchmaking is
 * the one exception, and it is not one: it opens nothing, it takes a place in a list, so it
 * has a flag of its own and can be given up without a room to tear down.
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
    private var queueJob: Job? = null

    init {
        closeIdleMatches()
        refreshOpenRooms()
        observeFriends()
    }

    /**
     * Sends a friend the code of the room being hosted. Only offered while waiting, because
     * that is the only moment there is a room worth joining.
     */
    fun inviteFriend(userId: String) {
        val waiting = _uiState.value.waitingSession ?: return
        val account = sessionManager.state.value
        val ownId = account.user?.userId ?: return
        val ownName = account.profile?.username.orEmpty()
        if (userId in _uiState.value.invitedUserIds) return
        _uiState.update { it.copy(invitedUserIds = it.invitedUserIds + userId) }
        viewModelScope.launch {
            val result = socialRepository.sendInvite(ownId, ownName, userId, waiting.roomCode)
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

    fun setTurnDuration(seconds: Int) =
        updateConfiguration { it.copy(timing = it.timing.copy(turnDurationSeconds = seconds)) }

    fun setRoomPassword(value: String) = updateConfiguration { it.copy(password = value) }

    fun setHostSeat(value: PlayerId) = updateConfiguration { it.copy(hostSeat = value) }

    fun dismissPasswordPrompt() =
        _uiState.update { it.copy(passwordPromptCode = null, joinPassword = "") }

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
        val code = state.roomCodeInput
        if (!RoomCredentials.isValidCode(code)) {
            _uiState.update { it.copy(message = AppError.ROOM_CODE_INVALID.message) }
            return
        }
        // Try without one first. A protected room answers ROOM_PASSWORD_WRONG, and that is
        // when we ask — this path had no password field at all, so every protected room
        // rejected the player with "wrong password" before they could type one.
        enterRoom(
            onFailure = { error ->
                if (error == AppError.ROOM_PASSWORD_WRONG) {
                    _uiState.update {
                        it.copy(passwordPromptCode = code, joinPassword = "", message = null)
                    }
                    true
                } else {
                    false
                }
            },
        ) { repository.joinRoom(code, state.joinPassword) }
    }

    /** A protected room asks for its password first rather than failing the tap. */
    fun joinListedRoom(room: OnlineRoom) {
        if (room.requiresPassword) {
            _uiState.update { it.copy(passwordPromptCode = room.roomCode, joinPassword = "") }
            return
        }
        enterRoom { repository.joinRoom(room.roomCode) }
    }

    fun confirmPasswordPrompt() {
        val code = _uiState.value.passwordPromptCode ?: return
        val password = _uiState.value.joinPassword
        _uiState.update { it.copy(passwordPromptCode = null) }
        enterRoom { repository.joinRoom(code, password) }
    }

    /**
     * Takes a place in the matchmaking list and holds it until a rival turns up.
     *
     * Nothing is created here. Pressing this used to open a room when no open one was found,
     * so two players who pressed it seconds apart sat in a room each and never met; the list
     * is what they now both land in. It carries no room configuration either — the pairing
     * settles the colours by chance, and neither player is offered the choice.
     */
    fun quickMatch() {
        if (_uiState.value.isBusy || _uiState.value.isQueued) return
        val ranked = _uiState.value.canPlayRanked
        _uiState.update { it.copy(isQueued = true, message = null) }
        queueJob = viewModelScope.launch {
            repository.matchmake(ranked).collect { state ->
                when (state) {
                    is MatchmakingState.Paired -> {
                        _uiState.update { it.copy(isQueued = false) }
                        _events.emit(OnlineLobbyEvent.OpenGame(state.session))
                    }

                    is MatchmakingState.Failed -> _uiState.update {
                        it.copy(isQueued = false, message = state.error.message)
                    }

                    MatchmakingState.Searching -> Unit
                }
            }
        }
    }

    /**
     * Gives up the place in the list.
     *
     * Also called when the lobby stops being looked at, because a place in the list is a
     * promise to be there when a rival is found and a backgrounded app cannot keep it.
     */
    fun leaveQueue() {
        if (!_uiState.value.isQueued) return
        queueJob?.cancel()
        queueJob = null
        _uiState.update { it.copy(isQueued = false) }
    }

    fun cancelWaiting() {
        val session = _uiState.value.waitingSession ?: return
        waitingJob?.cancel()
        viewModelScope.launch { repository.leaveRoom(session) }
        _uiState.update { it.copy(waitingSession = null, isBusy = false) }
    }

    /**
     * Settles anything the player walked out of before showing them a lobby.
     *
     * There is deliberately no "return to your match" here. Either the match is still live,
     * in which case its room code gets the player back into it, or nobody has moved in ten
     * minutes and it is already decided — offering a way back into a match the opponent gave
     * up on hours ago helped no one.
     */
    private fun closeIdleMatches() {
        val userId = sessionManager.state.value.user?.userId ?: return
        viewModelScope.launch { repository.closeIdleMatches(userId) }
    }

    private fun enterRoom(
        waitForOpponent: Boolean = false,
        onFinally: () -> Unit = {},
        /** Returns true when it has handled the error itself and no message should show. */
        onFailure: (AppError) -> Boolean = { false },
        action: suspend () -> OnlineLobbyResult,
    ) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            when (val result = action()) {
                is OnlineLobbyResult.Failure -> {
                    val handled = onFailure(result.error)
                    _uiState.update {
                        it.copy(isBusy = false, message = if (handled) null else result.error.message)
                    }
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
    }
}
