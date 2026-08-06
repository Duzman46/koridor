package com.duzman46.gridbound.monetization

import com.duzman46.gridbound.BuildConfig
import com.duzman46.gridbound.monetization.domain.Entitlement
import com.duzman46.gridbound.monetization.domain.ProductCatalog
import com.duzman46.gridbound.monetization.domain.ProductKind
import com.duzman46.gridbound.monetization.domain.PurchaseRecord
import com.duzman46.gridbound.monetization.domain.PurchaseState
import com.duzman46.gridbound.monetization.domain.StoreOffer
import com.duzman46.gridbound.monetization.domain.StoreProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseModelTest {

    // --- Catalog ----------------------------------------------------------------------

    @Test
    fun `an unconfigured product is never offered`() {
        // This is what stops a purchase flow being shown against an id Play does not know.
        val unconfigured = StoreProduct("", Entitlement.REMOVE_ADS, ProductKind.NON_CONSUMABLE)
        assertFalse(unconfigured.isConfigured)
        assertTrue(ProductCatalog.configured.all(StoreProduct::isConfigured))
    }

    @Test
    fun `every entitlement maps to at most one product`() {
        val entitlements = ProductCatalog.products.map(StoreProduct::entitlement)
        assertEquals(entitlements.size, entitlements.distinct().size)
    }

    @Test
    fun `lookup by product id only finds configured products`() {
        ProductCatalog.configured.forEach { product ->
            assertEquals(product, ProductCatalog.forProductId(product.productId))
        }
        assertNull(ProductCatalog.forProductId("not-a-real-product"))
        assertNull(ProductCatalog.forProductId(""))
    }

    @Test
    fun `the ads product is configured by default`() {
        // PREMIUM_PRODUCT_ID defaults to remove_ads, so there is always something to sell.
        assertTrue(BuildConfig.PREMIUM_PRODUCT_ID.isNotBlank())
        assertEquals(
            Entitlement.REMOVE_ADS,
            ProductCatalog.forProductId(BuildConfig.PREMIUM_PRODUCT_ID)?.entitlement,
        )
    }

    // --- Purchase state ---------------------------------------------------------------

    @Test
    fun `only an owned purchase grants entitlements`() {
        assertTrue(record(PurchaseState.OWNED).grantsEntitlements)
        listOf(PurchaseState.PENDING, PurchaseState.CANCELLED, PurchaseState.FAILED).forEach {
            assertFalse("$it should grant nothing", record(it).grantsEntitlements)
        }
    }

    @Test
    fun `a pending purchase unlocks nothing yet`() {
        // Waiting on a slow payment method is normal, not a failure, and not an unlock.
        val pending = record(PurchaseState.PENDING)
        assertTrue(pending.entitlements.isEmpty())
        assertFalse(pending.needsAcknowledgement)
    }

    @Test
    fun `an owned purchase maps its products onto entitlements`() {
        val owned = record(PurchaseState.OWNED)
        assertEquals(setOf(Entitlement.REMOVE_ADS), owned.entitlements)
    }

    @Test
    fun `an unknown product grants nothing`() {
        val unknown = record(PurchaseState.OWNED).copy(productIds = listOf("mystery"))
        assertTrue(unknown.entitlements.isEmpty())
    }

    @Test
    fun `an unacknowledged purchase is flagged for acknowledgement`() {
        // Play refunds a non-consumable that is not acknowledged within three days.
        assertTrue(record(PurchaseState.OWNED, acknowledged = false).needsAcknowledgement)
        assertFalse(record(PurchaseState.OWNED, acknowledged = true).needsAcknowledgement)
    }

    // --- Offers -----------------------------------------------------------------------

    @Test
    fun `an owned or pending offer cannot be bought again`() {
        assertFalse(offer(isOwned = true).isPurchasable)
        assertFalse(offer(isPending = true).isPurchasable)
        assertTrue(offer().isPurchasable)
    }

    @Test
    fun `an offer without a price cannot be bought`() {
        // No price means Play has not returned details, so the flow would fail anyway.
        assertFalse(offer(price = null).isPurchasable)
    }

    // --- What may be sold ---------------------------------------------------------------

    @Test
    fun `the only thing on sale is removing ads`() {
        // A guard on the design commitment. Board skins used to be sold too; they were
        // removed because a cosmetic store earned little and cost a whole screen, a purchase
        // flow and an entitlement per theme. Anything added back here must still be unable to
        // affect play — no wall counts, no clocks, no AI.
        assertEquals(setOf(Entitlement.REMOVE_ADS), Entitlement.entries.toSet())
    }

    private fun record(
        state: PurchaseState,
        acknowledged: Boolean = false,
    ) = PurchaseRecord(
        purchaseToken = "token",
        productIds = listOf(BuildConfig.PREMIUM_PRODUCT_ID),
        state = state,
        isAcknowledged = acknowledged,
        orderId = "order",
        purchaseTimeMillis = 0L,
    )

    private fun offer(
        isOwned: Boolean = false,
        isPending: Boolean = false,
        price: String? = "₺29,99",
    ) = StoreOffer(
        product = StoreProduct("id", Entitlement.REMOVE_ADS, ProductKind.NON_CONSUMABLE),
        title = "Remove ads",
        description = "",
        formattedPrice = price,
        isOwned = isOwned,
        isPending = isPending,
    )
}
