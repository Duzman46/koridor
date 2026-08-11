package com.duzman46.gridbound.presentation

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.data.SettingsManager
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.domain.models.AppSettings
import com.duzman46.gridbound.monetization.BillingManager
import com.duzman46.gridbound.monetization.BillingState
import com.duzman46.gridbound.monetization.MonetizationManager
import com.duzman46.gridbound.monetization.MonetizationState
import com.duzman46.gridbound.monetization.domain.Entitlement
import com.duzman46.gridbound.session.SessionManager
import com.duzman46.gridbound.session.SessionState
import com.duzman46.gridbound.ui.localization.LocaleController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val monetizationManager: MonetizationManager,
    private val billingManager: BillingManager,
    private val sessionManager: SessionManager,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsManager.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.Data.STATE_FLOW_STOP_TIMEOUT_MILLIS),
        initialValue = AppSettings(),
    )
    val monetization: StateFlow<MonetizationState> = monetizationManager.state
    val billing: StateFlow<BillingState> = billingManager.state
    val session: StateFlow<SessionState> = sessionManager.state

    init {
        // Entitlements are per account, so the billing layer has to be told who is playing.
        // Without this, signing into a second account on one device would inherit the
        // first account's purchases.
        viewModelScope.launch {
            sessionManager.state
                .map { it.user?.userId?.takeIf { _ -> it.canUseSocialFeatures } }
                .distinctUntilChanged()
                .collect(billingManager::onAccountChanged)
        }
    }

    fun initializeMonetization(activity: Activity) = monetizationManager.initialize(activity)

    fun buy(activity: Activity, entitlement: Entitlement) =
        billingManager.purchase(activity, entitlement)

    // No restorePurchases() here any more. Nothing in the interface calls it: Play is asked what
    // this account owns every time billing connects, so a reinstall restores itself. The manager
    // keeps the method because it still answers ALREADY_OWNED with it.

    fun dismissBillingMessage() = billingManager.dismissMessage()

    fun showPrivacyOptions(activity: Activity) = monetizationManager.showPrivacyOptions(activity)

    fun showInterstitialAfterCompletedMatch(activity: Activity, onFinished: () -> Unit) =
        monetizationManager.showInterstitialAfterCompletedMatch(activity, onFinished)

    /**
     * Gives a guest who started offline a real identity as soon as one can be created, so
     * their progress begins syncing without another prompt.
     */
    fun recoverBackendIdentity() {
        viewModelScope.launch { sessionManager.ensureBackendIdentity() }
    }

    /**
     * Stores the choice, syncs it to the player's profile, and hands it to the platform.
     *
     * The screens update from the settings flow through `ProvideAppLocale` — nothing is
     * recreated, so the back stack and the current match survive a language change. The
     * platform call is what makes the choice outlast the process.
     */
    fun setLanguage(context: Context, language: AppLanguage) {
        viewModelScope.launch {
            settingsManager.setLanguage(language)
            sessionManager.updatePreferredLanguage(language.tag)
            LocaleController.apply(context, language)
        }
    }
}
