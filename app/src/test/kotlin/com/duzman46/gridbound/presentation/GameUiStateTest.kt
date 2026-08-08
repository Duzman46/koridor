package com.duzman46.gridbound.presentation

import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.GameStatus
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.online.model.MatchChatEntry
import com.duzman46.gridbound.online.model.MatchMessage
import com.duzman46.gridbound.presentation.game.GameUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameUiStateTest {

    // --- Who won ----------------------------------------------------------------------

    @Test
    fun `a board that decided itself carries the result`() {
        val state = GameUiState(boardState = won(GameStatus.PLAYER_TWO_WON))
        assertEquals(PlayerId.PLAYER_TWO, state.winner)
    }

    @Test
    fun `a match the room ended still has a winner`() {
        // Resignations, walk-outs and clocks running down leave the position untouched, so
        // without this the screen would sit on a board nobody is going to move again.
        val state = online(onlineWinner = PlayerId.PLAYER_ONE)
        assertEquals(PlayerId.PLAYER_ONE, state.winner)
    }

    @Test
    fun `a match still being played has none`() {
        assertNull(online().winner)
    }

    // --- What leaving costs -----------------------------------------------------------

    @Test
    fun `walking out of a live online match costs it`() {
        assertTrue(online().leavingForfeits)
    }

    @Test
    fun `walking out of a bot or hot-seat game costs nothing`() {
        assertFalse(GameUiState(mode = GameMode.VS_AI).leavingForfeits)
        assertFalse(GameUiState(mode = GameMode.LOCAL_TWO_PLAYER).leavingForfeits)
    }

    @Test
    fun `a room still waiting for an opponent is nobody's match to lose`() {
        // Nothing has started, so there is no rival to award it to and the room is simply
        // released. isOnlineConnected is what tells the two apart.
        assertFalse(online(connected = false).leavingForfeits)
    }

    @Test
    fun `a match that is already over cannot be forfeited again`() {
        assertFalse(online(onlineWinner = PlayerId.PLAYER_TWO).leavingForfeits)
        assertFalse(online().copy(boardState = won(GameStatus.PLAYER_ONE_WON)).leavingForfeits)
    }

    // --- Canned messages --------------------------------------------------------------

    @Test
    fun `only an online match carries messages`() {
        // A bot has nothing to say and the second player on a shared handset is in the room.
        assertTrue(online().showsMatchMessages)
        assertFalse(GameUiState(mode = GameMode.VS_AI).showsMatchMessages)
        assertFalse(GameUiState(mode = GameMode.LOCAL_TWO_PLAYER).showsMatchMessages)
    }

    @Test
    fun `switching them off takes away both halves`() {
        val muted = online().copy(matchMessagesEnabled = false)
        assertFalse(muted.showsMatchMessages)
        assertFalse(muted.canSendMessage)
    }

    @Test
    fun `nothing can be said to a match that is over`() {
        assertTrue(online().canSendMessage)
        assertFalse(online(onlineWinner = PlayerId.PLAYER_TWO).canSendMessage)
        assertFalse(online().copy(boardState = won(GameStatus.PLAYER_ONE_WON)).canSendMessage)
    }

    @Test
    fun `each seat's last message is told apart by who wrote it`() {
        val state = online().copy(
            localUserId = "mine",
            chat = listOf(
                MatchChatEntry("theirs", MatchMessage.GOOD_LUCK, 1L),
                MatchChatEntry("mine", MatchMessage.THANKS, 2L),
            ),
        )

        assertEquals(MatchMessage.GOOD_LUCK, state.rivalMessage?.message)
        assertEquals(MatchMessage.THANKS, state.ownMessage?.message)
    }

    // --- What each half of the row draws ------------------------------------------------

    @Test
    fun `a phrase is spelled out for the player who sent it as much as for the one reading it`() {
        // The reported fault: the sender's own bubble was drawn without its words, so eight
        // of the fourteen messages reached them as a bare glyph while the opponent read the
        // phrase. What a message says does not depend on who is looking at it.
        val state = talking()

        assertTrue(state.rivalBubble!!.showsWords)
        assertTrue(state.ownBubble!!.showsWords)
    }

    @Test
    fun `a face carries no words on either side`() {
        val state = online().copy(
            localUserId = "mine",
            chat = listOf(
                MatchChatEntry("theirs", MatchMessage.WOW, 1L),
                MatchChatEntry("mine", MatchMessage.SMILE, 2L),
            ),
        )

        assertFalse(state.rivalBubble!!.showsWords)
        assertFalse(state.ownBubble!!.showsWords)
    }

    @Test
    fun `each half is drawn from the message that seat actually sent`() {
        val state = talking()

        assertEquals(MatchMessage.GOOD_LUCK, state.rivalBubble?.entry?.message)
        assertEquals(MatchMessage.THANKS, state.ownBubble?.entry?.message)
    }

    // --- Muting a match -----------------------------------------------------------------

    @Test
    fun `muting silences the rival and nothing else`() {
        val muted = talking().copy(matchMessagesMuted = true)

        assertNull(muted.rivalBubble)
        // The row has to survive it: it is where the mute was reached from and the only way
        // back. So does speaking, which nobody asked to be relieved of.
        assertTrue(muted.showsMatchMessages)
        assertTrue(muted.canSendMessage)
        assertNotNull(muted.ownBubble)
    }

    @Test
    fun `unmuting brings the rival straight back`() {
        val muted = talking().copy(matchMessagesMuted = true)

        assertNotNull(muted.copy(matchMessagesMuted = false).rivalBubble)
    }

    @Test
    fun `the setting still takes the row away, mute or no mute`() {
        // The two are different questions and only one of them is permanent. Switching the
        // preference off leaves nothing on the board at all, as it always did.
        val off = talking().copy(matchMessagesEnabled = false, matchMessagesMuted = true)

        assertFalse(off.showsMatchMessages)
        assertFalse(off.showsRivalMessages)
        assertFalse(off.canSendMessage)
        assertFalse(off.canOpenMessages)
    }

    @Test
    fun `a mute outlives the connection and the match, so the way to lift it has to as well`() {
        // The reported fault. The only control that clears a mute lives behind a button gated
        // on being able to send, and every one of those conditions is about a message reaching
        // somebody. A player who muted and then hit a blip — or simply played the match out —
        // was left looking at a mute with nothing to press.
        val muted = talking().copy(matchMessagesMuted = true)
        val blip = muted.copy(isOnlineConnected = false)
        val over = muted.copy(onlineWinner = PlayerId.PLAYER_TWO)
        val decided = muted.copy(boardState = won(GameStatus.PLAYER_ONE_WON))

        assertFalse(blip.canSendMessage)
        assertTrue(blip.canOpenMessages)
        assertTrue(over.canOpenMessages)
        assertTrue(decided.canOpenMessages)
    }

    @Test
    fun `a player with nothing to lift is not offered a message that cannot be sent`() {
        // The other half of the rule. Opening the sheet is only ever worth it for one of two
        // reasons, and an unmuted player out of contact has neither.
        assertTrue(online().canOpenMessages)
        assertFalse(online(connected = false).canOpenMessages)
        assertFalse(online(onlineWinner = PlayerId.PLAYER_TWO).canOpenMessages)
    }

    /** An online match in which both seats have said a phrase. */
    private fun talking() = online().copy(
        localUserId = "mine",
        chat = listOf(
            MatchChatEntry("theirs", MatchMessage.GOOD_LUCK, 1L),
            MatchChatEntry("mine", MatchMessage.THANKS, 2L),
        ),
    )

    private fun online(connected: Boolean = true, onlineWinner: PlayerId? = null) = GameUiState(
        mode = GameMode.ONLINE,
        isOnlineConnected = connected,
        onlineWinner = onlineWinner,
    )

    private fun won(status: GameStatus) = BoardState.initial().copy(status = status)
}
