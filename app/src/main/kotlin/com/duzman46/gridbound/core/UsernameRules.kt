package com.duzman46.gridbound.core

import java.util.Locale

/**
 * Username policy. Pure logic with no Android or Firebase dependency so it can be unit
 * tested directly and reused by the Cloud Functions contract documented in README.md.
 */
object UsernameRules {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 16

    private val ALLOWED = Regex("^[A-Za-z0-9_]+$")

    /**
     * Names that would let a player impersonate the game or its staff. Compared against the
     * normalized form, so casing and Turkish dotted/dotless I variants are covered.
     */
    private val RESERVED = setOf(
        "admin", "administrator", "moderator", "mod", "koridor", "support", "help",
        "system", "root", "official", "staff", "quoridor", "guest", "anonymous",
        "null", "undefined", "me", "you",
    )

    /**
     * Case-folds with [Locale.ROOT] on purpose: a Turkish locale would fold "I" to "ı" and
     * let two visually distinct names collapse onto different keys depending on the device.
     */
    fun normalize(raw: String): String = raw.trim().lowercase(Locale.ROOT)

    fun validate(raw: String): Outcome<String> {
        val trimmed = raw.trim()
        return when {
            trimmed.isEmpty() -> Outcome.Failure(AppError.USERNAME_BLANK)
            trimmed.length < MIN_LENGTH -> Outcome.Failure(AppError.USERNAME_TOO_SHORT)
            trimmed.length > MAX_LENGTH -> Outcome.Failure(AppError.USERNAME_TOO_LONG)
            !ALLOWED.matches(trimmed) -> Outcome.Failure(AppError.USERNAME_INVALID_CHARACTERS)
            normalize(trimmed) in RESERVED -> Outcome.Failure(AppError.USERNAME_NOT_ALLOWED)
            else -> Outcome.Success(trimmed)
        }
    }

    /** Builds a legal starting username for a brand new account. */
    fun suggestFrom(seed: String?, fallbackSuffix: String): String {
        val cleaned = seed.orEmpty()
            .trim()
            .replace(Regex("[^A-Za-z0-9_]"), "")
            .take(MAX_LENGTH)
        return if (validate(cleaned) is Outcome.Success) {
            cleaned
        } else {
            "player_" + fallbackSuffix.filter(Char::isLetterOrDigit).takeLast(6).ifEmpty { "000000" }
        }
    }
}
