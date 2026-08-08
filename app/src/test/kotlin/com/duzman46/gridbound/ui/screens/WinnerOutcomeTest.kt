package com.duzman46.gridbound.ui.screens

import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.PlayerId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which side the result screen is on.
 *
 * Both players reach it, so the answer decides the artwork as well as the words: confetti and
 * a pulsing gold trophy used to be drawn unconditionally, directly above "the move clock ran
 * out" — the screen congratulating the player in the same frame as it told them they had lost.
 */
class WinnerOutcomeTest {

    @Test
    fun `an online player beaten by the other seat has lost`() {
        assertTrue(didLose(GameMode.ONLINE, PlayerId.PLAYER_TWO, PlayerId.PLAYER_ONE))
    }

    @Test
    fun `and one whose own seat won has not`() {
        assertFalse(didLose(GameMode.ONLINE, PlayerId.PLAYER_ONE, PlayerId.PLAYER_ONE))
    }

    @Test
    fun `a bot match is read the same way, from whichever seat the player took`() {
        assertTrue(didLose(GameMode.VS_AI, PlayerId.PLAYER_ONE, PlayerId.PLAYER_TWO))
        assertFalse(didLose(GameMode.VS_AI, PlayerId.PLAYER_TWO, PlayerId.PLAYER_TWO))
    }

    @Test
    fun `a shared handset has nobody to commiserate with`() {
        // Both players are looking at the same screen and one of them has won, so there is no
        // side to take and the celebration belongs to the room.
        assertFalse(didLose(GameMode.LOCAL_TWO_PLAYER, PlayerId.PLAYER_ONE, PlayerId.PLAYER_TWO))
        assertFalse(didLose(GameMode.LOCAL_TWO_PLAYER, PlayerId.PLAYER_TWO, PlayerId.PLAYER_TWO))
    }
}
