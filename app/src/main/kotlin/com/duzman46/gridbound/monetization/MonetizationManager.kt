package com.duzman46.gridbound.monetization

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    private val _state = MutableStateFlow(MonetizationState())
    val state: StateFlow<MonetizationState> = _state.asStateFlow()

    /**
     * The three fields the ads SDK's own callbacks and this class's coroutines both touch.
     *
     * Everything here is meant to run on the main thread, and with the dispatchers named that is
     * what happens — but "meant to" is not a memory barrier. A plain field written on one thread
     * and read on another carries no happens-before edge, so a stale `interstitialLoading` read
     * would either start a second load or refuse a legitimate one, and a stale `interstitialAd`
     * would show an ad that has already been spent. Volatile is the cheapest way to make the
     * invariant hold rather than merely be intended; these are touched a handful of times a
     * match, so the cost is nothing.
     */
    @Volatile
    private var adsInitialized = false

    @Volatile
    private var interstitialAd: InterstitialAd? = null

    @Volatile
    private var interstitialLoading = false
    private var interstitialShowing = false
    private var interstitialRetries = 0
    private var consentRetries = 0

    init {
        // `Dispatchers.Main` for the reason spelled out on [scheduleInterstitialRetry], and this
        // is the collector that most needed it: [scope] is `Dispatchers.Default`, and an
        // entitlement that changes under this app — an account switch, a refund, a restore —
        // delivers here and goes straight on into [updateConsentState], which is
        // `MobileAds.initialize` and `InterstitialAd.load`. Both are main-thread-only entry
        // points into the ads SDK. Inheriting the scope's dispatcher meant every one of those
        // paths called them from a background thread, and a throw out of `load` leaves
        // [interstitialLoading] true forever, which retires advertising for the life of the
        // process. The two scheduled retries below already name the dispatcher; this one is the
        // path that reaches the SDK first.
        scope.launch(Dispatchers.Main) {
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
        requestConsent(activity)
    }

    /**
     * Asks the consent framework whether ads may be requested at all, and keeps asking.
     *
     * This is the switch every ad in the app hangs off: [updateConsentState] reads
     * `canRequestAds()`, and until it is true there is no banner, no interstitial and no reason
     * for the loader to run. The call reaches Google over the network, so a launch on a bad
     * connection — or on a handset whose network blocks the ad hosts — fails it.
     *
     * It used to be asked exactly once per process. One failed launch therefore meant an app
     * with no advertising at all until it was killed and reopened, which is indistinguishable
     * from the ads being broken and is the shape of the fault most likely to be reported that
     * way. The retry mirrors the interstitial's: 5, 10, 20, 40 seconds, then every 80, and it
     * stops as soon as the answer is yes or the activity it was given is gone.
     */
    private fun requestConsent(activity: Activity) {
        val request = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            request,
            {
                consentRetries = 0
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    updateConsentState()
                }
            },
            { error ->
                AppLog.warn("consent-info-update-${error.errorCode}")
                updateConsentState()
                scheduleConsentRetry(activity)
            },
        )
    }

    private fun scheduleConsentRetry(activity: Activity) {
        val attempt = consentRetries.coerceAtMost(MAX_RETRY_BACKOFF_STEPS)
        consentRetries++
        scope.launch(Dispatchers.Main) {
            delay(RETRY_BASE_MILLIS shl attempt)
            // Nothing to recover if the answer arrived some other way, and nothing to hold on
            // to if the screen that would show the form has gone.
            if (consentInformation.canRequestAds()) return@launch
            if (activity.isFinishing || activity.isDestroyed) return@launch
            requestConsent(activity)
        }
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
     * No gate at all. It counted matches and showed an ad every third, then every match with a
     * minute's floor between two; the owner asked for every match and every exit without
     * exception, and both gates are gone. The only thing that can still stop an ad is not
     * holding one — which is why [loadInterstitial] retries rather than giving up.
     *
     * [onFinished] runs in every path, so a missing or failed ad never strands the player.
     */
    fun showInterstitialAfterCompletedMatch(activity: Activity, onFinished: () -> Unit) {
        if (interstitialShowing || _state.value.isPremium || !_state.value.adsAllowed) {
            onFinished()
            return
        }
        val ad = interstitialAd
        if (ad == null) {
            // Nothing held. Ask for one so the next exit has it, and let the player through:
            // an ad that does not exist cannot be shown, and holding the player on a dead
            // screen until one arrives is both a worse game and against AdMob's own rules on
            // interrupting navigation.
            loadInterstitial()
            onFinished()
            return
        }
        // An ad can only be shown over a window that is actually in front of the player. If this
        // activity has gone — the process is being torn down, or another has taken the screen —
        // showing does nothing and the ad is spent for no impression, so the loaded one is kept
        // for the next exit instead of being thrown away on a window nobody is looking at.
        if (activity.isFinishing || activity.isDestroyed) {
            onFinished()
            return
        }
        interstitialShowing = true
        interstitialAd = null
        // Refilled here rather than on dismissal. The slot is empty from this line until the
        // player closes the ad, and the request takes a moment; asking now means it runs while
        // they are reading the ad instead of starting after it, which is the difference between
        // the next exit having one in hand and finding the slot still empty.
        loadInterstitial()
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

    /**
     * Keeps one interstitial in hand at all times.
     *
     * A request that comes back empty used to end there, and nothing asked again until the next
     * time an ad was wanted — so a single empty answer cost the very next exit its ad. A new
     * AdMob app answers empty often, because it has no history for the auction to price, so that
     * was the common case rather than the rare one.
     *
     * It now retries, backing off 5, 10, 20, 40 seconds and then every 80. Backing off matters:
     * asking again immediately for something the auction has just said it does not have is what
     * AdMob's own guidance calls out, and a tight loop on a phone with no connection would cost
     * battery for nothing. Eighty seconds is short against the length of a match, so a player
     * who starts a game with nothing in hand normally has one by the time they finish.
     */
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
                    interstitialRetries = 0
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialLoading = false
                    interstitialAd = null
                    // The code is worth carrying: 3 is an empty auction and will pass on its
                    // own, while 0 and 1 are the app's own configuration and never will.
                    AppLog.warn("interstitial-load-${error.code}")
                    scheduleInterstitialRetry()
                }
            },
        )
    }

    /**
     * [scope] is built on `Dispatchers.Default`, and every entry point into the ads SDK has to be
     * on the main thread — so the dispatcher is named here rather than inherited. Off the main
     * thread `InterstitialAd.load` is not merely discouraged: if it throws, `interstitialLoading`
     * is left true and the guard at the top of [loadInterstitial] then refuses every later
     * request for the life of the process. One missing dispatcher would cost all advertising
     * after the first empty auction.
     */
    private fun scheduleInterstitialRetry() {
        val attempt = interstitialRetries.coerceAtMost(MAX_RETRY_BACKOFF_STEPS)
        interstitialRetries++
        scope.launch(Dispatchers.Main) {
            delay(RETRY_BASE_MILLIS shl attempt)
            loadInterstitial()
        }
    }

    private companion object {
        const val RETRY_BASE_MILLIS = 5_000L

        /** Caps the backoff at eighty seconds, which is short against the length of a match. */
        const val MAX_RETRY_BACKOFF_STEPS = 4
    }
}
