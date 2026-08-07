package com.duzman46.gridbound.presentation.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.leaderboard.domain.LeaderboardCursor
import com.duzman46.gridbound.leaderboard.domain.LeaderboardEntry
import com.duzman46.gridbound.leaderboard.domain.LeaderboardRepository
import com.duzman46.gridbound.leaderboard.domain.LeaderboardScope
import com.duzman46.gridbound.leaderboard.domain.OwnStanding
import com.duzman46.gridbound.session.SessionManager
import com.duzman46.gridbound.social.domain.Friend
import com.duzman46.gridbound.social.domain.FriendshipStatus
import com.duzman46.gridbound.social.domain.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LeaderboardTabState(
    val entries: List<LeaderboardEntry> = emptyList(),
    val cursor: LeaderboardCursor? = null,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
    val isAppending: Boolean = false,
    val error: UiText? = null,
) {
    val isEmpty: Boolean get() = entries.isEmpty() && !isLoading && error == null
}

data class LeaderboardUiState(
    val selectedScope: LeaderboardScope = LeaderboardScope.GLOBAL,
    val tabs: Map<LeaderboardScope, LeaderboardTabState> = emptyMap(),
    val ownStanding: OwnStanding? = null,
    val isOwnStandingLoading: Boolean = false,
    val ownStandingError: UiText? = null,
    /** True while the player holds no place on the board because they have not linked one. */
    val isGuest: Boolean = false,
    /** False until there is an account that could hold a place at all. */
    val hasAccount: Boolean = false,
) {
    val current: LeaderboardTabState
        get() = tabs[selectedScope] ?: LeaderboardTabState()

    /**
     * What stands in for the player's own row when there is none to show.
     *
     * Every answer here is about this player, not about the request that came back empty. A
     * guest is told the particular reason they are unranked, someone with no account is told
     * to make one, and anybody else has already done both of those things — so what they are
     * told is that their position did not load, which is the truth and is also something they
     * can act on by retrying. Reaching for the sign-in line whenever a row is missing is what
     * once answered a signed-in player with "sign in", and there is no state in which that
     * sentence is wrong and harmless at the same time.
     */
    val noStandingMessage: UiText
        get() = when {
            isGuest -> UiText.Res(R.string.leaderboard_guest_not_ranked)
            !hasAccount -> UiText.Res(R.string.leaderboard_sign_in_required)
            else -> ownStandingError ?: UiText.Res(R.string.error_unknown)
        }
}

/**
 * Paginated leaderboard.
 *
 * Each tab keeps its own list and cursor, so switching back and forth does not refetch, and
 * ranks are numbered here because only this layer knows how many rows precede a page.
 */
@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val repository: LeaderboardRepository,
    private val socialRepository: SocialRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    /** Confirmed friends, used to build the friends board without a second lookup. */
    private var friendIds: Set<String> = emptySet()

    private val _uiState = MutableStateFlow(
        sessionManager.state.value.let { session ->
            LeaderboardUiState(
                isGuest = session.isGuest,
                hasAccount = session.canUseSocialFeatures,
            )
        },
    )
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init {
        selectScope(LeaderboardScope.GLOBAL)
        refreshOwnStanding()
        viewModelScope.launch {
            val userId = sessionManager.state.value.user?.userId ?: return@launch
            socialRepository.observeFriendships(userId).collect { friends ->
                friendIds = friends
                    .filter { it.status == FriendshipStatus.FRIENDS }
                    .map(Friend::userId)
                    .toSet()
                // Refresh the friends board when the list behind it changes.
                if (_uiState.value.tabs.containsKey(LeaderboardScope.FRIENDS)) {
                    loadFirstPage(LeaderboardScope.FRIENDS)
                }
            }
        }
    }

    fun selectScope(scope: LeaderboardScope) {
        _uiState.update { it.copy(selectedScope = scope) }
        if (_uiState.value.tabs[scope] == null) loadFirstPage(scope)
    }

    fun retry() {
        loadFirstPage(_uiState.value.selectedScope)
        refreshOwnStanding()
    }

    /** Called when the list nears its end. Ignored while a page is already in flight. */
    fun loadMore() {
        val scope = _uiState.value.selectedScope
        val tab = _uiState.value.tabs[scope] ?: return
        if (tab.isLoading || tab.isAppending || !tab.hasMore || tab.cursor == null) return
        updateTab(scope) { it.copy(isAppending = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.loadPage(scope, tab.cursor, friendIds)) {
                is Outcome.Success -> updateTab(scope) { existing ->
                    val startRank = existing.entries.size
                    existing.copy(
                        entries = existing.entries + result.value.entries.numbered(startRank),
                        cursor = result.value.cursor,
                        hasMore = result.value.hasMore,
                        isAppending = false,
                    )
                }

                is Outcome.Failure -> updateTab(scope) {
                    it.copy(isAppending = false, error = result.error.message)
                }
            }
        }
    }

    private fun loadFirstPage(scope: LeaderboardScope) {
        updateTab(scope) { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.loadPage(scope, cursor = null, friendIds = friendIds)) {
                is Outcome.Success -> updateTab(scope) {
                    LeaderboardTabState(
                        entries = result.value.entries.numbered(0),
                        cursor = result.value.cursor,
                        hasMore = result.value.hasMore,
                    )
                }

                is Outcome.Failure -> updateTab(scope) {
                    LeaderboardTabState(isLoading = false, error = result.error.message)
                }
            }
        }
    }

    /**
     * A guest is skipped rather than asked and refused: they hold no place on the board, so
     * there is no position to fetch and nothing an error message would be true about.
     */
    private fun refreshOwnStanding() {
        val session = sessionManager.state.value
        val userId = session.user?.userId?.takeUnless { session.isGuest } ?: return
        _uiState.update { it.copy(isOwnStandingLoading = true, ownStandingError = null) }
        viewModelScope.launch {
            when (val result = repository.loadOwnStanding(userId)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(ownStanding = result.value, isOwnStandingLoading = false)
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(isOwnStandingLoading = false, ownStandingError = result.error.message)
                }
            }
        }
    }

    private fun updateTab(scope: LeaderboardScope, transform: (LeaderboardTabState) -> LeaderboardTabState) {
        _uiState.update { state ->
            val existing = state.tabs[scope] ?: LeaderboardTabState()
            state.copy(tabs = state.tabs + (scope to transform(existing)))
        }
    }
}

private fun List<LeaderboardEntry>.numbered(startRank: Int): List<LeaderboardEntry> =
    mapIndexed { index, entry -> entry.copy(rank = startRank + index + 1) }
