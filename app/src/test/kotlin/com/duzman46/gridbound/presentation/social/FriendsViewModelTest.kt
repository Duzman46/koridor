package com.duzman46.gridbound.presentation.social

import com.duzman46.gridbound.online.FakeOnlineGameRepository
import com.duzman46.gridbound.session.FakeAuthRepository
import com.duzman46.gridbound.session.FakeGameRepository
import com.duzman46.gridbound.session.FakeSocialRepository
import com.duzman46.gridbound.session.FakeUserProfileRepository
import com.duzman46.gridbound.session.SessionManager
import com.duzman46.gridbound.social.domain.Friend
import com.duzman46.gridbound.social.domain.FriendshipStatus
import com.duzman46.gridbound.social.domain.PlayerRequest
import com.duzman46.gridbound.social.domain.PresenceState
import com.duzman46.gridbound.social.domain.RequestKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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
 * What the friends screen does when the database stops answering.
 *
 * The three listeners it opens — relationships, presence and the request channel — report a
 * refused read or a lost connection by throwing into their flows. Uncaught in `viewModelScope`
 * that is not an error state but the end of the process, so the failing case is asserted here
 * rather than left to a rules deployment to find. The test would fail with the very exception
 * the app would have died of, which is the point.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
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
    fun `a listener that throws leaves the screen standing`() = runTest(dispatcher) {
        social.listenerFailure = IllegalStateException("permission denied")
        val model = FriendsViewModel(social, FakeOnlineGameRepository(), signedIn())
        advanceUntilIdle()

        // Nothing known about any of the three, which is what the screen showed before the
        // reads landed anyway. The assertions matter less than getting this far at all.
        assertEquals(emptyList<Friend>(), model.uiState.value.friends)
        assertEquals(emptyMap<String, PresenceState>(), model.uiState.value.presence)
        assertEquals(emptyList<PlayerRequest>(), model.uiState.value.invites)
    }

    @Test
    fun `and the same three still feed the screen when they answer`() = runTest(dispatcher) {
        // The other half: a guard that swallowed the flows rather than their failures would
        // leave a friends screen that is permanently empty and never says why.
        social.friendships.value = listOf(
            Friend("bob-uid", "bob", "avatar_02", 1200, FriendshipStatus.FRIENDS),
        )
        social.presence.value = mapOf("bob-uid" to PresenceState.ONLINE)
        social.requests.value = listOf(
            PlayerRequest("bob-uid", "bob", RequestKind.GAME_INVITE, "AB3D5F"),
        )

        val model = FriendsViewModel(social, FakeOnlineGameRepository(), signedIn())
        advanceUntilIdle()

        assertEquals(listOf("bob-uid"), model.uiState.value.onlineFriends.map(Friend::userId))
        assertEquals(listOf("bob-uid"), model.uiState.value.invites.map(PlayerRequest::fromUserId))
    }

    @Test
    fun `a refusal on one read is not the end of the next`() = runTest(dispatcher) {
        // A caught listener has to leave the flow it was collected through able to speak again,
        // or the first blip of the session costs the screen for as long as it is open.
        social.listenerFailure = IllegalStateException("permission denied")
        val session = signedIn()
        val model = FriendsViewModel(social, FakeOnlineGameRepository(), session)
        advanceUntilIdle()
        assertTrue(model.uiState.value.friends.isEmpty())

        social.listenerFailure = null
        social.friendships.value = listOf(
            Friend("bob-uid", "bob", "avatar_02", 1200, FriendshipStatus.FRIENDS),
        )
        // The next change of session is what resubscribes, and signing out and in is one.
        session.signOut()
        advanceUntilIdle()
        session.signInWithEmail("player@example.com", "longenough1")
        advanceUntilIdle()

        assertEquals(listOf("bob-uid"), model.uiState.value.friends.map(Friend::userId))
    }

    /** A session on a real account, which is the only kind the friends screen listens for. */
    private suspend fun TestScope.signedIn(): SessionManager {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        managerScope = scope
        val manager = SessionManager(auth, profiles, social, game, scope)
        manager.signInWithEmail("player@example.com", "longenough1")
        advanceUntilIdle()
        return manager
    }
}
