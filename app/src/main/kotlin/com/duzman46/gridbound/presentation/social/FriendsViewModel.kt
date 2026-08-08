package com.duzman46.gridbound.presentation.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.online.domain.OnlineGameRepository
import com.duzman46.gridbound.online.model.OnlineLobbyResult
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.online.model.RoomConfiguration
import com.duzman46.gridbound.online.model.RoomVisibility
import com.duzman46.gridbound.profile.domain.UserProfile
import com.duzman46.gridbound.session.SessionManager
import com.duzman46.gridbound.social.domain.Friend
import com.duzman46.gridbound.social.domain.FriendshipAction
import com.duzman46.gridbound.social.domain.FriendshipStatus
import com.duzman46.gridbound.social.domain.PlayerRequest
import com.duzman46.gridbound.social.domain.PresenceState
import com.duzman46.gridbound.social.domain.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsUiState(
    val query: String = "",
    val searchResult: UserProfile? = null,
    val searchStatus: FriendshipStatus = FriendshipStatus.NONE,
    val isSearching: Boolean = false,
    val searchMessage: UiText? = null,
    val friends: List<Friend> = emptyList(),
    val presence: Map<String, PresenceState> = emptyMap(),
    val invites: List<PlayerRequest> = emptyList(),
    /** Set while this player is holding a room open for a friend they have just invited. */
    val hostedInvite: HostedInvite? = null,
    val isBusy: Boolean = false,
    val message: UiText? = null,
    val requiresAccount: Boolean = false,
) {
    private fun of(status: FriendshipStatus) = friends.filter { it.status == status }

    val onlineFriends: List<Friend>
        get() = of(FriendshipStatus.FRIENDS)
            .filter { presence[it.userId] == PresenceState.ONLINE }

    val offlineFriends: List<Friend>
        get() = of(FriendshipStatus.FRIENDS)
            .filterNot { presence[it.userId] == PresenceState.ONLINE }

    val incomingRequests: List<Friend> get() = of(FriendshipStatus.REQUEST_RECEIVED)
    val outgoingRequests: List<Friend> get() = of(FriendshipStatus.REQUEST_SENT)
    val blocked: List<Friend> get() = of(FriendshipStatus.BLOCKED)

    val friendIds: Set<String>
        get() = of(FriendshipStatus.FRIENDS).map(Friend::userId).toSet()

    val isEmpty: Boolean get() = friends.isEmpty() && !isBusy
}

/**
 * A room opened for one named friend, and the wait for them to walk into it.
 *
 * The name is carried rather than looked up again: the panel says who is being waited for, and
 * that has to keep reading correctly even if the friend list underneath happens to change.
 */
data class HostedInvite(
    val session: OnlineSession,
    val friendName: String,
)

sealed interface FriendsEvent {
    /** The invited friend has taken the other seat; both devices open the board now. */
    data class OpenGame(val session: OnlineSession) : FriendsEvent
}

/**
 * Backs the friends screen: search, requests, blocking and invitations.
 *
 * Presence is observed only for the players already on screen, so opening this screen costs
 * one listener per relationship rather than a subscription to everyone who is online.
 *
 * Every listener is caught where it is collected, as they are in every other online view model
 * here. A database listener reports a refused read or a connection it has lost by throwing into
 * its flow, and an exception that reaches [viewModelScope] is not an error state — it is the
 * process. This screen subscribes three of them, so a rules deployment or a tunnel would close
 * the app on a player looking at their friends. Caught, that half of the screen falls back to
 * what it showed before the read landed, and the next change of session subscribes again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val repository: SocialRepository,
    private val onlineRepository: OnlineGameRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FriendsUiState(requiresAccount = !sessionManager.state.value.canUseSocialFeatures),
    )
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FriendsEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<FriendsEvent> = _events.asSharedFlow()

    private var hostedInviteJob: Job? = null

    private val friendsFlow: StateFlow<List<Friend>> = sessionManager.state
        .flatMapLatest { session ->
            val userId = session.user?.userId
            if (userId == null || !session.canUseSocialFeatures) {
                flowOf(emptyList())
            } else {
                repository.observeFriendships(userId).catch { emit(emptyList()) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            friendsFlow.collect { friends -> _uiState.update { it.copy(friends = friends) } }
        }
        viewModelScope.launch {
            friendsFlow
                .flatMapLatest { friends ->
                    repository.observePresence(friends.map(Friend::userId).toSet())
                        .catch { emit(emptyMap()) }
                }
                .collect { presence -> _uiState.update { it.copy(presence = presence) } }
        }
        viewModelScope.launch {
            sessionManager.state
                .flatMapLatest { session ->
                    val userId = session.user?.userId?.takeIf { session.canUseSocialFeatures }
                    if (userId == null) {
                        flowOf(emptyList())
                    } else {
                        repository.observeRequests(userId).catch { emit(emptyList()) }
                    }
                }
                // The bar over the app answers a request the moment it arrives; this list is
                // the standing record of what is still open, so only the asks belong in it.
                .collect { requests ->
                    val open = requests.filter { it.kind.isAsk }
                    _uiState.update { it.copy(invites = open) }
                }
        }
        viewModelScope.launch {
            sessionManager.state.collect { session ->
                _uiState.update { it.copy(requiresAccount = !session.canUseSocialFeatures) }
            }
        }
    }

    fun setQuery(value: String) =
        _uiState.update { it.copy(query = value, searchMessage = null, searchResult = null) }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty() || _uiState.value.isSearching) return
        _uiState.update { it.copy(isSearching = true, searchMessage = null, searchResult = null) }
        viewModelScope.launch {
            when (val result = repository.findByUsername(query)) {
                is Outcome.Success -> {
                    val profile = result.value
                    val ownId = sessionManager.state.value.user?.userId
                    _uiState.update { state ->
                        state.copy(
                            isSearching = false,
                            // Finding yourself is not a result worth offering actions on.
                            searchResult = profile?.takeIf { it.userId != ownId },
                            searchStatus = state.friends
                                .firstOrNull { it.userId == profile?.userId }
                                ?.status
                                ?: FriendshipStatus.NONE,
                            searchMessage = if (profile == null || profile.userId == ownId) {
                                UiText.Res(R.string.friends_search_empty)
                            } else {
                                null
                            },
                        )
                    }
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(isSearching = false, searchMessage = result.error.message)
                }
            }
        }
    }

    fun sendRequest(userId: String) = act(userId, FriendshipAction.SEND_REQUEST)

    fun accept(userId: String) = act(userId, FriendshipAction.ACCEPT)

    fun decline(userId: String) = act(userId, FriendshipAction.DECLINE)

    fun cancelRequest(userId: String) = act(userId, FriendshipAction.CANCEL)

    fun removeFriend(userId: String) = act(userId, FriendshipAction.REMOVE)

    fun block(userId: String) = act(userId, FriendshipAction.BLOCK)

    fun unblock(userId: String) = act(userId, FriendshipAction.UNBLOCK)

    /**
     * Opens a room for one friend and tells them about it, then holds the screen on that room
     * until they arrive.
     *
     * The room comes first and the invitation second, because an invitation carries a code and
     * there is no code until the room exists. If the invitation then fails to send there is
     * nobody coming, so the room is closed again rather than left waiting for a message that
     * was never delivered.
     *
     * Friends-only visibility: this room was opened for one person, and a stranger taking the
     * seat out of the public browser would be exactly the wrong outcome.
     */
    fun inviteToGame(friend: Friend) {
        val account = sessionManager.state.value
        val ownId = account.user?.userId ?: return
        val ownName = account.profile?.username.orEmpty()
        if (_uiState.value.isBusy || _uiState.value.hostedInvite != null) return
        _uiState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            val room = invitationRoom(ranked = account.canUseSocialFeatures)
            when (val opened = onlineRepository.createRoom(room)) {
                is OnlineLobbyResult.Failure -> _uiState.update {
                    it.copy(isBusy = false, message = opened.error.message)
                }

                is OnlineLobbyResult.Success -> {
                    val session = opened.session
                    val sent =
                        repository.sendInvite(ownId, ownName, friend.userId, session.roomCode)
                    if (sent is Outcome.Failure) {
                        onlineRepository.leaveRoom(session)
                        _uiState.update { it.copy(isBusy = false, message = sent.error.message) }
                        return@launch
                    }
                    // No "invitation sent" note: the panel that replaces this screen says who
                    // is being waited for, which is the same news said better.
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            hostedInvite = HostedInvite(session, friend.username),
                        )
                    }
                    awaitInvitedFriend(session)
                }
            }
        }
    }

    /** Gives up on the room, so the friend cannot walk into a seat nobody is holding. */
    fun cancelHostedInvite() {
        val hosted = _uiState.value.hostedInvite ?: return
        hostedInviteJob?.cancel()
        _uiState.update { it.copy(hostedInvite = null, message = null) }
        viewModelScope.launch { onlineRepository.leaveRoom(hosted.session) }
    }

    /**
     * The same wait the lobby does after creating a room: watch it until the second seat fills,
     * then hand the session to the screen so it can open the board.
     */
    private fun awaitInvitedFriend(session: OnlineSession) {
        hostedInviteJob?.cancel()
        hostedInviteJob = viewModelScope.launch {
            onlineRepository.observeRoom(session.roomCode)
                .catch { _uiState.update { state -> state.copy(hostedInvite = null) } }
                .collect { room ->
                    when {
                        // The room being deleted is the ending a waiting room actually has:
                        // the sweep removes it rather than marking it, and until that arrived
                        // as an event this panel went on naming a friend it was no longer
                        // holding a seat for.
                        room == null -> _uiState.update {
                            it.copy(hostedInvite = null, message = AppError.ROOM_NOT_FOUND.message)
                        }

                        room.status.isPlayable && room.playerCount == 2 -> {
                            _events.emit(FriendsEvent.OpenGame(session))
                            hostedInviteJob?.cancel()
                        }

                        room.status.isOver -> _uiState.update {
                            it.copy(hostedInvite = null, message = AppError.ROOM_NOT_FOUND.message)
                        }

                        else -> Unit
                    }
                }
        }
    }

    /**
     * A standard game, deliberately not whatever the player last set up in the lobby's create
     * form: an invitation is "come and play", not a negotiation about the rules.
     *
     * A null seat leaves the colours to be drawn, so inviting somebody is not also a way to
     * take the first move off them every time. Ranked only where a rating can follow the
     * players, which on this screen is always — friends need an account — but stated rather
     * than assumed.
     */
    private fun invitationRoom(ranked: Boolean) = RoomConfiguration(
        visibility = RoomVisibility.FRIENDS,
        ranked = ranked,
    )

    fun dismissInvite(fromUserId: String) {
        val ownId = sessionManager.state.value.user?.userId ?: return
        viewModelScope.launch { repository.clearRequest(ownId, fromUserId) }
    }

    private fun act(otherUserId: String, action: FriendshipAction) {
        val ownId = sessionManager.state.value.user?.userId ?: return
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            val result = repository.applyFriendshipAction(ownId, otherUserId, action)
            _uiState.update { state ->
                state.copy(
                    isBusy = false,
                    message = (result as? Outcome.Failure)?.error?.message,
                    // Keep the search card's buttons in step with what just happened.
                    searchStatus = if (state.searchResult?.userId == otherUserId &&
                        result is Outcome.Success
                    ) {
                        nextSearchStatus(action)
                    } else {
                        state.searchStatus
                    },
                )
            }
        }
    }

    private fun nextSearchStatus(action: FriendshipAction): FriendshipStatus = when (action) {
        FriendshipAction.SEND_REQUEST -> FriendshipStatus.REQUEST_SENT
        FriendshipAction.ACCEPT -> FriendshipStatus.FRIENDS
        FriendshipAction.BLOCK -> FriendshipStatus.BLOCKED
        else -> FriendshipStatus.NONE
    }
}
