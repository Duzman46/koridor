package com.duzman46.gridbound.online.domain

import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.online.model.MatchmakingState
import com.duzman46.gridbound.online.model.OnlineLobbyResult
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.online.model.RoomConfiguration
import kotlinx.coroutines.flow.Flow

interface OnlineGameRepository {
    val isConfigured: Boolean

    suspend fun createRoom(configuration: RoomConfiguration): OnlineLobbyResult

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
     * Ends this player's matches that nobody has moved in for
     * [com.duzman46.gridbound.core.Constants.Online.IDLE_FORFEIT_MILLIS], awarding each to
     * whichever seat is not on the clock — which may well be the opponent's.
     *
     * Swept on the way into the lobby, so a walked-away match is settled by the next person
     * to open the app rather than sitting open forever.
     */
    suspend fun closeIdleMatches(userId: String): Outcome<Unit>

    fun observeRoom(roomCode: String): Flow<OnlineRoom>

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
