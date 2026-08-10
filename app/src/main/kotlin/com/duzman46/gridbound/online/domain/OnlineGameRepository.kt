package com.duzman46.gridbound.online.domain

import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.online.model.MatchMessage
import com.duzman46.gridbound.online.model.MatchmakingState
import com.duzman46.gridbound.online.model.OnlineLobbyResult
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.online.model.RoomConfiguration
import kotlinx.coroutines.flow.Flow

interface OnlineGameRepository {
    val isConfigured: Boolean

    suspend fun createRoom(configuration: RoomConfiguration): OnlineLobbyResult

    /**
     * Opens — or walks into — the one room the match just played is entitled to a rerun in.
     *
     * Both players are offered the rematch button and both may press it in the same second, so
     * the room cannot be a fresh code each time: two rooms means two invitations crossing, each
     * player accepting the other's, and the pair split across a board apiece with an absent
     * rival and a clock running down. The code is derived from the finished match instead, so
     * both devices aim at one node, whichever of them gets there first hosts, and the other
     * takes the free seat.
     *
     * @param playedSeat the seat held in the match just finished. It is given up here — blue
     *   opens, and a rematch that returned the first move to whoever asked for it would be a
     *   rematch on better terms than the match itself.
     */
    suspend fun rematchRoom(
        playedRoomCode: String,
        opponentUserId: String,
        playedSeat: PlayerId,
    ): OnlineLobbyResult

    /** @param password required only when the room is protected. */
    suspend fun joinRoom(roomCode: String, password: String = ""): OnlineLobbyResult

    /**
     * Joins the matchmaking list and stays in it until a rival is found.
     *
     * Deliberately not a room. Quick match used to look for an open room and open one when it
     * found none, so two players who pressed it at the same moment each ended up hosting and
     * waiting in a room of their own, invisible to each other forever. Now nobody opens
     * anything: both write themselves into a list, and the room appears once there is somebody
     * to put in it.
     *
     * Collecting the flow is what holds the place in the list. Stopping — cancelling, leaving
     * the screen, the process dying — gives it up, so nobody is ever paired against an app
     * that is not running.
     */
    fun matchmake(ranked: Boolean): Flow<MatchmakingState>

    /** Public rooms still waiting for an opponent, newest first. */
    suspend fun loadOpenRooms(): Outcome<List<OnlineRoom>>

    /**
     * The same list, and every change to it as it happens.
     *
     * The browser used to re-ask for the whole list every fifteen seconds, which is wrong twice
     * over. It is too slow — a room opened one second after a poll stays invisible for the next
     * fourteen, and by the time it appears somebody else has usually taken it. And it is too
     * expensive — a full indexed query every fifteen seconds for as long as the lobby is open,
     * whether or not a single room changed, on a screen a player may sit on for minutes.
     *
     * One listener replaces both. The database pushes: a room that opens appears at once, and a
     * room that fills disappears the moment it fills. Nothing is asked for when nothing changes.
     */
    fun observeOpenRooms(): Flow<List<OnlineRoom>>

    /**
     * Ends this player's matches that nobody has moved in for
     * [com.duzman46.gridbound.core.Constants.Online.IDLE_FORFEIT_MILLIS], awarding each to
     * whichever seat is not on the clock — which may well be the opponent's.
     *
     * Swept on the way into the lobby, so a walked-away match is settled by the next person
     * to open the app rather than sitting open forever.
     */
    suspend fun closeIdleMatches(userId: String): Outcome<Unit>

    /**
     * The room as it stands, and every change to it. Null once the room is not there any more.
     *
     * A deletion used to be dropped rather than delivered, which is the one ending a room
     * waiting for an opponent actually has: the sweep removes such a room outright rather than
     * marking it, so every panel waiting on one waited forever, showing a code nobody could
     * join and offering no hint that anything had happened.
     */
    fun observeRoom(roomCode: String): Flow<OnlineRoom?>

    /**
     * Sends a move. Returns false when the room moved on underneath us, which means the
     * caller should resynchronise rather than retry.
     */
    suspend fun submitAction(
        session: OnlineSession,
        expectedVersion: Long,
        action: GameAction,
    ): Boolean

    /** Concedes the match to the opponent. */
    suspend fun resign(session: OnlineSession): Outcome<Unit>

    /**
     * Says one of the fixed [MatchMessage] values to the other seat.
     *
     * Written into the room under this player's own id, so it is swept away with the room and
     * there is no second lifetime to manage. Whether it is allowed at all is decided by the
     * database rules, which refuse a key outside the vocabulary, a message written under the
     * other player's id, and one sent too soon after the last.
     */
    suspend fun sendMessage(session: OnlineSession, message: MatchMessage): Outcome<Unit>

    /**
     * Ends the match once the move clock has run out, handing the win to whichever seat is
     * not on it. Either player may call it and both of their devices do, so the result does
     * not wait on the winner's phone being awake; the room settles once and the second call
     * finds nothing left to decide.
     *
     * The database rules re-check the deadline against the server clock, so a device with a
     * wrong or tampered clock cannot end a turn early.
     */
    suspend fun resolveTurnTimeout(session: OnlineSession): Outcome<Unit>

    /**
     * Closes this match if nobody has moved in
     * [com.duzman46.gridbound.core.Constants.Online.IDLE_FORFEIT_MILLIS], handing the win to
     * whichever seat is not on the clock. The backstop for a room whose move clock is switched
     * off, where nothing else would ever settle it.
     */
    suspend fun resolveIdleMatch(session: OnlineSession): Outcome<Unit>

    /**
     * Leaves without conceding. A match in progress is left intact so the player can come
     * back; a room still waiting for an opponent is closed.
     */
    suspend fun leaveRoom(session: OnlineSession)
}
