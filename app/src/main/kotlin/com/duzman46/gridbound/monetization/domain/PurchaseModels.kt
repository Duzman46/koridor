package com.duzman46.gridbound.monetization.domain

import com.duzman46.gridbound.core.UiText

/**
 * Where a purchase has got to.
 *
 * Play reports these as separate outcomes and they need separate handling: a pending
 * purchase is not a failure, and a cancelled one is not an error worth alarming anyone about.
 */
enum class PurchaseState {
    /** Paid for and owned. */
    OWNED,

    /** Awaiting a slow payment method. Nothing is unlocked yet, and that is normal. */
    PENDING,

    /** The player backed out. Not an error. */
    CANCELLED,

    FAILED,
}

/**
 * A purchase as the app understands it.
 *
 * @param purchaseToken Play's identifier for the transaction. It is the deduplication key:
 *   a token may grant its entitlement exactly once, ever.
 */
data class PurchaseRecord(
    val purchaseToken: String,
    val productIds: List<String>,
    val state: PurchaseState,
    val isAcknowledged: Boolean,
    val orderId: String?,
    val purchaseTimeMillis: Long,
) {
    val grantsEntitlements: Boolean get() = state == PurchaseState.OWNED

    val entitlements: Set<Entitlement>
        get() = if (!grantsEntitlements) {
            emptySet()
        } else {
            productIds.mapNotNull { ProductCatalog.forProductId(it)?.entitlement }.toSet()
        }

    /** A non-consumable must be acknowledged within three days or Play refunds it. */
    val needsAcknowledgement: Boolean get() = grantsEntitlements && !isAcknowledged
}

/** What the store screen shows for one product. */
data class StoreOffer(
    val product: StoreProduct,
    val title: String,
    val description: String,
    val formattedPrice: String?,
    val isOwned: Boolean,
    val isPending: Boolean,
) {
    val isPurchasable: Boolean get() = !isOwned && !isPending && formattedPrice != null
}

sealed interface BillingMessage {
    data class Info(val text: UiText) : BillingMessage
    data class Error(val text: UiText) : BillingMessage
}
