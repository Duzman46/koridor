package com.duzman46.gridbound.leaderboard.data

import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.data.firebase.awaitSnapshot
import com.duzman46.gridbound.data.firebase.int
import com.duzman46.gridbound.data.firebase.string
import com.duzman46.gridbound.leaderboard.domain.LeaderboardCursor
import com.duzman46.gridbound.leaderboard.domain.LeaderboardEntry
import com.duzman46.gridbound.leaderboard.domain.LeaderboardPage
import com.duzman46.gridbound.leaderboard.domain.LeaderboardRepository
import com.duzman46.gridbound.leaderboard.domain.LeaderboardScope
import com.duzman46.gridbound.leaderboard.domain.LeaderboardWeek
import com.duzman46.gridbound.leaderboard.domain.OwnStanding
import com.duzman46.gridbound.online.data.FirebaseProvider
import com.duzman46.gridbound.profile.data.ProfileCodec
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.Query
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Reads leaderboards straight from the profile and weekly aggregate nodes.
 *
 * Ordering relies on `.indexOn: ["rating"]` in database.rules.json, so the server does the
 * sorting and a page costs one query rather than a full download.
 */
@Singleton
class RtdbLeaderboardRepository @Inject constructor(
    private val firebase: FirebaseProvider,
) : LeaderboardRepository {

    override suspend fun loadPage(
        scope: LeaderboardScope,
        cursor: LeaderboardCursor?,
        friendIds: Set<String>,
    ): Outcome<LeaderboardPage> = dbCall("leaderboard-page") {
        when (scope) {
            LeaderboardScope.FRIENDS -> loadFriendsPage(friendIds)
            LeaderboardScope.GLOBAL, LeaderboardScope.WEEKLY -> loadRankedPage(scope, cursor)
        }
    }

    override suspend fun loadOwnStanding(userId: String): Outcome<OwnStanding> =
        dbCall("leaderboard-own") {
            val profile = usersRef().child(userId).awaitSnapshot()
            val entry = profile.toEntry() ?: return@dbCall Outcome.Failure(AppError.NOT_SIGNED_IN)

            // Rank is the number of players rated above this one, plus one. The scan is
            // capped so a single lookup stays bounded no matter how large the board grows.
            val above = usersRef()
                .orderByChild(ProfileCodec.Keys.RATING)
                .startAfter(entry.rating.toDouble())
                .limitToFirst(Constants.Leaderboard.RANK_SCAN_LIMIT)
                .awaitSnapshot()
            val count = above.childrenCount.toInt()
            val capped = count >= Constants.Leaderboard.RANK_SCAN_LIMIT
            Outcome.Success(
                OwnStanding(
                    entry = entry.copy(rank = count + 1),
                    isApproximate = capped,
                ),
            )
        }

    private suspend fun loadRankedPage(
        scope: LeaderboardScope,
        cursor: LeaderboardCursor?,
    ): Outcome<LeaderboardPage> {
        val base = when (scope) {
            LeaderboardScope.WEEKLY -> weeklyRef()
            else -> usersRef()
        }
        // Fetch one extra row to learn whether a further page exists without a second query.
        val pageSize = Constants.Leaderboard.PAGE_SIZE
        val query: Query = base.orderByChild(ProfileCodec.Keys.RATING).let { ordered ->
            if (cursor == null) {
                ordered.limitToLast(pageSize + 1)
            } else {
                ordered.endBefore(cursor.rating.toDouble(), cursor.userId).limitToLast(pageSize + 1)
            }
        }
        val snapshot = query.awaitSnapshot()
        // Realtime Database returns ascending order; a leaderboard reads top-first.
        val ascending = snapshot.children.mapNotNull(DataSnapshot::toEntry)
        val descending = ascending.asReversed()
        val hasMore = descending.size > pageSize
        val page = if (hasMore) descending.take(pageSize) else descending
        // Ranks are left unset here: only the caller knows how many rows precede this page.
        return Outcome.Success(
            LeaderboardPage(
                entries = page,
                hasMore = hasMore,
                cursor = page.lastOrNull()?.let { LeaderboardCursor(it.rating, it.userId) },
            ),
        )
    }

    /**
     * Friends are a small, explicit set, so they are fetched by id and sorted on the device
     * rather than through an index.
     */
    private suspend fun loadFriendsPage(friendIds: Set<String>): Outcome<LeaderboardPage> {
        if (friendIds.isEmpty()) {
            return Outcome.Success(LeaderboardPage(emptyList(), hasMore = false, cursor = null))
        }
        val entries = coroutineScope {
            friendIds.map { id -> async { usersRef().child(id).awaitSnapshot().toEntry() } }
                .mapNotNull { it.await() }
        }
        val ranked = entries.sortedByDescending(LeaderboardEntry::rating)
        return Outcome.Success(LeaderboardPage(ranked, hasMore = false, cursor = null))
    }

    private fun usersRef() = firebase.database.getReference(Constants.Backend.USERS_PATH)

    private fun weeklyRef() = firebase.database
        .getReference(Constants.Backend.LEADERBOARDS_PATH)
        .child(Constants.Backend.WEEKLY_LEADERBOARD_PATH)
        .child(LeaderboardWeek.current())

    private suspend fun <T> dbCall(
        operation: String,
        block: suspend () -> Outcome<T>,
    ): Outcome<T> {
        if (!firebase.isConfigured) return Outcome.Failure(AppError.SERVICE_UNAVAILABLE)
        return try {
            block()
        } catch (error: Exception) {
            AppLog.warn(operation, error)
            Outcome.Failure(
                if (error is FirebaseNetworkException) AppError.NETWORK else AppError.UNKNOWN,
            )
        }
    }
}

/** Shared by the profile node and the weekly aggregate, which use the same field names. */
private fun DataSnapshot.toEntry(): LeaderboardEntry? {
    val userId = key ?: return null
    val username = child(ProfileCodec.Keys.USERNAME).getValue(String::class.java) ?: return null
    return LeaderboardEntry(
        userId = userId,
        username = username,
        avatarId = string(ProfileCodec.Keys.AVATAR_ID, Constants.Profile.DEFAULT_AVATAR_ID),
        rating = int(ProfileCodec.Keys.RATING, Constants.Backend.STARTING_RATING),
        wins = int(ProfileCodec.Keys.WINS),
        totalGames = int(ProfileCodec.Keys.TOTAL_GAMES),
    )
}
