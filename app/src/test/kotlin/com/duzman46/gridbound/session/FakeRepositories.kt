package com.duzman46.gridbound.session

import android.content.Context
import com.duzman46.gridbound.auth.domain.AccountType
import com.duzman46.gridbound.auth.domain.AuthRepository
import com.duzman46.gridbound.auth.domain.AuthState
import com.duzman46.gridbound.auth.domain.AuthUser
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.domain.models.AppSettings
import com.duzman46.gridbound.domain.models.GameStatistics
import com.duzman46.gridbound.domain.models.ThemeMode
import com.duzman46.gridbound.domain.repository.GameRepository
import com.duzman46.gridbound.game.board.BoardTheme
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.profile.domain.UserProfile
import com.duzman46.gridbound.profile.domain.UserProfileRepository
import com.duzman46.gridbound.social.domain.Friend
import com.duzman46.gridbound.social.domain.FriendshipAction
import com.duzman46.gridbound.social.domain.GameInvite
import com.duzman46.gridbound.social.domain.PresenceState
import com.duzman46.gridbound.social.domain.SocialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * In-memory doubles for the session collaborators.
 *
 * They model the behaviour that matters for the session rules — a linked guest keeps its
 * user id, deletion order, what survives sign-out — rather than pretending to be Firebase.
 */
class FakeAuthRepository(
    override val isConfigured: Boolean = true,
    override val isGoogleSignInAvailable: Boolean = true,
) : AuthRepository {

    val state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val authState: Flow<AuthState> = state

    var deleteCount = 0
    var signOutCount = 0
    var nextFailure: AppError? = null

    /** Anonymous ids are handed out in order so a test can assert the id did not change. */
    private var nextGuestId = 1

    override fun currentUser(): AuthUser? = (state.value as? AuthState.SignedIn)?.user

    override suspend fun signInAsGuest(): Outcome<AuthUser> = complete {
        currentUser()?.takeIf { it.accountType == AccountType.GUEST }
            ?: AuthUser("guest-${nextGuestId++}", null, AccountType.GUEST, false)
    }

    override suspend fun signInWithEmail(email: String, password: String): Outcome<AuthUser> =
        complete { AuthUser("email-user", email, AccountType.EMAIL, false) }

    override suspend fun createAccountWithEmail(email: String, password: String): Outcome<AuthUser> =
        complete { AuthUser("email-user", email, AccountType.EMAIL, false) }

    override suspend fun sendPasswordReset(email: String): Outcome<Unit> =
        nextFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)

    override suspend fun signInWithGoogle(activityContext: Context): Outcome<AuthUser> =
        complete { AuthUser("google-user", "player@example.com", AccountType.GOOGLE, true) }

    override suspend fun linkGuestWithGoogle(activityContext: Context): Outcome<AuthUser> =
        link(AccountType.GOOGLE, "player@example.com")

    override suspend fun linkGuestWithEmail(email: String, password: String): Outcome<AuthUser> =
        link(AccountType.EMAIL, email)

    override suspend fun signOut() {
        signOutCount++
        state.value = AuthState.SignedOut
    }

    override suspend fun deleteAccount(): Outcome<Unit> {
        deleteCount++
        nextFailure?.let { return Outcome.Failure(it) }
        state.value = AuthState.SignedOut
        return Outcome.Success(Unit)
    }

    /** Linking upgrades in place: the user id must survive. */
    private fun link(type: AccountType, email: String): Outcome<AuthUser> {
        val current = currentUser() ?: return Outcome.Failure(AppError.NOT_SIGNED_IN)
        nextFailure?.let { return Outcome.Failure(it) }
        val linked = current.copy(accountType = type, email = email)
        state.value = AuthState.SignedIn(linked)
        return Outcome.Success(linked)
    }

    private fun complete(build: () -> AuthUser): Outcome<AuthUser> {
        nextFailure?.let { return Outcome.Failure(it) }
        val user = build()
        state.value = AuthState.SignedIn(user)
        return Outcome.Success(user)
    }
}

class FakeUserProfileRepository : UserProfileRepository {
    val profiles = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    val deletedUserIds = mutableListOf<String>()
    var ensureCount = 0

    /** Simulates a database that refuses the profile write, e.g. rules not deployed. */
    var failEnsure = false

    override fun observeProfile(userId: String): Flow<UserProfile?> =
        profiles.map { it[userId] }

    override suspend fun loadProfile(userId: String): Outcome<UserProfile> =
        profiles.value[userId]?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.UNKNOWN)

    override suspend fun ensureProfile(user: AuthUser, suggestedName: String?): Outcome<UserProfile> {
        ensureCount++
        if (failEnsure) return Outcome.Failure(AppError.UNKNOWN)
        val existing = profiles.value[user.userId]
        val profile = existing?.copy(accountType = user.accountType, email = user.email)
            ?: UserProfile(
                userId = user.userId,
                username = suggestedName ?: "player_${user.userId.takeLast(4)}",
                normalizedUsername = (suggestedName ?: "player_${user.userId.takeLast(4)}").lowercase(),
                displayName = suggestedName ?: user.userId,
                avatarId = "avatar_01",
                email = user.email,
                accountType = user.accountType,
            )
        profiles.value = profiles.value + (user.userId to profile)
        return Outcome.Success(profile)
    }

    override suspend fun isUsernameAvailable(username: String): Outcome<Boolean> =
        Outcome.Success(profiles.value.values.none { it.username.equals(username, true) })

    override suspend fun changeUsername(userId: String, username: String): Outcome<String> {
        val profile = profiles.value[userId] ?: return Outcome.Failure(AppError.NOT_SIGNED_IN)
        if (profiles.value.values.any { it.userId != userId && it.username.equals(username, true) }) {
            return Outcome.Failure(AppError.USERNAME_TAKEN)
        }
        profiles.value = profiles.value + (
            userId to profile.copy(username = username, normalizedUsername = username.lowercase())
            )
        return Outcome.Success(username)
    }

    override suspend fun updateDisplayName(userId: String, displayName: String): Outcome<Unit> =
        mutate(userId) { it.copy(displayName = displayName) }

    override suspend fun updateAvatar(userId: String, avatarId: String): Outcome<Unit> =
        mutate(userId) { it.copy(avatarId = avatarId) }

    override suspend fun updatePreferredLanguage(userId: String, languageTag: String): Outcome<Unit> =
        mutate(userId) { it.copy(preferredLanguage = languageTag) }

    override suspend fun setTutorialCompleted(userId: String, completed: Boolean): Outcome<Unit> =
        mutate(userId) { it.copy(tutorialCompleted = completed) }

    override suspend fun deleteAccountData(userId: String): Outcome<Unit> {
        deletedUserIds += userId
        profiles.value = profiles.value - userId
        return Outcome.Success(Unit)
    }

    private fun mutate(userId: String, change: (UserProfile) -> UserProfile): Outcome<Unit> {
        val profile = profiles.value[userId] ?: return Outcome.Failure(AppError.NOT_SIGNED_IN)
        profiles.value = profiles.value + (userId to change(profile))
        return Outcome.Success(Unit)
    }
}

class FakeSocialRepository : SocialRepository {
    val presenceStarted = mutableListOf<String>()
    val presenceCleared = mutableListOf<String>()
    val deletedUserIds = mutableListOf<String>()

    override fun observeFriendships(userId: String): Flow<List<Friend>> = flowOf(emptyList())
    override fun observeInvites(userId: String): Flow<List<GameInvite>> = flowOf(emptyList())
    override fun observePresence(userIds: Set<String>): Flow<Map<String, PresenceState>> =
        flowOf(emptyMap())

    override suspend fun findByUsername(username: String): Outcome<UserProfile?> =
        Outcome.Success(null)

    override suspend fun applyFriendshipAction(
        userId: String,
        otherUserId: String,
        action: FriendshipAction,
    ): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun sendInvite(
        fromUserId: String,
        toUserId: String,
        roomCode: String,
    ): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun dismissInvite(userId: String, inviteId: String): Outcome<Unit> =
        Outcome.Success(Unit)

    override fun startPresence(userId: String) {
        presenceStarted += userId
    }

    override suspend fun clearPresence(userId: String) {
        presenceCleared += userId
    }

    override suspend fun deleteSocialData(userId: String): Outcome<Unit> {
        deletedUserIds += userId
        return Outcome.Success(Unit)
    }
}

class FakeGameRepository : GameRepository {
    private val settingsState = MutableStateFlow(AppSettings())
    private val tutorialState = MutableStateFlow(false)
    private val guestState = MutableStateFlow(false)

    override val settings: Flow<AppSettings> = settingsState
    override val statistics: Flow<GameStatistics> = MutableStateFlow(GameStatistics())
    override val tutorialCompleted: Flow<Boolean> = tutorialState
    override val guestModeAccepted: Flow<Boolean> = guestState

    val currentSettings: AppSettings get() = settingsState.value
    val isTutorialCompleted: Boolean get() = tutorialState.value
    val isGuestModeAccepted: Boolean get() = guestState.value

    override suspend fun setTutorialCompleted(completed: Boolean) {
        tutorialState.value = completed
    }

    override suspend fun setGuestModeAccepted(accepted: Boolean) {
        guestState.value = accepted
    }

    override suspend fun setLanguage(language: AppLanguage) {
        settingsState.value = settingsState.value.copy(language = language)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        settingsState.value = settingsState.value.copy(themeMode = mode)
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        settingsState.value = settingsState.value.copy(dynamicColor = enabled)
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        settingsState.value = settingsState.value.copy(soundEnabled = enabled)
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        settingsState.value = settingsState.value.copy(hapticsEnabled = enabled)
    }

    override suspend fun setDifficulty(difficulty: Difficulty) {
        settingsState.value = settingsState.value.copy(difficulty = difficulty)
    }

    override suspend fun setBoardTheme(theme: BoardTheme) {
        settingsState.value = settingsState.value.copy(boardTheme = theme)
    }

    override suspend fun recordCompletedGame(
        mode: GameMode,
        difficulty: Difficulty,
        winner: PlayerId,
        localPlayer: PlayerId,
        turns: Int,
    ) = Unit
}
