package com.duzman46.gridbound.online

import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.online.data.RoomCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two room codes that are derived rather than drawn.
 *
 * Both are locks: everybody who could write the room computes the same code, aims at the same
 * node, and the transaction there settles which of them gets it. A lock only works if the
 * derivation agrees on every device, so the properties below are the whole of what makes them
 * one lock rather than two rooms.
 */
class RoomCredentialsTest {

    private val alice = "alice-uid"
    private val bob = "bob-uid"

    @Test
    fun `both players derive the same rematch room, whichever of them is asking`() {
        // This is the defect: two players pressing rematch in the same second used to open a
        // code each, cross their invitations, and end up on a board apiece opposite a rival
        // who never arrives. Neither device knows which of the pair the other calls "me", so
        // the two ids have to fold in an order that does not depend on the answer.
        assertEquals(
            RoomCredentials.rematchCode("PLAYED", alice, bob),
            RoomCredentials.rematchCode("PLAYED", bob, alice),
        )
    }

    @Test
    fun `a different match is a different room`() {
        assertNotEquals(
            RoomCredentials.rematchCode("PLAY01", alice, bob),
            RoomCredentials.rematchCode("PLAY02", alice, bob),
        )
    }

    @Test
    fun `and a different pair is too`() {
        assertNotEquals(
            RoomCredentials.rematchCode("PLAYED", alice, bob),
            RoomCredentials.rematchCode("PLAYED", alice, "carol-uid"),
        )
    }

    @Test
    fun `a rematch code is a room code the join path will accept`() {
        val code = RoomCredentials.rematchCode("PLAYED", alice, bob)
        assertTrue(RoomCredentials.isValidCode(code))
        assertEquals(code, RoomCredentials.normalizeCode(code))
    }

    @Test
    fun `a rematch never lands in the room a pairing would have used`() {
        // The two derivations share an alphabet and a length, and both are looked up as plain
        // room codes, so they have to be told apart by their seed rather than by where they
        // are stored.
        assertNotEquals(
            RoomCredentials.rematchCode("PLAYED", alice, bob),
            RoomCredentials.meetingCode(alice, 1L),
        )
    }

    @Test
    fun `the queue code still folds exactly as it did`() {
        // The worker computes this one too, character for character, and a change here that
        // nothing noticed would leave the backstop pairing couples the phones already had.
        val code = RoomCredentials.meetingCode(alice, 1_700_000_000_000L)
        assertEquals(Constants.Online.ROOM_CODE_LENGTH, code.length)
        assertTrue(code.all(Constants.Online.ROOM_CODE_ALPHABET::contains))
        assertEquals(code, RoomCredentials.meetingCode(alice, 1_700_000_000_000L))
    }
}
