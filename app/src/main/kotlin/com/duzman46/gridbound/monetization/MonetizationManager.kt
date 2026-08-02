package com.duzman46.gridbound.monetization

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.duzman46.gridbound.BuildConfig
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MonetizationState(
    val isPremium: Boolean = false,
    val billingReady: Boolean = false,
    val premiumPrice: String? = null,
    val adsAllowed: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
)

@Singleton
class MonetizationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : PurchasesUpdatedListener {
    private val preferences = context.getSharedPreferences("monetization", Context.MODE_PRIVATE)
    private val consentInformation = UserMessagingPlatform.getConsentInformation(context)
    private val _state = MutableStateFlow(
        MonetizationState(isPremium = preferences.getBoolean(PREMIUM_CACHE_KEY, false)),
    )
    val state: StateFlow<MonetizationState> = _state.asStateFlow()

    private var productDetails: ProductDetails? = null
    private var billingConnectionStarted = false
    private var adsInitialized = false
    private var interstitialAd: InterstitialAd? = null
    private var interstitialLoading = false
    private var interstitialShowing = false
    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    fun initialize(activity: Activity) {
        connectBilling()
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
            { updateConsentState() },
        )
    }

    fun buyPremium(activity: Activity) {
        val details = productDetails ?: return
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    fun restorePurchases() {
        if (billingClient.isReady) queryPurchases() else connectBilling()
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { updateConsentState() }
    }

    @SuppressLint("UseKtx")
    fun showInterstitialAfterCompletedMatch(activity: Activity, onFinished: () -> Unit) {
        if (interstitialShowing || _state.value.isPremium || !_state.value.adsAllowed) {
            onFinished()
            return
        }
        val completedSinceAd = preferences.getInt(COMPLETED_MATCHES_KEY, 0) + 1
        val ad = interstitialAd
        if (completedSinceAd < MATCHES_PER_INTERSTITIAL || ad == null) {
            preferences.edit().putInt(COMPLETED_MATCHES_KEY, completedSinceAd).apply()
            if (ad == null) loadInterstitial()
            onFinished()
            return
        }
        preferences.edit().putInt(COMPLETED_MATCHES_KEY, 0).apply()
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

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            updateEntitlement(purchases, definitiveResult = false)
        }
    }

    private fun connectBilling() {
        if (billingClient.isReady) {
            queryProduct()
            queryPurchases()
            return
        }
        if (billingConnectionStarted) return
        billingConnectionStarted = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                billingConnectionStarted = false
                val ready = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                _state.update { it.copy(billingReady = ready) }
                if (ready) {
                    queryProduct()
                    queryPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                billingConnectionStarted = false
                _state.update { it.copy(billingReady = false) }
            }
        })
    }

    private fun queryProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(BuildConfig.PREMIUM_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        billingClient.queryProductDetailsAsync(params) { billingResult, result ->
            productDetails = result.productDetailsList.firstOrNull()
            val price = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
            _state.update {
                it.copy(
                    billingReady = billingResult.responseCode == BillingClient.BillingResponseCode.OK,
                    premiumPrice = price,
                )
            }
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                updateEntitlement(purchases, definitiveResult = true)
            }
        }
    }

    private fun updateEntitlement(purchases: List<Purchase>, definitiveResult: Boolean) {
        val purchased = purchases.filter {
            BuildConfig.PREMIUM_PRODUCT_ID in it.products &&
                it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        if (purchased.isNotEmpty()) {
            setPremium(true)
            purchased.filterNot(Purchase::isAcknowledged).forEach { purchase ->
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(params) { result ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) setPremium(true)
                }
            }
        } else if (definitiveResult) {
            setPremium(false)
        }
    }

    @SuppressLint("UseKtx")
    private fun setPremium(isPremium: Boolean) {
        preferences.edit().putBoolean(PREMIUM_CACHE_KEY, isPremium).apply()
        _state.update { it.copy(isPremium = isPremium) }
        updateConsentState()
    }

    private fun updateConsentState() {
        val canRequestAds = consentInformation.canRequestAds()
        val adsAllowed = canRequestAds && !_state.value.isPremium &&
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
                }
            },
        )
    }

    private companion object {
        const val PREMIUM_CACHE_KEY = "premium_owned"
        const val COMPLETED_MATCHES_KEY = "completed_matches_since_interstitial"
        const val MATCHES_PER_INTERSTITIAL = 1
    }
}
