package com.duzman46.gridbound.leaderboard.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import java.util.Locale

enum class LeaderboardScope {
    GLOBAL,
    WEEKLY,
    FRIENDS,
}

data class LeaderboardEntry(
    val userId: String,
    val username: String,
    val avatarId: String,
    val rating: Int,
    val wins: Int,
    val totalGames: Int,
    /** 1-based position, or null when it is outside the scanned range. */
    val rank: Int? = null,
)

/**
 * @param hasMore whether another page exists after this one
 * @param cursor opaque position to resume from; null when the list is exhausted
 */
data class LeaderboardPage(
    val entries: List<LeaderboardEntry>,
    val hasMore: Boolean,
    val cursor: LeaderboardCursor?,
)

/** Where the next page starts. Rating plus user id, because ratings are not unique. */
data class LeaderboardCursor(
    val rating: Int,
    val userId: String,
)

/**
 * The player's own position.
 *
 * @param isApproximate true when the scan hit its limit, so [LeaderboardEntry.rank] is a
 *   lower bound rather than an exact position.
 */
data class OwnStanding(
    val entry: LeaderboardEntry,
    val isApproximate: Boolean,
)

/**
 * ISO-8601 week key such as `2026-W32`, used to bucket the weekly board.
 *
 * Computed in UTC so every device and the Cloud Function agree on which week a match belongs
 * to; the same expression is mirrored in functions/src/index.ts.
 */
object LeaderboardWeek {
    fun keyFor(epochMillis: Long): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val fields = WeekFields.ISO
        val week = date.get(fields.weekOfWeekBasedYear())
        val year = date.get(fields.weekBasedYear())
        return "%04d-W%02d".format(Locale.ROOT, year, week)
    }

    fun current(now: Long = System.currentTimeMillis()): String = keyFor(now)
}
