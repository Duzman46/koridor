package com.duzman46.gridbound.social

import com.duzman46.gridbound.social.data.FriendshipWrites
import com.duzman46.gridbound.social.domain.FriendshipAction
import com.duzman46.gridbound.social.domain.FriendshipRules
import com.duzman46.gridbound.social.domain.FriendshipStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which rows of a friendship change have to land together, and which one may be refused.
 *
 * The database grants a player write access to the other player's row only while that player
 * has not blocked them — deliberately, because a refusal there is what stops a block from
 * being detectable. A block is the one action where that same rule is aimed at the write the
 * blocker needs: clearing the other player's row is exactly what their block refuses, and a
 * multi-path update is all-or-nothing, so sending both halves together meant that being
 * blocked first made somebody unblockable.
 */
class FriendshipWritesTest {

    private val alice = "alice-uid"
    private val bob = "bob-uid"

    private fun writesFor(action: FriendshipAction, mine: FriendshipStatus) =
        FriendshipWrites.of(
            action = action,
            update = requireNotNull(
                FriendshipRules.apply(action, mine, FriendshipStatus.NONE),
            ),
            userId = alice,
            otherUserId = bob,
            now = 7L,
        )

    @Test
    fun `a block never names the other player's row in the update that must land`() {
        val writes = writesFor(FriendshipAction.BLOCK, FriendshipStatus.NONE)
        assertFalse(writes.atomic.keys.any { it.startsWith("friendships/$bob/") })
        assertEquals(
            "BLOCKED",
            writes.atomic["friendships/$alice/$bob/status"],
        )
    }

    @Test
    fun `and withdraws whatever was already in flight alongside it`() {
        val writes = writesFor(FriendshipAction.BLOCK, FriendshipStatus.NONE)
        assertTrue(writes.atomic.containsKey("invites/$alice/$bob"))
        assertEquals(null, writes.atomic["invites/$alice/$bob"])
    }

    @Test
    fun `the other player's row still goes, as a write that is allowed to fail`() {
        // Blocking a current friend has to clear their copy too, or they keep seeing the
        // blocker in their list — which advertises the block instead of hiding it.
        val writes = writesFor(FriendshipAction.BLOCK, FriendshipStatus.FRIENDS)
        assertEquals(mapOf<String, Any?>("friendships/$bob/$alice" to null), writes.mirror)
    }

    @Test
    fun `every other action still travels as one all-or-nothing update`() {
        // A request aimed at somebody who has blocked you is refused on their row, and having
        // that refusal take the whole update down is what keeps the block invisible.
        val writes = writesFor(FriendshipAction.SEND_REQUEST, FriendshipStatus.NONE)
        assertTrue(writes.mirror.isEmpty())
        assertEquals("REQUEST_SENT", writes.atomic["friendships/$alice/$bob/status"])
        assertEquals("REQUEST_RECEIVED", writes.atomic["friendships/$bob/$alice/status"])
    }

    @Test
    fun `clearing a relationship removes the row rather than storing a value for it`() {
        val writes = writesFor(FriendshipAction.REMOVE, FriendshipStatus.FRIENDS)
        assertEquals(
            mapOf<String, Any?>(
                "friendships/$alice/$bob" to null,
                "friendships/$bob/$alice" to null,
            ),
            writes.atomic,
        )
        assertTrue(writes.mirror.isEmpty())
    }
}
