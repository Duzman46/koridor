package com.duzman46.gridbound.navigation

import com.duzman46.gridbound.auth.domain.AccountType
import com.duzman46.gridbound.auth.domain.AuthUser
import com.duzman46.gridbound.profile.domain.UserProfile
import com.duzman46.gridbound.session.SessionState
import com.duzman46.gridbound.session.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The entry sequence, stated as the four questions it answers in order.
 *
 * Worth pinning down away from Compose because every one of these cases is a place the app
 * used to leak a player past a gate, and none of them needs a device to be wrong.
 */
class EntryDestinationTest {

    @Test
    fun `nobody has entered yet`() {
        assertEquals(EntryDestination.WELCOME, entryDestinationFor(SessionState()))
    }

    @Test
    fun `a player who has not learned the game gets the tutorial`() {
        assertEquals(EntryDestination.TUTORIAL, entryDestinationFor(signedIn()))
    }

    @Test
    fun `skipping the tutorial does not skip the username`() {
        // The whole of the reported bug: the tutorial handed the player straight to the game
        // whether they finished it or skipped it, and the account stayed unnamed.
        val session = signedIn()
        assertEquals(
            EntryDestination.USERNAME,
            entryDestinationFor(session, EntryStep.TUTORIAL),
        )
    }

    @Test
    fun `finishing the tutorial reaches the same gate`() {
        assertEquals(
            EntryDestination.USERNAME,
            entryDestinationFor(signedIn(tutorialCompleted = true)),
        )
    }

    @Test
    fun `the username gate is the last thing between a real account and the game`() {
        assertEquals(
            EntryDestination.HOME,
            entryDestinationFor(signedIn(tutorialCompleted = true, usernameChosen = true)),
        )
    }

    @Test
    fun `answering the username gate is believed before the session catches up`() {
        // changeUsername has returned but the preference has not been read back yet. Without
        // this the picker would send the player straight back to the picker.
        val session = signedIn(tutorialCompleted = true)
        assertEquals(EntryDestination.HOME, entryDestinationFor(session, EntryStep.USERNAME))
    }

    @Test
    fun `a guest is never asked to name themselves`() {
        val guest = signedIn(accountType = AccountType.GUEST, tutorialCompleted = true)
        assertEquals(EntryDestination.HOME, entryDestinationFor(guest))
    }

    @Test
    fun `a guest with no backend identity still reaches the tutorial and the game`() {
        val local = SessionState(status = SessionStatus.LOCAL_ONLY)
        assertEquals(EntryDestination.TUTORIAL, entryDestinationFor(local))
        assertEquals(
            EntryDestination.HOME,
            entryDestinationFor(local.copy(tutorialCompleted = true)),
        )
    }

    @Test
    fun `signing in is believed before the session catches up`() {
        assertEquals(
            EntryDestination.TUTORIAL,
            entryDestinationFor(SessionState(), EntryStep.ENTRY),
        )
    }

    @Test
    fun `an account whose profile has not arrived is not held at the gate`() {
        // There would be nowhere to write the answer. It is asked on the next launch.
        val session = signedIn(tutorialCompleted = true).copy(profile = null)
        assertEquals(EntryDestination.HOME, entryDestinationFor(session))
    }

    private fun signedIn(
        accountType: AccountType = AccountType.GOOGLE,
        tutorialCompleted: Boolean = false,
        usernameChosen: Boolean = false,
    ) = SessionState(
        status = SessionStatus.SIGNED_IN,
        user = AuthUser("uid", "player@example.com", accountType, true),
        profile = UserProfile(
            userId = "uid",
            username = "guest_483920",
            normalizedUsername = "guest_483920",
            avatarId = "avatar_01",
            accountType = accountType,
        ),
        tutorialCompleted = tutorialCompleted,
        usernameChosen = usernameChosen,
    )
}
