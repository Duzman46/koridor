package com.duzman46.gridbound.match.domain

import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.rating.MatchScore

/** How a match ended. Kept apart from the winner so timeouts and quits can be told apart. */
enum class MatchEndReason {
    /** A pawn reached its goal row. */
    NORMAL,

    /** A player ran out of time. */
    TIMEOUT,

    /** A player resigned on purpose. */
    RESIGNATION,

    /** A player left and did not return within the grace period. */
    DISCONNECT,
    ;

    /**
     * Only a finished game of Koridor can be verified from the final board. The other
     * endings are decided by elapsed time or absence, which the board cannot show.
     */
    val isVerifiableFromBoard: Boolean get() = this == NORMAL
}

/**
 * A completed online match, reported by a participant and rated by trusted server code.
 *
 * [matchId] is derived from the room and its creation time, so a client that submits the
 * same match twice writes to the same key — and the database rules refuse to overwrite an
 * existing report. That is the idempotency guarantee for rating.
 */
data class MatchReport(
    val matchId: String,
    val roomCode: String,
    val hostUid: String,
    val guestUid: String,
    /** null means a draw. */
    val winnerUid: String?,
    val endReason: MatchEndReason,
    val ranked: Boolean,
    val turnCount: Int,
    val reportedAt: Long,
    val reportedBy: String,
) {
    init {
        require(hostUid.isNotBlank() && guestUid.isNotBlank()) { "a match needs two players" }
        require(hostUid != guestUid) { "a player cannot face themselves" }
        require(winnerUid == null || winnerUid == hostUid || winnerUid == guestUid) {
            "the winner must be one of the participants"
        }
    }

    val isDraw: Boolean get() = winnerUid == null

    fun scoreFor(userId: String): MatchScore = when {
        winnerUid == null -> MatchScore.DRAW
        winnerUid == userId -> MatchScore.WIN
        else -> MatchScore.LOSS
    }

    companion object {
        /**
         * Stable across retries and across both devices: the same room and start time always
         * produce the same id, so two clients reporting the same match collide by design.
         */
        fun matchId(roomCode: String, createdAt: Long): String = "${roomCode}_$createdAt"

        fun winnerUid(winner: PlayerId?, hostUid: String, guestUid: String): String? =
            when (winner) {
                PlayerId.PLAYER_ONE -> hostUid
                PlayerId.PLAYER_TWO -> guestUid
                null -> null
            }
    }
}

/** Whether a report has been picked up and rated by the server. */
enum class MatchProcessingState {
    PENDING,
    RATED,
    /** Rejected by the server, for example because the board did not support the claim. */
    REJECTED,
}
