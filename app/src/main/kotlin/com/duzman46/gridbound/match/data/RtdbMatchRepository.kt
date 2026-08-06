package com.duzman46.gridbound.match.data

import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.data.firebase.runTransactionSuspend
import com.duzman46.gridbound.match.domain.MatchProcessingState
import com.duzman46.gridbound.match.domain.MatchReport
import com.duzman46.gridbound.match.domain.MatchRepository
import com.duzman46.gridbound.online.data.FirebaseProvider
import com.google.firebase.FirebaseNetworkException
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
}
