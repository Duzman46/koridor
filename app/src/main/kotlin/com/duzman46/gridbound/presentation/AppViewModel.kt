package com.duzman46.gridbound.presentation

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.data.SettingsManager
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.domain.models.AppSettings
import com.duzman46.gridbound.monetization.MonetizationManager
import com.duzman46.gridbound.monetization.MonetizationState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val monetizationManager: MonetizationManager,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsManager.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.Data.STATE_FLOW_STOP_TIMEOUT_MILLIS),
        initialValue = AppSettings(),
    )
    val monetization: StateFlow<MonetizationState> = monetizationManager.state

    fun initializeMonetization(activity: Activity) = monetizationManager.initialize(activity)

    fun buyPremium(activity: Activity) = monetizationManager.buyPremium(activity)

    fun restorePurchases() = monetizationManager.restorePurchases()

    fun showPrivacyOptions(activity: Activity) = monetizationManager.showPrivacyOptions(activity)

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsManager.setLanguage(language) }
    }
}
