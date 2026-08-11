package com.duzman46.gridbound.presentation.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.achievements.domain.Achievement
import com.duzman46.gridbound.achievements.domain.AchievementState
import com.duzman46.gridbound.achievements.domain.achievementStates
import com.duzman46.gridbound.data.StatisticsManager
import com.duzman46.gridbound.domain.repository.GameRepository
import com.duzman46.gridbound.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Notices a badge the moment it is earned, and says so once.
 *
 * A badge is a question asked of the statistics, so nothing has to be written when one is won —
 * but somebody does have to be told, and told exactly once. That is what the seen set is for, and
 * it is the only thing this stores.
 *
 * **The catch-up rule.** The seen set is absent on any install that predates the shelf, and every
 * badge such a player already satisfies would otherwise arrive at once as a stack of news about
 * matches played weeks ago. Absent means: award them all, announce none. From then on the set
 * exists, and only what is genuinely new is announced.
 *
 * A badge is marked seen as soon as it is announced rather than when the notice is dismissed, so
 * turning the phone over does not replay it.
 */
@HiltViewModel
class AchievementAlertViewModel @Inject constructor(
    statisticsManager: StatisticsManager,
    sessionManager: SessionManager,
    private val repository: GameRepository,
) : ViewModel() {

    private val _fresh = MutableStateFlow<List<Achievement>>(emptyList())

    /** The badges won since the player was last told, newest run first. Empty when there is nothing to say. */
    val fresh: StateFlow<List<Achievement>> = _fresh.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                statisticsManager.statistics,
                sessionManager.state,
                repository.seenAchievements,
            ) { statistics, session, seen ->
                val unlocked = achievementStates(statistics, session.tutorialCompleted)
                    .filter(AchievementState::unlocked)
                    .map { it.achievement }
                unlocked to seen
            }.collect { (unlocked, seen) ->
                val ids = unlocked.map(Achievement::name).toSet()
                if (seen == null) {
                    // First look on this install. Whatever the record already earns is granted
                    // silently — a notice about a match played last week is not news.
                    repository.markAchievementsSeen(ids)
                    return@collect
                }
                val newly = unlocked.filterNot { it.name in seen }
                if (newly.isEmpty()) return@collect
                _fresh.value = newly
                repository.markAchievementsSeen(seen + ids)
            }
        }
    }

    fun dismiss() {
        _fresh.value = emptyList()
    }
}
