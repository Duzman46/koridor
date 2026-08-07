package com.duzman46.gridbound.online

import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.online.data.RoomCredentials
import com.duzman46.gridbound.online.model.MatchmakingEntry
import com.duzman46.gridbound.online.model.MatchmakingRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pairing decision, which two phones reach separately and have to agree on.
 *
 * Every case here is really the same question asked from both sides: given one list, does
 * exactly one of the two devices decide to write the room? A test that only asks one side
 * would pass on a rule that pairs nobody, and on one that pairs everybody twice.
 */
class MatchmakingTest {

    // --- Who pairs with whom ------------------------------------------------------------

    @Test
    fun `an empty list pairs nobody`() {
        assertNull(MatchmakingRules.partnerFor("alice", emptyList()))
    }

    @Test
    fun `waiting alone pairs nobody`() {
        assertNull(MatchmakingRules.partnerFor("alice", listOf(entry("alice", 1000, 10))))
    }

    @Test
    fun `a player not in the list pairs nobody`() {
        // The list is read before this device's own entry has come back from the server.
        // Acting on it then would pair somebody using a rating and a stamp nobody agreed to.
        val waiting = listOf(entry("bob", 1000, 10), entry("carol", 1010, 20))
        assertNull(MatchmakingRules.partnerFor("alice", waiting))
    }

    @Test
    fun `two waiting players pair with each other, and only one of them writes`() {
        val waiting = listOf(entry("alice", 1000, 10), entry("bob", 1010, 20))
        assertNull(MatchmakingRules.partnerFor("alice", waiting))
        assertEquals("alice", MatchmakingRules.partnerFor("bob", waiting)?.userId)
    }

    @Test
    fun `the newer entry is the one that writes`() {
        // The device that has just looked at the list is the one that acts on it; the player
        // already waiting is watching and does nothing. Either rule pairs them — this one
        // saves a round trip, because the newcomer acts on the read it has just done.
        val waiting = listOf(entry("alice", 1000, 50), entry("bob", 1000, 20))
        assertEquals("bob", MatchmakingRules.partnerFor("alice", waiting)?.userId)
        assertNull(MatchmakingRules.partnerFor("bob", waiting))
    }

    @Test
    fun `entries stamped in the same millisecond still settle on one writer`() {
        val waiting = listOf(entry("alice", 1000, 20), entry("bob", 1000, 20))
        assertEquals("alice", MatchmakingRules.partnerFor("bob", waiting)?.userId)
        assertNull(MatchmakingRules.partnerFor("alice", waiting))
    }

    @Test
    fun `the closest rating wins over the closest arrival`() {
        val waiting = listOf(
            entry("alice", 1000, 30),
            entry("bob", 1400, 20),
            entry("carol", 1010, 10),
        )
        assertEquals("carol", MatchmakingRules.partnerFor("alice", waiting)?.userId)
    }

    @Test
    fun `a player nobody chose back does not pair`() {
        // The middle rating is nearest to both of the others, and choosing your own nearest
        // and writing would have both of them drag the same player into a match. Only a
        // mutual choice is acted on, so the third waits for someone else.
        val waiting = listOf(
            entry("alice", 1000, 10),
            entry("bob", 1005, 20),
            entry("carol", 1010, 30),
        )
        // bob and carol are five apart, alice and bob are five apart; the tie is settled by
        // the older entry, so bob's nearest is alice and carol is left over.
        assertEquals("alice", MatchmakingRules.partnerFor("bob", waiting)?.userId)
        assertNull(MatchmakingRules.partnerFor("carol", waiting))
        assertNull(MatchmakingRules.partnerFor("alice", waiting))
    }

    @Test
    fun `exactly one pair forms however the list is ordered`() {
        val waiting = listOf(
            entry("alice", 1200, 40),
            entry("bob", 1000, 10),
            entry("carol", 1205, 20),
            entry("dave", 1600, 30),
        )
        val writers = waiting.map(MatchmakingEntry::userId)
            .filter { MatchmakingRules.partnerFor(it, waiting) != null }
        assertEquals(listOf("alice"), writers)
        assertEquals("carol", MatchmakingRules.partnerFor("alice", waiting)?.userId)
        // Shuffling the list is a different device's view of the same data, and the answer
        // has to survive it or the two phones write two rooms.
        val shuffled = waiting.reversed()
        assertEquals("carol", MatchmakingRules.partnerFor("alice", shuffled)?.userId)
    }

    @Test
    fun `an entry left over from a phone that never came back is ignored`() {
        // Ages are measured against this player's own stamp, both of which the server wrote,
        // so a handset with a wrong clock still discounts the same entries as everyone else.
        val stale = Constants.Online.MATCHMAKING_STALE_MILLIS
        val waiting = listOf(
            entry("alice", 1000, stale + 1_000),
            entry("ghost", 1000, 0),
        )
        assertNull(MatchmakingRules.partnerFor("alice", waiting))
    }

    // --- The room a pairing lands in ----------------------------------------------------

    @Test
    fun `a meeting code is a valid room code`() {
        val code = RoomCredentials.meetingCode("alice", 1_700_000_000_000L)
        assertTrue(RoomCredentials.isValidCode(code))
        assertEquals(Constants.Online.ROOM_CODE_LENGTH, code.length)
    }

    @Test
    fun `both phones derive the same room for the same player`() {
        assertEquals(
            RoomCredentials.meetingCode("alice", 1_700_000_000_000L),
            RoomCredentials.meetingCode("alice", 1_700_000_000_000L),
        )
    }

    @Test
    fun `each player has a room of their own`() {
        // Two devices claiming the same player must collide, and two claiming different
        // players must not — that is the whole of the guard against being claimed twice.
        assertNotEquals(
            RoomCredentials.meetingCode("alice", 1_700_000_000_000L),
            RoomCredentials.meetingCode("bob", 1_700_000_000_000L),
        )
    }

    @Test
    fun `queueing again never aims at the room the last attempt left behind`() {
        assertNotEquals(
            RoomCredentials.meetingCode("alice", 1_700_000_000_000L),
            RoomCredentials.meetingCode("alice", 1_700_000_000_001L),
        )
    }

    private fun entry(userId: String, rating: Int, queuedAt: Long) =
        MatchmakingEntry(userId = userId, rating = rating, ranked = true, queuedAt = queuedAt)
}
