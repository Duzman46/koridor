package com.duzman46.gridbound.core

/**
 * Result of an operation that can fail in a way the player needs to hear about.
 *
 * Repositories return [Outcome] rather than throwing, so every caller is forced to handle
 * the failure path and no raw exception can escape into the UI.
 */
sealed interface Outcome<out T> {
    data class Success<out T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>

    val successOrNull: T?
        get() = (this as? Success)?.value

    val errorOrNull: AppError?
        get() = (this as? Failure)?.error
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}
