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
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebase: FirebaseProvider,
    private val googleCredentialClient: GoogleCredentialClient,
) : AuthRepository {

    override val isConfigured: Boolean get() = firebase.isConfigured

    override val isGoogleSignInAvailable: Boolean
        get() = firebase.isConfigured && googleCredentialClient.isAvailable

    override val authState: Flow<AuthState> =
        if (!firebase.isConfigured) {
            flowOf(AuthState.SignedOut)
        } else {
            callbackFlow {
                val auth = firebase.auth
                val listener = FirebaseAuth.AuthStateListener { instance ->
                    trySend(instance.currentUser.toAuthState())
                }
                auth.addAuthStateListener(listener)
                awaitClose { auth.removeAuthStateListener(listener) }
            }
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
            linkCurrentUser("link-google") { it.linkWithCredential(credential).await().user }
        }

    override suspend fun linkGuestWithEmail(email: String, password: String): Outcome<AuthUser> {
        val credential = com.google.firebase.auth.EmailAuthProvider
            .getCredential(email.trim(), password)
        return linkCurrentUser("link-email") { it.linkWithCredential(credential).await().user }
    }

    override suspend fun signOut() {
        if (!firebase.isConfigured) return
        firebase.auth.signOut()
        googleCredentialClient.clearSelection()
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
        block: suspend (FirebaseUser) -> FirebaseUser?,
    ): Outcome<AuthUser> {
        if (!firebase.isConfigured) return Outcome.Failure(AppError.SERVICE_UNAVAILABLE)
        val current = firebase.auth.currentUser ?: return Outcome.Failure(AppError.NOT_SIGNED_IN)
        return try {
            val linked = block(current) ?: return Outcome.Failure(AppError.UNKNOWN)
            Outcome.Success(linked.toAuthUser())
        } catch (error: Exception) {
            AppLog.warn(operation, error)
            Outcome.Failure(error.toAppError())
        }
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
        "ERROR_CREDENTIAL_ALREADY_IN_USE" -> AppError.CREDENTIAL_IN_USE
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
        "ERROR_CREDENTIAL_ALREADY_IN_USE" -> AppError.CREDENTIAL_IN_USE
        else -> AppError.UNKNOWN
    }
    else -> AppError.UNKNOWN
}
