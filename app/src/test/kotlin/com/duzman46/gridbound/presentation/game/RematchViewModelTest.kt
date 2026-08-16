package com.duzman46.gridbound.presentation.game

import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.online.FakeOnlineGameRepository
import com.duzman46.gridbound.online.domain.OnlineGameRepository
import com.duzman46.gridbound.online.model.OnlineLobbyResult
import com.duzman46.gridbound.online.model.OnlineRoomStatus
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.session.FakeAuthRepository
import com.duzman46.gridbound.session.FakeGameRepository
import com.duzman46.gridbound.session.FakeSocialRepository
import com.duzman46.gridbound.session.FakeUserProfileRepository
import com.duzman46.gridbound.session.SessionManager
import com.duzman46.gridbound.social.domain.SocialRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Who asks, when both players press "rematch" at the same moment.
 *
 * There is one room per finished match rather than one per tap, so the second press does not open
 * anything — it walks into the seat the first one left. Sending an invitation from there is an
 * offer of a room its recipient is already sitting in, and it is one that cannot be taken back:
 * the room is in play the instant it is sent, so the watcher hands the match over and neither the
 * refusal path nor `onCleared` is ever reached to withdraw it. Both players then carried a "wants
 * a rematch" bar across the live board for the ten minutes an invitation lives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RematchViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val online = RematchOnlineRepository()
    private val social = RecordingSocialRepository()
    private val auth = FakeAuthRepository()
    private val profiles = FakeUserProfileRepository()
    private val game = FakeGameRepository()
    private var managerScope: CoroutineScope? = null
    /** Stands in for the process-lifetime scope Hilt provides; see `CoroutineModule`. */
    private val applicationScope = CoroutineScope(SupervisorJob() + dispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        managerScope?.cancel()
        applicationScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `the device that walked into the other player's room does not invite them to it`() =
        runTest(dispatcher) {
            online.hosted = false
            val model = viewModel(signedIn())

            model.ask(PLAYED_ROOM, OPPONENT_ID, PlayerId.PLAYER_ONE)
            advanceUntilIdle()

            assertEquals(emptyList<String>(), social.rematchesSent)
            assertEquals(RematchStage.WAITING, model.uiState.value.stage)
        }

    @Test
    fun `and the device that opened it does`() = runTest(dispatcher) {
        // The other half, because a guard that simply stopped asking would leave two players
        // sitting in separate rooms waiting for a question neither of them was ever sent.
        online.hosted = true
        val model = viewModel(signedIn())

        model.ask(PLAYED_ROOM, OPPONENT_ID, PlayerId.PLAYER_ONE)
        advanceUntilIdle()

        assertEquals(listOf(REMATCH_ROOM), social.rematchesSent)
    }

    @Test
    fun `once the rematch is under way neither inbox is still offering it`() =
        runTest(dispatcher) {
            online.hosted = true
            val model = viewModel(signedIn())
            val accepted = mutableListOf<OnlineSession>()
            val collector = launch { model.accepted.collect { accepted += it } }

            model.ask(PLAYED_ROOM, OPPONENT_ID, PlayerId.PLAYER_ONE)
            advanceUntilIdle()
            // The opponent walks in, which is the only "yes" a rematch has.
            online.rooms.value = online.waitingRoom(OnlineRoomStatus.IN_PROGRESS)
            advanceUntilIdle()

            assertEquals(listOf(REMATCH_ROOM), accepted.map(OnlineSession::roomCode))
            // Both directions. This player's question to the opponent is the one they sent; the
            // opposite entry is what an older build — or the same double press read the other
            // way round — leaves in this player's own inbox, and it would otherwise hang over
            // the live board offering the game they are looking at.
            assertTrue((OWN_ID to OPPONENT_ID) in social.cleared)
            assertTrue((OPPONENT_ID to OWN_ID) in social.cleared)
            collector.cancel()
        }

    private fun viewModel(session: SessionManager) =
        RematchViewModel(online, social, session, applicationScope)

    /** A session on a real account, which is the only kind that may ask for a rematch. */
    private suspend fun TestScope.signedIn(): SessionManager {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        managerScope = scope
        val manager = SessionManager(auth, profiles, social, game, scope)
        manager.signInWithEmail("player@example.com", "longenough1")
        advanceUntilIdle()
        return manager
    }

    private companion object {
        const val PLAYED_ROOM = "AB3D5F"
        const val REMATCH_ROOM = "RM4T7H"

        /** What [FakeAuthRepository] names an email account. */
        const val OWN_ID = "email-user"
        const val OPPONENT_ID = "bob-uid"
    }
}

/**
 * A rematch that can be told which of the two devices it is standing in for.
 *
 * Everything but [rematchRoom] is the shared double's, because everything but the answer to "did
 * this device open the room or take a seat in it" behaves identically either way.
 */
private class RematchOnlineRepository(
    private val fake: FakeOnlineGameRepository = FakeOnlineGameRepository(),
) : OnlineGameRepository by fake {

    /** True for the device that got to the derived room code first. */
    var hosted = true

    val rooms get() = fake.rooms
    fun waitingRoom(status: OnlineRoomStatus) = fake.waitingRoom(status).copy(roomCode = "RM4T7H")

    override suspend fun rematchRoom(
        playedRoomCode: String,
        opponentUserId: String,
        playedSeat: PlayerId,
    ): OnlineLobbyResult = OnlineLobbyResult.Success(
        OnlineSession("RM4T7H", "email-user", playedSeat.opponent),
        hosted = hosted,
    )
}

/** Notes what reached the request channel, which is the whole of what these tests are about. */
private class RecordingSocialRepository(
    private val fake: FakeSocialRepository = FakeSocialRepository(),
) : SocialRepository by fake {

    /** The room code of every rematch invitation actually sent. */
    val rematchesSent = mutableListOf<String>()

    /** Every entry taken out of the channel, as (recipient, sender). */
    val cleared = mutableListOf<Pair<String, String>>()

    override suspend fun sendRematch(
        fromUserId: String,
        fromUsername: String,
        toUserId: String,
        roomCode: String,
        playedRoomCode: String,
    ): Outcome<Unit> {
        rematchesSent += roomCode
        return Outcome.Success(Unit)
    }

    override suspend fun clearRequest(recipientId: String, senderId: String): Outcome<Unit> {
        cleared += recipientId to senderId
        return Outcome.Success(Unit)
    }
}
