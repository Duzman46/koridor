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

    /**
     * The name is measured trimmed, because trimmed is what gets stored: `RoomCodec` writes
     * `roomName.trim().take(MAX)`. Measuring the raw text instead meant a name the codec would
     * have stored intact was refused as too long on the strength of the spaces around it — a
     * rejection the host could not see the cause of, since the offending characters are blank.
     */
    fun validate(): AppError? = when {
        roomName.trim().length > Constants.Online.ROOM_NAME_MAX_LENGTH -> AppError.ROOM_NAME_TOO_LONG
        password.isNotBlank() && password.length < Constants.Online.ROOM_PASSWORD_MIN_LENGTH ->
            AppError.ROOM_PASSWORD_TOO_SHORT

        else -> null
    }

    companion object {
        /**
         * What a rerun of [played] is played under: exactly what the first game was.
         *
         * A rematch used to be built from scratch and carried only the visibility, the rating
         * and the seats, which meant [timing] — the one setting on this form a player actually
         * feels — was silently defaulted. Two people who had deliberately turned the move clock
         * off got it back at sixty seconds and either of them could then lose a rematch on time
         * they had agreed not to play against; a two-minute room halved, a thirty-second room
         * doubled. The played room is read anyway, to find out whether the match was rated, so
         * carrying the rest of it costs nothing.
         *
         * [gameMode] is carried for the same reason, even though there is one of them today and
         * so nothing to get wrong yet. A promise kept by accident stops being kept the moment a
         * second mode ships, and the rematch would quietly downgrade to the classic board
         * without anybody having asked it to.
         *
         * Two things are deliberately *not* carried. The room is always private, whatever the
         * played one was: it is opened for one named opponent who is about to be handed the
         * code, so a browser listing is only a way for a stranger to take their seat. And it
         * never has a password, which would be a lock with the key already given out — the
         * played room's password is not readable here in any case, only the fact that it had
         * one.
         *
         * @param played the finished match, or null when it could not be read. A rematch on the
         *   defaults is still better than no rematch, and an unreadable room is treated as
         *   unrated for the same reason a guest's is: nothing that cannot be shown to have been
         *   rated may move a rating.
         */
        fun rematchOf(played: OnlineRoom?, hostSeat: PlayerId): RoomConfiguration =
            RoomConfiguration(
                visibility = RoomVisibility.PRIVATE,
                ranked = played?.ranked == true,
                gameMode = played?.gameMode ?: OnlineGameMode.CLASSIC,
                timing = played?.timing ?: RoomTiming(),
                hostSeat = hostSeat,
            )
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
    /**
     * What each player last said, at most one entry each and empty until somebody does.
     *
     * Carried on the room rather than fetched separately: the board screen is already
     * listening to this node, so a message costs no second listener and arrives on the same
     * update as the move it was a reaction to.
     */
    val chat: List<MatchChatEntry> = emptyList(),
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
    /**
     * @param hosted whether this device opened the room or walked into one that was already
     *   standing. Only a rematch has two answers to that, and only a rematch needs one: both
     *   players are offered the button, the code is derived from the match they just played, so
     *   whichever device gets there first hosts and the other takes the free seat. The one that
     *   joined must not then send an invitation — it would be an offer of a room its recipient
     *   is already sitting in, and because the room is in play the moment it is sent, nothing
     *   that would withdraw it ever runs. The bar hangs over the live board for the ten minutes
     *   an invitation lives. Defaults to false so a path that has not thought about the
     *   question is treated as the seat-taker, which is the answer that sends nothing.
     */
    data class Success(
        val session: OnlineSession,
        val hosted: Boolean = false,
    ) : OnlineLobbyResult

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
