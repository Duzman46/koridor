package com.duzman46.gridbound.auth.data

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.duzman46.gridbound.BuildConfig
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.core.Outcome
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over Credential Manager that yields a Google ID token.
 *
 * The token is handed straight to Firebase and never stored, logged or written to the
 * database. Cancellation is reported as its own error so the UI can stay silent instead of
 * showing a failure the player caused on purpose.
 */
@Singleton
class GoogleCredentialClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val isAvailable: Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    private val credentialManager: CredentialManager by lazy { CredentialManager.create(context) }

    /** @param activityContext must be an Activity so the account picker can be shown. */
    suspend fun requestIdToken(activityContext: Context): Outcome<String> {
        if (!isAvailable) return Outcome.Failure(AppError.GOOGLE_UNAVAILABLE)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build(),
            )
            .build()
        return try {
            val credential = credentialManager.getCredential(activityContext, request).credential
            val isGoogleIdToken = credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            if (isGoogleIdToken) {
                Outcome.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                AppLog.warn("google-credential-unexpected-type")
                Outcome.Failure(AppError.GOOGLE_UNAVAILABLE)
            }
        } catch (error: GetCredentialCancellationException) {
            AppLog.debug("Google sign-in cancelled by the player")
            Outcome.Failure(AppError.GOOGLE_CANCELLED)
        } catch (error: NoCredentialException) {
            AppLog.warn("google-credential-none", error)
            Outcome.Failure(AppError.GOOGLE_UNAVAILABLE)
        } catch (error: GetCredentialException) {
            AppLog.warn("google-credential", error)
            Outcome.Failure(AppError.GOOGLE_UNAVAILABLE)
        }
    }

    /** Drops the remembered choice so the next sign-in shows the picker again. */
    suspend fun clearSelection() {
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
            .onFailure { AppLog.warn("google-credential-clear", it) }
    }
}
