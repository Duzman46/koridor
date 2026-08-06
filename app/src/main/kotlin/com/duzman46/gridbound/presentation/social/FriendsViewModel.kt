package com.duzman46.gridbound.presentation.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.profile.domain.UserProfile
import com.duzman46.gridbound.session.SessionManager
import com.duzman46.gridbound.social.domain.Friend
import com.duzman46.gridbound.social.domain.FriendshipAction
import com.duzman46.gridbound.social.domain.FriendshipStatus
import com.duzman46.gridbound.social.domain.GameInvite
import com.duzman46.gridbound.social.domain.PresenceState
import com.duzman46.gridbound.social.domain.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val invites: List<GameInvite> = emptyList(),
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
 * Backs the friends screen: search, requests, blocking and invitations.
 *
 * Presence is observed only for the players already on screen, so opening this screen costs
 * one listener per relationship rather than a subscription to everyone who is online.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val repository: SocialRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FriendsUiState(requiresAccount = !sessionManager.state.value.canUseSocialFeatures),
    )
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    private val friendsFlow: StateFlow<List<Friend>> = sessionManager.state
        .flatMapLatest { session ->
            val userId = session.user?.userId
            if (userId == null || !session.canUseSocialFeatures) {
                flowOf(emptyList())
            } else {
                repository.observeFriendships(userId)
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
                }
                .collect { presence -> _uiState.update { it.copy(presence = presence) } }
        }
        viewModelScope.launch {
            sessionManager.state
                .flatMapLatest { session ->
                    session.user?.userId
                        ?.takeIf { session.canUseSocialFeatures }
                        ?.let(repository::observeInvites)
                        ?: flowOf(emptyList())
                }
                .collect { invites -> _uiState.update { it.copy(invites = invites) } }
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

    fun invite(userId: String, roomCode: String) {
        val ownId = sessionManager.state.value.user?.userId ?: return
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            val result = repository.sendInvite(ownId, userId, roomCode)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    message = when (result) {
                        is Outcome.Success -> UiText.Res(R.string.friends_invite_sent)
                        is Outcome.Failure -> result.error.message
                    },
                )
            }
        }
    }

    fun dismissInvite(inviteId: String) {
        val ownId = sessionManager.state.value.user?.userId ?: return
        viewModelScope.launch { repository.dismissInvite(ownId, inviteId) }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null, searchMessage = null) }

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
