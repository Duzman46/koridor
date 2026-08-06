package com.duzman46.gridbound.data.firebase

import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.Query
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

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
                    error != null -> continuation.resumeWithException(error.toException())
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
            close(error.toException())
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
