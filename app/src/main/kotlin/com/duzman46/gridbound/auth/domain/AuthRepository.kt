package com.duzman46.gridbound.auth.domain

import android.content.Context
import com.duzman46.gridbound.core.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Every authentication operation the app performs.
 *
 * The UI never touches FirebaseAuth directly; it calls this repository and renders the
 * returned [Outcome]. Google sign-in needs an Activity context for the Credential Manager
 * bottom sheet, which is why those methods take one.
 */
interface AuthRepository {
    /** False when the Firebase connection values are missing from the build configuration. */
    val isConfigured: Boolean

    /** True when a Google OAuth web client ID is configured for Credential Manager. */
    val isGoogleSignInAvailable: Boolean

    /**
     * True when the last link failed because the credential offered already belongs to an
     * account, and that credential is still held — so [signInToExistingAccount] has
     * something real to sign in with rather than a guess about what went wrong.
     */
    val hasCredentialForExistingAccount: Boolean

    val authState: Flow<AuthState>

    /** The current user without waiting for the state flow, or null when signed out. */
    fun currentUser(): AuthUser?

    suspend fun signInAsGuest(): Outcome<AuthUser>

    suspend fun signInWithEmail(email: String, password: String): Outcome<AuthUser>

    suspend fun createAccountWithEmail(email: String, password: String): Outcome<AuthUser>

    /**
     * Proves an address and password belong together, changing nothing about who is signed in.
     *
     * The caller is about to erase a guest's rows and then sign in as somebody else. That order
     * is the safe one for the database and the dangerous one for the player, and this is what
     * makes it safe for both: a wrong password fails here, before anything is destroyed.
     */
    suspend fun verifyEmailCredential(email: String, password: String): Outcome<Unit>

    suspend fun sendPasswordReset(email: String): Outcome<Unit>

    /** @param activityContext must be an Activity; Credential Manager renders UI over it. */
    suspend fun signInWithGoogle(activityContext: Context): Outcome<AuthUser>

    /** Upgrades the current anonymous user in place, keeping the same user id and progress. */
    suspend fun linkGuestWithGoogle(activityContext: Context): Outcome<AuthUser>

    /** Upgrades the current anonymous user in place, keeping the same user id and progress. */
    suspend fun linkGuestWithEmail(email: String, password: String): Outcome<AuthUser>

    /**
     * Signs in as the account the last failed link collided with.
     *
     * Not a link and not a merge — Firebase offers neither between two identities that both
     * exist. Nothing of the current session survives it, so the caller owes the player a
     * plain warning first and owes the database the removal of whatever the abandoned
     * identity owns, while that identity is still the one asking.
     *
     * Succeeds only when the signed-in user really is that account. A completed call proves
     * the backend answered, not that the player in the chair changed, and success on the
     * weaker reading is what turns a hand-over that did nothing into a screen announcing it
     * worked.
     */
    suspend fun signInToExistingAccount(): Outcome<AuthUser>

    /**
     * Empties the chair the anonymous player was sitting in.
     *
     * The auth record itself is best effort: Firebase refuses to delete a credential whose
     * sign-in it considers stale, and an anonymous session has nothing to present a second
     * time — a guest who has been playing for weeks simply cannot prove anything. By this
     * point their data is already gone, so a refusal costs no more than an auth record with
     * nothing attached.
     *
     * The session is not best effort. Whatever becomes of the record, no anonymous user may
     * still be signed in when this returns, because everything after it is a sign-in as
     * somebody else and none of it is safe to reason about over a guest who is still there.
     */
    suspend fun discardGuestIdentity()

    /**
     * Drops the credential a failed link is holding, unspent.
     *
     * The offer it enables is a question, and declining is an answer to it. A credential
     * kept past its answer is one that can be spent on a question nobody asked.
     */
    fun forgetExistingAccountCredential()

    suspend fun signOut()

    /**
     * Presents the account's own credential again, so Firebase counts the sign-in as fresh.
     *
     * Destructive operations are refused on a session that has been open for a while, and
     * the refusal arrives as an exception thrown by the operation itself rather than as a
     * question the player could have answered. Asking first turns it into a prompt they
     * expect, before anything irreversible has happened.
     *
     * @param activityContext the hosting Activity, or null when none could be reached. A
     *   Google account is proved through the Credential Manager sheet, which has nowhere to
     *   draw without one and says so rather than failing silently.
     * @param password the account's password, read only for an email account.
     */
    suspend fun reauthenticate(activityContext: Context?, password: String): Outcome<Unit>

    /**
     * Removes the Firebase identity. Profile data is erased by
     * [com.duzman46.gridbound.profile.domain.UserProfileRepository.deleteAccountData]
     * before this is called, because the rules stop allowing those writes afterwards.
     */
    suspend fun deleteAccount(): Outcome<Unit>
}
