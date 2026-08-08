package com.duzman46.gridbound.presentation.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.online.domain.OnlineGameRepository
import com.duzman46.gridbound.online.model.OnlineLobbyResult
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.session.SessionManager
import com.duzman46.gridbound.social.domain.PlayerRequest
import com.duzman46.gridbound.social.domain.RequestKind
import com.duzman46.gridbound.social.domain.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RequestBarUiState(
    /** The one question being put to the player, or null when nobody is asking anything. */
    val request: PlayerRequest? = null,
    val isAnswering: Boolean = false,
    /** Why the last answer did not go through. Belongs to [request] and dies with it. */
    val message: UiText? = null,
)

sealed interface RequestBarEvent {
    /** The room let us in; the board is next. */
    data class OpenGame(val session: OnlineSession) : RequestBarEvent

    /** The room wants a password, and only the lobby has somewhere to type one. */
    data class OpenLobby(val roomCode: String) : RequestBarEvent
}

/**
 * Watches the live request channel for the whole app and answers what the player decides.
 *
 * One instance, owned above the navigation graph, because the channel is not a screen's
 * business: an invitation or a rematch arrives while the player is anywhere at all, and a
 * listener per screen would mean subscribing and unsubscribing on every navigation, with a
 * request landing in the gap simply never being seen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RequestChannelViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    private val onlineRepository: OnlineGameRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestBarUiState())
    val uiState: StateFlow<RequestBarUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RequestBarEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<RequestBarEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            sessionManager.state
                .flatMapLatest { session ->
                    // Everybody with an identity listens, guests included. This used to demand a
                    // real account on the reasoning that the channel is a social feature, but
                    // the rules are narrower than that and always were: a game invitation needs
                    // a friendship, which nobody can have with a guest, while a rematch needs
                    // only a finished room the two of them played. So the one thing that can
                    // ever reach a guest here is a rematch from the player they have just
                    // played, and refusing to listen protected them from nothing — it only made
                    // the offer unanswerable, leaving the sender waiting on a question that was
                    // never asked.
                    session.user?.userId
                        ?.let(socialRepository::observeRequests)
                        ?: flowOf(emptyList())
                }
                .collect { requests ->
                    // One bar, so one question: the newest, because a bar interrupts what the
                    // player is doing and the freshest ask is the one someone is waiting on
                    // right now. Anything it passes over is still listed on the friends screen.
                    // A refusal carries no question at all and is for the winner screen to read.
                    val next = requests
                        .filter { it.kind.isAsk }
                        .maxByOrNull(PlayerRequest::createdAt)
                    _uiState.update { state ->
                        if (next?.fromUserId == state.request?.fromUserId) {
                            state.copy(request = next)
                        } else {
                            RequestBarUiState(request = next)
                        }
                    }
                }
        }
    }

    /**
     * Takes the room the request points at, so accepting lands the player on the board rather
     * than on a lobby they then have to work.
     */
    fun accept() {
        val request = _uiState.value.request ?: return
        val ownId = sessionManager.state.value.user?.userId ?: return
        if (_uiState.value.isAnswering) return
        _uiState.update { it.copy(isAnswering = true, message = null) }
        viewModelScope.launch {
            when (val result = onlineRepository.joinRoom(request.roomCode)) {
                is OnlineLobbyResult.Success -> {
                    clear(ownId, request)
                    _events.tryEmit(RequestBarEvent.OpenGame(result.session))
                }

                is OnlineLobbyResult.Failure ->
                    if (result.error == AppError.ROOM_PASSWORD_WRONG) {
                        // A bar has nowhere to type a password. The lobby does, and it opens
                        // with the code already filled in.
                        clear(ownId, request)
                        _events.tryEmit(RequestBarEvent.OpenLobby(request.roomCode))
                    } else {
                        // The request is left standing. The room may be a moment behind the
                        // invitation, and throwing away an invitation the player did answer
                        // is worse than a bar that says what went wrong.
                        _uiState.update {
                            it.copy(isAnswering = false, message = result.error.message)
                        }
                    }
            }
        }
    }

    fun decline() {
        val request = _uiState.value.request ?: return
        val session = sessionManager.state.value
        val ownId = session.user?.userId ?: return
        if (_uiState.value.isAnswering) return
        _uiState.update { it.copy(isAnswering = true, message = null) }
        viewModelScope.launch {
            if (request.kind == RequestKind.REMATCH) {
                // Answered, not merely dropped: somebody is sitting on a winner screen waiting
                // to hear, and silence would leave them there until the request timed out.
                socialRepository.declineRematch(
                    fromUserId = ownId,
                    fromUsername = session.profile?.username.orEmpty(),
                    toUserId = request.fromUserId,
                    roomCode = request.roomCode,
                    playedRoomCode = request.playedRoomCode,
                )
            }
            clear(ownId, request)
        }
    }

    /**
     * Drops the entry both on screen and in the database. The local half goes first so the bar
     * closes on the tap rather than on the round trip.
     */
    private suspend fun clear(ownId: String, request: PlayerRequest) {
        _uiState.value = RequestBarUiState()
        socialRepository.clearRequest(ownId, request.fromUserId)
    }
}
