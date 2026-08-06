package com.duzman46.gridbound.online.model

import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.PlayerId

enum class RoomVisibility {
    /** Listed in the public room browser. */
    PUBLIC,

    /** Reachable only with the room code. */
    PRIVATE,

    /** Listed for the host's friends only. */
    FRIENDS,
}

enum class OnlineRoomStatus {
    /** Created, no opponent yet. */
    WAITING,

    /** Both players are present; the board is being handed to them. */
    STARTING,

    IN_PROGRESS,
    FINISHED,

    /** Closed before it started, or abandoned by the host. */
    CANCELLED,

    /** Timed out without ever being played. */
    EXPIRED,
    ;

    val isJoinable: Boolean get() = this == WAITING
    val isPlayable: Boolean get() = this == STARTING || this == IN_PROGRESS
    val isOver: Boolean get() = this == FINISHED || this == CANCELLED || this == EXPIRED
}

enum class OnlineGameMode {
    /** The standard 9x9 game with ten walls each. */
    CLASSIC,
}

/** How a finished room reached its end, mirrored onto the match report. */
enum class RoomEndReason {
    NORMAL,
    TIMEOUT,
    RESIGNATION,
    DISCONNECT,
}

/** Turn and match clocks, in seconds. Zero means no limit. */
data class RoomTiming(
    val turnDurationSeconds: Int = DEFAULT_TURN_SECONDS,
    val totalDurationSeconds: Int = DEFAULT_TOTAL_SECONDS,
) {
    val hasTurnLimit: Boolean get() = turnDurationSeconds > 0
    val hasTotalLimit: Boolean get() = totalDurationSeconds > 0

    companion object {
        const val DEFAULT_TURN_SECONDS = 60
        const val DEFAULT_TOTAL_SECONDS = 900

        val TURN_OPTIONS = listOf(0, 30, 60, 120)
        val TOTAL_OPTIONS = listOf(0, 300, 600, 900, 1_800)

        val UNLIMITED = RoomTiming(turnDurationSeconds = 0, totalDurationSeconds = 0)
    }
}

/** Everything the host chooses when opening a room. */
data class RoomConfiguration(
    val roomName: String = "",
    val visibility: RoomVisibility = RoomVisibility.PRIVATE,
    val ranked: Boolean = true,
    val gameMode: OnlineGameMode = OnlineGameMode.CLASSIC,
    val timing: RoomTiming = RoomTiming(),
    /** Empty means no password. Never stored or transmitted in the clear. */
    val password: String = "",
) {
    val hasPassword: Boolean get() = password.isNotBlank()

    fun validate(): AppError? = when {
        roomName.length > Constants.Online.ROOM_NAME_MAX_LENGTH -> AppError.ROOM_NAME_TOO_LONG
        password.isNotBlank() && password.length < Constants.Online.ROOM_PASSWORD_MIN_LENGTH ->
            AppError.ROOM_PASSWORD_TOO_SHORT

        else -> null
    }
}

data class OnlineRoom(
    val roomId: String,
    val roomCode: String,
    val roomName: String,
    val hostUserId: String,
    val guestUserId: String?,
    /** Denormalised so the room browser needs one query rather than one read per row. */
    val hostName: String,
    val hostRating: Int,
    val visibility: RoomVisibility,
    val status: OnlineRoomStatus,
    val gameMode: OnlineGameMode,
    val ranked: Boolean,
    val requiresPassword: Boolean,
    val createdAt: Long,
    val expiresAt: Long,
    val timing: RoomTiming,
    val currentTurnUserId: String?,
    val boardState: BoardState,
    val lastMoveAt: Long,
    val winnerUserId: String?,
    val endReason: RoomEndReason?,
    /** Incremented on every accepted move; the optimistic-concurrency guard. */
    val version: Long,
) {
    val playerCount: Int get() = if (guestUserId.isNullOrBlank()) 1 else 2

    fun playerFor(userId: String): PlayerId? = when (userId) {
        hostUserId -> PlayerId.PLAYER_ONE
        guestUserId -> PlayerId.PLAYER_TWO
        else -> null
    }

    fun userFor(playerId: PlayerId): String? = when (playerId) {
        PlayerId.PLAYER_ONE -> hostUserId
        PlayerId.PLAYER_TWO -> guestUserId
    }

    fun isMember(userId: String): Boolean = userId == hostUserId || userId == guestUserId

    /**
     * Milliseconds left on the current player's clock, or null when the room has no turn
     * limit or is not being played.
     */
    fun turnMillisRemaining(now: Long): Long? {
        if (!timing.hasTurnLimit || status != OnlineRoomStatus.IN_PROGRESS) return null
        val deadline = lastMoveAt + timing.turnDurationSeconds * 1_000L
        return (deadline - now).coerceAtLeast(0L)
    }

    /** True once the player whose turn it is has run out of time. */
    fun hasTurnExpired(now: Long): Boolean =
        timing.hasTurnLimit &&
            status == OnlineRoomStatus.IN_PROGRESS &&
            now > lastMoveAt + timing.turnDurationSeconds * 1_000L
}

/** A player's seat in a room. */
data class OnlineSession(
    val roomCode: String,
    val userId: String,
    val playerId: PlayerId,
)

sealed interface OnlineLobbyResult {
    data class Success(val session: OnlineSession) : OnlineLobbyResult
    data class Failure(val error: AppError) : OnlineLobbyResult
}

/** Filters applied to the public room browser. */
data class RoomBrowserFilter(
    val ranked: Boolean? = null,
    val friendsOnly: Boolean = false,
    val maxTurnSeconds: Int? = null,
    val waitingOnly: Boolean = true,
) {
    fun matches(room: OnlineRoom, friendIds: Set<String>): Boolean {
        if (waitingOnly && room.status != OnlineRoomStatus.WAITING) return false
        if (ranked != null && room.ranked != ranked) return false
        if (friendsOnly && room.hostUserId !in friendIds) return false
        if (maxTurnSeconds != null) {
            val turn = room.timing.turnDurationSeconds
            if (turn == 0 || turn > maxTurnSeconds) return false
        }
        return true
    }
}
