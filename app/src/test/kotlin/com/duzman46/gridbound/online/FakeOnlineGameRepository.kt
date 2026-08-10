package com.duzman46.gridbound.online

import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.online.domain.OnlineGameRepository
import com.duzman46.gridbound.online.model.MatchMessage
import com.duzman46.gridbound.online.model.MatchmakingState
import com.duzman46.gridbound.online.model.OnlineGameMode
import com.duzman46.gridbound.online.model.OnlineLobbyResult
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineRoomStatus
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.online.model.RoomConfiguration
import com.duzman46.gridbound.online.model.RoomTiming
import com.duzman46.gridbound.online.model.RoomVisibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * A room the lobby can be handed, and a hand on the flow it watches it through.
 *
 * The room is published as a StateFlow so a test can delete it — which is the case no other
 * double can produce, and the one the waiting panel was getting wrong.
 */
class FakeOnlineGameRepository : OnlineGameRepository {
    override val isConfigured: Boolean = true

    val created = mutableListOf<CreatedRoom>()
    val rooms = MutableStateFlow<OnlineRoom?>(waitingRoom(OnlineRoomStatus.WAITING))

    data class CreatedRoom(val configuration: RoomConfiguration, val hostSeat: PlayerId?)

    fun waitingRoom(status: OnlineRoomStatus): OnlineRoom = OnlineRoom(
        roomId = "AB3D5F",
        roomCode = "AB3D5F",
        roomName = "",
        hostUserId = "alice-uid",
        guestUserId = "",
        hostName = "alice",
        hostRating = 1000,
        visibility = RoomVisibility.PUBLIC,
        status = status,
        gameMode = OnlineGameMode.CLASSIC,
        ranked = true,
        requiresPassword = false,
        createdAt = 1L,
        expiresAt = 2L,
        timing = RoomTiming(),
        currentTurnUserId = "alice-uid",
        boardState = BoardState.initial(),
        lastMoveAt = 1L,
        winnerUserId = "",
        endReason = null,
        hostSeat = PlayerId.PLAYER_ONE,
        version = 0L,
    )

    override suspend fun createRoom(configuration: RoomConfiguration): OnlineLobbyResult {
        created += CreatedRoom(configuration, configuration.hostSeat)
        return OnlineLobbyResult.Success(
            OnlineSession("AB3D5F", "alice-uid", configuration.hostSeat ?: PlayerId.PLAYER_ONE),
        )
    }

    override suspend fun rematchRoom(
        playedRoomCode: String,
        opponentUserId: String,
        playedSeat: PlayerId,
    ): OnlineLobbyResult =
        OnlineLobbyResult.Success(OnlineSession("AB3D5F", "alice-uid", playedSeat.opponent))

    override suspend fun joinRoom(roomCode: String, password: String): OnlineLobbyResult =
        OnlineLobbyResult.Success(OnlineSession(roomCode, "alice-uid", PlayerId.PLAYER_TWO))

    override fun matchmake(ranked: Boolean): Flow<MatchmakingState> =
        flowOf(MatchmakingState.Searching)

    override suspend fun loadOpenRooms(): Outcome<List<OnlineRoom>> = Outcome.Success(openRooms.value)

    override fun observeOpenRooms(): Flow<List<OnlineRoom>> = openRooms

    /** The browser's live list. Push to it to make a room appear the way the database would. */
    val openRooms = MutableStateFlow<List<OnlineRoom>>(emptyList())

    override suspend fun closeIdleMatches(userId: String): Outcome<Unit> = Outcome.Success(Unit)

    override fun observeRoom(roomCode: String): Flow<OnlineRoom?> = rooms

    override suspend fun submitAction(
        session: OnlineSession,
        expectedVersion: Long,
        action: GameAction,
    ): Boolean = true

    override suspend fun resign(session: OnlineSession): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun sendMessage(
        session: OnlineSession,
        message: MatchMessage,
    ): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun resolveTurnTimeout(session: OnlineSession): Outcome<Unit> =
        Outcome.Success(Unit)

    override suspend fun resolveIdleMatch(session: OnlineSession): Outcome<Unit> =
        Outcome.Success(Unit)

    override suspend fun leaveRoom(session: OnlineSession) = Unit
}
