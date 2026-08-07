package com.duzman46.gridbound.presentation.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.BuildConfig
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.EmailRules
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.core.PasswordRules
import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.session.SessionManager
import com.duzman46.gridbound.session.SessionState
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

data class AccountUiState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: UiText? = null,
    val info: UiText? = null,
    val privacyPolicyUrl: String = BuildConfig.PRIVACY_POLICY_URL,
    val termsUrl: String = BuildConfig.TERMS_URL,
) {
    val hasPrivacyPolicy: Boolean get() = privacyPolicyUrl.isNotBlank()
    val hasTerms: Boolean get() = termsUrl.isNotBlank()
}

sealed interface AccountEvent {
    data object SignedOut : AccountEvent
    data object AccountDeleted : AccountEvent
    data object Linked : AccountEvent
}

/** Account management: linking a guest, signing out and permanent deletion. */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {

    val session: StateFlow<SessionState> = sessionManager.state

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AccountEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<AccountEvent> = _events.asSharedFlow()

    fun setEmail(value: String) = _uiState.update { it.copy(email = value, error = null) }

    fun setPassword(value: String) = _uiState.update { it.copy(password = value, error = null) }

    fun dismissMessages() = _uiState.update { it.copy(error = null, info = null) }

    fun linkWithGoogle(activityContext: Context) = submit {
        when (val result = sessionManager.linkGuestWithGoogle(activityContext)) {
            is Outcome.Success -> succeed(R.string.auth_link_success, AccountEvent.Linked)
            is Outcome.Failure ->
                if (result.error == AppError.GOOGLE_CANCELLED) idle() else fail(result.error)
        }
    }

    fun linkWithEmail() = submit {
        val state = _uiState.value
        if (!EmailRules.isValid(state.email)) return@submit fail(AppError.EMAIL_INVALID)
        val password = PasswordRules.validate(state.password)
        if (password is Outcome.Failure) return@submit fail(password.error)
        when (val result = sessionManager.linkGuestWithEmail(state.email, state.password)) {
            is Outcome.Success -> succeed(R.string.auth_link_success, AccountEvent.Linked)
            is Outcome.Failure -> fail(result.error)
        }
    }

    fun signOut() = submit {
        sessionManager.signOut()
        idle()
        _events.emit(AccountEvent.SignedOut)
    }

    /** @param activityContext the hosting Activity; a Google account is re-checked over it. */
    fun deleteAccount(activityContext: Context?) = submit {
        val result = sessionManager.deleteAccount(activityContext, _uiState.value.password)
        when (result) {
            is Outcome.Success -> succeed(R.string.account_delete_success, AccountEvent.AccountDeleted)
            is Outcome.Failure ->
                // Backing out of the account picker is a decision, not a failure, and on this
                // screen it is the decision to keep the account.
                if (result.error == AppError.GOOGLE_CANCELLED) idle() else fail(result.error)
        }
    }

    private fun submit(block: suspend () -> Unit) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, error = null, info = null) }
        viewModelScope.launch { block() }
    }

    private suspend fun succeed(messageRes: Int, event: AccountEvent) {
        _uiState.update {
            it.copy(isSubmitting = false, password = "", info = UiText.Res(messageRes))
        }
        _events.emit(event)
    }

    private fun fail(error: AppError) =
        _uiState.update { it.copy(isSubmitting = false, error = error.message) }

    private fun idle() = _uiState.update { it.copy(isSubmitting = false) }
}
