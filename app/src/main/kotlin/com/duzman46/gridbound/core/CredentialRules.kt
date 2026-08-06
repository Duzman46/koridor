package com.duzman46.gridbound.core

/**
 * Client-side credential checks.
 *
 * These exist to give immediate feedback and to avoid pointless network round trips. They
 * are never a security control: Firebase Authentication re-validates everything server side.
 */
object EmailRules {
    // Deliberately permissive. Rejecting an address that is actually deliverable is a worse
    // failure than letting the server have the final say.
    private val PATTERN = Regex("^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+$")

    fun isValid(email: String): Boolean = PATTERN.matches(email.trim())
}

object PasswordRules {
    val MIN_LENGTH = Constants.Profile.MIN_PASSWORD_LENGTH

    fun validate(password: String): Outcome<String> = when {
        password.length < MIN_LENGTH -> Outcome.Failure(AppError.PASSWORD_WEAK)
        else -> Outcome.Success(password)
    }

    fun validateMatching(password: String, confirmation: String): Outcome<String> = when {
        password != confirmation -> Outcome.Failure(AppError.PASSWORD_MISMATCH)
        else -> validate(password)
    }
}
