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
}
