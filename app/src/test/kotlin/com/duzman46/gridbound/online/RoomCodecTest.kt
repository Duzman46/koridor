package com.duzman46.gridbound.online

import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.online.data.OnlineBoardCodec
import com.duzman46.gridbound.online.data.RoomCodec
import com.duzman46.gridbound.online.model.MatchChatEntry
import com.duzman46.gridbound.online.model.MatchMessage
import com.duzman46.gridbound.online.model.RoomConfiguration
import com.duzman46.gridbound.online.model.RoomTiming
import com.google.firebase.database.ServerValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class RoomCodecTest {
    private val boardCodec = OnlineBoardCodec()
    private val codec = RoomCodec(boardCodec)

    @Test
    fun `a room stored with a match clock still decodes`() {
        // Rooms opened before the match clock was dropped still carry it, and some of them
        // are matches being played right now. An unknown field must cost nothing.
        val room = codec.decode("ABC234", storedRoom(mapOf("totalDurationSeconds" to 900)))

        assertNotNull(room)
        assertEquals(60, room?.timing?.turnDurationSeconds)
    }

    @Test
    fun `a room stored before seats existed puts its host in the opening one`() {
        // That protocol had no way to record anything else, so the absent field is not
        // missing data — it is the answer. Any other reading would swap the colours and the
        // turn order under a match that is already being played.
        val room = codec.decode(
            "ABC234",
            storedRoom(mapOf("totalDurationSeconds" to 900)) - RoomCodec.Keys.HOST_SEAT,
        )

        assertEquals(PlayerId.PLAYER_ONE, room?.hostSeat)
        assertEquals("host-uid", room?.userFor(PlayerId.PLAYER_ONE))
        assertEquals("guest-uid", room?.userFor(PlayerId.PLAYER_TWO))
    }

    @Test
    fun `the room carries what each player last said`() {
        val room = codec.decode(
            "ABC234",
            storedRoom(
                mapOf(
                    "chat" to mapOf(
                        "guest-uid" to mapOf("key" to "GOOD_LUCK", "at" to 1_700L),
                    ),
                ),
            ),
        )

        assertEquals(1, room?.chat?.size)
        assertEquals(MatchMessage.GOOD_LUCK, room?.chat?.first()?.message)
        assertEquals("guest-uid", room?.chat?.first()?.userId)
        assertEquals(1_700L, room?.chat?.first()?.sentAt)
    }

    @Test
    fun `a message this build has no words for is dropped rather than shown`() {
        // The vocabulary can only grow, so an unrecognised key is a phone on a newer release.
        // An empty bubble would be worse than nothing, and it must not cost the whole room.
        val room = codec.decode(
            "ABC234",
            storedRoom(
                mapOf(
                    "chat" to mapOf(
                        "guest-uid" to mapOf("key" to "SOMETHING_NEWER", "at" to 1_700L),
                        "host-uid" to mapOf("key" to "THANKS", "at" to 1_800L),
                    ),
                ),
            ),
        )

        assertNotNull(room)
        assertEquals(listOf(MatchMessage.THANKS), room?.chat?.map { it.message })
    }

    @Test
    fun `a room nobody has spoken in carries no messages`() {
        assertEquals(emptyList<MatchChatEntry>(), codec.decode("ABC234", storedRoom())?.chat)
    }

    @Test
    fun `a message is sent under the server's clock`() {
        // A handset that could date its own messages could backdate them, and the gap the
        // rules enforce between two messages is measured on exactly that stamp.
        val payload = codec.encodeMessage(MatchMessage.NICE_MOVE)

        assertEquals("NICE_MOVE", payload["key"])
        assertEquals(ServerValue.TIMESTAMP, payload["at"])
    }

    @Test
    fun `a new room is opened without one`() {
        val payload = codec.encodeNewRoom(
            configuration = RoomConfiguration(timing = RoomTiming(turnDurationSeconds = 30)),
            host = null,
            hostUserId = "host-uid",
            hostSeat = PlayerId.PLAYER_ONE,
            now = 1_000L,
        )

        assertEquals(30, payload["turnDurationSeconds"])
        assertFalse(payload.containsKey("totalDurationSeconds"))
    }

    private fun storedRoom(extras: Map<String, Any?> = emptyMap()): Map<String, Any?> = mapOf(
        "roomName" to "Test",
        "hostUserId" to "host-uid",
        "guestUserId" to "guest-uid",
        "hostName" to "hostname",
        "hostRating" to 1_000,
        "visibility" to "PUBLIC",
        "status" to "IN_PROGRESS",
        "gameMode" to "CLASSIC",
        "ranked" to true,
        "requiresPassword" to false,
        "createdAt" to 1L,
        "expiresAt" to 2L,
        "turnDurationSeconds" to 60,
        "currentTurnUserId" to "host-uid",
        "board" to boardCodec.encodeBoard(BoardState.initial()),
        "lastMoveAt" to 1L,
        "winnerUserId" to "",
        "endReason" to "",
        "version" to 0L,
        "hostSeat" to "PLAYER_ONE",
    ) + extras
}
