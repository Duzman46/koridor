package com.duzman46.gridbound.session

import com.duzman46.gridbound.auth.domain.AccountType
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.core.UsernameRules
import com.duzman46.gridbound.domain.models.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    private val auth = FakeAuthRepository()
    private val profiles = FakeUserProfileRepository()
    private val social = FakeSocialRepository()
    private val game = FakeGameRepository()

    private var managerScope: CoroutineScope? = null

    /**
     * SessionManager keeps an eagerly started StateFlow alive for the life of the process.
     * It gets a scope this test owns and cancels afterwards, on an unconfined dispatcher so
     * the shared flow collects as soon as it is constructed rather than waiting for the test
     * to yield. runTest's backgroundScope is not suitable: its work is not executed by
     * advanceUntilIdle, so the derived state would never leave its initial value.
     */
    private fun TestScope.manager(): SessionManager {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        managerScope = scope
        return SessionManager(auth, profiles, social, game, scope)
    }

    @After
    fun tearDown() {
        managerScope?.cancel()
    }

    // --- Guest entry -------------------------------------------------------------------

    @Test
    fun `entering guest mode records the choice and creates a profile`() = runTest {
        val session = manager()
        assertTrue(session.enterGuestMode() is Outcome.Success)
        advanceUntilIdle()

        assertTrue(game.isGuestModeAccepted)
        assertEquals(1, profiles.ensureCount)
        assertEquals(SessionStatus.SIGNED_IN, session.state.value.status)
        assertTrue(session.state.value.isGuest)
    }

    @Test
    fun `a guest is named for themselves and never asked`() = runTest {
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()

        val username = session.state.value.profile?.username
        assertNotNull(username)
        assertTrue(UsernameRules.validate(username!!) is Outcome.Success)
        assertTrue(username.startsWith("guest_"))
        // The entry gate is what would ask, and for a guest it has nothing to ask about.
        assertFalse(session.state.value.needsUsername)
    }

    @Test
    fun `a real account is held at the username gate until it answers`() = runTest {
        val session = manager()
        session.signInWithEmail("player@example.com", "longenough1")
        advanceUntilIdle()
        assertTrue(session.state.value.needsUsername)

        session.changeUsername("Koray")
        advanceUntilIdle()

        assertFalse(session.state.value.needsUsername)
    }

    @Test
    fun `a name the player typed counts on a device that never saw them type it`() = runTest {
        // A reinstall or a second handset has no record of the answer. The name itself is
        // the record: it is not one the app would ever have generated.
        val session = manager()
        session.signInWithEmail("player@example.com", "longenough1")
        advanceUntilIdle()
        val userId = session.state.value.user!!.userId

        profiles.profiles.value = profiles.profiles.value.mapValues {
            it.value.copy(username = "Koray", normalizedUsername = "koray")
        }
        advanceUntilIdle()

        assertFalse(session.state.value.needsUsername)
        assertNotNull(userId)
    }

    @Test
    fun `a guest who cannot reach the network still gets in`() = runTest {
        // The whole point of LOCAL_ONLY: a first launch offline must still reach the game.
        auth.nextFailure = AppError.NETWORK
        val session = manager()

        assertTrue(session.enterGuestMode() is Outcome.Success)
        advanceUntilIdle()

        assertTrue(game.isGuestModeAccepted)
        assertEquals(SessionStatus.LOCAL_ONLY, session.state.value.status)
        assertTrue(session.state.value.hasEntered)
        assertTrue(session.state.value.isGuest)
    }

    @Test
    fun `a real failure is not swallowed`() = runTest {
        auth.nextFailure = AppError.TOO_MANY_REQUESTS
        val session = manager()
        assertEquals(AppError.TOO_MANY_REQUESTS, session.enterGuestMode().errorOrNull)
    }

    @Test
    fun `a guest gets in even when the profile cannot be written`() = runTest {
        // Happens against a project whose database rules are not deployed yet: the identity
        // is created but the profile write is refused. The player must still reach the game.
        profiles.failEnsure = true
        val session = manager()

        assertTrue(session.enterGuestMode() is Outcome.Success)
        advanceUntilIdle()

        assertTrue(session.state.value.hasEntered)
        assertNull(session.state.value.profile)
    }

    // --- Linking a guest ---------------------------------------------------------------

    @Test
    fun `linking a guest keeps the same user id and the same profile`() = runTest {
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()
        val guestId = session.state.value.user?.userId
        assertNotNull(guestId)

        // Give the guest something to lose, then link.
        session.setTutorialCompleted(true)
        session.changeUsername("Koray")
        advanceUntilIdle()

        val linked = session.linkGuestWithEmail("player@example.com", "longenough1")
        advanceUntilIdle()

        assertTrue(linked is Outcome.Success)
        assertEquals(guestId, session.state.value.user?.userId)
        assertEquals(AccountType.EMAIL, session.state.value.user?.accountType)
        // Progress survived the upgrade.
        assertEquals("Koray", profiles.profiles.value[guestId]?.username)
        assertTrue(profiles.profiles.value[guestId]?.tutorialCompleted == true)
    }

    @Test
    fun `a linked account unlocks social features`() = runTest {
        // Linking through email rather than Google: the Google path needs an Activity for
        // Credential Manager, and the decision under test is the same either way.
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()
        assertFalse(session.state.value.canUseSocialFeatures)

        session.linkGuestWithEmail("player@example.com", "longenough1")
        advanceUntilIdle()
        assertTrue(session.state.value.canUseSocialFeatures)
    }

    @Test
    fun `a failed link leaves the guest as they were`() = runTest {
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()
        val guestId = session.state.value.user?.userId

        auth.nextFailure = AppError.CREDENTIAL_IN_USE
        val result = session.linkGuestWithEmail("taken@example.com", "longenough1")
        advanceUntilIdle()

        assertEquals(AppError.CREDENTIAL_IN_USE, result.errorOrNull)
        assertEquals(guestId, session.state.value.user?.userId)
        assertTrue(session.state.value.isGuest)
    }

    // --- Tutorial ----------------------------------------------------------------------

    @Test
    fun `completing the tutorial is recorded locally and in the profile`() = runTest {
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()

        session.setTutorialCompleted(true)
        advanceUntilIdle()

        assertTrue(game.isTutorialCompleted)
        assertTrue(profiles.profiles.value.values.single().tutorialCompleted)
        assertTrue(session.state.value.tutorialCompleted)
    }

    @Test
    fun `a cloud completion alone satisfies the gate`() = runTest {
        // A player who finished the tutorial on another device must not be asked again.
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()
        val userId = session.state.value.user!!.userId

        profiles.profiles.value = profiles.profiles.value.mapValues {
            it.value.copy(tutorialCompleted = true)
        }
        advanceUntilIdle()

        assertFalse(game.isTutorialCompleted)
        assertTrue(session.state.value.tutorialCompleted)
        assertNotNull(userId)
    }

    // --- Language ----------------------------------------------------------------------

    @Test
    fun `language choice is stored and mirrored onto the profile`() = runTest {
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()

        game.setLanguage(AppLanguage.ARABIC)
        session.updatePreferredLanguage(AppLanguage.ARABIC.tag)
        advanceUntilIdle()

        assertEquals(AppLanguage.ARABIC, game.currentSettings.language)
        assertEquals("ar", profiles.profiles.value.values.single().preferredLanguage)
    }

    @Test
    fun `following the device language stores an empty tag`() = runTest {
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()

        session.updatePreferredLanguage(AppLanguage.SYSTEM.tag)
        advanceUntilIdle()

        assertEquals("", profiles.profiles.value.values.single().preferredLanguage)
        assertTrue(AppLanguage.SYSTEM.followsDevice)
    }

    // --- Sign out ----------------------------------------------------------------------

    @Test
    fun `signing out clears presence and the guest opt-in`() = runTest {
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()
        val userId = session.state.value.user!!.userId

        session.signOut()
        advanceUntilIdle()

        assertEquals(1, auth.signOutCount)
        assertTrue(userId in social.presenceCleared)
        assertFalse(game.isGuestModeAccepted)
        // Back to the welcome screen, not stuck in a half-signed-in state.
        assertEquals(SessionStatus.SIGNED_OUT, session.state.value.status)
    }

    // --- Account deletion ---------------------------------------------------------------

    @Test
    fun `deleting an account proves the credential, then erases data, then the credential`() =
        runTest {
            val session = manager()
            session.signInWithEmail("player@example.com", "longenough1")
            advanceUntilIdle()
            val userId = session.state.value.user!!.userId

            val result = session.deleteAccount(activityContext = null, password = "longenough1")
            advanceUntilIdle()

            assertTrue(result is Outcome.Success)
            assertEquals(1, auth.reauthenticateCount)
            // Social links and profile go first: once the identity is gone, the database rules
            // no longer authorise those writes.
            assertEquals(listOf(userId), social.deletedUserIds)
            assertEquals(listOf(userId), profiles.deletedUserIds)
            assertEquals(1, auth.deleteCount)
            assertEquals(SessionStatus.SIGNED_OUT, session.state.value.status)
        }

    @Test
    fun `deleting an account resets local state`() = runTest {
        val session = manager()
        session.signInWithEmail("player@example.com", "longenough1")
        advanceUntilIdle()
        session.setTutorialCompleted(true)
        session.changeUsername("Koray")
        advanceUntilIdle()

        session.deleteAccount(activityContext = null, password = "longenough1")
        advanceUntilIdle()

        assertFalse(game.isTutorialCompleted)
        assertFalse(game.isGuestModeAccepted)
        assertFalse(game.isUsernameChosen)
    }

    @Test
    fun `a stale sign-in stops the deletion before anything is erased`() = runTest {
        // The failure Firebase actually returns for a session that has been open a while.
        // Arriving after the profile was gone, it left an account with nothing in it.
        val session = manager()
        session.signInWithEmail("player@example.com", "longenough1")
        advanceUntilIdle()

        auth.reauthenticationFailure = AppError.REQUIRES_RECENT_LOGIN
        val result = session.deleteAccount(activityContext = null, password = "wrong")
        advanceUntilIdle()

        assertEquals(AppError.REQUIRES_RECENT_LOGIN, result.errorOrNull)
        assertTrue(profiles.deletedUserIds.isEmpty())
        assertTrue(social.deletedUserIds.isEmpty())
        assertEquals(0, auth.deleteCount)
        assertNotNull(session.state.value.profile)
    }

    @Test
    fun `a failed credential deletion is reported`() = runTest {
        val session = manager()
        session.signInWithEmail("player@example.com", "longenough1")
        advanceUntilIdle()

        auth.nextFailure = AppError.UNKNOWN
        val result = session.deleteAccount(activityContext = null, password = "longenough1")
        advanceUntilIdle()

        assertEquals(AppError.UNKNOWN, result.errorOrNull)
    }

    @Test
    fun `deleting without a session fails cleanly`() = runTest {
        val session = manager()
        val result = session.deleteAccount(activityContext = null, password = "")
        assertEquals(AppError.NOT_SIGNED_IN, result.errorOrNull)
        assertEquals(0, auth.deleteCount)
        assertEquals(0, auth.reauthenticateCount)
    }

    // --- Presence ----------------------------------------------------------------------

    @Test
    fun `presence starts only for a real account`() = runTest {
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()
        assertTrue(social.presenceStarted.isEmpty())

        session.signInWithEmail("player@example.com", "longenough1")
        advanceUntilIdle()
        assertEquals(listOf("email-user"), social.presenceStarted)
    }

    @Test
    fun `the signed out state exposes no user`() = runTest {
        val session = manager()
        advanceUntilIdle()
        assertNull(session.state.value.user)
        assertFalse(session.state.value.hasEntered)
    }
}
