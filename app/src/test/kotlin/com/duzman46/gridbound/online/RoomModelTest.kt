package com.duzman46.gridbound.online

import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.online.data.RoomCredentials
import com.duzman46.gridbound.online.model.OnlineGameMode
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineRoomStatus
import com.duzman46.gridbound.online.model.RoomBrowserFilter
import com.duzman46.gridbound.online.model.RoomConfiguration
import com.duzman46.gridbound.online.model.RoomTiming
import com.duzman46.gridbound.online.model.RoomVisibility
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomModelTest {

    private val host = "host-uid"
    private val guest = "guest-uid"

    // --- Room codes -------------------------------------------------------------------

    @Test
    fun `generated codes avoid characters that look alike`() {
        val random = Random(20260806)
        repeat(200) {
            val code = RoomCredentials.generateCode(random)
            assertEquals(Constants.Online.ROOM_CODE_LENGTH, code.length)
            assertTrue(RoomCredentials.isValidCode(code))
            // 0/O and 1/I are excluded so a code read aloud cannot be mistyped.
            assertFalse("code $code contains a confusable character", code.any { it in "01OI" })
        }
    }

    @Test
    fun `codes are normalised from casual input`() {
        assertEquals("ABC234", RoomCredentials.normalizeCode("  abc234 "))
        assertEquals("ABC234", RoomCredentials.normalizeCode("abc-234"))
        assertEquals("ABC234", RoomCredentials.normalizeCode("ABC234EXTRA"))
    }

    @Test
    fun `normalisation drops characters outside the alphabet`() {
        assertFalse(RoomCredentials.isValidCode(RoomCredentials.normalizeCode("OOOOOO")))
    }

    // --- Passwords --------------------------------------------------------------------

    @Test
    fun `a blank password produces no hash`() {
        assertNull(RoomCredentials.hashPassword("ABC234", ""))
        assertNull(RoomCredentials.hashPassword("ABC234", "   "))
    }

    @Test
    fun `hashing is stable and never returns the password`() {
        val hash = RoomCredentials.hashPassword("ABC234", "secret123")
        assertEquals(hash, RoomCredentials.hashPassword("ABC234", "secret123"))
        assertNotEquals("secret123", hash)
        assertEquals(64, hash?.length)
    }

    @Test
    fun `the same password in two rooms hashes differently`() {
        // Salting with the room code stops one table covering every room.
        assertNotEquals(
            RoomCredentials.hashPassword("ABC234", "secret123"),
            RoomCredentials.hashPassword("XYZ789", "secret123"),
        )
    }

    // --- Status transitions -----------------------------------------------------------

    @Test
    fun `only a waiting room accepts a new player`() {
        assertTrue(OnlineRoomStatus.WAITING.isJoinable)
        listOf(
            OnlineRoomStatus.STARTING,
            OnlineRoomStatus.IN_PROGRESS,
            OnlineRoomStatus.FINISHED,
            OnlineRoomStatus.CANCELLED,
            OnlineRoomStatus.EXPIRED,
        ).forEach { assertFalse("$it should not be joinable", it.isJoinable) }
    }

    @Test
    fun `a match can be played only while starting or in progress`() {
        assertTrue(OnlineRoomStatus.STARTING.isPlayable)
        assertTrue(OnlineRoomStatus.IN_PROGRESS.isPlayable)
        assertFalse(OnlineRoomStatus.WAITING.isPlayable)
        assertFalse(OnlineRoomStatus.FINISHED.isPlayable)
    }

    @Test
    fun `terminal states are recognised as over`() {
        assertTrue(OnlineRoomStatus.FINISHED.isOver)
        assertTrue(OnlineRoomStatus.CANCELLED.isOver)
        assertTrue(OnlineRoomStatus.EXPIRED.isOver)
        assertFalse(OnlineRoomStatus.WAITING.isOver)
        assertFalse(OnlineRoomStatus.IN_PROGRESS.isOver)
    }

    // --- Seats ------------------------------------------------------------------------

    @Test
    fun `seats map both ways`() {
        val room = room()
        assertEquals(PlayerId.PLAYER_ONE, room.playerFor(host))
        assertEquals(PlayerId.PLAYER_TWO, room.playerFor(guest))
        assertNull(room.playerFor("stranger"))
        assertEquals(host, room.userFor(PlayerId.PLAYER_ONE))
        assertEquals(guest, room.userFor(PlayerId.PLAYER_TWO))
    }

    @Test
    fun `membership excludes strangers`() {
        val room = room()
        assertTrue(room.isMember(host))
        assertTrue(room.isMember(guest))
        assertFalse(room.isMember("stranger"))
    }

    @Test
    fun `player count reflects an empty guest seat`() {
        assertEquals(1, room(guestUserId = null).playerCount)
        assertEquals(1, room(guestUserId = "").playerCount)
        assertEquals(2, room().playerCount)
    }

    // --- Clocks -----------------------------------------------------------------------

    @Test
    fun `an untimed room never expires a turn`() {
        val room = playing(timing = RoomTiming.UNLIMITED, lastMoveAt = 0L)
        assertNull(room.turnMillisRemaining(now = 10_000_000L))
        assertFalse(room.hasTurnExpired(now = 10_000_000L))
    }

    @Test
    fun `a turn expires once the clock runs out`() {
        val room = playing(timing = RoomTiming(turnDurationSeconds = 60), lastMoveAt = 1_000L)
        assertFalse(room.hasTurnExpired(now = 1_000L + 59_000L))
        assertTrue(room.hasTurnExpired(now = 1_000L + 61_000L))
    }

    @Test
    fun `remaining time counts down and floors at zero`() {
        val room = playing(timing = RoomTiming(turnDurationSeconds = 60), lastMoveAt = 1_000L)
        assertEquals(60_000L, room.turnMillisRemaining(now = 1_000L))
        assertEquals(30_000L, room.turnMillisRemaining(now = 31_000L))
        assertEquals(0L, room.turnMillisRemaining(now = 999_000L))
    }

    @Test
    fun `a room still waiting runs no clock`() {
        // A host sitting in an empty room must never time out against themselves.
        val room = room(timing = RoomTiming(turnDurationSeconds = 60), lastMoveAt = 1_000L)
        assertNull(room.turnMillisRemaining(now = 999_000L))
        assertFalse(room.hasTurnExpired(now = 999_000L))
    }

    private fun playing(timing: RoomTiming, lastMoveAt: Long) =
        room(status = OnlineRoomStatus.IN_PROGRESS, timing = timing, lastMoveAt = lastMoveAt)

    @Test
    fun `a finished room has no running clock`() {
        val room = room(
            status = OnlineRoomStatus.FINISHED,
            timing = RoomTiming(turnDurationSeconds = 60),
            lastMoveAt = 1_000L,
        )
        assertNull(room.turnMillisRemaining(now = 999_000L))
        assertFalse(room.hasTurnExpired(now = 999_000L))
    }

    // --- Configuration ----------------------------------------------------------------

    @Test
    fun `a default configuration is valid`() {
        assertNull(RoomConfiguration().validate())
    }

    @Test
    fun `an overlong room name is rejected`() {
        val configuration = RoomConfiguration(
            roomName = "a".repeat(Constants.Online.ROOM_NAME_MAX_LENGTH + 1),
        )
        assertEquals(AppError.ROOM_NAME_TOO_LONG, configuration.validate())
    }

    @Test
    fun `a short password is rejected but no password is fine`() {
        assertEquals(
            AppError.ROOM_PASSWORD_TOO_SHORT,
            RoomConfiguration(password = "ab").validate(),
        )
        assertNull(RoomConfiguration(password = "").validate())
        assertNull(RoomConfiguration(password = "abcd").validate())
    }

    // --- Browser filter ---------------------------------------------------------------

    @Test
    fun `the browser hides rooms that are no longer waiting`() {
        val filter = RoomBrowserFilter()
        assertTrue(filter.matches(room(status = OnlineRoomStatus.WAITING), emptySet()))
        assertFalse(filter.matches(room(status = OnlineRoomStatus.IN_PROGRESS), emptySet()))
    }

    @Test
    fun `the ranked filter separates the two kinds of room`() {
        assertTrue(RoomBrowserFilter(ranked = true).matches(room(ranked = true), emptySet()))
        assertFalse(RoomBrowserFilter(ranked = true).matches(room(ranked = false), emptySet()))
        assertTrue(RoomBrowserFilter(ranked = false).matches(room(ranked = false), emptySet()))
    }

    @Test
    fun `the fast filter excludes slow and untimed rooms`() {
        val filter = RoomBrowserFilter(maxTurnSeconds = 60)
        assertTrue(filter.matches(room(timing = RoomTiming(turnDurationSeconds = 30)), emptySet()))
        assertTrue(filter.matches(room(timing = RoomTiming(turnDurationSeconds = 60)), emptySet()))
        assertFalse(filter.matches(room(timing = RoomTiming(turnDurationSeconds = 120)), emptySet()))
        assertFalse(filter.matches(room(timing = RoomTiming.UNLIMITED), emptySet()))
    }

    @Test
    fun `the friends filter needs the host to be a friend`() {
        val filter = RoomBrowserFilter(friendsOnly = true)
        assertFalse(filter.matches(room(), emptySet()))
        assertTrue(filter.matches(room(), setOf(host)))
    }

    private fun room(
        guestUserId: String? = guest,
        status: OnlineRoomStatus = OnlineRoomStatus.WAITING,
        ranked: Boolean = true,
        timing: RoomTiming = RoomTiming(),
        lastMoveAt: Long = 0L,
    ) = OnlineRoom(
        roomId = "ABC234",
        roomCode = "ABC234",
        roomName = "Test",
        hostUserId = host,
        guestUserId = guestUserId,
        hostName = "hostname",
        hostRating = 1000,
        visibility = RoomVisibility.PUBLIC,
        status = status,
        gameMode = OnlineGameMode.CLASSIC,
        ranked = ranked,
        requiresPassword = false,
        createdAt = 0L,
        expiresAt = Long.MAX_VALUE,
        timing = timing,
        currentTurnUserId = host,
        boardState = BoardState.initial(),
        lastMoveAt = lastMoveAt,
        winnerUserId = null,
        endReason = null,
        version = 0L,
    )
}
