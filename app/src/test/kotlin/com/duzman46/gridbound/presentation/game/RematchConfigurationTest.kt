package com.duzman46.gridbound.presentation.game

import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.online.model.OnlineGameMode
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineRoomStatus
import com.duzman46.gridbound.online.model.RoomConfiguration
import com.duzman46.gridbound.online.model.RoomTiming
import com.duzman46.gridbound.online.model.RoomVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a rematch is played under, which is supposed to be what the first game was played under.
 *
 * The room the rematch is built from is read anyway — the rating half of the answer comes off it
 * — and everything that was not read off it was silently replaced by a default. The move clock is
 * the one a player chooses deliberately and the one they feel, so it is the one asserted hardest
 * here: a room with no limit came back at sixty seconds and either player could then lose the
 * rerun on a clock they had agreed not to play against.
 *
 * These are model assertions rather than repository ones on purpose. Everything the defect turned
 * on happens before Firebase is reached, so it can be settled without a device.
 */
class RematchConfigurationTest {

    @Test
    fun `a room with no move clock is rerun with no move clock`() {
        val configuration = RoomConfiguration.rematchOf(
            played = room(timing = RoomTiming.UNLIMITED),
            hostSeat = PlayerId.PLAYER_TWO,
        )

        assertEquals(RoomTiming.UNLIMITED, configuration.timing)
        assertFalse(configuration.timing.hasTurnLimit)
    }

    @Test
    fun `and every other clock is carried across at the length it was`() {
        // Both directions of the same failure: the default sat in the middle of the options, so
        // a long room was halved and a short one doubled.
        RoomTiming.TURN_OPTIONS.forEach { seconds ->
            val configuration = RoomConfiguration.rematchOf(
                played = room(timing = RoomTiming(seconds)),
                hostSeat = PlayerId.PLAYER_ONE,
            )

            assertEquals(seconds, configuration.timing.turnDurationSeconds)
        }
    }

    @Test
    fun `the rating of the match is carried, and an unreadable room is not rated`() {
        assertTrue(RoomConfiguration.rematchOf(room(ranked = true), PlayerId.PLAYER_ONE).ranked)
        assertFalse(RoomConfiguration.rematchOf(room(ranked = false), PlayerId.PLAYER_ONE).ranked)
        // Null is the played room failing to decode. Nothing that cannot be shown to have been
        // rated is allowed to move a rating.
        assertFalse(RoomConfiguration.rematchOf(null, PlayerId.PLAYER_ONE).ranked)
    }

    @Test
    fun `a room that could not be read still opens, on the defaults`() {
        // The alternative is refusing the rematch outright, which is a worse answer to a read
        // that may simply have been slow.
        val configuration = RoomConfiguration.rematchOf(null, PlayerId.PLAYER_TWO)

        assertEquals(RoomTiming(), configuration.timing)
        assertEquals(OnlineGameMode.CLASSIC, configuration.gameMode)
        assertEquals(PlayerId.PLAYER_TWO, configuration.hostSeat)
    }

    @Test
    fun `the seat is the caller's to name, and the room is never listed or locked`() {
        // The seat is given up rather than kept: blue opens, and a rematch that handed the first
        // move back to whoever asked for it would be a rematch on better terms than the match.
        val configuration = RoomConfiguration.rematchOf(
            played = room(visibility = RoomVisibility.PUBLIC),
            hostSeat = PlayerId.PLAYER_TWO,
        )

        assertEquals(PlayerId.PLAYER_TWO, configuration.hostSeat)
        // Private however the played room was listed: it is opened for one named opponent, so a
        // browser entry is only a way for a stranger to take their seat.
        assertEquals(RoomVisibility.PRIVATE, configuration.visibility)
        assertFalse(configuration.hasPassword)
        assertEquals(null, configuration.validate())
    }

    private fun room(
        timing: RoomTiming = RoomTiming(),
        ranked: Boolean = true,
        visibility: RoomVisibility = RoomVisibility.PRIVATE,
    ): OnlineRoom = OnlineRoom(
        roomId = "AB3D5F",
        roomCode = "AB3D5F",
        roomName = "",
        hostUserId = "alice-uid",
        guestUserId = "bob-uid",
        hostName = "alice",
        hostRating = 1000,
        visibility = visibility,
        status = OnlineRoomStatus.FINISHED,
        gameMode = OnlineGameMode.CLASSIC,
        ranked = ranked,
        requiresPassword = false,
        createdAt = 1L,
        expiresAt = 2L,
        timing = timing,
        currentTurnUserId = "",
        boardState = BoardState.initial(),
        lastMoveAt = 1L,
        winnerUserId = "alice-uid",
        endReason = null,
        hostSeat = PlayerId.PLAYER_ONE,
        version = 4L,
    )
}
