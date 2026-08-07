package com.duzman46.gridbound.session

import android.content.Context
import com.duzman46.gridbound.auth.domain.AuthRepository
import com.duzman46.gridbound.auth.domain.AuthState
import com.duzman46.gridbound.auth.domain.AuthUser
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.core.UsernameRules
import com.duzman46.gridbound.di.ApplicationScope
import com.duzman46.gridbound.domain.repository.GameRepository
import com.duzman46.gridbound.profile.domain.UserProfile
import com.duzman46.gridbound.profile.domain.UserProfileRepository
import com.duzman46.gridbound.social.domain.SocialRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SessionStatus {
    LOADING,

    /** No identity and no choice made yet: the welcome screen is shown. */
    SIGNED_OUT,

    /**
     * The player chose to play as a guest but has no Firebase identity, because the device
     * is offline or the online service is not configured. Local play and the tutorial work;
     * anything online reports that there is no connection.
     */
    LOCAL_ONLY,

    SIGNED_IN,
}

/**
 * Everything a screen needs to know about who is playing.
 *
 * @param tutorialCompleted true when either the device or the cloud profile has recorded a
 *   finished tutorial, so a player who already learned the game is never asked twice.
 */
data class SessionState(
    val status: SessionStatus = SessionStatus.LOADING,
    val user: AuthUser? = null,
    val profile: UserProfile? = null,
    val tutorialCompleted: Boolean = false,
    val isOnlineAvailable: Boolean = false,
    val isGoogleSignInAvailable: Boolean = false,
    /** False until the player has named themselves; a profile starts with a generated one. */
    val usernameChosen: Boolean = false,
) {
    val isGuest: Boolean
        get() = status == SessionStatus.LOCAL_ONLY || user?.accountType?.isGuest == true

    /** True once the player may leave the welcome screen and reach the game. */
    val hasEntered: Boolean
        get() = status == SessionStatus.SIGNED_IN || status == SessionStatus.LOCAL_ONLY

    /** Guests may play and learn, but competitive and social features need a real account. */
    val canUseSocialFeatures: Boolean get() = status == SessionStatus.SIGNED_IN && !isGuest

    /**
     * A real account has to be named before it plays a single move: the name is what other
     * players see on the leaderboard, in a friend request and across the board from them,
     * and it is the one thing about an account nobody else can supply.
     *
     * Never true for a guest. A guest is handed a name instead, because asking someone who
     * chose the "no account" door to fill in a form is the opposite of what they asked for.
     *
     * The profile is required because there is nowhere to write the answer without one: an
     * account signed in against an unreachable backend is left alone until it has one.
     */
    val needsUsername: Boolean
        get() = canUseSocialFeatures && profile != null && !usernameChosen
}

/**
 * The single place the app signs players in, keeps their profile in step and decides what a
 * guest is allowed to do. Screens talk to this, never to FirebaseAuth.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SessionManager @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: UserProfileRepository,
    private val socialRepository: SocialRepository,
    private val gameRepository: GameRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    val state: StateFlow<SessionState> = combine(
        authRepository.authState.flatMapLatest { auth ->
            when (auth) {
                is AuthState.SignedIn -> profileRepository.observeProfile(auth.user.userId)
                    .let { profiles -> combine(flowOf(auth), profiles) { a, p -> a to p } }

                else -> flowOf(auth to null)
            }
        },
        gameRepository.tutorialCompleted,
        gameRepository.guestModeAccepted,
        gameRepository.usernameChosen,
    ) { (auth, profile), locallyCompleted, guestAccepted, nameChosen ->
        SessionState(
            status = when {
                auth is AuthState.SignedIn -> SessionStatus.SIGNED_IN
                auth is AuthState.Loading -> SessionStatus.LOADING
                // Signed out but the player already opted into guest play: keep them in the
                // game instead of bouncing them back to the welcome screen while offline.
                guestAccepted -> SessionStatus.LOCAL_ONLY
                else -> SessionStatus.SIGNED_OUT
            },
            user = (auth as? AuthState.SignedIn)?.user,
            profile = profile,
            tutorialCompleted = locallyCompleted || profile?.tutorialCompleted == true,
            isOnlineAvailable = authRepository.isConfigured,
            isGoogleSignInAvailable = authRepository.isGoogleSignInAvailable,
            // The local flag only knows about this install. A name that is not one the app
            // made up was typed by a person, so a reinstall or a second handset does not
            // demand that an established player name themselves a second time.
            usernameChosen = nameChosen ||
                profile?.username?.let { !UsernameRules.isGenerated(it) } == true,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = SessionState(
            isOnlineAvailable = authRepository.isConfigured,
            isGoogleSignInAvailable = authRepository.isGoogleSignInAvailable,
        ),
    )

    init {
        // Declared after `state` on purpose: an initializer that launched a coroutine
        // reading `state` could run before the field was assigned.
        //
        // Presence follows the identity — registered as soon as there is one, and torn down
        // by the server's disconnect handler if the app dies without a clean sign-out.
        scope.launch {
            state.map { it.user?.userId.takeIf { _ -> it.canUseSocialFeatures } }
                .distinctUntilChanged()
                .collect { userId -> userId?.let(socialRepository::startPresence) }
        }
    }

    val isConfigured: Boolean get() = authRepository.isConfigured

    val isGoogleSignInAvailable: Boolean get() = authRepository.isGoogleSignInAvailable

    /**
     * Enters guest play. An anonymous identity is created when the network allows it, but
     * the choice is recorded either way, so a first launch with no connection still reaches
     * the tutorial and local matches instead of stalling on the welcome screen.
     */
    suspend fun enterGuestMode(): Outcome<Unit> {
        gameRepository.setGuestModeAccepted(true)
        val identity = authRepository.signInAsGuest()
        if (identity is Outcome.Failure) {
            // No identity. Offline or an unconfigured backend still lets the player in as a
            // local guest; anything else is a genuine failure worth reporting.
            return if (identity.error.blocksLocalPlay) identity else Outcome.Success(Unit)
        }
        // The identity exists, so the player can already play. A profile that cannot be
        // written yet — rules not deployed, a transient backend error — must not keep them
        // out of the game; ensureProfile runs again on the next sign-in.
        profileRepository.ensureProfile((identity as Outcome.Success).value)
        return Outcome.Success(Unit)
    }

    /**
     * Gives an offline guest a real anonymous identity once a connection is available, so
     * their local progress can start syncing without another prompt.
     */
    suspend fun ensureBackendIdentity() {
        if (state.value.status != SessionStatus.LOCAL_ONLY) return
        authRepository.signInAsGuest().thenEnsureProfile()
    }

    suspend fun signInWithEmail(email: String, password: String): Outcome<UserProfile> =
        authRepository.signInWithEmail(email, password).thenEnsureProfile()

    suspend fun createAccountWithEmail(email: String, password: String): Outcome<UserProfile> =
        authRepository.createAccountWithEmail(email, password).thenEnsureProfile()

    suspend fun signInWithGoogle(activityContext: Context): Outcome<UserProfile> =
        authRepository.signInWithGoogle(activityContext).thenEnsureProfile()

    /**
     * Upgrades the current guest without changing the user id, so the profile that already
     * holds their stats simply gains a credential.
     *
     * A guest who never got an anonymous identity — they first launched with no connection —
     * has no user id and no cloud progress to carry over, so for them this is an ordinary
     * sign-in. Without that branch the only way a local guest could reach a real account was
     * to sign out first, and the button in front of them answered "you are not signed in".
     */
    suspend fun linkGuestWithGoogle(activityContext: Context): Outcome<UserProfile> =
        if (state.value.status == SessionStatus.LOCAL_ONLY) {
            signInWithGoogle(activityContext)
        } else {
            authRepository.linkGuestWithGoogle(activityContext).thenEnsureProfile()
        }

    suspend fun linkGuestWithEmail(email: String, password: String): Outcome<UserProfile> =
        if (state.value.status == SessionStatus.LOCAL_ONLY) {
            createAccountWithEmail(email, password)
        } else {
            authRepository.linkGuestWithEmail(email, password).thenEnsureProfile()
        }

    suspend fun sendPasswordReset(email: String): Outcome<Unit> =
        authRepository.sendPasswordReset(email)

    /** Returns the player to the welcome screen: the guest opt-in is cleared too. */
    suspend fun signOut() {
        currentUserId()?.let { socialRepository.clearPresence(it) }
        authRepository.signOut()
        gameRepository.setGuestModeAccepted(false)
    }

    suspend fun changeUsername(username: String): Outcome<String> {
        val userId = currentUserId() ?: return Outcome.Failure(AppError.NOT_SIGNED_IN)
        return profileRepository.changeUsername(userId, username).also { result ->
            // Recorded locally, so the "what should we call you?" step is asked once and
            // never again — including for a guest, whose generated name is otherwise
            // indistinguishable from one they picked.
            if (result is Outcome.Success) gameRepository.setUsernameChosen(true)
        }
    }

    suspend fun isUsernameAvailable(username: String): Outcome<Boolean> =
        profileRepository.isUsernameAvailable(username)

    suspend fun updateAvatar(avatarId: String): Outcome<Unit> {
        val userId = currentUserId() ?: return Outcome.Failure(AppError.NOT_SIGNED_IN)
        return profileRepository.updateAvatar(userId, avatarId)
    }

    /** Records locally first so the gate is correct offline, then mirrors to the profile. */
    suspend fun setTutorialCompleted(completed: Boolean) {
        gameRepository.setTutorialCompleted(completed)
        currentUserId()?.let { profileRepository.setTutorialCompleted(it, completed) }
    }

    fun updatePreferredLanguage(languageTag: String) {
        val userId = currentUserId() ?: return
        scope.launch { profileRepository.updatePreferredLanguage(userId, languageTag) }
    }

    /**
     * Erases everything the account owns, then the credential itself.
     *
     * The order is forced from both ends. The database rules only authorise a player to
     * delete their own rows while they are still signed in, so the data cannot go last —
     * and Firebase refuses to delete a credential whose sign-in it considers stale, so the
     * proof has to come first. Deleting the data on a session that then turns out to be too
     * old to remove leaves a player signed into an account with nothing in it and no way
     * back, which is worse than the refusal.
     *
     * @param activityContext the hosting Activity; a Google account proves itself through
     *   the Credential Manager sheet, which draws over it.
     * @param password the account's password, needed only by an email account.
     */
    suspend fun deleteAccount(activityContext: Context?, password: String): Outcome<Unit> {
        val userId = currentUserId() ?: return Outcome.Failure(AppError.NOT_SIGNED_IN)
        val proof = authRepository.reauthenticate(activityContext, password)
        if (proof is Outcome.Failure) return proof
        // Social links first: they live under other players' nodes, which stop being
        // writable the moment this identity is gone.
        val socialDeletion = socialRepository.deleteSocialData(userId)
        if (socialDeletion is Outcome.Failure) return socialDeletion
        val dataDeletion = profileRepository.deleteAccountData(userId)
        if (dataDeletion is Outcome.Failure) return dataDeletion
        val accountDeletion = authRepository.deleteAccount()
        if (accountDeletion is Outcome.Success) {
            gameRepository.setTutorialCompleted(false)
            gameRepository.setGuestModeAccepted(false)
            gameRepository.setUsernameChosen(false)
        }
        return accountDeletion
    }

    private fun currentUserId(): String? =
        state.value.user?.userId ?: authRepository.currentUser()?.userId

    private suspend fun Outcome<AuthUser>.thenEnsureProfile(): Outcome<UserProfile> = when (this) {
        is Outcome.Failure -> this
        is Outcome.Success -> profileRepository.ensureProfile(value)
    }
}
