package com.duzman46.gridbound.leaderboard.domain

import com.duzman46.gridbound.core.Outcome

interface LeaderboardRepository {
    /**
     * Loads one page in descending rating order.
     *
     * @param cursor null for the first page, otherwise the cursor from the previous page
     * @param friendIds required for [LeaderboardScope.FRIENDS]; ignored otherwise
     */
    suspend fun loadPage(
        scope: LeaderboardScope,
        cursor: LeaderboardCursor? = null,
        friendIds: Set<String> = emptySet(),
    ): Outcome<LeaderboardPage>

    /** The signed-in player's own row and position. */
    suspend fun loadOwnStanding(userId: String): Outcome<OwnStanding>
}
