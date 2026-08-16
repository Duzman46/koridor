package com.duzman46.gridbound.data.firebase

import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.AppLog
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.Query
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A database call the server turned down, with the reason kept rather than flattened.
 *
 * [DatabaseError.toException] collapses every reason the database has into a single
 * [DatabaseException] whose only distinguishing mark is an English sentence in its message. A
 * read the rules refused, a token that had quietly expired and a handset genuinely off the
 * network therefore arrived at the screen looking identical — and every screen answered all
 * three with "check your internet connection", which is a lie to two players out of three and
 * the one piece of advice that cannot help them.
 *
 * It does NOT extend `DatabaseException`, though the obvious design did and this file shipped
 * that way for an afternoon. That constructor is `@RestrictedApi` — callable only from inside
 * Firebase's own library group — so subclassing it compiles happily and then fails
 * `lintRelease`, which is to say it fails at the release gate rather than at the desk. Nothing
 * depended on the supertype: the only `is DatabaseException` in the app is a sentence in a
 * comment. What matters is [appError], the reason already translated into the one vocabulary the
 * screens speak, and [toDatabaseAppError] matches on this type first.
 */
class DatabaseListenerException internal constructor(
    val appError: AppError,
    /** The database's own code, kept for the log rather than for a decision. */
    val code: Int,
    message: String,
) : Exception(message)

/**
 * What a database failure means to the player.
 *
 * The fallback is deliberately [AppError.UNKNOWN] rather than [AppError.NETWORK]: a failure
 * nobody has classified is not evidence of anything about the player's connection, and saying
 * so anyway is how a token expiry came to be reported as a WiFi problem.
 */
fun Throwable.toDatabaseAppError(): AppError = when (this) {
    is DatabaseListenerException -> appError
    is FirebaseNetworkException -> AppError.NETWORK
    else -> AppError.UNKNOWN
}

/**
 * The player-facing reading of one database error code.
 *
 * Only the codes that mean something different to a player are named. `PERMISSION_DENIED` is
 * read as the service being out of reach rather than as a sign-in problem, because it is far
 * more often a rule that has not been deployed than an identity the player could fix; a token
 * that has actually expired says so in a code of its own, and that one is worth telling them
 * about because signing in again is the thing that helps.
 */
private fun DatabaseError.toAppError(): AppError = when (code) {
    DatabaseError.NETWORK_ERROR, DatabaseError.DISCONNECTED -> AppError.NETWORK
    DatabaseError.EXPIRED_TOKEN, DatabaseError.INVALID_TOKEN -> AppError.NOT_SIGNED_IN
    DatabaseError.PERMISSION_DENIED,
    DatabaseError.UNAVAILABLE,
    DatabaseError.OPERATION_FAILED,
    DatabaseError.MAX_RETRIES,
    -> AppError.SERVICE_UNAVAILABLE

    else -> AppError.UNKNOWN
}

/** The exception a closed listener or a refused transaction unwinds as. */
private fun DatabaseError.toListenerException(): DatabaseListenerException =
    DatabaseListenerException(toAppError(), code, toException().message.orEmpty())

/**
 * Reports a genuine failure and lets cancellation past untouched.
 *
 * `runCatching` inside a `suspend` function catches `CancellationException` along with
 * everything else, because on the JVM that is exactly what it is: an ordinary `Exception`.
 * Treated as a failure it becomes a Crashlytics report that says nothing except that a player
 * left a screen, and thousands of those are how a real regression goes unnoticed. Worse, the
 * coroutine that was asked to stop carries on to whatever follows the `runCatching` as though
 * nothing had been asked of it, which is the one thing structured concurrency exists to prevent.
 */
inline fun <T> Result<T>.warnOnFailure(operation: String): Result<T> = onFailure { error ->
    if (error is CancellationException) throw error
    AppLog.warn(operation, error)
}

/** Suspends until the Play Services task settles. */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        when {
            !continuation.isActive -> Unit
            task.isSuccessful -> continuation.resume(task.result)
            else -> continuation.resumeWithException(
                task.exception ?: IllegalStateException("Firebase task failed without an exception"),
            )
        }
    }
}

/**
 * Runs an atomic transaction and reports whether it committed.
 *
 * `applyLocally` is false so an aborted transaction never flashes optimistic state onto a
 * screen that is already listening to the same node.
 */
suspend fun DatabaseReference.runTransactionSuspend(
    update: (MutableData) -> Transaction.Result,
): Boolean = suspendCancellableCoroutine { continuation ->
    runTransaction(
        object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result = update(currentData)

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?,
            ) {
                when {
                    !continuation.isActive -> Unit
                    error != null -> continuation.resumeWithException(error.toListenerException())
                    else -> continuation.resume(committed)
                }
            }
        },
        false,
    )
}

/** Reads the node once. */
suspend fun Query.awaitSnapshot(): DataSnapshot = get().await()

/** Emits the node's current value and every subsequent change until the collector stops. */
fun Query.snapshotFlow(): Flow<DataSnapshot> = callbackFlow {
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            trySend(snapshot)
        }

        override fun onCancelled(error: DatabaseError) {
            // Closed with the reason intact. Every listener in the app ends here, and what a
            // collector can tell about why it stopped is decided by this one line.
            close(error.toListenerException())
        }
    }
    addValueEventListener(listener)
    awaitClose { removeEventListener(listener) }
}

fun DataSnapshot.stringOrNull(key: String): String? =
    child(key).getValue(String::class.java)?.takeIf(String::isNotBlank)

fun DataSnapshot.string(key: String, fallback: String = ""): String =
    stringOrNull(key) ?: fallback

fun DataSnapshot.long(key: String, fallback: Long = 0L): Long =
    child(key).getValue(Long::class.java) ?: fallback

fun DataSnapshot.int(key: String, fallback: Int = 0): Int =
    child(key).getValue(Long::class.java)?.toInt() ?: fallback

fun DataSnapshot.bool(key: String, fallback: Boolean = false): Boolean =
    child(key).getValue(Boolean::class.java) ?: fallback
