package com.duzman46.gridbound.online.data

import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.engine.GameEngine
import com.duzman46.gridbound.game.models.ActionResult
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.online.domain.OnlineGameRepository
import com.duzman46.gridbound.online.model.OnlineLobbyResult
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineRoomStatus
import com.duzman46.gridbound.online.model.OnlineSession
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class FirebaseOnlineGameRepository @Inject constructor(
    private val firebase: FirebaseProvider,
    private val codec: OnlineBoardCodec,
    private val gameEngine: GameEngine,
    private val random: Random,
) : OnlineGameRepository {
    override val isConfigured: Boolean
        get() = firebase.isConfigured

    override suspend fun createRoom(): OnlineLobbyResult = onlineCall {
        val userId = authenticatedUserId()
        repeat(Constants.Online.MAX_ROOM_CREATE_ATTEMPTS) {
            val roomCode = generateRoomCode()
            val now = System.currentTimeMillis()
            val initialRoom = mapOf(
                "hostUid" to userId,
                "guestUid" to "",
                "status" to OnlineRoomStatus.WAITING.name,
                "turnUid" to userId,
                "revision" to 0L,
                "board" to codec.encodeBoard(BoardState.initial()),
                "createdAt" to now,
                "updatedAt" to now,
            )
            val created = roomReference(roomCode).transact { current ->
                if (current.value != null) return@transact Transaction.abort()
                current.value = initialRoom
                Transaction.success(current)
            }
            if (created) {
                return@onlineCall OnlineLobbyResult.Success(
                    OnlineSession(roomCode, userId, PlayerId.PLAYER_ONE),
                )
            }
        }
        OnlineLobbyResult.Failure("Boş bir oda kodu üretilemedi. Lütfen tekrar dene.")
    }

    override suspend fun joinRoom(roomCode: String): OnlineLobbyResult = onlineCall {
        val normalizedCode = roomCode.trim().uppercase(Locale.ROOT)
        if (!isValidRoomCode(normalizedCode)) {
            return@onlineCall OnlineLobbyResult.Failure("Oda kodu 6 harf veya rakamdan oluşmalı.")
        }
        val userId = authenticatedUserId()
        val reference = roomReference(normalizedCode)
        val initialRoom = codec.decodeRoom(normalizedCode, reference.get().awaitValue().value)
            ?: return@onlineCall OnlineLobbyResult.Failure("Oda bulunamadı veya kapatıldı.")
        if (
            initialRoom.hostUid != userId &&
            initialRoom.guestUid != userId &&
            (initialRoom.status != OnlineRoomStatus.WAITING || initialRoom.guestUid != null)
        ) {
            return@onlineCall OnlineLobbyResult.Failure("Oda dolu veya artık aktif değil.")
        }
        var assignedPlayer: PlayerId? = null
        val joined = reference.transact { current ->
            val room = codec.decodeRoom(normalizedCode, current.value) ?: return@transact Transaction.abort()
            when {
                room.hostUid == userId -> assignedPlayer = PlayerId.PLAYER_ONE
                room.guestUid == userId -> assignedPlayer = PlayerId.PLAYER_TWO
                room.status != OnlineRoomStatus.WAITING || room.guestUid != null -> return@transact Transaction.abort()
                else -> {
                    assignedPlayer = PlayerId.PLAYER_TWO
                    current.child("guestUid").value = userId
                    current.child("status").value = OnlineRoomStatus.ACTIVE.name
                    current.child("updatedAt").value = System.currentTimeMillis()
                }
            }
            Transaction.success(current)
        }
        val playerId = assignedPlayer
        if (joined && playerId != null) {
            OnlineLobbyResult.Success(OnlineSession(normalizedCode, userId, playerId))
        } else {
            OnlineLobbyResult.Failure("Oda bulunamadı, dolu veya artık aktif değil.")
        }
    }

    override fun observeRoom(roomCode: String): Flow<OnlineRoom> = callbackFlow {
        if (!isConfigured) {
            close(IllegalStateException("Çevrimiçi servis henüz yapılandırılmadı."))
            return@callbackFlow
        }
        val reference = roomReference(roomCode)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val room = codec.decodeRoom(roomCode, snapshot.value)
                if (room == null) close(IllegalStateException("Oda kapatıldı.")) else trySend(room)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        reference.addValueEventListener(listener)
        awaitClose { reference.removeEventListener(listener) }
    }

    override suspend fun submitAction(
        session: OnlineSession,
        expectedRevision: Long,
        action: GameAction,
    ): Boolean = runCatching {
        roomReference(session.roomCode).transact { current ->
            val room = codec.decodeRoom(session.roomCode, current.value) ?: return@transact Transaction.abort()
            if (
                room.status != OnlineRoomStatus.ACTIVE ||
                room.revision != expectedRevision ||
                room.playerFor(session.userId) != room.boardState.currentPlayer
            ) {
                return@transact Transaction.abort()
            }
            val result = gameEngine.perform(room.boardState, action)
            if (result !is ActionResult.Success) return@transact Transaction.abort()
            current.child("board").value = codec.encodeBoard(result.state)
            current.child("revision").value = room.revision + 1L
            current.child("turnUid").value = result.state.status.winner?.let { "" }
                ?: room.userFor(result.state.currentPlayer).orEmpty()
            current.child("updatedAt").value = System.currentTimeMillis()
            if (result.state.status.winner != null) {
                current.child("status").value = OnlineRoomStatus.FINISHED.name
            }
            Transaction.success(current)
        }
    }.getOrDefault(false)

    override suspend fun leaveRoom(session: OnlineSession) {
        runCatching {
            roomReference(session.roomCode).transact { current ->
                val room = codec.decodeRoom(session.roomCode, current.value) ?: return@transact Transaction.abort()
                if (room.status == OnlineRoomStatus.FINISHED) return@transact Transaction.success(current)
                when (session.playerId) {
                    PlayerId.PLAYER_ONE -> if (room.status == OnlineRoomStatus.WAITING) {
                        current.value = null
                    } else {
                        current.child("status").value = OnlineRoomStatus.ABANDONED.name
                    }
                    PlayerId.PLAYER_TWO -> {
                        current.child("guestUid").value = ""
                        current.child("status").value = OnlineRoomStatus.WAITING.name
                    }
                }
                if (current.value != null) current.child("updatedAt").value = System.currentTimeMillis()
                Transaction.success(current)
            }
        }
    }

    private suspend fun authenticatedUserId(): String {
        firebase.auth.currentUser?.uid?.let { return it }
        return checkNotNull(firebase.auth.signInAnonymously().awaitValue().user?.uid)
    }

    private fun roomReference(roomCode: String): DatabaseReference =
        firebase.database.getReference(Constants.Online.ROOMS_PATH).child(roomCode)

    private fun generateRoomCode(): String = buildString(Constants.Online.ROOM_CODE_LENGTH) {
        repeat(Constants.Online.ROOM_CODE_LENGTH) {
            append(Constants.Online.ROOM_CODE_ALPHABET[random.nextInt(Constants.Online.ROOM_CODE_ALPHABET.length)])
        }
    }

    private fun isValidRoomCode(code: String): Boolean =
        code.length == Constants.Online.ROOM_CODE_LENGTH && code.all(Constants.Online.ROOM_CODE_ALPHABET::contains)

    private suspend fun onlineCall(block: suspend () -> OnlineLobbyResult): OnlineLobbyResult {
        if (!isConfigured) return OnlineLobbyResult.Failure("Çevrimiçi servis henüz yapılandırılmadı.")
        return runCatching { block() }.getOrElse {
            OnlineLobbyResult.Failure(it.localizedMessage ?: "Bağlantı kurulamadı. İnternetini kontrol et.")
        }
    }
}

private suspend fun DatabaseReference.transact(
    update: (MutableData) -> Transaction.Result,
): Boolean = suspendCancellableCoroutine { continuation ->
    runTransaction(
        object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result = update(currentData)

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
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

private suspend fun <T> Task<T>.awaitValue(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        when {
            !continuation.isActive -> Unit
            task.isSuccessful -> continuation.resume(task.result)
            else -> continuation.resumeWithException(task.exception ?: IllegalStateException("Firebase işlemi başarısız."))
        }
    }
}
