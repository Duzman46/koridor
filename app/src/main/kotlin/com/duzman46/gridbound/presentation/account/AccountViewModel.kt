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
import com.duzman46.gridbound.core.UsernameRules
import com.duzman46.gridbound.profile.domain.UserProfile
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
    /**
     * True while the player is being asked whether to give up everything they did as a
     * guest in order to sign in as the account the credential they offered already belongs
     * to. Nothing has happened yet at this point, and declining leaves them exactly as they
     * were.
     */
    val existingAccountWarning: Boolean = false,
    /**
     * True while the player is being asked whether to give up their guest progress in order to
     * sign in to an e-mail account they already have. Nothing has happened yet; the password is
     * not even checked until they say yes, and declining leaves them exactly as they were.
     */
    val emailSignInWarning: Boolean = false,
    val privacyPolicyUrl: String = BuildConfig.PRIVACY_POLICY_URL,
    val termsUrl: String = BuildConfig.TERMS_URL,
) {
    val hasPrivacyPolicy: Boolean get() = privacyPolicyUrl.isNotBlank()
    val hasTerms: Boolean get() = termsUrl.isNotBlank()
}

sealed interface AccountEvent {
    data object SignedOut : AccountEvent
    data object AccountDeleted : AccountEvent

    /**
     * The player has a real account now, either by linking their guest identity or by
     * handing it over to one that already existed.
     *
     * @param needsUsername true when the name the account carries is one the app handed out
     *   rather than one its owner chose, and the entry gate has to be reached before
     *   anybody else sees it. The answer travels on the event because the session flow has
     *   not necessarily caught up with an identity that changed a fraction of a second ago,
     *   and a gate reading a stale session waves the player straight past.
     */
    data class Linked(val needsUsername: Boolean) : AccountEvent
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
            is Outcome.Success -> announceAccount(R.string.auth_link_success, result.value)
            is Outcome.Failure -> when {
                // Backing out of the account picker is a decision, not a failure.
                result.error == AppError.GOOGLE_CANCELLED -> idle()

                // The credential belongs to an account that already exists, and Firebase
                // cannot fold one identity into another. Signing in as that account is the
                // only thing left, and it costs the guest everything — so it is put to the
                // player as a question rather than reported to them as an error.
                result.error == AppError.CREDENTIAL_IN_USE &&
                    sessionManager.canSignInToExistingAccount -> warnAboutExistingAccount()

                else -> fail(result.error)
            }
        }
    }

    fun linkWithEmail() = submit {
        val state = _uiState.value
        if (!EmailRules.isValid(state.email)) return@submit fail(AppError.EMAIL_INVALID)
        val password = PasswordRules.validate(state.password)
        if (password is Outcome.Failure) return@submit fail(password.error)
        when (val result = sessionManager.linkGuestWithEmail(state.email, state.password)) {
            is Outcome.Success -> announceAccount(R.string.auth_link_success, result.value)
            is Outcome.Failure -> fail(result.error)
        }
    }

    /**
     * Accepts the loss the warning described and signs in as the other account.
     *
     * Only reachable from that warning, which is what makes losing the guest's progress a
     * decision the player made rather than a consequence they discovered.
     *
     * The success line is drawn from the session having become that account, because
     * [SessionManager.signInToExistingAccount] succeeds on nothing weaker. A hand-over that
     * did not land says so, in the failure the player can read and act on.
     */
    /** Puts the cost in front of the player. Nothing is checked or erased until they agree. */
    fun requestEmailSignIn() {
        _uiState.update { it.copy(emailSignInWarning = true, error = null, info = null) }
    }

    fun dismissEmailSignInWarning() {
        _uiState.update { it.copy(emailSignInWarning = false) }
    }

    /**
     * Accepts that loss and hands the device over.
     *
     * The password is proven before a single row is deleted -- see
     * [SessionManager.signInWithExistingEmail] -- so a typo here costs nothing but the attempt.
     */
    fun signInWithExistingEmail() = submit {
        _uiState.update { it.copy(emailSignInWarning = false) }
        val current = _uiState.value
        if (!EmailRules.isValid(current.email)) return@submit fail(AppError.EMAIL_INVALID)
        if (current.password.isBlank()) return@submit fail(AppError.INVALID_CREDENTIALS)
        when (val result = sessionManager.signInWithExistingEmail(current.email, current.password)) {
            is Outcome.Success ->
                announceAccount(R.string.auth_existing_account_success, result.value)

            is Outcome.Failure -> fail(result.error)
        }
    }

    fun signInToExistingAccount() = submit {
        _uiState.update { it.copy(existingAccountWarning = false) }
        when (val result = sessionManager.signInToExistingAccount()) {
            is Outcome.Success ->
                announceAccount(R.string.auth_existing_account_success, result.value)

            is Outcome.Failure -> fail(result.error)
        }
    }

    /**
     * Declining costs nothing: they are still a guest, with everything they had.
     *
     * The offer is answered all the same, so the credential kept only to answer it is
     * handed back rather than left where a later question could spend it.
     */
    fun dismissExistingAccountWarning() {
        sessionManager.declineExistingAccount()
        _uiState.update { it.copy(existingAccountWarning = false) }
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

    /**
     * @param profile the account as it now stands. Whether its name is one the app handed
     *   out is the only trustworthy answer here to "has this player ever named themselves?"
     *   — it comes from the write that just landed, not from a session flow still catching
     *   up with an identity that changed a moment ago.
     */
    private suspend fun announceAccount(messageRes: Int, profile: UserProfile) =
        succeed(messageRes, AccountEvent.Linked(UsernameRules.isGenerated(profile.username)))

    private fun warnAboutExistingAccount() =
        _uiState.update { it.copy(isSubmitting = false, existingAccountWarning = true) }

    private fun fail(error: AppError) =
        _uiState.update { it.copy(isSubmitting = false, error = error.message) }

    private fun idle() = _uiState.update { it.copy(isSubmitting = false) }
}
