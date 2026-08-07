package com.duzman46.gridbound.leaderboard

import com.duzman46.gridbound.leaderboard.data.holdsBoardPlace
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who a board is allowed to list, decided from a profile that was fetched by id.
 *
 * The all-time board is an ordered query and can leave guests out through the index it is
 * sorted by. The friends board and a player's own standing cannot — they fetch named profiles
 * one at a time — so for them the exclusion has to be a question asked of each profile. Which
 * question is the whole of this file.
 *
 * Asking "is this profile in the board's index?" is the wrong one, and it is the regression
 * these tests pin. That index is a copy of the rating written after a rated match or when a
 * credential is linked, so every profile older than the copy carries none — and reading its
 * absence as anonymity answered "guest" for accounts that plainly were not one, which emptied
 * the friends board and told signed-in players they were not signed in. Note what the function
 * being tested is not given: nothing about the index at all.
 */
class BoardPlaceTest {

    @Test
    fun `a linked account holds a place, whatever the index currently knows about it`() {
        assertTrue(holdsBoardPlace("GOOGLE"))
        assertTrue(holdsBoardPlace("EMAIL"))
    }

    @Test
    fun `an anonymous one does not`() {
        assertFalse(holdsBoardPlace("GUEST"))
    }

    @Test
    fun `a profile that cannot say what it is is treated as anonymous`() {
        // The safe way round. Letting an unreadable profile onto the table would make the one
        // field that keeps guests off it optional in practice.
        assertFalse(holdsBoardPlace(null))
        assertFalse(holdsBoardPlace(""))
        assertFalse(holdsBoardPlace("SOMETHING_ELSE"))
    }
}
