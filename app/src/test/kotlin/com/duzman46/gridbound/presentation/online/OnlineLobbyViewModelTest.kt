package com.duzman46.gridbound.presentation.online

import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.online.FakeOnlineGameRepository
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineRoomStatus
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `a room that opens reaches the browser without anyone asking`() = runTest(dispatcher) {
        // The browser used to re-ask every fifteen seconds, so a room opened one second after a
        // poll stayed invisible for fourteen and was usually taken by the time it appeared.
        // Nothing sits between the room being written and it being on screen now.
        val model = viewModel()
        model.watchOpenRooms()
        assertEquals(emptyList<OnlineRoom>(), model.uiState.value.openRooms)

        val opened = repository.waitingRoom(OnlineRoomStatus.WAITING)
        repository.openRooms.value = listOf(opened)

        assertEquals(listOf(opened), model.uiState.value.openRooms)
    }

    @Test
    fun `a room that fills leaves the browser the same way`() = runTest(dispatcher) {
        val model = viewModel()
        model.watchOpenRooms()
        repository.openRooms.value = listOf(repository.waitingRoom(OnlineRoomStatus.WAITING))
        assertEquals(1, model.uiState.value.openRooms.size)

        repository.openRooms.value = emptyList()

        assertEquals(emptyList<OnlineRoom>(), model.uiState.value.openRooms)
    }

    @Test
    fun `nothing is watched until the lobby is on screen`() = runTest(dispatcher) {
        // A live listener is a query the database keeps synced for as long as it is attached.
        // Started in init, it would go on working through the whole match the player walked
        // into — which is worse than the poll it replaced, not better.
        val model = viewModel()

        repository.openRooms.value = listOf(repository.waitingRoom(OnlineRoomStatus.WAITING))

        assertEquals(emptyList<OnlineRoom>(), model.uiState.value.openRooms)
    }

    @Test
    fun `and nothing is watched once it leaves`() = runTest(dispatcher) {
        val model = viewModel()
        model.watchOpenRooms()
        model.stopWatchingRooms()

        repository.openRooms.value = listOf(repository.waitingRoom(OnlineRoomStatus.WAITING))

        assertEquals(emptyList<OnlineRoom>(), model.uiState.value.openRooms)
    }

    @Test
    fun `the spinner gives up rather than turning forever`() = runTest(dispatcher) {
        // Firebase's listener does not fail when it cannot reach the database — it simply never
        // fires. Left alone, that is an indicator animating at sixty frames a second, for as
        // long as the player sits there, to say nothing.
        repository.roomsAnswer = false
        val model = viewModel()
        model.watchOpenRooms()
        assertTrue(model.uiState.value.isLoadingRooms)

        advanceTimeBy(Constants.Online.ROOM_BROWSER_FIRST_ANSWER_MILLIS + 1)

        assertFalse(model.uiState.value.isLoadingRooms)
    }
}
