package com.duzman46.gridbound.monetization

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import com.duzman46.gridbound.BuildConfig
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.di.ApplicationScope
import com.duzman46.gridbound.monetization.domain.Entitlement
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MonetizationState(
    val isPremium: Boolean = false,
    val adsAllowed: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
)

/**
 * Advertising and consent.
 *
 * Purchases live in [BillingManager]; this reads one entitlement from it — whether ads have
 * been removed — and otherwise stays out of the store's way.
 */
@Singleton
class MonetizationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val billingManager: BillingManager,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val consentInformation = UserMessagingPlatform.getConsentInformation(context)
    private val preferences = context.getSharedPreferences("monetization", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(MonetizationState())
    val state: StateFlow<MonetizationState> = _state.asStateFlow()

    private var adsInitialized = false
    private var interstitialAd: InterstitialAd? = null
    private var interstitialLoading = false
    private var interstitialShowing = false

    init {
        scope.launch {
            billingManager.state.collect { billing ->
                val isPremium = billing.owns(Entitlement.REMOVE_ADS)
                if (_state.value.isPremium != isPremium) {
                    _state.update { it.copy(isPremium = isPremium) }
                    updateConsentState()
                }
            }
        }
    }

    fun initialize(activity: Activity) {
        billingManager.connect()
        val request = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            request,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    updateConsentState()
                }
            },
            { error ->
                AppLog.warn("consent-info-update")
                updateConsentState()
            },
        )
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { updateConsentState() }
    }

    /**
     * Shows a full-screen ad on the way out of a match, if one is due.
     *
     * Called from the victory screen's three exits and from confirming "leave the game" on the
     * board — every point at which a match is over for this player and they are already
     * changing screens. Never during a turn, and never while a board they are still playing on
     * is in front of them.
     *
     * One gate: enough time since the last one. It used to also count matches and show an ad
     * every third, which is why one gate is left — with the count at one, the counter is a
     * comparison that is always true and a stored integer nobody reads.
     *
     * [onFinished] runs in every path, so a missing or failed ad never strands the player.
     */
    @SuppressLint("UseKtx")
    fun showInterstitialAfterCompletedMatch(activity: Activity, onFinished: () -> Unit) {
        if (interstitialShowing || _state.value.isPremium || !_state.value.adsAllowed) {
            onFinished()
            return
        }
        val sinceLastAd = System.currentTimeMillis() - preferences.getLong(LAST_INTERSTITIAL_AT_KEY, 0L)
        val ad = interstitialAd
        if (sinceLastAd < MIN_INTERSTITIAL_GAP_MILLIS || ad == null) {
            if (ad == null) loadInterstitial()
            onFinished()
            return
        }
        preferences.edit()
            .putLong(LAST_INTERSTITIAL_AT_KEY, System.currentTimeMillis())
            .apply()
        interstitialShowing = true
        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialShowing = false
                loadInterstitial()
                onFinished()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialShowing = false
                loadInterstitial()
                onFinished()
            }
        }
        ad.show(activity)
    }

    private fun updateConsentState() {
        val adsAllowed = consentInformation.canRequestAds() &&
            !_state.value.isPremium &&
            (BuildConfig.DEBUG || BuildConfig.MONETIZATION_CONFIGURED)
        _state.update {
            it.copy(
                adsAllowed = adsAllowed,
                privacyOptionsRequired = consentInformation.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
            )
        }
        if (adsAllowed && !adsInitialized) {
            adsInitialized = true
            MobileAds.initialize(context) { loadInterstitial() }
        } else if (adsAllowed && interstitialAd == null) {
            loadInterstitial()
        }
    }

    private fun loadInterstitial() {
        if (interstitialLoading || interstitialAd != null || !_state.value.adsAllowed) return
        interstitialLoading = true
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            com.google.android.gms.ads.AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialLoading = false
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialLoading = false
                    interstitialAd = null
                    AppLog.warn("interstitial-load")
                }
            },
        )
    }

    private companion object {
        const val LAST_INTERSTITIAL_AT_KEY = "last_interstitial_at"

        /**
         * The floor between two full-screen ads.
         *
         * A match that is finished or abandoned is now an ad every time, which is what the
         * owner asked for and is the placement AdMob considers natural — the player is leaving
         * the board either way. This is only here to stop the one sequence that would put two
         * ads a few seconds apart: a match ends, the player takes the ad on the way out of the
         * victory screen, starts another and leaves it at once. A minute is longer than that
         * sequence and far shorter than any real game, so it never costs an ad anyone played
         * for.
         */
        const val MIN_INTERSTITIAL_GAP_MILLIS = 60_000L
    }
}
