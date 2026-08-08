package com.duzman46.gridbound.monetization

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the billing layer has to start reading a player's entitlements.
 *
 * Nothing else feeds them: the state the app decides "this player has paid" from is written by
 * one reader and by nothing on the purchase path, so a call that starts no reader leaves a
 * player who has paid looking at ads for the whole process while the purchase sits correctly
 * on disk. That is what happened on every launch that went straight into guest play — the
 * first call of a process carries null, it was judged against a field that also starts null,
 * and "no change" was the wrong answer to a question nobody had asked yet.
 */
class BillingAccountSwitchTest {

    private val account = "alice-uid"

    @Test
    fun `the first call of a process starts a reader even though nothing changed`() {
        val switch = AccountSwitch.of(reading = false, current = null, next = null)
        assertTrue(switch.restartsReader)
    }

    @Test
    fun `and nothing is carried over on it, because nothing has moved yet`() {
        // Launching straight into a signed-in account is null then the account id, with no
        // guest in between: treating that as a link would sweep the guest bucket into it.
        assertFalse(AccountSwitch.of(reading = false, current = null, next = account).migratesGuestPurchases)
    }

    @Test
    fun `a repeated call for the same account changes nothing`() {
        assertFalse(AccountSwitch.of(reading = true, current = account, next = account).restartsReader)
        assertFalse(AccountSwitch.of(reading = true, current = null, next = null).restartsReader)
    }

    @Test
    fun `a guest linking an account keeps what they bought as one`() {
        val switch = AccountSwitch.of(reading = true, current = null, next = account)
        assertTrue(switch.restartsReader)
        assertTrue(switch.migratesGuestPurchases)
    }

    @Test
    fun `signing out starts a reader on the guest bucket and carries nothing back`() {
        val switch = AccountSwitch.of(reading = true, current = account, next = null)
        assertTrue(switch.restartsReader)
        assertFalse(switch.migratesGuestPurchases)
    }

    @Test
    fun `swapping one account for another never carries entitlements across`() {
        val switch = AccountSwitch.of(reading = true, current = account, next = "bob-uid")
        assertTrue(switch.restartsReader)
        assertFalse(switch.migratesGuestPurchases)
    }
}
