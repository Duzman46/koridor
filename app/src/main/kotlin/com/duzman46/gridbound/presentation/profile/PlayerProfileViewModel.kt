package com.duzman46.gridbound.presentation.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.profile.domain.UserProfile
import com.duzman46.gridbound.profile.domain.UserProfileRepository
import com.duzman46.gridbound.session.SessionManager
import com.duzman46.gridbound.social.domain.Friend
import com.duzman46.gridbound.social.domain.FriendshipAction
import com.duzman46.gridbound.social.domain.FriendshipStatus
import com.duzman46.gridbound.social.domain.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerProfileUiState(
    val profile: UserProfile? = null,
    /** The relationship as the signed-in player sees it, which is what the one button says. */
    val status: FriendshipStatus = FriendshipStatus.NONE,
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    /** True when a list has led the player back to their own page; there is nothing to do. */
    val isSelf: Boolean = false,
    /** A guest can read a record but has no durable identity to hang a friendship on. */
    val requiresAccount: Boolean = false,
    val error: UiText? = null,
    val message: UiText? = null,
)

/**
 * Backs another player's page.
 *
 * The record is fetched once — a rating cannot move while you are looking at it — but the
 * relationship is observed, because it can: the other player may accept, cancel or block
 * while their page is open, and a button offering to send a request that would now be refused
 * is worse than no button. That live status is also why nothing here guesses at the new state
 * after an action; the listener says what actually happened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profileRepository: UserProfileRepository,
    private val socialRepository: SocialRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val userId = savedStateHandle.get<String>(USER_ID_KEY).orEmpty()

    private val _uiState = MutableStateFlow(PlayerProfileUiState())
    val uiState: StateFlow<PlayerProfileUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            sessionManager.state.collect { session ->
                _uiState.update {
                    it.copy(
                        isSelf = userId.isNotBlank() && userId == session.user?.userId,
                        requiresAccount = !session.canUseSocialFeatures,
                    )
                }
            }
        }
        viewModelScope.launch {
            sessionManager.state
                .flatMapLatest { session ->
                    session.user?.userId
                        ?.takeIf { session.canUseSocialFeatures }
                        ?.let(socialRepository::observeFriendships)
                        ?: flowOf(emptyList())
                }
                .collect { friends -> _uiState.update { it.copy(status = friends.statusOf()) } }
        }
    }

    fun retry() = load()

    fun sendRequest() = act(FriendshipAction.SEND_REQUEST)

    fun accept() = act(FriendshipAction.ACCEPT)

    fun unblock() = act(FriendshipAction.UNBLOCK)

    private fun load() {
        if (userId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = AppError.UNKNOWN.message) }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = profileRepository.loadProfile(userId)) {
                is Outcome.Success ->
                    _uiState.update { it.copy(profile = result.value, isLoading = false) }

                is Outcome.Failure ->
                    _uiState.update { it.copy(isLoading = false, error = result.error.message) }
            }
        }
    }

    private fun act(action: FriendshipAction) {
        val ownId = sessionManager.state.value.user?.userId ?: return
        if (userId.isBlank() || _uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            val result = socialRepository.applyFriendshipAction(ownId, userId, action)
            _uiState.update {
                it.copy(isBusy = false, message = (result as? Outcome.Failure)?.error?.message)
            }
        }
    }

    /** No entry at all is the same thing as no relationship. */
    private fun List<Friend>.statusOf(): FriendshipStatus =
        firstOrNull { it.userId == userId }?.status ?: FriendshipStatus.NONE

    companion object {
        /** The navigation argument this screen is opened with. */
        const val USER_ID_KEY = "userId"
    }
}
