package com.duzman46.gridbound.session

import com.duzman46.gridbound.auth.domain.AccountType
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.core.UsernameRules
import com.duzman46.gridbound.domain.models.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    /**
     * How late a held session arrives. Any value under the catch-up bound would do; this one is
     * only chosen to be obviously nothing like it, so a test that ends at the bound instead has
     * clearly taken the other path.
     */
    private val catchUpMillis = 200L

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
    fun `the session follows the identity even when the profile never answers`() = runTest {
        // Who is playing is an auth fact; what they have played is a database read. Holding
        // the session until the read lands leaves every screen drawing the previous player,
        // which is how a sign-in that worked could still look like nothing happened.
        profiles.profileReadsNeverAnswer = true
        val session = manager()

        session.signInWithEmail("player@example.com", "longenough1")
        advanceUntilIdle()

        assertEquals(SessionStatus.SIGNED_IN, session.state.value.status)
        assertEquals("email-user", session.state.value.user?.userId)
        assertFalse(session.state.value.isGuest)
        assertNull(session.state.value.profile)
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

        // Give the guest something to lose, then link. Not a chosen name: a guest has none
        // to give, which is the subject of the guest-naming tests below.
        session.setTutorialCompleted(true)
        session.updateAvatar("avatar_07")
        advanceUntilIdle()

        val linked = session.linkGuestWithEmail("player@example.com", "longenough1")
        advanceUntilIdle()

        assertTrue(linked is Outcome.Success)
        assertEquals(guestId, session.state.value.user?.userId)
        assertEquals(AccountType.EMAIL, session.state.value.user?.accountType)
        // Progress survived the upgrade.
        assertEquals("avatar_07", profiles.profiles.value[guestId]?.avatarId)
        assertTrue(profiles.profiles.value[guestId]?.tutorialCompleted == true)
    }

    @Test
    fun `a linked account is asked for a name it has never been asked for`() = runTest {
        // The name it carries out of guest play was handed out, and the account is about to
        // be visible to other players under it.
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()

        session.linkGuestWithEmail("player@example.com", "longenough1")
        advanceUntilIdle()

        assertTrue(session.state.value.needsUsername)
        assertTrue(UsernameRules.isGenerated(session.state.value.profile!!.username))
    }

    // --- Naming a guest ------------------------------------------------------------------

    @Test
    fun `a guest is not offered a new name and cannot take one`() = runTest {
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()
        val handedOut = session.state.value.profile!!.username

        assertFalse(session.state.value.canChangeUsername)
        val renamed = session.changeUsername("Koray")
        advanceUntilIdle()

        assertEquals(AppError.NOT_SIGNED_IN, renamed.errorOrNull)
        assertEquals(handedOut, session.state.value.profile?.username)
        // The refusal must not look like an answer to "what should we call you?" either.
        assertFalse(game.isUsernameChosen)
    }

    @Test
    fun `a real account names itself and the answer sticks`() = runTest {
        val session = manager()
        session.signInWithEmail("player@example.com", "longenough1")
        advanceUntilIdle()
        assertTrue(session.state.value.canChangeUsername)

        assertTrue(session.changeUsername("Koray") is Outcome.Success)
        advanceUntilIdle()

        assertEquals("Koray", session.state.value.profile?.username)
        assertTrue(game.isUsernameChosen)
    }

    @Test
    fun `an account just linked is named as soon as the session catches up`() = runTest {
        // The gate that asks for the name is opened on the credential that landed; the
        // session is rebuilt from a flow behind it. A player sent to the username screen and
        // then told they are not signed in is those two disagreeing, and the wait exists for
        // this case: a session that is behind and then arrives.
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()
        auth.holdIdentityFeed()

        val linked = session.linkGuestWithEmail("player@example.com", "longenough1")
        advanceUntilIdle()

        assertTrue(linked is Outcome.Success)
        // The session is exactly as stale as it is on the device: still the guest.
        assertTrue(session.state.value.isGuest)
        assertFalse(session.state.value.canChangeUsername)

        val startedAt = testScheduler.currentTime
        val catchesUp = launch {
            delay(catchUpMillis)
            auth.releaseIdentityFeed()
        }
        val named = session.changeUsername("Koray")
        // Read before the join, which would otherwise run the clock to the release itself and
        // make a call that never waited at all look exactly like one that waited and was
        // answered.
        val waited = testScheduler.currentTime - startedAt
        catchesUp.join()

        assertTrue(named is Outcome.Success)
        assertEquals("Koray", profiles.profiles.value[session.state.value.user?.userId]?.username)
        assertTrue(game.isUsernameChosen)
        // The write went when the session agreed and not when the clock ran out. Without the
        // wait it would have gone before the connection had the token the rules check it
        // against; with the wait but no catch-up it would have gone a whole timeout later, and
        // both of those look identical from the assertions above.
        assertEquals(catchUpMillis, waited)
        assertTrue(session.state.value.canChangeUsername)
    }

    @Test
    fun `an account handed over to is named as soon as the session catches up`() = runTest {
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()
        profiles.seedExistingAccount(username = UsernameRules.generatedName(), rating = 1400)
        auth.hasCredentialForExistingAccount = true
        auth.holdIdentityFeed()

        // The hand-over reports what it can prove, and with the session held it can prove
        // nothing. The account is signed in all the same, which is the state under test.
        session.signInToExistingAccount()
        advanceUntilIdle()
        assertFalse(session.state.value.canChangeUsername)

        val startedAt = testScheduler.currentTime
        val catchesUp = launch {
            delay(catchUpMillis)
            auth.releaseIdentityFeed()
        }
        val named = session.changeUsername("Koray")
        val waited = testScheduler.currentTime - startedAt
        catchesUp.join()

        assertTrue(named is Outcome.Success)
        assertEquals(
            "Koray",
            profiles.profiles.value[FakeAuthRepository.EXISTING_USER_ID]?.username,
        )
        assertEquals(catchUpMillis, waited)
    }

    @Test
    fun `a session that never catches up is written past once the wait is up`() = runTest {
        // The other end of the same wait, and the one that decides how long a player stands
        // behind a spinner on the username gate — a screen with no back arrow, no system back
        // and its own submit button held down for the duration. Bounded by the catch-up's own
        // constant and not by the sign-in's, which is fifteen times longer and is the right
        // answer to a different question.
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()
        auth.holdIdentityFeed()

        session.linkGuestWithEmail("player@example.com", "longenough1")
        advanceUntilIdle()
        val startedAt = testScheduler.currentTime

        val named = session.changeUsername("Koray")
        advanceUntilIdle()

        // Not a veto: the identity was read from the source the session is built on, so a flow
        // that never agrees is a stale reading rather than a refusal, and the name still lands.
        assertTrue(named is Outcome.Success)
        assertEquals(
            Constants.Backend.SESSION_CATCHUP_TIMEOUT_MILLIS,
            testScheduler.currentTime - startedAt,
        )
    }

    @Test
    fun `a guest is refused without waiting on a session that is not going to change`() = runTest {
        // The other half of the rule: the live identity decides, and a guest's says guest.
        // Nothing is in flight, so nothing is waited for.
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()
        auth.holdIdentityFeed()
        val startedAt = testScheduler.currentTime

        val renamed = session.changeUsername("Koray")

        assertEquals(AppError.NOT_SIGNED_IN, renamed.errorOrNull)
        assertEquals(startedAt, testScheduler.currentTime)
        assertFalse(game.isUsernameChosen)
    }

    // --- Handing over to an account that already exists -----------------------------------

    @Test
    fun `handing over erases the guest, then brings the other account's own record`() = runTest {
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()
        val guestId = session.state.value.user!!.userId
        profiles.seedExistingAccount(username = "Koray", rating = 1450)

        auth.hasCredentialForExistingAccount = true
        val handedOver = session.signInToExistingAccount()
        advanceUntilIdle()

        assertTrue(handedOver is Outcome.Success)
        // Erased while the guest was still the one authorised to erase it, credential and all.
        assertEquals(listOf(guestId), profiles.deletedUserIds)
        assertEquals(listOf(guestId), auth.discardedGuestIds)
        assertNull(profiles.profiles.value[guestId])
        // What arrives is the other account's history, not a merge of the two.
        assertEquals(FakeAuthRepository.EXISTING_USER_ID, session.state.value.user?.userId)
        assertEquals(1450, session.state.value.profile?.rating)
        assertEquals("Koray", session.state.value.profile?.username)
        assertFalse(session.state.value.isGuest)
    }

    @Test
    fun `a hand-over the session never carries is not reported as success`() = runTest {
        // The one failure that matters most, because it is the one that used to be reported
        // as a success: every call along the way answers, and the player is still not in the
        // account the screen has just told them they are in.
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()
        profiles.seedExistingAccount(username = "Koray", rating = 1450)

        auth.hasCredentialForExistingAccount = true
        auth.existingAccountSignInStrandsSession = true
        val handedOver = session.signInToExistingAccount()
        advanceUntilIdle()

        assertEquals(AppError.ACCOUNT_SWITCH_FAILED, handedOver.errorOrNull)
        assertNull(session.state.value.user)
        // Not a guest either: their record is gone, and saying otherwise would offer to keep
        // progress that no longer exists.
        assertFalse(session.state.value.isGuest)
    }

    @Test
    fun `handing over forgets what the device knew about the guest`() = runTest {
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()
        profiles.seedExistingAccount(username = "Koray", rating = 1450)

        auth.hasCredentialForExistingAccount = true
        session.signInToExistingAccount()
        advanceUntilIdle()

        // Guest entry left standing reads as "still a guest" for as long as no identity is
        // in place, and the name answer left standing belongs to somebody who is now gone.
        assertFalse(game.isGuestModeAccepted)
        assertFalse(game.isUsernameChosen)
    }

    @Test
    fun `a guest whose identity cannot be deleted still loses the chair`() = runTest {
        // Firebase refuses to delete a credential whose sign-in it considers stale, which is
        // every anonymous session more than a few minutes old. The record can survive that;
        // an anonymous user still signed in underneath the next sign-in cannot.
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()
        profiles.seedExistingAccount(username = "Koray", rating = 1450)

        auth.guestDeletionRefused = true
        auth.hasCredentialForExistingAccount = true
        val handedOver = session.signInToExistingAccount()
        advanceUntilIdle()

        assertTrue(handedOver is Outcome.Success)
        assertEquals(FakeAuthRepository.EXISTING_USER_ID, session.state.value.user?.userId)
        assertEquals(1450, session.state.value.profile?.rating)
        assertFalse(session.state.value.isGuest)
    }

    @Test
    fun `declining the offer changes nothing and leaves no credential behind`() = runTest {
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()
        val guestId = session.state.value.user?.userId
        val guestProfile = session.state.value.profile

        auth.hasCredentialForExistingAccount = true
        session.declineExistingAccount()
        advanceUntilIdle()

        assertEquals(guestId, session.state.value.user?.userId)
        assertEquals(guestProfile, session.state.value.profile)
        assertTrue(session.state.value.isGuest)
        assertTrue(game.isGuestModeAccepted)
        assertTrue(profiles.deletedUserIds.isEmpty())
        assertTrue(auth.discardedGuestIds.isEmpty())
        // The question was answered, so nothing is left that could answer it again.
        assertFalse(session.canSignInToExistingAccount)
    }

    @Test
    fun `a guest that cannot be erased is not handed over either`() = runTest {
        // Otherwise the profile row and the reservation holding its name would be stranded
        // under a user id nobody can authenticate as ever again.
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()
        val guestId = session.state.value.user!!.userId

        profiles.failDelete = true
        auth.hasCredentialForExistingAccount = true
        val handedOver = session.signInToExistingAccount()
        advanceUntilIdle()

        assertEquals(AppError.NETWORK, handedOver.errorOrNull)
        assertTrue(auth.discardedGuestIds.isEmpty())
        assertEquals(guestId, session.state.value.user?.userId)
        assertNotNull(session.state.value.profile)
        assertTrue(session.state.value.isGuest)
    }

    @Test
    fun `nothing is destroyed without a credential to hand over to`() = runTest {
        // The offer is built on the credential Firebase actually kept, never on the error
        // message the player was shown. With no credential there is nothing to hand over to,
        // and a guest whose data had already gone would be left with neither account.
        val session = manager()
        session.enterGuestMode()
        advanceUntilIdle()

        assertFalse(session.canSignInToExistingAccount)
        val handedOver = session.signInToExistingAccount()
        advanceUntilIdle()

        assertEquals(AppError.UNKNOWN, handedOver.errorOrNull)
        assertTrue(profiles.deletedUserIds.isEmpty())
        assertTrue(auth.discardedGuestIds.isEmpty())
        assertTrue(session.state.value.isGuest)
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

    @Test
    fun `signing out forgets that this player answered the name question`() = runTest {
        // Whoever signs in next has not answered it. Left set, the flag would wave a brand
        // new account past the gate still wearing the name the app invented for it.
        val session = manager()
        session.signInWithEmail("player@example.com", "longenough1")
        advanceUntilIdle()
        session.changeUsername("Koray")
        advanceUntilIdle()
        assertTrue(game.isUsernameChosen)

        session.signOut()
        advanceUntilIdle()

        assertFalse(game.isUsernameChosen)
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
