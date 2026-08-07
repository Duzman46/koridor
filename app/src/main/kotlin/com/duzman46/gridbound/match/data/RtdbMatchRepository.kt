package com.duzman46.gridbound.match.data

import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.data.firebase.awaitSnapshot
import com.duzman46.gridbound.data.firebase.runTransactionSuspend
import com.duzman46.gridbound.data.firebase.string
import com.duzman46.gridbound.data.firebase.stringOrNull
import com.duzman46.gridbound.match.domain.MatchOutcome
import com.duzman46.gridbound.match.domain.MatchProcessingState
import com.duzman46.gridbound.match.domain.MatchReport
import com.duzman46.gridbound.match.domain.MatchRepository
import com.duzman46.gridbound.match.domain.RecentMatch
import com.duzman46.gridbound.online.data.FirebaseProvider
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.Transaction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RtdbMatchRepository @Inject constructor(
    private val firebase: FirebaseProvider,
) : MatchRepository {

    override suspend fun reportMatch(report: MatchReport): Outcome<Unit> {
        if (!firebase.isConfigured) return Outcome.Failure(AppError.SERVICE_UNAVAILABLE)
        return try {
            val payload = mapOf(
                Keys.ROOM_CODE to report.roomCode,
                Keys.HOST_UID to report.hostUid,
                Keys.GUEST_UID to report.guestUid,
                Keys.WINNER_UID to report.winnerUid.orEmpty(),
                Keys.END_REASON to report.endReason.name,
                Keys.RANKED to report.ranked,
                Keys.TURN_COUNT to report.turnCount,
                Keys.REPORTED_AT to report.reportedAt,
                Keys.REPORTED_BY to report.reportedBy,
                Keys.STATE to MatchProcessingState.PENDING.name,
            )
            firebase.database
                .getReference(Constants.Backend.MATCH_RESULTS_PATH)
                .child(report.matchId)
                .runTransactionSuspend { current ->
                    // An existing report wins. Aborting here is the idempotency guarantee:
                    // whichever player reports first defines the match, and a retry or the
                    // opponent's duplicate report changes nothing.
                    if (current.value != null) return@runTransactionSuspend Transaction.abort()
                    current.value = payload
                    Transaction.success(current)
                }
            // A committed write and an aborted duplicate are both success from the caller's
            // point of view: after either one, exactly one report exists.
            Outcome.Success(Unit)
        } catch (error: Exception) {
            AppLog.warn("report-match", error)
            Outcome.Failure(
                if (error is FirebaseNetworkException) AppError.NETWORK else AppError.UNKNOWN,
            )
        }
    }

    override suspend fun loadRecentMatches(userId: String): Outcome<List<RecentMatch>> {
        if (!firebase.isConfigured) return Outcome.Failure(AppError.SERVICE_UNAVAILABLE)
        if (userId.isBlank()) return Outcome.Failure(AppError.NOT_SIGNED_IN)
        return try {
            val history = firebase.database
                .getReference(Constants.Backend.RECENT_MATCHES_PATH)
                .child(userId)
                .awaitSnapshot()
            // Sorted here rather than by the database, because the node is keyed by match id:
            // an ordered query would need an index on a node capped at ten rows, which is a
            // server-side sort of a list that fits in one screen.
            Outcome.Success(
                history.children
                    .mapNotNull(DataSnapshot::toRecentMatch)
                    .sortedByDescending(RecentMatch::playedAt),
            )
        } catch (error: Exception) {
            AppLog.warn("load-recent-matches", error)
            Outcome.Failure(
                if (error is FirebaseNetworkException) AppError.NETWORK else AppError.UNKNOWN,
            )
        }
    }

    object Keys {
        const val ROOM_CODE = "roomCode"
        const val HOST_UID = "hostUid"
        const val GUEST_UID = "guestUid"
        const val WINNER_UID = "winnerUid"
        const val END_REASON = "endReason"
        const val RANKED = "ranked"
        const val TURN_COUNT = "turnCount"
        const val REPORTED_AT = "reportedAt"
        const val REPORTED_BY = "reportedBy"
        const val STATE = "state"
    }

    /** The fields `worker/src/sweep.ts` writes under `recentMatches/{uid}/{matchId}`. */
    object HistoryKeys {
        const val OPPONENT_NAME = "opponentName"
        const val RESULT = "result"
        const val PLAYED_AT = "playedAt"
        const val RATING_CHANGE = "ratingChange"
    }
}

/**
 * A history row, or null when it is too damaged to draw.
 *
 * Only the server writes here, so a row missing its time or its result is a fault rather than
 * a variation — and a fault is better left out of the list than shown as a nameless game on an
 * unknown date. The rating change is the one field allowed to be absent, and its absence is
 * the fact the row is carrying: see [RecentMatch.ratingChange].
 */
private fun DataSnapshot.toRecentMatch(): RecentMatch? {
    val playedAt = child(RtdbMatchRepository.HistoryKeys.PLAYED_AT)
        .getValue(Long::class.java) ?: return null
    val outcome = MatchOutcome.entries.firstOrNull {
        it.name == stringOrNull(RtdbMatchRepository.HistoryKeys.RESULT)
    } ?: return null
    return RecentMatch(
        opponentName = string(RtdbMatchRepository.HistoryKeys.OPPONENT_NAME),
        outcome = outcome,
        playedAt = playedAt,
        ratingChange = child(RtdbMatchRepository.HistoryKeys.RATING_CHANGE)
            .getValue(Long::class.java)?.toInt(),
    )
}
