package com.duzman46.gridbound.profile.domain

import com.duzman46.gridbound.auth.domain.AuthUser
import com.duzman46.gridbound.core.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes player profiles.
 *
 * Only the presentation-facing fields can be changed here. Rating, win/loss counts and
 * entitlements are written by trusted server code and are rejected by the database rules
 * if a client attempts them.
 */
interface UserProfileRepository {
    /** Emits null while signed out or when the profile has not been created yet. */
    fun observeProfile(userId: String): Flow<UserProfile?>

    suspend fun loadProfile(userId: String): Outcome<UserProfile>

    /**
     * Creates the profile on first sign-in, otherwise refreshes the fields that change with
     * each session. Safe to call on every sign-in.
     */
    suspend fun ensureProfile(user: AuthUser, suggestedName: String? = null): Outcome<UserProfile>

    /** Validates format first, then checks the uniqueness index. */
    suspend fun isUsernameAvailable(username: String): Outcome<Boolean>

    /** Claims the name atomically and releases the previous one. */
    suspend fun changeUsername(userId: String, username: String): Outcome<String>

    suspend fun updateDisplayName(userId: String, displayName: String): Outcome<Unit>

    suspend fun updateAvatar(userId: String, avatarId: String): Outcome<Unit>

    suspend fun updatePreferredLanguage(userId: String, languageTag: String): Outcome<Unit>

    suspend fun setTutorialCompleted(userId: String, completed: Boolean): Outcome<Unit>

    /**
     * Erases the player's personal data. Called before the Firebase identity is removed,
     * because the rules stop authorising these writes once the identity is gone.
     */
    suspend fun deleteAccountData(userId: String): Outcome<Unit>
}
