package com.duzman46.gridbound.presentation.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.match.domain.MatchRepository
import com.duzman46.gridbound.match.domain.RecentMatch
import com.duzman46.gridbound.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * @param isUnavailable true when the list could not be read at all. The section then draws
 *   nothing: "no games yet" is a sentence about a player, and saying it because the network
 *   dropped tells a player with a hundred matches that they have never played one.
 */
data class RecentGamesState(
    val matches: List<RecentMatch> = emptyList(),
    val isLoading: Boolean = true,
    val isUnavailable: Boolean = false,
)

/**
 * Backs the recent-games section on both profile pages.
 *
 * One view model for the two of them, because it is one section: the page it appears on
 * decides only whose history is being asked for. [PlayerProfileViewModel.USER_ID_KEY] is the
 * navigation argument another player's page is opened with, and its absence is what "my own
 * profile" looks like from here — that route carries no id because the signed-in player is
 * the one the session already knows about. Splitting this in two would mean two places to
 * keep a fetch, a failure policy and a sort order in step for no gain.
 *
 * The list is read once rather than observed. It only changes when a match ends, which cannot
 * happen while its own profile page is open, and a listener held on somebody else's history
 * for as long as their page is up is a subscription paying for nothing.
 */
@HiltViewModel
class RecentGamesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val matchRepository: MatchRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(RecentGamesState())
    val state: StateFlow<RecentGamesState> = _state.asStateFlow()

    init {
        val requested = savedStateHandle
            .get<String>(PlayerProfileViewModel.USER_ID_KEY)
            .orEmpty()
        viewModelScope.launch {
            sessionManager.state
                // The session is what supplies the id on the player's own page, and it does
                // not have one at the moment this screen is built. Collecting rather than
                // reading once is what makes the list turn up on a cold start instead of on
                // the second visit; the filter below is what stops it being asked for before
                // there is anybody to ask about.
                .map { session -> requested.ifBlank { session.user?.userId.orEmpty() } }
                .distinctUntilChanged()
                .collect { userId -> if (userId.isNotBlank()) load(userId) }
        }
    }

    private suspend fun load(userId: String) {
        _state.value = RecentGamesState()
        _state.value = when (val result = matchRepository.loadRecentMatches(userId)) {
            is Outcome.Success -> RecentGamesState(matches = result.value, isLoading = false)
            is Outcome.Failure -> RecentGamesState(isLoading = false, isUnavailable = true)
        }
    }
}
