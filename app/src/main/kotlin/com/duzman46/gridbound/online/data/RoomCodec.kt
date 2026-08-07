package com.duzman46.gridbound.online.data

import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.online.model.MatchChatEntry
import com.duzman46.gridbound.online.model.MatchMessage
import com.duzman46.gridbound.online.model.OnlineGameMode
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineRoomStatus
import com.duzman46.gridbound.online.model.RoomConfiguration
import com.duzman46.gridbound.online.model.RoomEndReason
import com.duzman46.gridbound.online.model.RoomTiming
import com.duzman46.gridbound.online.model.RoomVisibility
import com.duzman46.gridbound.profile.domain.UserProfile
import com.duzman46.gridbound.util.enumValueOrDefault
import com.google.firebase.database.ServerValue
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
            // Only the keys named here are read, so a room stored by an older build carries
            // fields this one has never heard of and decodes exactly the same.
            timing = RoomTiming(turnDurationSeconds = map.int(Keys.TURN_DURATION, 0)),
            currentTurnUserId = map.string(Keys.CURRENT_TURN_USER_ID),
            boardState = board,
            lastMoveAt = map.long(Keys.LAST_MOVE_AT),
            winnerUserId = map.string(Keys.WINNER_USER_ID),
            endReason = map.string(Keys.END_REASON)?.let {
                enumValueOrDefault(it, RoomEndReason.NORMAL)
            },
            // A room written before seats existed always had its host in seat one, because
            // that was the only shape the protocol had. The absent field means exactly that.
            hostSeat = enumValueOrDefault(map.string(Keys.HOST_SEAT), PlayerId.PLAYER_ONE),
            version = map.long(Keys.VERSION),
            chat = decodeChat(map[Keys.CHAT]),
        )
    }.getOrNull()

    /**
     * The messages stored under the room, keyed by the player who said each one.
     *
     * A key this build does not recognise is dropped rather than shown as a blank bubble: the
     * vocabulary can only ever grow, so an unknown key is a phone running a newer release and
     * saying something this one has no words for.
     */
    private fun decodeChat(value: Any?): List<MatchChatEntry> {
        val entries = value as? Map<*, *> ?: return emptyList()
        return entries.mapNotNull { (userId, stored) ->
            val fields = stored as? Map<*, *> ?: return@mapNotNull null
            val message = MatchMessage.forKey(fields.string(Keys.CHAT_MESSAGE))
                ?: return@mapNotNull null
            MatchChatEntry(
                userId = userId as? String ?: return@mapNotNull null,
                message = message,
                sentAt = fields.long(Keys.CHAT_SENT_AT),
            )
        }
    }

    /**
     * One player's message, written under their own id.
     *
     * The stamp is the server's, and the rules insist on it: they refuse a message that
     * arrives too soon after the last one, and a device that could date its own messages
     * could date them backwards and send as many as it liked.
     */
    fun encodeMessage(message: MatchMessage): Map<String, Any?> = mapOf(
        Keys.CHAT_MESSAGE to message.name,
        Keys.CHAT_SENT_AT to ServerValue.TIMESTAMP,
    )

    /**
     * The payload written when a room is opened. Values must line up with what the database
     * rules require of a new room, otherwise the write is rejected.
     *
     * @param hostSeat settled by the caller rather than here, because the session handed back
     *   to the host has to name the same seat this payload commits to.
     */
    fun encodeNewRoom(
        configuration: RoomConfiguration,
        host: UserProfile?,
        hostUserId: String,
        hostSeat: PlayerId,
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
        // A host who took seat two cannot be on the clock, because seat one opens and nobody
        // holds it yet. The empty string is the same "no one" the room ends on.
        Keys.CURRENT_TURN_USER_ID to if (hostSeat == PlayerId.PLAYER_ONE) hostUserId else "",
        Keys.BOARD to boardCodec.encodeBoard(BoardState.initial()),
        Keys.LAST_MOVE_AT to now,
        Keys.WINNER_USER_ID to "",
        Keys.END_REASON to "",
        Keys.VERSION to 0L,
        Keys.HOST_SEAT to hostSeat.name,
        // Public rooms are listed by this composite so the browser can filter server side.
        Keys.BROWSE_KEY to browseKey(configuration.visibility, OnlineRoomStatus.WAITING),
    )

    /**
     * The payload for a room matchmaking produced.
     *
     * It opens straight into [OnlineRoomStatus.IN_PROGRESS] because the pairing already found
     * both players: there is no free seat and nothing to wait for. Neither of them chose a
     * colour, so [hostSeat] was drawn — and drawn by the device writing this, because the
     * room is the only place an answer both phones read can live.
     *
     * Never listed: the browser exists to find a room with a seat going spare, and this has
     * none. It is also unprotected — a password is a host closing their own room to strangers,
     * and there is no host here in that sense.
     *
     * @param expiresAt the only field the handset's own clock decides, because nothing reads
     *   it but the sweep that eventually clears the room away.
     */
    fun encodePairedRoom(
        host: UserProfile?,
        hostUserId: String,
        guestUserId: String,
        hostSeat: PlayerId,
        ranked: Boolean,
        expiresAt: Long,
    ): Map<String, Any?> = mapOf(
        Keys.ROOM_NAME to "",
        Keys.HOST_USER_ID to hostUserId,
        Keys.GUEST_USER_ID to guestUserId,
        Keys.HOST_NAME to host?.username.orEmpty(),
        Keys.HOST_RATING to (host?.rating ?: Constants.Backend.STARTING_RATING),
        Keys.VISIBILITY to RoomVisibility.PRIVATE.name,
        Keys.STATUS to OnlineRoomStatus.IN_PROGRESS.name,
        Keys.GAME_MODE to OnlineGameMode.CLASSIC.name,
        Keys.RANKED to ranked,
        Keys.REQUIRES_PASSWORD to false,
        // Server-stamped, and this one is load-bearing: the player at the other end tells
        // this room apart from a match they walked out of earlier by asking whether it was
        // made after they queued, and both stamps have to come off the same clock.
        Keys.CREATED_AT to ServerValue.TIMESTAMP,
        Keys.EXPIRES_AT to expiresAt,
        Keys.TURN_DURATION to RoomTiming.DEFAULT_TURN_SECONDS,
        Keys.CURRENT_TURN_USER_ID to
            if (hostSeat == PlayerId.PLAYER_ONE) hostUserId else guestUserId,
        Keys.BOARD to boardCodec.encodeBoard(BoardState.initial()),
        // Server-stamped too, because this starts the opening player's clock and the phone
        // that wrote the room must not be able to shorten the other player's first turn.
        Keys.LAST_MOVE_AT to ServerValue.TIMESTAMP,
        Keys.WINNER_USER_ID to "",
        Keys.END_REASON to "",
        Keys.VERSION to 0L,
        Keys.HOST_SEAT to hostSeat.name,
        Keys.BROWSE_KEY to browseKey(RoomVisibility.PRIVATE, OnlineRoomStatus.IN_PROGRESS),
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
        const val CURRENT_TURN_USER_ID = "currentTurnUserId"
        const val BOARD = "board"
        const val LAST_MOVE_AT = "lastMoveAt"
        const val WINNER_USER_ID = "winnerUserId"
        const val END_REASON = "endReason"
        const val VERSION = "version"
        const val BROWSE_KEY = "browseKey"
        const val HOST_SEAT = "hostSeat"

        /** chat/{uid} — one slot per player, torn down with the room around it. */
        const val CHAT = "chat"
        const val CHAT_MESSAGE = "key"
        const val CHAT_SENT_AT = "at"

        /** Lives under roomSecrets/{code}; never written into the room itself. */
        const val PASSWORD_HASH = "passwordHash"

        /**
         * The hash a joiner offers. The rules compare it against the stored secret and then
         * it is discarded, so it never lingers in a readable node.
         */
        const val PASSWORD_ATTEMPT = "passwordAttempt"
    }
}
