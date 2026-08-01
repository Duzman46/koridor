package com.duzman46.gridbound.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.data.SettingsManager
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.domain.models.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsManager.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.Data.STATE_FLOW_STOP_TIMEOUT_MILLIS),
        initialValue = AppSettings(),
    )

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsManager.setLanguage(language) }
    }
}
