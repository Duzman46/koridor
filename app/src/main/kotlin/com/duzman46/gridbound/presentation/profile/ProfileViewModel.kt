package com.duzman46.gridbound.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.core.UsernameRules
import com.duzman46.gridbound.session.SessionManager
import com.duzman46.gridbound.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UsernameFieldState(
    val value: String = "",
    val isChecking: Boolean = false,
    val isAvailable: Boolean = false,
    val message: UiText? = null,
    val isError: Boolean = false,
)

data class ProfileEditState(
    val username: UsernameFieldState = UsernameFieldState(),
    val displayName: String = "",
    val avatarId: String = "",
    val isSubmitting: Boolean = false,
    val error: UiText? = null,
    val initialized: Boolean = false,
)

/**
 * Backs the username picker and the profile editor.
 *
 * Availability is checked with a debounce so typing does not fire a request per keystroke,
 * and the final claim still goes through the transactional path in the repository — the
 * check here is a convenience, never the guarantee.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {

    val session: StateFlow<SessionState> = sessionManager.state

    private val _editState = MutableStateFlow(ProfileEditState())
    val editState: StateFlow<ProfileEditState> = _editState.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    private val usernameQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            usernameQuery
                .debounce(AVAILABILITY_DEBOUNCE_MILLIS)
                .distinctUntilChanged()
                .collect { candidate -> checkAvailability(candidate) }
        }
        viewModelScope.launch {
            // Seed the editor once from the loaded profile, then leave the fields alone so
            // a background profile update cannot overwrite what the player is typing.
            sessionManager.state
                .stateIn(viewModelScope, SharingStarted.Eagerly, sessionManager.state.value)
                .collect { state ->
                    val profile = state.profile ?: return@collect
                    if (_editState.value.initialized) return@collect
                    _editState.update {
                        it.copy(
                            username = UsernameFieldState(value = profile.username),
                            displayName = profile.displayName,
                            avatarId = profile.avatarId,
                            initialized = true,
                        )
                    }
                }
        }
    }

    fun setUsername(value: String) {
        val trimmed = value.take(UsernameRules.MAX_LENGTH)
        _editState.update {
            it.copy(
                username = it.username.copy(
                    value = trimmed,
                    isAvailable = false,
                    message = null,
                    isError = false,
                    isChecking = trimmed.isNotBlank(),
                ),
                error = null,
            )
        }
        usernameQuery.value = trimmed
    }

    fun setDisplayName(value: String) = _editState.update { it.copy(displayName = value, error = null) }

    fun setAvatar(avatarId: String) = _editState.update { it.copy(avatarId = avatarId, error = null) }

    /** Claims the username. Used by the first-run picker and by the profile editor. */
    fun saveUsername(onSuccess: () -> Unit = {}) = submit {
        val candidate = _editState.value.username.value
        when (val result = sessionManager.changeUsername(candidate)) {
            is Outcome.Success -> {
                _editState.update {
                    it.copy(
                        isSubmitting = false,
                        username = it.username.copy(value = result.value, isAvailable = true),
                    )
                }
                _saved.tryEmit(Unit)
                onSuccess()
            }

            is Outcome.Failure -> fail(result.error)
        }
    }

    fun saveProfile(onSuccess: () -> Unit = {}) = submit {
        val state = _editState.value
        val profile = session.value.profile
        if (profile != null && state.username.value.trim() != profile.username) {
            val renamed = sessionManager.changeUsername(state.username.value)
            if (renamed is Outcome.Failure) return@submit fail(renamed.error)
        }
        val displayName = sessionManager.updateDisplayName(state.displayName)
        if (displayName is Outcome.Failure) return@submit fail(displayName.error)
        val avatar = sessionManager.updateAvatar(state.avatarId)
        if (avatar is Outcome.Failure) return@submit fail(avatar.error)
        _editState.update { it.copy(isSubmitting = false) }
        _saved.tryEmit(Unit)
        onSuccess()
    }

    private suspend fun checkAvailability(candidate: String) {
        if (candidate.isBlank()) {
            _editState.update { it.copy(username = it.username.copy(isChecking = false)) }
            return
        }
        if (candidate == session.value.profile?.username) {
            _editState.update {
                it.copy(username = it.username.copy(isChecking = false, isAvailable = true))
            }
            return
        }
        when (val result = sessionManager.isUsernameAvailable(candidate)) {
            is Outcome.Success -> _editState.update {
                it.copy(
                    username = it.username.copy(
                        isChecking = false,
                        isAvailable = result.value,
                        isError = !result.value,
                        message = if (result.value) {
                            UiText.Res(R.string.username_available)
                        } else {
                            AppError.USERNAME_TAKEN.message
                        },
                    ),
                )
            }

            is Outcome.Failure -> _editState.update {
                it.copy(
                    username = it.username.copy(
                        isChecking = false,
                        isAvailable = false,
                        isError = true,
                        message = result.error.message,
                    ),
                )
            }
        }
    }

    private fun submit(block: suspend () -> Unit) {
        if (_editState.value.isSubmitting) return
        _editState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch { block() }
    }

    private fun fail(error: AppError) {
        _editState.update { it.copy(isSubmitting = false, error = error.message) }
    }

    private companion object {
        const val AVAILABILITY_DEBOUNCE_MILLIS = 450L
    }
}
