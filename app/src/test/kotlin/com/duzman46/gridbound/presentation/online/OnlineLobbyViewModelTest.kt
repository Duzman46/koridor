package com.duzman46.gridbound.presentation.online

import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.online.domain.OnlineGameRepository
import com.duzman46.gridbound.online.model.MatchMessage
import com.duzman46.gridbound.online.model.MatchmakingState
import com.duzman46.gridbound.online.model.OnlineLobbyResult
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineRoomStatus
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.online.model.RoomConfiguration
import com.duzman46.gridbound.online.model.RoomTiming
import com.duzman46.gridbound.online.model.RoomVisibility
import com.duzman46.gridbound.session.FakeAuthRepository
import com.duzman46.gridbound.session.FakeGameRepository
import com.duzman46.gridbound.session.FakeSocialRepository
import com.duzman46.gridbound.session.FakeUserProfileRepository
import com.duzman46.gridbound.session.SessionManager
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The lobby's two answers that nothing else can give.
 *
 * The colour the create form shows is the colour the room is written with, and the waiting
 * panel is told when the room it is waiting on stops existing. Both used to be silently
 * untrue: the seat was drawn at write time while the picker had already claimed blue, and a
 * deleted room was dropped rather than delivered, so the panel went on offering a code that
 * opened nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnlineLobbyViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeOnlineGameRepository()
    private val social = FakeSocialRepository()
    private var managerScope: CoroutineScope? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        managerScope?.cancel()
        Dispatchers.resetMain()
    }

    private fun viewModel(random: Random = Random(1)): OnlineLobbyViewModel {
        val scope = TestScope(dispatcher).also { managerScope = it }
        return OnlineLobbyViewModel(
            repository = repository,
            socialRepository = social,
            sessionManager = SessionManager(
                authRepository = FakeAuthRepository(),
                profileRepository = FakeUserProfileRepository(),
                socialRepository = social,
                gameRepository = FakeGameRepository(),
                scope = scope,
            ),
            random = random,
        )
    }

    @Test
    fun `the create form opens with a seat already drawn`() = runTest(dispatcher) {
        // Null renders exactly as a chosen blue, so leaving it for the repository to draw at
        // write time told every host who never touched the picker that they had blue.
        assertNotNull(viewModel().uiState.value.configuration.hostSeat)
    }

    @Test
    fun `the drawn seat is the seat the room is created with`() = runTest(dispatcher) {
        val model = viewModel()
        val shown = model.uiState.value.configuration.hostSeat
        model.createRoom()
        assertEquals(shown, repository.created.single().hostSeat)
    }

    @Test
    fun `both colours come up`() = runTest(dispatcher) {
        // A fair coin rather than a constant dressed up as one: the picker is honest either
        // way, but a host who never gets red is a host who always opens.
        val drawn = (0..40).map { seed ->
            viewModel(Random(seed)).uiState.value.configuration.hostSeat
        }.toSet()
        assertEquals(PlayerId.entries.toSet(), drawn)
    }

    @Test
    fun `a room deleted under the waiting panel takes the panel down with it`() =
        runTest(dispatcher) {
            val model = viewModel()
            model.createRoom()
            assertNotNull(model.uiState.value.waitingSession)

            repository.rooms.value = null

            assertNull(model.uiState.value.waitingSession)
            assertNotNull(model.uiState.value.message)
        }

    @Test
    fun `and so does one that expires without ever being played`() = runTest(dispatcher) {
        val model = viewModel()
        model.createRoom()
        repository.rooms.value = repository.waitingRoom(OnlineRoomStatus.EXPIRED)
        assertNull(model.uiState.value.waitingSession)
    }

    @Test
    fun `a rival walking in opens the board rather than clearing the panel`() =
        runTest(dispatcher) {
            val model = viewModel()
            model.createRoom()
            repository.rooms.value = repository.waitingRoom(OnlineRoomStatus.IN_PROGRESS)
                .copy(guestUserId = "bob-uid")
            assertNotNull(model.uiState.value.waitingSession)
        }
}

/**
 * A room the lobby can be handed, and a hand on the flow it watches it through.
 *
 * The room is published as a StateFlow so a test can delete it — which is the case that
 * matters here and the one no other double can produce.
 */
private class FakeOnlineGameRepository : OnlineGameRepository {
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
        gameMode = com.duzman46.gridbound.online.model.OnlineGameMode.CLASSIC,
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

    override suspend fun loadOpenRooms(): Outcome<List<OnlineRoom>> = Outcome.Success(emptyList())

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
