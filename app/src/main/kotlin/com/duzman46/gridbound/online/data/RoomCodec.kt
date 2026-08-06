package com.duzman46.gridbound.online.data

import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.online.model.OnlineGameMode
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineRoomStatus
import com.duzman46.gridbound.online.model.RoomConfiguration
import com.duzman46.gridbound.online.model.RoomEndReason
import com.duzman46.gridbound.online.model.RoomTiming
import com.duzman46.gridbound.online.model.RoomVisibility
import com.duzman46.gridbound.profile.domain.UserProfile
import com.duzman46.gridbound.util.enumValueOrDefault
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps between [OnlineRoom] and its Realtime Database shape.
 *
 * Decoding works on the raw value map rather than a DataSnapshot, because the same payload
 * has to be read inside transactions, where only MutableData is available.
 *
 * The field names here are the contract shared with database.rules.json and
 * functions/src/index.ts; renaming one means renaming it in all three.
 */
@Singleton
class RoomCodec @Inject constructor(
    private val boardCodec: OnlineBoardCodec,
) {
    fun decode(roomCode: String, value: Any?): OnlineRoom? = runCatching {
        val map = value as? Map<*, *> ?: return null
        val board = boardCodec.decodeBoard(map[Keys.BOARD]) ?: return null
        OnlineRoom(
            roomId = roomCode,
            roomCode = roomCode,
            roomName = map.string(Keys.ROOM_NAME).orEmpty(),
            hostUserId = map.string(Keys.HOST_USER_ID) ?: return null,
            guestUserId = map.string(Keys.GUEST_USER_ID),
            hostName = map.string(Keys.HOST_NAME).orEmpty(),
            hostRating = map.int(Keys.HOST_RATING, Constants.Backend.STARTING_RATING),
            visibility = enumValueOrDefault(map.string(Keys.VISIBILITY), RoomVisibility.PRIVATE),
            status = enumValueOrDefault(map.string(Keys.STATUS), OnlineRoomStatus.WAITING),
            gameMode = enumValueOrDefault(map.string(Keys.GAME_MODE), OnlineGameMode.CLASSIC),
            ranked = map[Keys.RANKED] as? Boolean ?: false,
            requiresPassword = map[Keys.REQUIRES_PASSWORD] as? Boolean ?: false,
            createdAt = map.long(Keys.CREATED_AT),
            expiresAt = map.long(Keys.EXPIRES_AT),
            timing = RoomTiming(
                turnDurationSeconds = map.int(Keys.TURN_DURATION, 0),
                totalDurationSeconds = map.int(Keys.TOTAL_DURATION, 0),
            ),
            currentTurnUserId = map.string(Keys.CURRENT_TURN_USER_ID),
            boardState = board,
            lastMoveAt = map.long(Keys.LAST_MOVE_AT),
            winnerUserId = map.string(Keys.WINNER_USER_ID),
            endReason = map.string(Keys.END_REASON)?.let {
                enumValueOrDefault(it, RoomEndReason.NORMAL)
            },
            version = map.long(Keys.VERSION),
        )
    }.getOrNull()

    /**
     * The payload written when a room is opened. Values must line up with what the database
     * rules require of a new room, otherwise the write is rejected.
     */
    fun encodeNewRoom(
        configuration: RoomConfiguration,
        host: UserProfile?,
        hostUserId: String,
        now: Long,
    ): Map<String, Any?> = mapOf(
        Keys.ROOM_NAME to configuration.roomName.trim().take(Constants.Online.ROOM_NAME_MAX_LENGTH),
        Keys.HOST_USER_ID to hostUserId,
        Keys.GUEST_USER_ID to "",
        Keys.HOST_NAME to host?.username.orEmpty(),
        Keys.HOST_RATING to (host?.rating ?: Constants.Backend.STARTING_RATING),
        Keys.VISIBILITY to configuration.visibility.name,
        Keys.STATUS to OnlineRoomStatus.WAITING.name,
        Keys.GAME_MODE to configuration.gameMode.name,
        Keys.RANKED to configuration.ranked,
        Keys.REQUIRES_PASSWORD to configuration.hasPassword,
        Keys.CREATED_AT to now,
        Keys.EXPIRES_AT to now + Constants.Online.WAITING_ROOM_EXPIRY_MILLIS,
        Keys.TURN_DURATION to configuration.timing.turnDurationSeconds,
        Keys.TOTAL_DURATION to configuration.timing.totalDurationSeconds,
        Keys.CURRENT_TURN_USER_ID to hostUserId,
        Keys.BOARD to boardCodec.encodeBoard(BoardState.initial()),
        Keys.LAST_MOVE_AT to now,
        Keys.WINNER_USER_ID to "",
        Keys.END_REASON to "",
        Keys.VERSION to 0L,
        // Public rooms are listed by this composite so the browser can filter server side.
        Keys.BROWSE_KEY to browseKey(configuration.visibility, OnlineRoomStatus.WAITING),
    )

    /**
     * Composite index value for the room browser: one `.indexOn` covers "public rooms that
     * are still waiting" without downloading every room in the database.
     */
    fun browseKey(visibility: RoomVisibility, status: OnlineRoomStatus): String =
        "${visibility.name}_${status.name}"

    private fun Map<*, *>.string(key: String): String? =
        (get(key) as? String)?.takeIf(String::isNotBlank)

    private fun Map<*, *>.long(key: String, fallback: Long = 0L): Long =
        (get(key) as? Number)?.toLong() ?: fallback

    private fun Map<*, *>.int(key: String, fallback: Int = 0): Int =
        (get(key) as? Number)?.toInt() ?: fallback

    object Keys {
        const val ROOM_NAME = "roomName"
        const val HOST_USER_ID = "hostUserId"
        const val GUEST_USER_ID = "guestUserId"
        const val HOST_NAME = "hostName"
        const val HOST_RATING = "hostRating"
        const val VISIBILITY = "visibility"
        const val STATUS = "status"
        const val GAME_MODE = "gameMode"
        const val RANKED = "ranked"
        const val REQUIRES_PASSWORD = "requiresPassword"
        const val CREATED_AT = "createdAt"
        const val EXPIRES_AT = "expiresAt"
        const val TURN_DURATION = "turnDurationSeconds"
        const val TOTAL_DURATION = "totalDurationSeconds"
        const val CURRENT_TURN_USER_ID = "currentTurnUserId"
        const val BOARD = "board"
        const val LAST_MOVE_AT = "lastMoveAt"
        const val WINNER_USER_ID = "winnerUserId"
        const val END_REASON = "endReason"
        const val VERSION = "version"
        const val BROWSE_KEY = "browseKey"

        /** Lives under roomSecrets/{code}; never written into the room itself. */
        const val PASSWORD_HASH = "passwordHash"

        /**
         * The hash a joiner offers. The rules compare it against the stored secret and then
         * it is discarded, so it never lingers in a readable node.
         */
        const val PASSWORD_ATTEMPT = "passwordAttempt"
    }
}
