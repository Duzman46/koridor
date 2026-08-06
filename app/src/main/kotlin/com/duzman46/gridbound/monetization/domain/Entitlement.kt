package com.duzman46.gridbound.monetization.domain

import com.duzman46.gridbound.BuildConfig

/**
 * Something a purchase unlocks.
 *
 * Everything here is cosmetic or convenience. Nothing on this list changes the rules, the
 * board, the clock or the AI, so a paying player never has an advantage over one who does
 * not pay — the game stays decided by play alone.
 */
enum class Entitlement {
    /** Removes banner and interstitial advertising. The only thing this game sells. */
    REMOVE_ADS,
}

/** How Play treats the product behind an entitlement. */
enum class ProductKind {
    /** Bought once and owned forever; must be acknowledged, never consumed. */
    NON_CONSUMABLE,

    /** Can be bought again after being used up; must be consumed. */
    CONSUMABLE,

    /** Recurring. Queried and acknowledged through the subscription product type. */
    SUBSCRIPTION,
}

/**
 * A purchasable item.
 *
 * @param productId the id as defined in the Play Console. Empty means the product has not
 *   been set up yet, and it is then hidden everywhere rather than offered as a dead button.
 */
data class StoreProduct(
    val productId: String,
    val entitlement: Entitlement,
    val kind: ProductKind,
) {
    val isConfigured: Boolean get() = productId.isNotBlank()
}

/**
 * The single place product ids live.
 *
 * Ids come from monetization.properties through BuildConfig, never hard-coded, so a store
 * change is a configuration change. A product whose id is not configured is not offered:
 * that is what stops the app shipping a purchase flow against an id Play does not know.
 */
object ProductCatalog {

    val products: List<StoreProduct> = listOf(
        StoreProduct(
            productId = BuildConfig.PREMIUM_PRODUCT_ID,
            entitlement = Entitlement.REMOVE_ADS,
            kind = ProductKind.NON_CONSUMABLE,
        ),
    )

    /** Only products Play actually knows about. */
    val configured: List<StoreProduct> = products.filter(StoreProduct::isConfigured)

    val inAppProductIds: List<String> = configured
        .filter { it.kind != ProductKind.SUBSCRIPTION }
        .map(StoreProduct::productId)

    val subscriptionProductIds: List<String> = configured
        .filter { it.kind == ProductKind.SUBSCRIPTION }
        .map(StoreProduct::productId)

    fun forProductId(productId: String): StoreProduct? =
        configured.firstOrNull { it.productId == productId }

    fun forEntitlement(entitlement: Entitlement): StoreProduct? =
        configured.firstOrNull { it.entitlement == entitlement }

    /** True when there is anything at all to sell. Drives whether the store is reachable. */
    val hasAnythingToSell: Boolean get() = configured.isNotEmpty()
}
