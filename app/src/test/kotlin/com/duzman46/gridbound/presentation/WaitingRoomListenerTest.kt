package com.duzman46.gridbound.presentation

import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.online.FakeOnlineGameRepository
import com.duzman46.gridbound.online.domain.OnlineGameRepository
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.presentation.game.RematchStage
import com.duzman46.gridbound.presentation.game.RematchViewModel
import com.duzman46.gridbound.presentation.online.OnlineLobbyViewModel
import com.duzman46.gridbound.presentation.social.FriendsViewModel
import com.duzman46.gridbound.session.FakeAuthRepository
import com.duzman46.gridbound.session.FakeGameRepository
import com.duzman46.gridbound.session.FakeSocialRepository
import com.duzman46.gridbound.session.FakeUserProfileRepository
import com.duzman46.gridbound.session.SessionManager
import com.duzman46.gridbound.social.domain.Friend
import com.duzman46.gridbound.social.domain.FriendshipStatus
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
 * The three screens that open a room and then wait in it, when the listener holding that wait
 * dies underneath them.
 *
 * This is one bug written out three times. The lobby waits for a stranger, the friends screen
 * waits for one named friend, the winner screen waits for a rematch — and all three watched the
 * room through `observeRoom(...).catch { }`, where the catch cleared some state and said
 * nothing. The sibling branch three lines below each of them, the one that fires when the room
 * is deleted, has always surfaced [AppError.ROOM_NOT_FOUND] properly; the failure branch was
 * the one nobody finished. The rematch was the worst of the three: it logged and then left
 * [RematchStage.WAITING] on screen with no collector alive behind it, which is a spinner that
 * cannot stop.
 *
 * The shape of these assertions is borrowed from `FriendsViewModelTest`, one screen over: get
 * far enough that the failing flow has been collected, then insist the screen is somewhere a
 * player can act from. Each one also insists the room was closed, because a room nobody is
 * watching any more is a seat the opponent still walks into.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WaitingRoomListenerTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val online = RefusingRoomListener()
    private val social = FakeSocialRepository()
    private val auth = FakeAuthRepository()
    private val profiles = FakeUserProfileRepository()
    private val game = FakeGameRepository()
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

    @Test
    fun `a listener that dies under the lobby takes the waiting panel with it`() =
        runTest(dispatcher) {
            val model = OnlineLobbyViewModel(
                repository = online,
                socialRepository = social,
                sessionManager = session(),
                random = Random(1),
            )

            model.createRoom()
            advanceUntilIdle()

            // Not still waiting on a room nothing is listening to, and not silent about it.
            assertNull(model.uiState.value.waitingSession)
            assertEquals(AppError.UNKNOWN.message, model.uiState.value.message)
            assertEquals(listOf("AB3D5F"), online.left)
        }

    @Test
    fun `a listener that dies under an invitation takes the invitation with it`() =
        runTest(dispatcher) {
            val model = FriendsViewModel(social, online, signedIn())

            model.inviteToGame(
                Friend("bob-uid", "bob", "avatar_02", 1200, FriendshipStatus.FRIENDS),
            )
            advanceUntilIdle()

            assertNull(model.uiState.value.hostedInvite)
            assertEquals(AppError.UNKNOWN.message, model.uiState.value.message)
            assertEquals(listOf("AB3D5F"), online.left)
        }

    @Test
    fun `a listener that dies under a rematch stops the wait instead of spinning forever`() =
        runTest(dispatcher) {
            // Stands in for the application scope the real one is given, which outlives the
            // view model so a room can still be closed on the way out.
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val model = RematchViewModel(online, social, signedIn(), scope)

            model.ask("PLAYED", "bob-uid", PlayerId.PLAYER_ONE)
            advanceUntilIdle()

            // WAITING with nothing behind it was the whole defect. IDLE is a button the player
            // can press again, which is the honest place to leave them.
            assertEquals(RematchStage.IDLE, model.uiState.value.stage)
            assertNotNull(model.uiState.value.message)
            assertEquals(listOf("AB3D5F"), online.left)
            scope.cancel()
        }

    private suspend fun TestScope.session(): SessionManager {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        managerScope = scope
        return SessionManager(auth, profiles, social, game, scope)
    }

    /** A session on a real account, which every path but the lobby's needs to get going. */
    private suspend fun TestScope.signedIn(): SessionManager = session().also {
        it.signInWithEmail("player@example.com", "longenough1")
        advanceUntilIdle()
    }
}

/**
 * The room double, with the one listener these screens live on refusing to answer.
 *
 * Delegation rather than a flag on the shared fake: what is being modelled here is a listener
 * the database closed — a refused read, a rules deployment, a token that went stale mid-wait —
 * and every other call on the repository has to go on working normally for the screens to reach
 * the point where they attach it at all.
 */
private class RefusingRoomListener(
    private val delegate: FakeOnlineGameRepository = FakeOnlineGameRepository(),
) : OnlineGameRepository by delegate {

    /** Rooms closed behind the screens, read off the double underneath. */
    val left: List<String> get() = delegate.left

    override fun observeRoom(roomCode: String): Flow<OnlineRoom?> = flow {
        throw IllegalStateException("permission denied")
    }
}
