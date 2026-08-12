package com.duzman46.gridbound.session

import android.content.Context
import com.duzman46.gridbound.auth.domain.AuthRepository
import com.duzman46.gridbound.auth.domain.AuthState
import com.duzman46.gridbound.auth.domain.AuthUser
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.Outcome
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
     * A guest is never offered a new name; everybody else may change theirs at will.
     *
     * The name a guest carries was handed to them, and nobody else can read it: a guest is
     * absent from the leaderboard, cannot be sent a friend request and never meets another
     * player in a ranked match. Renaming would change nothing anyone will ever see, while
     * permanently reserving that name against an identity that lasts only as long as this
     * install. The name that counts is the one chosen when an account is linked, and being
     * sent there is a better answer than a text field.
     */
    val canChangeUsername: Boolean get() = status == SessionStatus.SIGNED_IN && !isGuest

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
                // Who is playing is known the instant the identity changes; what they have
                // played is a database read that can be slow, refused, or waiting behind a
                // connection re-authenticating with the new token. Holding the pair until
                // both have landed keeps the whole session — status, user, everything a
                // screen draws — on the player who just left, for as long as that read
                // takes. A profile that has not arrived yet is null and says so.
                is AuthState.SignedIn -> profileRepository.observeProfile(auth.user.userId)
                    .onStart { emit(null) }
                    .map { profile -> auth to profile }

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
            usernameChosen = nameChosen || profile?.hasChosenName == true,
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
     * True when a link has just failed against an account that already exists, so the offer
     * to sign in as it can be made. Reads the credential Firebase handed back rather than
     * inferring anything from the error the player was shown.
     */
    val canSignInToExistingAccount: Boolean
        get() = authRepository.hasCredentialForExistingAccount

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

    /**
     * Gives up the guest identity and signs in as the account the link collided with.
     *
     * Firebase has no merge between two identities that both exist, so nothing of the
     * guest's crosses over — not the rating, not the record, not a friend. The player has
     * to have been told that in words before this runs; all this does is carry it out, and
     * what arrives is the other account's own history.
     *
     * The guest's profile row and the reservation holding its generated name go first,
     * while the guest is still the one asking. The rules authorise a player to delete only
     * their own rows, and the instant the other account signs in the guest's uid is
     * unreachable from this device and from every other one — so anything left behind is
     * left for good, with a name reserved against nobody. A guest has no friendships or
     * presence to clear: both are refused to them, so the profile is the whole of it.
     *
     * That order means a sign-in that then fails on the network leaves the player as a
     * local guest with nothing in the cloud, and the same button signs them in again. The
     * reverse order litters the database on every attempt that succeeds, and nothing can
     * ever tidy it.
     *
     * Success is the session carrying the other account, not the calls along the way coming
     * back without an exception. Everything the player is about to look at — their rating,
     * their record, whether the screen still offers to keep a guest's progress — is drawn
     * from [state], so [state] is the only thing whose agreement means the hand-over
     * happened.
     */
/**
     * Hands the device over to an e-mail account that already exists.
     *
     * The same erasure and the same order as [signInToExistingAccount], with one step in front
     * of it: the password is proven against a throwaway auth instance first. That is what makes
     * this safe to offer at all — the guest's rows are deleted before the sign-in, so without
     * the proof a mistyped password would take the guest's account and give nothing back.
     *
     * Nothing merges. The player has to have been told that in words before this runs.
     */
    suspend fun signInWithExistingEmail(email: String, password: String): Outcome<UserProfile> {
        val proven = authRepository.verifyEmailCredential(email, password)
        if (proven is Outcome.Failure) return proven

        val guestId = state.value.takeIf { it.isGuest }?.user?.userId
        if (guestId != null) {
            val erased = profileRepository.deleteAccountData(guestId)
            // Stop rather than orphan, exactly as the other hand-over does.
            if (erased is Outcome.Failure) return erased
            authRepository.discardGuestIdentity()
        }
        gameRepository.setGuestModeAccepted(false)
        gameRepository.setUsernameChosen(false)
        val account = when (val signedIn = authRepository.signInWithEmail(email, password)) {
            is Outcome.Failure -> return signedIn
            is Outcome.Success -> signedIn.value
        }
        val profile = profileRepository.ensureProfile(account)
        if (profile is Outcome.Failure) return profile
        return if (awaitIdentity(account.userId)) {
            profile
        } else {
            Outcome.Failure(AppError.ACCOUNT_SWITCH_FAILED)
        }
    }

        suspend fun signInToExistingAccount(): Outcome<UserProfile> {
        if (!authRepository.hasCredentialForExistingAccount) {
            return Outcome.Failure(AppError.UNKNOWN)
        }
        val guestId = state.value.takeIf { it.isGuest }?.user?.userId
        if (guestId != null) {
            val erased = profileRepository.deleteAccountData(guestId)
            // Stop rather than orphan. Nothing has been given up yet, so a player who tries
            // again in better conditions still has everything they started with.
            if (erased is Outcome.Failure) return erased
            authRepository.discardGuestIdentity()
        }
        // Both flags describe the player who is leaving, and both are read as if they
        // described whoever is here now. Guest entry left standing keeps the session
        // reading LOCAL_ONLY — a guest, with the offer to keep a guest's progress still on
        // screen — for as long as no identity is in place. The name answer left standing
        // waves the arriving account past the entry gate wearing whatever the app invented
        // for it.
        gameRepository.setGuestModeAccepted(false)
        gameRepository.setUsernameChosen(false)
        val account = when (val signedIn = authRepository.signInToExistingAccount()) {
            is Outcome.Failure -> return signedIn
            is Outcome.Success -> signedIn.value
        }
        val profile = profileRepository.ensureProfile(account)
        if (profile is Outcome.Failure) return profile
        return if (awaitIdentity(account.userId)) {
            profile
        } else {
            Outcome.Failure(AppError.ACCOUNT_SWITCH_FAILED)
        }
    }

    /**
     * Suspends until [state] reports [userId] as the signed-in account, and answers whether
     * it ever did.
     *
     * Bounded rather than open-ended: a confirmation that never returns is its own kind of
     * lie, and the caller has a message ready for a hand-over that did not land.
     */
    private suspend fun awaitIdentity(userId: String): Boolean =
        withTimeoutOrNull(Constants.Backend.IDENTITY_SETTLE_TIMEOUT_MILLIS) {
            state.first { it.status == SessionStatus.SIGNED_IN && it.user?.userId == userId }
            true
        } == true

    /**
     * Answers the offer of somebody else's account with "no", and the guest keeps
     * everything. The credential goes with the answer: it was kept only so the question
     * could be settled on the spot.
     */
    fun declineExistingAccount() = authRepository.forgetExistingAccountCredential()

    suspend fun sendPasswordReset(email: String): Outcome<Unit> =
        authRepository.sendPasswordReset(email)

    /** Returns the player to the welcome screen: the guest opt-in is cleared too. */
    suspend fun signOut() {
        currentUserId()?.let { socialRepository.clearPresence(it) }
        authRepository.signOut()
        gameRepository.setGuestModeAccepted(false)
        // The flag records that *this* player answered "what should we call you?". Whoever
        // signs in next has not, and a device that has been signed into once must not wave
        // the next account past the gate under a name the app invented for it.
        gameRepository.setUsernameChosen(false)
    }

    suspend fun changeUsername(username: String): Outcome<String> {
        val userId = namedAccountId() ?: return Outcome.Failure(AppError.NOT_SIGNED_IN)
        return profileRepository.changeUsername(userId, username).also { result ->
            // Recorded locally, so the "what should we call you?" step is asked once and
            // never again for this player, whatever a later reinstall knows about them.
            if (result is Outcome.Success) gameRepository.setUsernameChosen(true)
        }
    }

    /**
     * The id of the account entitled to a public name, or null when this player is a guest.
     *
     * Answered here and not only where the field is drawn: a name is the anchor of a public
     * identity, and a guest has none to anchor.
     *
     * [state] alone cannot answer it. It is rebuilt from [AuthRepository.authState], so a
     * credential that landed a moment ago reaches it a moment later — while the screen asking
     * for the name was opened on the write that landed, not on the flow. Between those two
     * moments the session still reads as the guest it was, and deciding the write on that
     * reading answers "you are not signed in" to the very player the app has just sent to the
     * username gate. Every route into that gate after a guest gains an account is in the gap:
     * a link, which keeps the user id so nothing else marks the change; a local guest whose
     * first credential creates the account outright; and the hand-over to an account the
     * credential already belonged to.
     *
     * The live identity is what settles it, because it is the same thing the gate was opened
     * on: it is a guest or it is not, and nothing is in flight either way. Where it agrees
     * with the session there is nothing to decide; where it does not, the session is the one
     * that is behind, and [awaitNamedIdentity] gives it the same bounded moment to catch up
     * that every other identity hand-over here is given.
     *
     * That wait is not politeness. The session moves when Firebase hands out a new ID token,
     * which is also when the database connection is given one, and the name is written under
     * rules that authorise it against the token. Writing first would hand the claim to a
     * connection still presenting the guest's, which the rules refuse.
     */
    private suspend fun namedAccountId(): String? {
        if (state.value.canChangeUsername) return currentUserId()
        val live = authRepository.currentUser() ?: return null
        if (live.accountType.isGuest) return null
        awaitNamedIdentity(live.userId)
        return live.userId
    }

    /**
     * Suspends until [state] reports [userId] as an account that may be named.
     *
     * Bounded, and its answer is deliberately not a veto: the identity has already been read
     * from the source the session itself is built on, so a flow that is slow to agree is a
     * stale reading, not a refusal.
     *
     * Bounded much more tightly than [awaitIdentity], which waits for a different thing on a
     * screen the player can walk away from; see [Constants.Backend.SESSION_CATCHUP_TIMEOUT_MILLIS]
     * for why a second is the right order of magnitude and fifteen is not.
     */
    private suspend fun awaitNamedIdentity(userId: String) {
        withTimeoutOrNull(Constants.Backend.SESSION_CATCHUP_TIMEOUT_MILLIS) {
            state.first { it.canChangeUsername && it.user?.userId == userId }
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
