package com.duzman46.gridbound.auth.domain

/** How the signed-in player proved who they are. */
enum class AccountType {
    /** Anonymous Firebase user. Progress lives on this device until the account is linked. */
    GUEST,
    EMAIL,
    GOOGLE,
    ;

    val isGuest: Boolean get() = this == GUEST
}

/**
 * The authenticated identity. Deliberately small: everything the game shows about a player
 * lives in [com.duzman46.gridbound.profile.domain.UserProfile], which is a separate node
 * with its own access rules.
 */
data class AuthUser(
    val userId: String,
    val email: String?,
    val accountType: AccountType,
    val isEmailVerified: Boolean,
)

sealed interface AuthState {
    /** Firebase has not reported an initial value yet. */
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
}
