package com.duzman46.gridbound.match

import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.leaderboard.domain.LeaderboardWeek
import com.duzman46.gridbound.match.domain.MatchEndReason
import com.duzman46.gridbound.match.domain.MatchOutcome
import com.duzman46.gridbound.match.domain.MatchReport
import com.duzman46.gridbound.rating.MatchScore
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchReportTest {

    private val host = "host-uid"
    private val guest = "guest-uid"

    @Test
    fun `the same room and start time always yield the same id`() {
        assertEquals(
            MatchReport.matchId("ABC123", 1_700_000_000_000L),
            MatchReport.matchId("ABC123", 1_700_000_000_000L),
        )
    }

    @Test
    fun `different rooms yield different ids`() {
        assertTrue(
            MatchReport.matchId("ABC123", 1L) != MatchReport.matchId("ABC124", 1L),
        )
    }

    @Test
    fun `a replayed room yields a different id`() {
        // Same code reused later must not collide with the earlier match.
        assertTrue(
            MatchReport.matchId("ABC123", 1L) != MatchReport.matchId("ABC123", 2L),
        )
    }

    @Test
    fun `winner maps from the board player to the right account`() {
        val seatOne = PlayerId.PLAYER_ONE
        assertEquals(host, MatchReport.winnerUid(PlayerId.PLAYER_ONE, seatOne, host, guest))
        assertEquals(guest, MatchReport.winnerUid(PlayerId.PLAYER_TWO, seatOne, host, guest))
        assertNull(MatchReport.winnerUid(null, seatOne, host, guest))
    }

    @Test
    fun `a host in seat two takes the win the board gives to seat two`() {
        // The board only ever says which seat won. Reading that as "seat one is the host"
        // would hand every win in a red-host room to the wrong account.
        val seatTwo = PlayerId.PLAYER_TWO
        assertEquals(guest, MatchReport.winnerUid(PlayerId.PLAYER_ONE, seatTwo, host, guest))
        assertEquals(host, MatchReport.winnerUid(PlayerId.PLAYER_TWO, seatTwo, host, guest))
        assertNull(MatchReport.winnerUid(null, seatTwo, host, guest))
    }

    @Test
    fun `scores are complementary`() {
        val report = report(winnerUid = host)
        assertEquals(MatchScore.WIN, report.scoreFor(host))
        assertEquals(MatchScore.LOSS, report.scoreFor(guest))
        assertFalse(report.isDraw)
    }

    @Test
    fun `a draw scores half for both`() {
        val report = report(winnerUid = null)
        assertEquals(MatchScore.DRAW, report.scoreFor(host))
        assertEquals(MatchScore.DRAW, report.scoreFor(guest))
        assertTrue(report.isDraw)
    }

    @Test
    fun `a winner who did not play is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            report(winnerUid = "someone-else")
        }
    }

    @Test
    fun `a player cannot face themselves`() {
        assertThrows(IllegalArgumentException::class.java) {
            report(winnerUid = host).copy(guestUid = host)
        }
    }

    @Test
    fun `only a normal ending can be checked against the board`() {
        assertTrue(MatchEndReason.NORMAL.isVerifiableFromBoard)
        assertFalse(MatchEndReason.TIMEOUT.isVerifiableFromBoard)
        assertFalse(MatchEndReason.RESIGNATION.isVerifiableFromBoard)
        assertFalse(MatchEndReason.DISCONNECT.isVerifiableFromBoard)
    }

    @Test
    fun `a history row's outcomes are the words the server writes`() {
        // worker/src/sweep.ts stores each recentMatches row's result as one of these three
        // strings and RtdbMatchRepository decodes it by matching the enum name against them.
        // There is no table in between, so renaming a constant here stops every row on every
        // profile decoding — and a page that decodes nothing says the player has never played.
        assertEquals(
            setOf("WIN", "LOSS", "DRAW"),
            MatchOutcome.entries.map(MatchOutcome::name).toSet(),
        )
    }

    @Test
    fun `week keys match the values the rating function computes`() {
        assertEquals("2026-W01", LeaderboardWeek.keyFor(utc(2026, 1, 4)))
        assertEquals("2026-W53", LeaderboardWeek.keyFor(utc(2027, 1, 1)))
        assertEquals("2026-W32", LeaderboardWeek.keyFor(utc(2026, 8, 6)))
    }

    @Test
    fun `a week key is stable across the whole week`() {
        val monday = LeaderboardWeek.keyFor(utc(2026, 8, 3))
        val sunday = LeaderboardWeek.keyFor(utc(2026, 8, 9))
        assertEquals(monday, sunday)
    }

    private fun report(winnerUid: String?) = MatchReport(
        matchId = MatchReport.matchId("ABC123", 1L),
        roomCode = "ABC123",
        hostUid = host,
        guestUid = guest,
        winnerUid = winnerUid,
        endReason = MatchEndReason.NORMAL,
        ranked = true,
        turnCount = 24,
        reportedAt = 1L,
        reportedBy = host,
    )

    private fun utc(year: Int, month: Int, day: Int): Long =
        ZonedDateTime.of(year, month, day, 12, 0, 0, 0, ZoneOffset.UTC)
            .toInstant()
            .let(Instant::toEpochMilli)
}
