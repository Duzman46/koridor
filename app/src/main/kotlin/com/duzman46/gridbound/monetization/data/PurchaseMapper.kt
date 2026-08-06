package com.duzman46.gridbound.monetization.data

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.duzman46.gridbound.monetization.domain.PurchaseRecord
import com.duzman46.gridbound.monetization.domain.PurchaseState

/**
 * Translates Play's responses into the app's own vocabulary.
 *
 * Kept apart from the billing client so the mapping — which is where the interesting
 * distinctions live, such as pending not being a failure — can be unit tested without Play
 * Services on the classpath.
 */
object PurchaseMapper {

    fun stateOf(purchaseState: Int): PurchaseState = when (purchaseState) {
        Purchase.PurchaseState.PURCHASED -> PurchaseState.OWNED
        Purchase.PurchaseState.PENDING -> PurchaseState.PENDING
        else -> PurchaseState.FAILED
    }

    fun toRecord(purchase: Purchase): PurchaseRecord = PurchaseRecord(
        purchaseToken = purchase.purchaseToken,
        productIds = purchase.products,
        state = stateOf(purchase.purchaseState),
        isAcknowledged = purchase.isAcknowledged,
        orderId = purchase.orderId,
        purchaseTimeMillis = purchase.purchaseTime,
    )

    /**
     * How a billing response should be reported.
     *
     * A cancelled flow is the player's own decision and must not surface as an error; an
     * item already owned is a signal to restore rather than to complain.
     */
    fun outcomeOf(responseCode: Int): BillingOutcome = when (responseCode) {
        BillingClient.BillingResponseCode.OK -> BillingOutcome.OK
        BillingClient.BillingResponseCode.USER_CANCELED -> BillingOutcome.CANCELLED
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> BillingOutcome.ALREADY_OWNED
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.NETWORK_ERROR,
        -> BillingOutcome.UNAVAILABLE

        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
        -> BillingOutcome.UNSUPPORTED

        else -> BillingOutcome.FAILED
    }
}

enum class BillingOutcome {
    OK,
    CANCELLED,
    ALREADY_OWNED,
    UNAVAILABLE,
    UNSUPPORTED,
    FAILED,
    ;

    /** Whether the player should be shown a failure message at all. */
    val isWorthReporting: Boolean get() = this != OK && this != CANCELLED
}
