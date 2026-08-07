package com.duzman46.gridbound.core

import java.util.Locale
import kotlin.random.Random

/**
 * Username policy. Pure logic with no Android or Firebase dependency so it can be unit
 * tested directly and reused by the Cloud Functions contract documented in README.md.
 */
object UsernameRules {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 16

    /** Wide enough that two guests colliding is a curiosity rather than a design problem. */
    private const val GENERATED_DIGITS = 6

    private const val GUEST_PREFIX = "guest_"

    private val ALLOWED = Regex("^[A-Za-z0-9_]+$")

    /**
     * The shape of every name the app has ever handed out by itself: `guest_483920` today,
     * `player_a1b2c3` from the uid before that, either with a digit appended after a
     * collision. Matched against the normalized form.
     */
    private val GENERATED = Regex("^(?:guest|player)_[a-z0-9]{1,10}$")

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

    /**
     * The name a player is given when they never pick one, so that choosing to play as a
     * guest asks them nothing at all.
     *
     * Digits rather than anything derived from the user id: a name is public and a uid is
     * not, and two guests on the same handset should not read as the same person.
     */
    fun generatedName(random: Random = Random.Default): String =
        GUEST_PREFIX + (1..GENERATED_DIGITS).joinToString("") { random.nextInt(10).toString() }

    /**
     * True for a name the app made up rather than one the player typed.
     *
     * This is a statement about the app's own naming scheme, not a guess about the player:
     * the only way to be wrong is to type a name in exactly that shape, and the cost of
     * being wrong is being invited to confirm your name once.
     */
    fun isGenerated(username: String): Boolean = GENERATED.matches(normalize(username))
}
