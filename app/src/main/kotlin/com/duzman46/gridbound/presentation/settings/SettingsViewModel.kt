package com.duzman46.gridbound.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.data.SettingsManager
import com.duzman46.gridbound.data.StatisticsManager
import com.duzman46.gridbound.domain.models.AppSettings
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.domain.models.GameStatistics
import com.duzman46.gridbound.domain.models.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val statistics: GameStatistics = GameStatistics(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    statisticsManager: StatisticsManager,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsManager.settings,
        statisticsManager.statistics,
        ::SettingsUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.Data.STATE_FLOW_STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
    )

    fun setThemeMode(mode: ThemeMode) = launchUpdate { settingsManager.setThemeMode(mode) }
    fun setLanguage(language: AppLanguage) = launchUpdate { settingsManager.setLanguage(language) }
    fun setSoundEnabled(enabled: Boolean) = launchUpdate { settingsManager.setSoundEnabled(enabled) }
    fun setNotificationsEnabled(enabled: Boolean) =
        launchUpdate { settingsManager.setNotificationsEnabled(enabled) }
    fun setHapticsEnabled(enabled: Boolean) = launchUpdate { settingsManager.setHapticsEnabled(enabled) }
    fun setMatchMessagesEnabled(enabled: Boolean) =
        launchUpdate { settingsManager.setMatchMessagesEnabled(enabled) }

    private fun launchUpdate(update: suspend () -> Unit) {
        viewModelScope.launch { update() }
    }
}
