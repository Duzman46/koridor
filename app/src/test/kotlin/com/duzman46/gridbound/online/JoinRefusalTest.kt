package com.duzman46.gridbound.online

import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.online.data.joinRefusal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a player is told when taking a seat does not work.
 *
 * The two ways a join fails look identical from the outside and mean opposite things. A
 * transaction whose handler stood down — the room went, or somebody was quicker — reports
 * itself as not committed. One the server refused arrives as an exception instead, which is
 * what a wrong room password is: the rules compare the hash the joiner supplies against the
 * stored secret and turn the write down. Letting that unwind reported it as "something went
 * wrong", so a player with a typo had no reason to suspect the password.
 */
class JoinRefusalTest {

    @Test
    fun `a seat taken is no refusal at all`() {
        assertNull(joinRefusal(committed = true, offered = true, requiresPassword = false))
        assertNull(joinRefusal(committed = true, offered = true, requiresPassword = true))
    }

    @Test
    fun `a protected room the server turned down is a wrong password`() {
        assertEquals(
            AppError.ROOM_PASSWORD_WRONG,
            joinRefusal(committed = false, offered = true, requiresPassword = true),
        )
    }

    @Test
    fun `a room that filled up under a protected join is not`() {
        // The handler never offered the seat, so the write never reached the server and the
        // password was never judged. Saying "wrong password" here sent a player looking for a
        // typo in a room that had simply gone.
        assertEquals(
            AppError.ROOM_FULL,
            joinRefusal(committed = false, offered = false, requiresPassword = true),
        )
    }

    @Test
    fun `an open room that could not be joined reports the seat, not a password`() {
        assertEquals(
            AppError.ROOM_FULL,
            joinRefusal(committed = false, offered = false, requiresPassword = false),
        )
        assertEquals(
            AppError.ROOM_FULL,
            joinRefusal(committed = false, offered = true, requiresPassword = false),
        )
    }
}
