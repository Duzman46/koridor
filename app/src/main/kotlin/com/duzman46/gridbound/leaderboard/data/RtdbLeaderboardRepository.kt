package com.duzman46.gridbound.leaderboard.data

import com.duzman46.gridbound.auth.domain.AccountType
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.data.firebase.awaitSnapshot
import com.duzman46.gridbound.data.firebase.int
import com.duzman46.gridbound.data.firebase.string
import com.duzman46.gridbound.data.firebase.stringOrNull
import com.duzman46.gridbound.leaderboard.domain.LeaderboardCursor
import com.duzman46.gridbound.leaderboard.domain.LeaderboardEntry
import com.duzman46.gridbound.leaderboard.domain.LeaderboardPage
import com.duzman46.gridbound.leaderboard.domain.LeaderboardRepository
import com.duzman46.gridbound.leaderboard.domain.LeaderboardScope
import com.duzman46.gridbound.leaderboard.domain.LeaderboardWeek
import com.duzman46.gridbound.leaderboard.domain.OwnStanding
import com.duzman46.gridbound.online.data.FirebaseProvider
import com.duzman46.gridbound.profile.data.ProfileCodec
import com.duzman46.gridbound.util.enumValueOrDefault
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
 * Ordering relies on the `.indexOn` entries in database.rules.json, so the server does the
 * sorting and a page costs one query rather than a full download.
 *
 * ## Why guests are absent, and how
 *
 * An anonymous player is given a rating like anyone else — they have to be, the game rates a
 * match without asking who is behind it — but a table of the best players is meant to list
 * players, and an account that exists until the app is uninstalled is not one of them.
 *
 * Refusing them here, in the query, would be the weak version: the row would still be written
 * and still be readable, so it would take one modified client to put them back. Both boards
 * therefore leave them out at the point the row is written, and each board's query can only
 * see what was written:
 *
 * - The weekly board is a table the server builds. `worker/src/sweep.ts` writes no row at all
 *   for a guest, and database.rules.json grants no client any write under `leaderboards`, so
 *   there is nothing to filter.
 * - The all-time board cannot work that way, because its rows are the profiles themselves and
 *   a profile has to exist. So it is ordered by [ProfileCodec.Keys.LEADERBOARD_RATING] — a
 *   copy of the rating that is written only for a linked account. A guest holds no value for
 *   that key, which in Realtime Database means they sort before every number and are dropped
 *   by [BOARD_RATING_FLOOR]. They are not filtered out of the answer; they were never in the
 *   index the question was asked of. The rules pin the copy to the rating it mirrors and
 *   refuse it to a `GUEST` profile, and they admit no query over `/users` that orders by
 *   anything else — so there is no reading of the table that puts a guest on it.
 *
 * ## Why nothing here asks whether the copy exists
 *
 * Being absent from that index is not the same fact as being a guest, and only one of the two
 * is what a board is asking. A linked account can be absent from it for a while — the copy is
 * written by the server after a rated match and by `RtdbUserProfileRepository` when a
 * credential is linked, and everything written before the copy existed waits on the backfill
 * in `worker/src/sweep.ts` to be taken on. Answering "are they a guest?" with "are they in the
 * index?" therefore reads every such account as anonymous, which emptied the friends board and
 * told signed-in players they were not signed in.
 *
 * So the ordered query uses the index, because that is what an index is for, and every read of
 * a single profile by id — the friends board, a player's own standing — asks the profile what
 * kind of account it is. That question every profile has answered since the first one.
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
            // Not NOT_SIGNED_IN: whoever this is, they are signed in — the caller had their
            // user id to ask with. A profile that will not yield a row is a fault, and saying
            // so is the difference between a message and the wrong message.
            val entry = profile.toBoardEntry()
                ?: return@dbCall Outcome.Failure(AppError.UNKNOWN)

            // Rank is the number of players rated above this one, plus one. The scan is
            // capped so a single lookup stays bounded no matter how large the board grows.
            //
            // Counting from the rating rather than from the copy the board is sorted by is
            // deliberate, and the rules allow exactly that: an account the backfill has not
            // reached yet is not on the table but its rating still says where it will land,
            // and a position it can be told now beats one that waits for a server sweep.
            val above = usersRef()
                .orderByChild(ProfileCodec.Keys.LEADERBOARD_RATING)
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
        val ordered: Query = when (scope) {
            // The weekly rows are the server's own, so every one of them belongs on the board
            // and its rating needs no second copy to be trusted.
            LeaderboardScope.WEEKLY -> weeklyRef().orderByChild(ProfileCodec.Keys.RATING)
            else -> usersRef()
                .orderByChild(ProfileCodec.Keys.LEADERBOARD_RATING)
                .startAt(BOARD_RATING_FLOOR)
        }
        // Fetch one extra row to learn whether a further page exists without a second query.
        val pageSize = Constants.Leaderboard.PAGE_SIZE
        val query: Query = if (cursor == null) {
            ordered.limitToLast(pageSize + 1)
        } else {
            ordered.endBefore(cursor.rating.toDouble(), cursor.userId).limitToLast(pageSize + 1)
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
            friendIds.map { id -> async { usersRef().child(id).awaitSnapshot().toBoardEntry() } }
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

/**
 * Every rating the rules will accept is at least this, so a range starting here keeps whoever
 * carries a board rating and drops whoever carries none — which is exactly the guests.
 */
private const val BOARD_RATING_FLOOR = 0.0

/**
 * A profile's row, or null when the profile holds no place on the board.
 *
 * Used wherever a profile is read by id instead of through the index, which is the one way a
 * guest could otherwise reach the table: their own standing, and the friends board.
 */
private fun DataSnapshot.toBoardEntry(): LeaderboardEntry? =
    if (holdsBoardPlace(stringOrNull(ProfileCodec.Keys.ACCOUNT_TYPE))) toEntry() else null

/**
 * Whether the account a profile describes is one a board is meant to list.
 *
 * @param accountType the profile's `accountType` field exactly as stored, or null when the
 *   profile carries none. Unreadable is read as anonymous: the one question that decides who
 *   is ranked is not one to answer generously on a profile too damaged to say what it is.
 */
internal fun holdsBoardPlace(accountType: String?): Boolean =
    !enumValueOrDefault(accountType, AccountType.GUEST).isGuest

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
