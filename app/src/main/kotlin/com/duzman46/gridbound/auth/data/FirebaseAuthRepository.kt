package com.duzman46.gridbound.auth.data

import android.content.Context
import com.duzman46.gridbound.auth.domain.AccountType
import com.duzman46.gridbound.auth.domain.AuthRepository
import com.duzman46.gridbound.auth.domain.AuthState
import com.duzman46.gridbound.auth.domain.AuthUser
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.data.firebase.await
import com.duzman46.gridbound.online.data.FirebaseProvider
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebase: FirebaseProvider,
    private val googleCredentialClient: GoogleCredentialClient,
) : AuthRepository {

    override val isConfigured: Boolean get() = firebase.isConfigured

    override val isGoogleSignInAvailable: Boolean
        get() = firebase.isConfigured && googleCredentialClient.isAvailable

    override val hasCredentialForExistingAccount: Boolean
        get() = existingAccountCredential != null

    /**
     * Nudged after an operation that changes who the current user is without replacing them.
     *
     * Linking a credential onto an anonymous user is the case that matters: the uid is
     * unchanged, so as far as Firebase's listeners are concerned nobody signed in and nobody
     * signed out, and the app would go on believing it is talking to a guest. The player
     * sees a link that appears to do nothing.
     */
    private val identityChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * The credential a link collided with, held until it is spent or a later attempt clears it.
     *
     * Firebase will not link a credential that already belongs to somebody, and there is no
     * merge to fall back on: the only thing left to offer is signing in as that account.
     * Keeping the credential is what lets the offer be answered on the spot. The player
     * proved they own it seconds earlier, and sending them back through the account picker
     * would both ask twice and leave room for a *different* account to come back the second
     * time — after they had already been warned about the first.
     */
    private var existingAccountCredential: AuthCredential? = null

    override val authState: Flow<AuthState> =
        if (!firebase.isConfigured) {
            flowOf(AuthState.SignedOut)
        } else {
            merge(
                callbackFlow {
                    val auth = firebase.auth
                    // The token listener rather than the auth-state one: the latter reports
                    // only a change of user, and gaining a credential is a change to the
                    // user you already had.
                    val listener = FirebaseAuth.IdTokenListener { instance ->
                        trySend(instance.currentUser.toAuthState())
                    }
                    auth.addIdTokenListener(listener)
                    awaitClose { auth.removeIdTokenListener(listener) }
                },
                identityChanged.map { firebase.auth.currentUser.toAuthState() },
            )
                // A token refreshes roughly hourly and says nothing new about the identity.
                // Passing that on would restart every listener downstream for no reason.
                .distinctUntilChanged()
        }

    override fun currentUser(): AuthUser? =
        if (!firebase.isConfigured) null else firebase.auth.currentUser?.toAuthUser()

    override suspend fun signInAsGuest(): Outcome<AuthUser> = authCall("sign-in-guest") {
        // An existing anonymous session is reused so guest progress survives a restart.
        firebase.auth.currentUser?.takeIf { it.isAnonymous }?.let { return@authCall it }
        firebase.auth.signInAnonymously().await().user
    }

    override suspend fun signInWithEmail(email: String, password: String): Outcome<AuthUser> =
        authCall("sign-in-email") {
            firebase.auth.signInWithEmailAndPassword(email.trim(), password).await().user
        }

    override suspend fun createAccountWithEmail(email: String, password: String): Outcome<AuthUser> =
        authCall("create-account-email") {
            firebase.auth.createUserWithEmailAndPassword(email.trim(), password).await().user
        }

    override suspend fun sendPasswordReset(email: String): Outcome<Unit> {
        if (!firebase.isConfigured) return Outcome.Failure(AppError.SERVICE_UNAVAILABLE)
        return try {
            firebase.auth.sendPasswordResetEmail(email.trim()).await()
            Outcome.Success(Unit)
        } catch (error: FirebaseAuthInvalidUserException) {
            // Deliberately reported as success: telling a caller whether an address is
            // registered would turn this screen into an account enumeration oracle.
            AppLog.debug("Password reset requested for an unknown address")
            Outcome.Success(Unit)
        } catch (error: Exception) {
            AppLog.warn("send-password-reset", error)
            Outcome.Failure(error.toAppError())
        }
    }

    override suspend fun signInWithGoogle(activityContext: Context): Outcome<AuthUser> =
        withGoogleCredential(activityContext) { credential ->
            authCall("sign-in-google") { firebase.auth.signInWithCredential(credential).await().user }
        }

    override suspend fun linkGuestWithGoogle(activityContext: Context): Outcome<AuthUser> =
        withGoogleCredential(activityContext) { credential ->
            linkCurrentUser("link-google", credential)
        }

    override suspend fun linkGuestWithEmail(email: String, password: String): Outcome<AuthUser> =
        linkCurrentUser("link-email", EmailAuthProvider.getCredential(email.trim(), password))

    override suspend fun signInToExistingAccount(): Outcome<AuthUser> {
        val credential = existingAccountCredential ?: return Outcome.Failure(AppError.UNKNOWN)
        // Spent on the attempt, win or lose. A retry belongs to the account picker, which
        // asks again who the player means; a credential lying around after its question was
        // answered is one that can be spent on a question nobody asked.
        existingAccountCredential = null
        return authCall("sign-in-existing-account") {
            firebase.auth.signInWithCredential(credential).await().user
        }
    }

    override suspend fun discardGuestIdentity() {
        if (!firebase.isConfigured) return
        val guest = firebase.auth.currentUser?.takeIf { it.isAnonymous } ?: return
        runCatching { guest.delete().await() }
            .onFailure { AppLog.warn("discard-guest-identity", it) }
    }

    override suspend fun signOut() {
        if (!firebase.isConfigured) return
        existingAccountCredential = null
        firebase.auth.signOut()
        googleCredentialClient.clearSelection()
    }

    override suspend fun reauthenticate(activityContext: Context?, password: String): Outcome<Unit> {
        if (!firebase.isConfigured) return Outcome.Failure(AppError.SERVICE_UNAVAILABLE)
        val user = firebase.auth.currentUser ?: return Outcome.Failure(AppError.NOT_SIGNED_IN)
        val credential = when (user.resolveAccountType()) {
            // Anonymous play rests on the session alone. There is no credential to present,
            // and Firebase asks for none.
            AccountType.GUEST -> return Outcome.Success(Unit)

            AccountType.GOOGLE -> {
                val host = activityContext ?: return Outcome.Failure(AppError.GOOGLE_UNAVAILABLE)
                when (val token = googleCredentialClient.requestIdToken(host)) {
                    is Outcome.Failure -> return token
                    is Outcome.Success -> GoogleAuthProvider.getCredential(token.value, null)
                }
            }

            AccountType.EMAIL -> {
                val email = user.email
                if (email.isNullOrBlank() || password.isEmpty()) {
                    return Outcome.Failure(AppError.INVALID_CREDENTIALS)
                }
                EmailAuthProvider.getCredential(email, password)
            }
        }
        return try {
            user.reauthenticate(credential).await()
            Outcome.Success(Unit)
        } catch (error: Exception) {
            AppLog.warn("reauthenticate", error)
            Outcome.Failure(error.toAppError())
        }
    }

    override suspend fun deleteAccount(): Outcome<Unit> {
        if (!firebase.isConfigured) return Outcome.Failure(AppError.SERVICE_UNAVAILABLE)
        val user = firebase.auth.currentUser ?: return Outcome.Failure(AppError.NOT_SIGNED_IN)
        return try {
            user.delete().await()
            googleCredentialClient.clearSelection()
            Outcome.Success(Unit)
        } catch (error: Exception) {
            AppLog.warn("delete-account", error)
            Outcome.Failure(error.toAppError())
        }
    }

    /**
     * Links a credential onto the signed-in anonymous user so the user id — and therefore
     * every stat, purchase and friendship keyed by it — is carried over unchanged.
     */
    private suspend fun linkCurrentUser(
        operation: String,
        credential: AuthCredential,
    ): Outcome<AuthUser> {
        if (!firebase.isConfigured) return Outcome.Failure(AppError.SERVICE_UNAVAILABLE)
        val current = firebase.auth.currentUser ?: return Outcome.Failure(AppError.NOT_SIGNED_IN)
        // Whatever the last attempt collided with is stale the moment a new one starts.
        existingAccountCredential = null
        return try {
            val linked = current.linkWithCredential(credential).await().user
                ?: return Outcome.Failure(AppError.UNKNOWN)
            identityChanged.tryEmit(Unit)
            Outcome.Success(linked.toAuthUser())
        } catch (error: Exception) {
            AppLog.warn(operation, error)
            existingAccountCredential = credentialForExistingAccount(credential, error)
            Outcome.Failure(error.toAppError())
        }
    }

    /**
     * The credential to sign in with when a link failed because its account already exists,
     * and null for every other failure.
     *
     * Google only, and that asymmetry is the whole point. A Google credential is proof in
     * itself — the player authenticated with Google to produce it — so the sign-in it enables
     * can only fail on the network. An email credential carries a password nobody has checked
     * against that account, and the hand-over it would lead to erases the guest's data before
     * it signs in: a mistyped password there would cost the player both identities. For that
     * case the address being taken is all they are told, and nothing is destroyed.
     *
     * The code is checked rather than the exception type, and only the one code will do. A
     * collision can also mean the address behind the credential belongs to an account that
     * signs in some other way, and no credential held here would get anybody into that one.
     *
     * Firebase's own updated credential wins where it supplies one, because some providers
     * treat the credential presented to a failed link as spent.
     */
    private fun credentialForExistingAccount(
        attempted: AuthCredential,
        error: Exception,
    ): AuthCredential? {
        if (attempted.provider != GoogleAuthProvider.PROVIDER_ID) return null
        val collision = error as? FirebaseAuthUserCollisionException ?: return null
        if (collision.errorCode != CREDENTIAL_ALREADY_IN_USE) return null
        return collision.updatedCredential ?: attempted
    }

    private suspend fun withGoogleCredential(
        activityContext: Context,
        block: suspend (AuthCredential) -> Outcome<AuthUser>,
    ): Outcome<AuthUser> {
        if (!firebase.isConfigured) return Outcome.Failure(AppError.SERVICE_UNAVAILABLE)
        return when (val token = googleCredentialClient.requestIdToken(activityContext)) {
            is Outcome.Failure -> token
            is Outcome.Success -> block(GoogleAuthProvider.getCredential(token.value, null))
        }
    }

    private suspend fun authCall(
        operation: String,
        block: suspend () -> FirebaseUser?,
    ): Outcome<AuthUser> {
        if (!firebase.isConfigured) return Outcome.Failure(AppError.SERVICE_UNAVAILABLE)
        return try {
            val user = block() ?: return Outcome.Failure(AppError.UNKNOWN)
            Outcome.Success(user.toAuthUser())
        } catch (error: Exception) {
            AppLog.warn(operation, error)
            Outcome.Failure(error.toAppError())
        }
    }
}

/** Firebase's code for "this credential already signs into an account of its own". */
private const val CREDENTIAL_ALREADY_IN_USE = "ERROR_CREDENTIAL_ALREADY_IN_USE"

private fun FirebaseUser?.toAuthState(): AuthState =
    if (this == null) AuthState.SignedOut else AuthState.SignedIn(toAuthUser())

private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
    userId = uid,
    email = email?.takeIf(String::isNotBlank),
    accountType = resolveAccountType(),
    isEmailVerified = isEmailVerified,
)

private fun FirebaseUser.resolveAccountType(): AccountType = when {
    isAnonymous -> AccountType.GUEST
    providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } -> AccountType.GOOGLE
    else -> AccountType.EMAIL
}

/**
 * Collapses every Firebase failure onto a message the player can act on. The original
 * exception is logged by the caller and never surfaces.
 */
private fun Exception.toAppError(): AppError = when (this) {
    is FirebaseNetworkException -> AppError.NETWORK
    is FirebaseTooManyRequestsException -> AppError.TOO_MANY_REQUESTS
    is FirebaseAuthWeakPasswordException -> AppError.PASSWORD_WEAK
    is FirebaseAuthRecentLoginRequiredException -> AppError.REQUIRES_RECENT_LOGIN
    is FirebaseAuthUserCollisionException -> when (errorCode) {
        CREDENTIAL_ALREADY_IN_USE -> AppError.CREDENTIAL_IN_USE
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> AppError.ACCOUNT_EXISTS_WITH_OTHER_METHOD
        else -> AppError.EMAIL_IN_USE
    }
    is FirebaseAuthInvalidUserException -> AppError.INVALID_CREDENTIALS
    is FirebaseAuthInvalidCredentialsException ->
        if (errorCode == "ERROR_INVALID_EMAIL") AppError.EMAIL_INVALID else AppError.INVALID_CREDENTIALS
    is FirebaseAuthException -> when (errorCode) {
        "ERROR_INVALID_EMAIL" -> AppError.EMAIL_INVALID
        "ERROR_EMAIL_ALREADY_IN_USE" -> AppError.EMAIL_IN_USE
        "ERROR_WEAK_PASSWORD" -> AppError.PASSWORD_WEAK
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" ->
            AppError.INVALID_CREDENTIALS
        "ERROR_TOO_MANY_REQUESTS" -> AppError.TOO_MANY_REQUESTS
        "ERROR_REQUIRES_RECENT_LOGIN" -> AppError.REQUIRES_RECENT_LOGIN
        CREDENTIAL_ALREADY_IN_USE -> AppError.CREDENTIAL_IN_USE
        else -> AppError.UNKNOWN
    }
    else -> AppError.UNKNOWN
}
