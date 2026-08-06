package com.duzman46.gridbound.online.domain

import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.game.models.GameAction
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
     * Joins the best available public room, or reports
     * [com.duzman46.gridbound.core.AppError.ROOM_NO_OPPONENT_FOUND] when there is none, so
     * the caller can offer to host instead.
     */
    suspend fun quickMatch(preferRanked: Boolean): OnlineLobbyResult

    /** Public rooms still waiting for an opponent, newest first. */
    suspend fun loadOpenRooms(): Outcome<List<OnlineRoom>>

    /**
     * The player's match still in progress, if any. Drives "return to your match" after the
     * app was killed or the network dropped.
     */
    suspend fun findResumableSession(userId: String): Outcome<OnlineSession?>

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
     * Ends the match in the caller's favour because the opponent's clock ran out. The
     * database rules re-check the deadline against the server clock, so a device with a
     * wrong or tampered clock cannot claim a win early.
     */
    suspend fun claimTurnTimeout(session: OnlineSession): Outcome<Unit>

    /**
     * Leaves without conceding. A match in progress is left intact so the player can come
     * back; a room still waiting for an opponent is closed.
     */
    suspend fun leaveRoom(session: OnlineSession)
}
