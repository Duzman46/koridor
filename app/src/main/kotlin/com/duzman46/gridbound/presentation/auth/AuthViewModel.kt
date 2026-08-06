package com.duzman46.gridbound.presentation.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.EmailRules
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.core.PasswordRules
import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val passwordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val isGoogleAvailable: Boolean = false,
    val error: UiText? = null,
    val info: UiText? = null,
)

sealed interface AuthEvent {
    /** The player is in and the tutorial gate should decide where they land. */
    data object Entered : AuthEvent

    /** A brand new account: send them to pick a username before anything else. */
    data object NeedsUsername : AuthEvent

    data object PasswordResetSent : AuthEvent
}

/**
 * Drives the welcome, sign-in, sign-up and password-reset screens.
 *
 * Every action is guarded by [AuthUiState.isSubmitting] so a double tap cannot fire two
 * requests, and failures arrive as [UiText] that already hides the backend error code.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(isGoogleAvailable = sessionManager.isGoogleSignInAvailable),
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    fun setEmail(value: String) = _uiState.update { it.copy(email = value, error = null) }

    fun setPassword(value: String) = _uiState.update { it.copy(password = value, error = null) }

    fun setConfirmPassword(value: String) =
        _uiState.update { it.copy(confirmPassword = value, error = null) }

    fun togglePasswordVisibility() =
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun dismissMessages() = _uiState.update { it.copy(error = null, info = null) }

    fun continueAsGuest() = submit {
        when (val result = sessionManager.enterGuestMode()) {
            is Outcome.Success -> _events.emit(AuthEvent.Entered)
            is Outcome.Failure -> fail(result.error)
        }
    }

    /** @param activityContext the hosting Activity; Credential Manager draws over it. */
    fun signInWithGoogle(activityContext: Context) = submit {
        when (val result = sessionManager.signInWithGoogle(activityContext)) {
            is Outcome.Success -> _events.emit(AuthEvent.Entered)
            is Outcome.Failure ->
                // A cancelled picker is the player's own choice, not an error worth showing.
                if (result.error == AppError.GOOGLE_CANCELLED) clearSubmitting() else fail(result.error)
        }
    }

    fun signInWithEmail() = submit {
        val state = _uiState.value
        if (!EmailRules.isValid(state.email)) return@submit fail(AppError.EMAIL_INVALID)
        if (state.password.isEmpty()) return@submit fail(AppError.INVALID_CREDENTIALS)
        when (val result = sessionManager.signInWithEmail(state.email, state.password)) {
            is Outcome.Success -> _events.emit(AuthEvent.Entered)
            is Outcome.Failure -> fail(result.error)
        }
    }

    fun createAccount() = submit {
        val state = _uiState.value
        if (!EmailRules.isValid(state.email)) return@submit fail(AppError.EMAIL_INVALID)
        val password = PasswordRules.validateMatching(state.password, state.confirmPassword)
        if (password is Outcome.Failure) return@submit fail(password.error)
        val result = sessionManager.createAccountWithEmail(
            email = state.email,
            password = state.password,
            username = state.email.substringBefore('@'),
        )
        when (result) {
            is Outcome.Success -> _events.emit(AuthEvent.NeedsUsername)
            is Outcome.Failure -> fail(result.error)
        }
    }

    fun sendPasswordReset() = submit {
        val state = _uiState.value
        if (!EmailRules.isValid(state.email)) return@submit fail(AppError.EMAIL_INVALID)
        when (val result = sessionManager.sendPasswordReset(state.email)) {
            is Outcome.Success -> {
                _uiState.update {
                    it.copy(isSubmitting = false, info = UiText.Res(R.string.auth_reset_sent))
                }
                _events.emit(AuthEvent.PasswordResetSent)
            }

            is Outcome.Failure -> fail(result.error)
        }
    }

    private fun submit(block: suspend () -> Unit) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, error = null, info = null) }
        viewModelScope.launch {
            block()
            // Success paths that navigate away leave the flag set on purpose so the screen
            // stays disabled during the transition; failure paths clear it in fail().
        }
    }

    private fun fail(error: AppError) {
        _uiState.update { it.copy(isSubmitting = false, error = error.message) }
    }

    private fun clearSubmitting() {
        _uiState.update { it.copy(isSubmitting = false) }
    }
}
