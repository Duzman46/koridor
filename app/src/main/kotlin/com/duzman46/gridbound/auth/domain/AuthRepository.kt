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
     * Removes the Firebase identity. Profile data is erased by
     * [com.duzman46.gridbound.profile.domain.UserProfileRepository.deleteAccountData]
     * before this is called, because the rules stop allowing those writes afterwards.
     */
    suspend fun deleteAccount(): Outcome<Unit>
}
