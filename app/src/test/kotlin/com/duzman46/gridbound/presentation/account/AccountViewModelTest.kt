package com.duzman46.gridbound.presentation.account

import com.duzman46.gridbound.session.FakeAuthRepository
import com.duzman46.gridbound.session.FakeGameRepository
import com.duzman46.gridbound.session.FakeSocialRepository
import com.duzman46.gridbound.session.FakeUserProfileRepository
import com.duzman46.gridbound.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
 * Covers the one answer this view model owes the navigator: whether the account the player
 * now has still has to be named. It is worked out from the profile the write returned rather
 * than from the session, which is still catching up with an identity that changed a moment
 * ago — and getting it wrong means an account going public under a name the app invented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val auth = FakeAuthRepository()
    private val profiles = FakeUserProfileRepository()
    private val social = FakeSocialRepository()
    private val game = FakeGameRepository()

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var sessionManager: SessionManager
    private var managerScope: CoroutineScope? = null

    @Before
    fun setUp() {
        // The view model launches on Dispatchers.Main, and its work has to be finished by the
        // time an assertion reads the events it emitted.
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        managerScope?.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `linking a guest ends at the name question they have never been asked`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            sessionManager.enterGuestMode()
            val events = collectEvents(viewModel)

            viewModel.setEmail("player@example.com")
            viewModel.setPassword("longenough1")
            viewModel.linkWithEmail()

            // The name it carries is the one handed to it as a guest, and it is about to be
            // the name other players see.
            assertEquals(listOf(AccountEvent.Linked(needsUsername = true)), events)
            assertNotNull(viewModel.uiState.value.info)
        }

    @Test
    fun `handing over to an account that named itself asks nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()
        sessionManager.enterGuestMode()
        profiles.seedExistingAccount(username = "Koray", rating = 1450)
        auth.hasCredentialForExistingAccount = true
        val events = collectEvents(viewModel)

        viewModel.signInToExistingAccount()
        advanceUntilIdle()

        assertEquals(listOf(AccountEvent.Linked(needsUsername = false)), events)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `a hand-over with nothing behind it is reported and moves nobody`() = runTest(dispatcher) {
        val viewModel = viewModel()
        sessionManager.enterGuestMode()
        val events = collectEvents(viewModel)

        viewModel.signInToExistingAccount()
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        assertNotNull(viewModel.uiState.value.error)
        assertTrue(sessionManager.state.value.isGuest)
    }

    @Test
    fun `a hand-over the session never carries is never announced as one that worked`() =
        runTest(dispatcher) {
            // Every call answers and the player is still not in that account. Announcing it
            // anyway is what put "signed in" over a screen still offering to keep a guest's
            // progress, and it is the one outcome this screen must never produce.
            val viewModel = viewModel()
            sessionManager.enterGuestMode()
            profiles.seedExistingAccount(username = "Koray", rating = 1450)
            auth.hasCredentialForExistingAccount = true
            auth.existingAccountSignInStrandsSession = true
            val events = collectEvents(viewModel)

            viewModel.signInToExistingAccount()
            advanceUntilIdle()

            assertTrue(events.isEmpty())
            assertNotNull(viewModel.uiState.value.error)
            assertNull(viewModel.uiState.value.info)
        }

    @Test
    fun `staying a guest costs the guest nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()
        sessionManager.enterGuestMode()
        auth.hasCredentialForExistingAccount = true
        val events = collectEvents(viewModel)

        viewModel.dismissExistingAccountWarning()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.existingAccountWarning)
        assertTrue(events.isEmpty())
        assertTrue(sessionManager.state.value.isGuest)
        assertNotNull(sessionManager.state.value.profile)
        assertTrue(profiles.deletedUserIds.isEmpty())
        assertFalse(sessionManager.canSignInToExistingAccount)
    }

    /**
     * The event stream has no replay, so collection has to be running before the call that
     * emits. It runs in the background scope, which never finishes on its own and is torn
     * down with the test; by the time the list is read the unconfined dispatcher has run
     * every emission to completion.
     */
    private fun TestScope.collectEvents(viewModel: AccountViewModel): List<AccountEvent> {
        val events = mutableListOf<AccountEvent>()
        backgroundScope.launch { viewModel.events.toList(events) }
        return events
    }

    private fun viewModel(): AccountViewModel {
        val scope = CoroutineScope(dispatcher)
        managerScope = scope
        sessionManager = SessionManager(auth, profiles, social, game, scope)
        return AccountViewModel(sessionManager)
    }
}
