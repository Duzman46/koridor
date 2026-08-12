package com.duzman46.gridbound.session

import android.content.Context
import com.duzman46.gridbound.auth.domain.AccountType
import com.duzman46.gridbound.auth.domain.AuthRepository
import com.duzman46.gridbound.auth.domain.AuthState
import com.duzman46.gridbound.auth.domain.AuthUser
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.core.UsernameRules
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.domain.models.AppSettings
import com.duzman46.gridbound.domain.models.GameStatistics
import com.duzman46.gridbound.domain.models.ThemeMode
import com.duzman46.gridbound.domain.repository.GameRepository
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.profile.domain.UserProfile
import com.duzman46.gridbound.profile.domain.UserProfileRepository
import com.duzman46.gridbound.social.domain.ContentReportReason
import com.duzman46.gridbound.social.domain.Friend
import com.duzman46.gridbound.social.domain.FriendshipAction
import com.duzman46.gridbound.social.domain.PlayerRequest
import com.duzman46.gridbound.social.domain.PresenceState
import com.duzman46.gridbound.social.domain.SocialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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

    private val state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val authState: Flow<AuthState> = state

    var deleteCount = 0
    var signOutCount = 0
    var reauthenticateCount = 0
    var nextFailure: AppError? = null

    /** Stands in for Firebase refusing a stale sign-in until the credential is presented. */
    var reauthenticationFailure: AppError? = null

    /** The anonymous identities dropped on the way to somebody else's account. */
    val discardedGuestIds = mutableListOf<String>()

    /**
     * Stands in for Firebase refusing to delete a session it considers stale, which is
     * every anonymous session more than a few minutes old. The auth record survives; the
     * player must not still be sitting in it.
     */
    var guestDeletionRefused = false

    /**
     * Stands in for a sign-in the backend accepts without the session ever becoming that
     * account. The call answers and the chair stays empty, which is the shape of every way
     * a hand-over can be announced without having happened.
     */
    var existingAccountSignInStrandsSession = false

    /**
     * Stands in for Firebase having kept the credential a link collided with.
     *
     * Armed directly by a test rather than by running the link that produces it: that link
     * goes through the Google account picker and so demands an Activity, which a JVM test
     * has none of. What the session does once the credential exists is the part these tests
     * are about, and it is reached the same way either way.
     */
    override var hasCredentialForExistingAccount: Boolean = false

    /** Anonymous ids are handed out in order so a test can assert the id did not change. */
    private var nextGuestId = 1

    /**
     * Who Firebase would name as the signed-in user right now, which is not always what
     * [authState] has published.
     *
     * The real repository reads the two from different places — `currentUser` straight off
     * the auth object, `authState` out of a listener the session then rebuilds itself from —
     * so the live identity leads the published one for as long as that takes. Keeping them
     * apart here is what lets a test stand in the gap; see [holdIdentityFeed].
     */
    private var liveUser: AuthUser? = null

    private var identityFeedHeld = false

    /**
     * Stops [authState] carrying identity changes, leaving the live identity to move alone.
     *
     * Stands in for the instant after a credential lands: Firebase knows who this is, and
     * everything reading the session still sees the player who was there a moment ago.
     */
    fun holdIdentityFeed() {
        identityFeedHeld = true
    }

    /**
     * Lets [authState] carry identity changes again, and publishes the one it held back.
     *
     * The catch-up that actually happens on a device, and the case a test has to release the
     * feed to reach at all: held and never released models a session that never arrives, which
     * is the far end of the wait rather than the reason there is one.
     */
    fun releaseIdentityFeed() {
        identityFeedHeld = false
        becomeIdentity(liveUser)
    }

    override fun currentUser(): AuthUser? = liveUser

    override suspend fun signInAsGuest(): Outcome<AuthUser> = complete {
        currentUser()?.takeIf { it.accountType == AccountType.GUEST }
            ?: AuthUser("guest-${nextGuestId++}", null, AccountType.GUEST, false)
    }

    override suspend fun signInWithEmail(email: String, password: String): Outcome<AuthUser> =
        complete { AuthUser("email-user", email, AccountType.EMAIL, false) }

    override suspend fun createAccountWithEmail(email: String, password: String): Outcome<AuthUser> =
        complete { AuthUser("email-user", email, AccountType.EMAIL, false) }

    /**
     * Whether a password check passes, and a record of every pair it was asked about.
     *
     * Separate from [nextFailure] on purpose: the whole point of the check is that it runs
     * BEFORE the guest's rows are deleted, so a test has to be able to fail the verification
     * while leaving the sign-in that follows it perfectly capable of succeeding.
     */
    var credentialAccepted: Boolean = true
    val verifiedCredentials = mutableListOf<Pair<String, String>>()

    override suspend fun verifyEmailCredential(email: String, password: String): Outcome<Unit> {
        verifiedCredentials += email to password
        return if (credentialAccepted) {
            Outcome.Success(Unit)
        } else {
            Outcome.Failure(AppError.INVALID_CREDENTIALS)
        }
    }

    override suspend fun sendPasswordReset(email: String): Outcome<Unit> =
        nextFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)

    override suspend fun signInWithGoogle(activityContext: Context): Outcome<AuthUser> =
        complete { AuthUser("google-user", "player@example.com", AccountType.GOOGLE, true) }

    override suspend fun linkGuestWithGoogle(activityContext: Context): Outcome<AuthUser> =
        link(AccountType.GOOGLE, "player@example.com")

    override suspend fun linkGuestWithEmail(email: String, password: String): Outcome<AuthUser> =
        link(AccountType.EMAIL, email)

    override suspend fun signInToExistingAccount(): Outcome<AuthUser> {
        if (!hasCredentialForExistingAccount) return Outcome.Failure(AppError.UNKNOWN)
        hasCredentialForExistingAccount = false
        val account = AuthUser(EXISTING_USER_ID, EXISTING_EMAIL, AccountType.GOOGLE, true)
        if (existingAccountSignInStrandsSession) return Outcome.Success(account)
        return complete { account }
    }

    override suspend fun discardGuestIdentity() {
        val guest = currentUser()?.takeIf { it.accountType.isGuest } ?: return
        if (!guestDeletionRefused) discardedGuestIds += guest.userId
        becomeIdentity(null)
    }

    override fun forgetExistingAccountCredential() {
        hasCredentialForExistingAccount = false
    }

    override suspend fun signOut() {
        signOutCount++
        hasCredentialForExistingAccount = false
        becomeIdentity(null)
    }

    override suspend fun reauthenticate(activityContext: Context?, password: String): Outcome<Unit> {
        reauthenticateCount++
        return reauthenticationFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }

    override suspend fun deleteAccount(): Outcome<Unit> {
        deleteCount++
        nextFailure?.let { return Outcome.Failure(it) }
        becomeIdentity(null)
        return Outcome.Success(Unit)
    }

    /** Linking upgrades in place: the user id must survive. */
    private fun link(type: AccountType, email: String): Outcome<AuthUser> {
        val current = currentUser() ?: return Outcome.Failure(AppError.NOT_SIGNED_IN)
        nextFailure?.let { return Outcome.Failure(it) }
        val linked = current.copy(accountType = type, email = email)
        becomeIdentity(linked)
        return Outcome.Success(linked)
    }

    private fun complete(build: () -> AuthUser): Outcome<AuthUser> {
        nextFailure?.let { return Outcome.Failure(it) }
        val user = build()
        becomeIdentity(user)
        return Outcome.Success(user)
    }

    private fun becomeIdentity(user: AuthUser?) {
        liveUser = user
        if (!identityFeedHeld) {
            state.value = user?.let(AuthState::SignedIn) ?: AuthState.SignedOut
        }
    }

    companion object {
        /** The account a colliding credential turns out to belong to. */
        const val EXISTING_USER_ID = "existing-user"
        const val EXISTING_EMAIL = "owner@example.com"
    }
}

class FakeUserProfileRepository : UserProfileRepository {
    val profiles = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    val deletedUserIds = mutableListOf<String>()
    var ensureCount = 0

    /** The record already waiting under the account a colliding credential belongs to. */
    fun seedExistingAccount(username: String, rating: Int) {
        val userId = FakeAuthRepository.EXISTING_USER_ID
        profiles.value = profiles.value + (
            userId to UserProfile(
                userId = userId,
                username = username,
                normalizedUsername = username.lowercase(),
                avatarId = "avatar_03",
                email = FakeAuthRepository.EXISTING_EMAIL,
                accountType = AccountType.GOOGLE,
                rating = rating,
            )
            )
    }

    /** Simulates a database that refuses the profile write, e.g. rules not deployed. */
    var failEnsure = false

    /** Simulates a database that is out of reach when the erase is attempted. */
    var failDelete = false

    /**
     * Simulates a listener that is never answered — a connection re-authenticating with a
     * token it has not been given yet, a read the backend simply sits on. The identity is
     * known all the same, and the session has to say so.
     */
    var profileReadsNeverAnswer = false

    override fun observeProfile(userId: String): Flow<UserProfile?> =
        if (profileReadsNeverAnswer) {
            MutableSharedFlow()
        } else {
            profiles.map { it[userId] }
        }

    override suspend fun loadProfile(userId: String): Outcome<UserProfile> =
        profiles.value[userId]?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.UNKNOWN)

    override suspend fun ensureProfile(user: AuthUser): Outcome<UserProfile> {
        ensureCount++
        if (failEnsure) return Outcome.Failure(AppError.UNKNOWN)
        val existing = profiles.value[user.userId]
        // A new profile is named by the app, never by the player: the same generated shape
        // the real repository writes, which is what tells the two apart later.
        val generated = UsernameRules.generatedName()
        val profile = existing?.copy(accountType = user.accountType, email = user.email)
            ?: UserProfile(
                userId = user.userId,
                username = generated,
                normalizedUsername = generated,
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

    override suspend fun updateAvatar(userId: String, avatarId: String): Outcome<Unit> =
        mutate(userId) { it.copy(avatarId = avatarId) }

    override suspend fun updatePreferredLanguage(userId: String, languageTag: String): Outcome<Unit> =
        mutate(userId) { it.copy(preferredLanguage = languageTag) }

    override suspend fun setTutorialCompleted(userId: String, completed: Boolean): Outcome<Unit> =
        mutate(userId) { it.copy(tutorialCompleted = completed) }

    override suspend fun deleteAccountData(userId: String): Outcome<Unit> {
        if (failDelete) return Outcome.Failure(AppError.NETWORK)
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

    /** Who was reported, why, and in which room. */
    val reports = mutableListOf<Triple<String, ContentReportReason, String>>()

    /**
     * What the three listeners publish. StateFlows so a test can change them mid-collection,
     * which is how the real ones behave: they stay open and speak again.
     */
    val friendships = MutableStateFlow<List<Friend>>(emptyList())
    val requests = MutableStateFlow<List<PlayerRequest>>(emptyList())
    val presence = MutableStateFlow<Map<String, PresenceState>>(emptyMap())

    /**
     * Stands in for how a database listener reports a refused read or a connection it has lost:
     * by throwing into the flow rather than by returning anything. Set, all three do it, because
     * what takes an app down is whichever one is not guarded.
     */
    var listenerFailure: Throwable? = null

    override fun observeFriendships(userId: String): Flow<List<Friend>> = orFail(friendships)
    override fun observeRequests(userId: String): Flow<List<PlayerRequest>> = orFail(requests)
    override fun observePresence(userIds: Set<String>): Flow<Map<String, PresenceState>> =
        orFail(presence)

    private fun <T> orFail(source: Flow<T>): Flow<T> =
        listenerFailure?.let { error -> flow { throw error } } ?: source

    override suspend fun findByUsername(username: String): Outcome<UserProfile?> =
        Outcome.Success(null)

    override suspend fun applyFriendshipAction(
        userId: String,
        otherUserId: String,
        action: FriendshipAction,
    ): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun sendInvite(
        fromUserId: String,
        fromUsername: String,
        toUserId: String,
        roomCode: String,
    ): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun sendRematch(
        fromUserId: String,
        fromUsername: String,
        toUserId: String,
        roomCode: String,
        playedRoomCode: String,
    ): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun declineRematch(
        fromUserId: String,
        fromUsername: String,
        toUserId: String,
        roomCode: String,
        playedRoomCode: String,
    ): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun clearRequest(recipientId: String, senderId: String): Outcome<Unit> =
        Outcome.Success(Unit)

    override suspend fun reportPlayer(
        reporterId: String,
        subjectId: String,
        reason: ContentReportReason,
        roomCode: String,
    ): Outcome<Unit> {
        reports += Triple(subjectId, reason, roomCode)
        return Outcome.Success(Unit)
    }

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
    private val usernameChosenState = MutableStateFlow(false)
    private val statisticsState = MutableStateFlow(GameStatistics())
    private val seenAchievementsState = MutableStateFlow<Set<String>?>(null)

    override val settings: Flow<AppSettings> = settingsState
    override val statistics: Flow<GameStatistics> = statisticsState
    override val tutorialCompleted: Flow<Boolean> = tutorialState
    override val guestModeAccepted: Flow<Boolean> = guestState
    override val usernameChosen: Flow<Boolean> = usernameChosenState
    override val seenAchievements: Flow<Set<String>?> = seenAchievementsState

    val currentSettings: AppSettings get() = settingsState.value
    val isTutorialCompleted: Boolean get() = tutorialState.value
    val isGuestModeAccepted: Boolean get() = guestState.value
    val isUsernameChosen: Boolean get() = usernameChosenState.value
    val seenBadges: Set<String>? get() = seenAchievementsState.value

    fun setStatistics(statistics: GameStatistics) {
        statisticsState.value = statistics
    }

    override suspend fun markAchievementsSeen(ids: Set<String>) {
        seenAchievementsState.value = ids
    }

    /** Whose record this is, and every id that has ever claimed it, oldest first. */
    var statisticsOwner: String? = null
    val statisticsClaims = mutableListOf<String>()

    override suspend fun claimStatisticsFor(userId: String) {
        if (userId.isBlank()) return
        statisticsClaims += userId
        val previous = statisticsOwner
        if (previous == userId) return
        if (previous != null) {
            statisticsState.value = GameStatistics()
            seenAchievementsState.value = emptySet()
        }
        statisticsOwner = userId
    }

    override suspend fun setTutorialCompleted(completed: Boolean) {
        tutorialState.value = completed
    }

    override suspend fun setGuestModeAccepted(accepted: Boolean) {
        guestState.value = accepted
    }

    override suspend fun setUsernameChosen(chosen: Boolean) {
        usernameChosenState.value = chosen
    }

    override suspend fun setLanguage(language: AppLanguage) {
        settingsState.value = settingsState.value.copy(language = language)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        settingsState.value = settingsState.value.copy(themeMode = mode)
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        settingsState.value = settingsState.value.copy(soundEnabled = enabled)
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        settingsState.value = settingsState.value.copy(notificationsEnabled = enabled)
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        settingsState.value = settingsState.value.copy(hapticsEnabled = enabled)
    }

    override suspend fun setMatchMessagesEnabled(enabled: Boolean) {
        settingsState.value = settingsState.value.copy(matchMessagesEnabled = enabled)
    }

    override suspend fun setDifficulty(difficulty: Difficulty) {
        settingsState.value = settingsState.value.copy(difficulty = difficulty)
    }

    override suspend fun recordCompletedGame(
        mode: GameMode,
        difficulty: Difficulty,
        winner: PlayerId,
        localPlayer: PlayerId,
        turns: Int,
    ) = Unit
}
