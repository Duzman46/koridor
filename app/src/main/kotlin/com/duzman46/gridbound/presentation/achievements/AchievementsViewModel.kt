package com.duzman46.gridbound.presentation.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.achievements.domain.AchievementGroup
import com.duzman46.gridbound.achievements.domain.AchievementState
import com.duzman46.gridbound.achievements.domain.achievementStates
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.data.StatisticsManager
import com.duzman46.gridbound.domain.models.GameStatistics
import com.duzman46.gridbound.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AchievementsUiState(
    val states: List<AchievementState> = emptyList(),
) {
    val unlocked: Int get() = states.count(AchievementState::unlocked)

    val total: Int get() = states.size

    /** 0f to 1f across the whole set, for the ring on the summary card. */
    val fraction: Float get() = if (total == 0) 0f else unlocked.toFloat() / total

    /** The badges of one group, in catalogue order. */
    fun of(group: AchievementGroup): List<AchievementState> =
        states.filter { it.achievement.group == group }

    /** How many of one group are earned, for the count beside its heading. */
    fun unlockedIn(group: AchievementGroup): Int = of(group).count(AchievementState::unlocked)
}

/**
 * The badge list, recomputed from the statistics every time either side of it changes.
 *
 * There is no store of earned badges to keep in step, which is why this view model is four lines
 * of plumbing: what a player has earned is a question about their record, and their record is
 * already a flow.
 */
@HiltViewModel
class AchievementsViewModel @Inject constructor(
    statisticsManager: StatisticsManager,
    sessionManager: SessionManager,
) : ViewModel() {

    val uiState: StateFlow<AchievementsUiState> = combine(
        statisticsManager.statistics,
        // The session's answer rather than the data store's: it is true when either this device
        // or the player's cloud profile has recorded the lesson, so the badge survives a
        // reinstall for anybody with an account.
        sessionManager.state,
    ) { statistics, session ->
        AchievementsUiState(achievementStates(statistics, session.tutorialCompleted))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.Data.STATE_FLOW_STOP_TIMEOUT_MILLIS),
        // The whole catalogue at zero rather than an empty list: the screen should open on the
        // badges waiting to be earned, not on nothing for the length of one read.
        initialValue = AchievementsUiState(
            achievementStates(GameStatistics(), tutorialCompleted = false),
        ),
    )
}
