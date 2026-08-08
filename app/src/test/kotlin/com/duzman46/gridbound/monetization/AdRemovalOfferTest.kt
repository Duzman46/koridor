package com.duzman46.gridbound.monetization

import com.duzman46.gridbound.monetization.domain.Entitlement
import com.duzman46.gridbound.monetization.domain.PurchaseRecord
import com.duzman46.gridbound.monetization.domain.PurchaseState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the app is still allowed to sell ad removal.
 *
 * Two screens draw a "Remove ads" button — the home sheet and the store card in Settings — and
 * both hide it on the same question. Left as a negation written out at each call site, one of
 * them could be changed and the other not, and the failure is a player who has paid being
 * offered the same upgrade again on the other screen. There is no way for them to read that
 * except as the money having gone nowhere.
 */
class AdRemovalOfferTest {

    @Test
    fun `an account that owns nothing is offered the upgrade`() {
        assertTrue(BillingState().offersAdRemoval)
    }

    @Test
    fun `an account that has paid is not`() {
        val paid = BillingState(entitlements = setOf(Entitlement.REMOVE_ADS))

        assertFalse(paid.offersAdRemoval)
    }

    @Test
    fun `a purchase still waiting on Play does not withdraw the offer`() {
        // Play holds a slow payment method — a bank transfer, a cash voucher — as PENDING for
        // hours or days, and it entitles nobody until it clears. Hiding the button on a
        // pending row would leave a player who never completes the payment with no way to buy
        // and no ads removed either.
        val pending = BillingState(
            entitlements = emptySet(),
            pendingPurchases = listOf(
                PurchaseRecord(
                    purchaseToken = "token",
                    productIds = listOf("remove_ads"),
                    state = PurchaseState.PENDING,
                    isAcknowledged = false,
                    orderId = null,
                    purchaseTimeMillis = 0L,
                ),
            ),
        )

        assertTrue(pending.hasPending)
        assertTrue(pending.offersAdRemoval)
    }
}
