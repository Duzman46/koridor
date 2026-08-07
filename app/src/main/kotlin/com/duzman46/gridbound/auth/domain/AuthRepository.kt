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

    val authState: Flow<AuthState>

    /** The current user without waiting for the state flow, or null when signed out. */
    fun currentUser(): AuthUser?

    suspend fun signInAsGuest(): Outcome<AuthUser>

    suspend fun signInWithEmail(email: String, password: String): Outcome<AuthUser>

    suspend fun createAccountWithEmail(email: String, password: String): Outcome<AuthUser>

    suspend fun sendPasswordReset(email: String): Outcome<Unit>

    /** @param activityContext must be an Activity; Credential Manager renders UI over it. */
    suspend fun signInWithGoogle(activityContext: Context): Outcome<AuthUser>

    /** Upgrades the current anonymous user in place, keeping the same user id and progress. */
    suspend fun linkGuestWithGoogle(activityContext: Context): Outcome<AuthUser>

    /** Upgrades the current anonymous user in place, keeping the same user id and progress. */
    suspend fun linkGuestWithEmail(email: String, password: String): Outcome<AuthUser>

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
