package com.duzman46.gridbound.social

import com.duzman46.gridbound.social.domain.FriendshipAction
import com.duzman46.gridbound.social.domain.FriendshipRules
import com.duzman46.gridbound.social.domain.FriendshipStatus
import com.duzman46.gridbound.social.domain.FriendshipStatus.BLOCKED
import com.duzman46.gridbound.social.domain.FriendshipStatus.FRIENDS
import com.duzman46.gridbound.social.domain.FriendshipStatus.NONE
import com.duzman46.gridbound.social.domain.FriendshipStatus.REQUEST_RECEIVED
import com.duzman46.gridbound.social.domain.FriendshipStatus.REQUEST_SENT
import com.duzman46.gridbound.social.domain.GameInvite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendshipRulesTest {

    // --- Requests ---------------------------------------------------------------------

    @Test
    fun `a request puts both sides in matching states`() {
        val update = FriendshipRules.apply(FriendshipAction.SEND_REQUEST, NONE, NONE)
        assertEquals(REQUEST_SENT, update?.mine)
        assertEquals(REQUEST_RECEIVED, update?.theirs)
    }

    @Test
    fun `the same request cannot be sent twice`() {
        assertNull(FriendshipRules.apply(FriendshipAction.SEND_REQUEST, REQUEST_SENT, REQUEST_RECEIVED))
    }

    @Test
    fun `a request to an existing friend does nothing`() {
        assertNull(FriendshipRules.apply(FriendshipAction.SEND_REQUEST, FRIENDS, FRIENDS))
    }

    @Test
    fun `crossed requests resolve into a friendship`() {
        // Both tapped "add" at the same time; the second one is treated as acceptance
        // rather than leaving the pair stuck.
        val update = FriendshipRules.apply(FriendshipAction.SEND_REQUEST, REQUEST_RECEIVED, REQUEST_SENT)
        assertEquals(FRIENDS, update?.mine)
        assertEquals(FRIENDS, update?.theirs)
    }

    @Test
    fun `accepting makes both sides friends`() {
        val update = FriendshipRules.apply(FriendshipAction.ACCEPT, REQUEST_RECEIVED, REQUEST_SENT)
        assertEquals(FRIENDS, update?.mine)
        assertEquals(FRIENDS, update?.theirs)
    }

    @Test
    fun `only a received request can be accepted`() {
        assertNull(FriendshipRules.apply(FriendshipAction.ACCEPT, REQUEST_SENT, REQUEST_RECEIVED))
        assertNull(FriendshipRules.apply(FriendshipAction.ACCEPT, NONE, NONE))
        assertNull(FriendshipRules.apply(FriendshipAction.ACCEPT, FRIENDS, FRIENDS))
    }

    @Test
    fun `declining clears both sides`() {
        val update = FriendshipRules.apply(FriendshipAction.DECLINE, REQUEST_RECEIVED, REQUEST_SENT)
        assertEquals(NONE, update?.mine)
        assertEquals(NONE, update?.theirs)
    }

    @Test
    fun `cancelling clears both sides`() {
        val update = FriendshipRules.apply(FriendshipAction.CANCEL, REQUEST_SENT, REQUEST_RECEIVED)
        assertEquals(NONE, update?.mine)
        assertEquals(NONE, update?.theirs)
    }

    @Test
    fun `only a sent request can be cancelled`() {
        assertNull(FriendshipRules.apply(FriendshipAction.CANCEL, REQUEST_RECEIVED, REQUEST_SENT))
    }

    @Test
    fun `removing a friend clears both sides`() {
        val update = FriendshipRules.apply(FriendshipAction.REMOVE, FRIENDS, FRIENDS)
        assertEquals(NONE, update?.mine)
        assertEquals(NONE, update?.theirs)
    }

    @Test
    fun `only a friend can be removed`() {
        assertNull(FriendshipRules.apply(FriendshipAction.REMOVE, REQUEST_SENT, REQUEST_RECEIVED))
        assertNull(FriendshipRules.apply(FriendshipAction.REMOVE, NONE, NONE))
    }

    // --- Blocking ---------------------------------------------------------------------

    @Test
    fun `blocking works from any state and clears their side`() {
        listOf(NONE, REQUEST_SENT, REQUEST_RECEIVED, FRIENDS).forEach { mine ->
            val update = FriendshipRules.apply(FriendshipAction.BLOCK, mine, NONE)
            assertNotNull("blocking should work from $mine", update)
            assertEquals(BLOCKED, update?.mine)
            assertEquals(NONE, update?.theirs)
        }
    }

    @Test
    fun `blocking someone twice is a no-op`() {
        assertNull(FriendshipRules.apply(FriendshipAction.BLOCK, BLOCKED, NONE))
    }

    @Test
    fun `a blocked player cannot be acted on except by unblocking`() {
        listOf(
            FriendshipAction.SEND_REQUEST,
            FriendshipAction.ACCEPT,
            FriendshipAction.DECLINE,
            FriendshipAction.CANCEL,
            FriendshipAction.REMOVE,
        ).forEach { action ->
            assertNull("$action should be refused while blocked", FriendshipRules.apply(action, BLOCKED, NONE))
        }
    }

    @Test
    fun `unblocking clears the relationship`() {
        val update = FriendshipRules.apply(FriendshipAction.UNBLOCK, BLOCKED, NONE)
        assertEquals(NONE, update?.mine)
        assertEquals(NONE, update?.theirs)
    }

    @Test
    fun `unblocking only applies to a blocked player`() {
        assertNull(FriendshipRules.apply(FriendshipAction.UNBLOCK, NONE, NONE))
        assertNull(FriendshipRules.apply(FriendshipAction.UNBLOCK, FRIENDS, FRIENDS))
    }

    @Test
    fun `someone who blocked me can never be reached`() {
        // Every action fails, and none of them reveals that a block is the reason.
        FriendshipAction.entries
            .filter { it != FriendshipAction.BLOCK }
            .forEach { action ->
                assertNull(
                    "$action should fail when they have blocked me",
                    FriendshipRules.apply(action, NONE, BLOCKED),
                )
            }
    }

    @Test
    fun `I can still block someone who blocked me`() {
        val update = FriendshipRules.apply(FriendshipAction.BLOCK, NONE, BLOCKED)
        assertEquals(BLOCKED, update?.mine)
    }

    // --- Gates ------------------------------------------------------------------------

    @Test
    fun `a blocked player cannot reach me`() {
        assertFalse(FriendshipRules.canReceiveFrom(BLOCKED))
        listOf(NONE, REQUEST_SENT, REQUEST_RECEIVED, FRIENDS).forEach {
            assertTrue(FriendshipRules.canReceiveFrom(it))
        }
    }

    @Test
    fun `only a friend can be invited`() {
        assertTrue(FriendshipRules.canInvite(FRIENDS))
        listOf(NONE, REQUEST_SENT, REQUEST_RECEIVED, BLOCKED).forEach {
            assertFalse("$it should not be invitable", FriendshipRules.canInvite(it))
        }
    }

    @Test
    fun `every state and action pair is total`() {
        // No combination may throw: the state machine must answer for all of them.
        FriendshipStatus.entries.forEach { mine ->
            FriendshipStatus.entries.forEach { theirs ->
                FriendshipAction.entries.forEach { action ->
                    FriendshipRules.apply(action, mine, theirs)
                }
            }
        }
    }

    // --- Invites ----------------------------------------------------------------------

    @Test
    fun `an invite expires after its deadline`() {
        val invite = GameInvite("id", "from", "name", "ABC234", createdAt = 0L, expiresAt = 1_000L)
        assertFalse(invite.isExpired(now = 999L))
        assertTrue(invite.isExpired(now = 1_001L))
    }

    @Test
    fun `an invite with no deadline never expires`() {
        val invite = GameInvite("id", "from", "name", "ABC234", createdAt = 0L, expiresAt = 0L)
        assertFalse(invite.isExpired(now = Long.MAX_VALUE))
    }
}
