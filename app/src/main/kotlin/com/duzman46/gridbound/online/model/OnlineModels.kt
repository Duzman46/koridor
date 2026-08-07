package com.duzman46.gridbound.online.model

import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.PlayerId

/**
 * Where a room shows up — settled by the way it was opened, never by a control on a form.
 *
 * A host used to pick this, and the picker was the reason the room browser was always empty:
 * it did the same job as the password field a few rows below it and defaulted to the closed
 * setting, so every room anyone opened was invisible. A room the player opens by hand is
 * listed, and a password is how one is closed. The other two values belong to rooms that were
 * opened *for* somebody, where a stranger walking in ahead of them is the one outcome nobody
 * wants — so the flow that opens the room states which, and no screen ever asks.
 */
enum class RoomVisibility {
    /** Listed in the public room browser: the room somebody opened for whoever turns up. */
    PUBLIC,

    /**
     * Unlisted. What matchmaking and a rematch write, because both already know both players:
     * one was paired out of the queue, the other is the person the last match was against.
     */
    PRIVATE,

    /** Listed for the host's friends only. What an invitation to a named friend opens. */
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

/**
 * The move clock, in seconds. Zero means no limit.
 *
 * A move clock is the only one a room keeps. A budget for the whole match is a second way to
 * lose without being beaten, and it decides games in the lobby rather than on the board: the
 * player who thought hardest loses to one who did not, whatever the position.
 */
data class RoomTiming(
    val turnDurationSeconds: Int = DEFAULT_TURN_SECONDS,
) {
    val hasTurnLimit: Boolean get() = turnDurationSeconds > 0

    companion object {
        const val DEFAULT_TURN_SECONDS = 60

        val TURN_OPTIONS = listOf(0, 30, 60, 120)

        val UNLIMITED = RoomTiming(turnDurationSeconds = 0)
    }
}

/** Everything the host chooses when opening a room. */
data class RoomConfiguration(
    val roomName: String = "",
    val visibility: RoomVisibility = RoomVisibility.PUBLIC,
    val ranked: Boolean = true,
    val gameMode: OnlineGameMode = OnlineGameMode.CLASSIC,
    val timing: RoomTiming = RoomTiming(),
    /** Empty means no password. Never stored or transmitted in the clear. */
    val password: String = "",
    /**
     * Which seat the host takes, which the host chooses by colour: seat one is blue and opens.
     * Null means "let the room decide", which is the default and is settled once, at creation
     * — a host who never touched the picker should not always get the first move.
     */
    val hostSeat: PlayerId? = null,
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
    /**
     * The seat the host took; the guest gets the other one. Seat one is blue and always moves
     * first, so a host who chose red sits here as [PlayerId.PLAYER_TWO] and the guest opens.
     */
    val hostSeat: PlayerId,
    /** Incremented on every accepted move; the optimistic-concurrency guard. */
    val version: Long,
) {
    val playerCount: Int get() = if (guestUserId.isNullOrBlank()) 1 else 2

    fun playerFor(userId: String): PlayerId? = when (userId) {
        hostUserId -> hostSeat
        guestUserId -> hostSeat.opponent
        else -> null
    }

    fun userFor(playerId: PlayerId): String? =
        if (playerId == hostSeat) hostUserId else guestUserId

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

    /**
     * True once nobody has moved for [Constants.Online.IDLE_FORFEIT_MILLIS] — the match has
     * been walked away from and is decided against whoever is on the clock. Independent of
     * the room's own turn timer, which may be switched off.
     */
    fun hasIdled(now: Long): Boolean =
        status == OnlineRoomStatus.IN_PROGRESS &&
            now - lastMoveAt >= Constants.Online.IDLE_FORFEIT_MILLIS

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
