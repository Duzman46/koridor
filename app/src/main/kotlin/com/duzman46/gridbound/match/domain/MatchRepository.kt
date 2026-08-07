package com.duzman46.gridbound.match.domain

import com.duzman46.gridbound.core.Outcome

interface MatchRepository {
    /**
     * Files a finished match for rating.
     *
     * Safe to call more than once: the report is keyed by [MatchReport.matchId] and written
     * with a transaction that refuses to replace an existing entry, so a retry after a lost
     * connection cannot rate the same match twice. Reporting never applies rating locally —
     * only trusted server code does that.
     */
    suspend fun reportMatch(report: MatchReport): Outcome<Unit>

    /**
     * The last matches the server recorded for a player, newest first.
     *
     * The player need not be the caller: this is part of a public profile, and the database
     * rules let any signed-in player read anyone's while letting nobody write their own.
     *
     * Fetched whole rather than a page at a time. The server keeps the node capped, so "all
     * of them" is already a small answer, and asking again to reveal rows the device is
     * already holding would be a round trip that buys nothing.
     */
    suspend fun loadRecentMatches(userId: String): Outcome<List<RecentMatch>>
}
