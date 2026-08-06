package com.duzman46.gridbound.online.data

import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.data.firebase.await
import com.duzman46.gridbound.data.firebase.awaitSnapshot
import com.duzman46.gridbound.data.firebase.runTransactionSuspend
import com.duzman46.gridbound.data.firebase.snapshotFlow
import com.duzman46.gridbound.game.engine.GameEngine
import com.duzman46.gridbound.game.models.ActionResult
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.online.domain.OnlineGameRepository
import com.duzman46.gridbound.online.model.OnlineLobbyResult
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineRoomStatus
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.online.model.RoomConfiguration
import com.duzman46.gridbound.online.model.RoomEndReason
import com.duzman46.gridbound.online.model.RoomVisibility
import com.duzman46.gridbound.profile.domain.UserProfileRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.Transaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

@Singleton
class FirebaseOnlineGameRepository @Inject constructor(
    private val firebase: FirebaseProvider,
    private val codec: RoomCodec,
    private val boardCodec: OnlineBoardCodec,
    private val gameEngine: GameEngine,
    private val profileRepository: UserProfileRepository,
    private val random: Random,
) : OnlineGameRepository {

    override val isConfigured: Boolean get() = firebase.isConfigured

    override suspend fun createRoom(configuration: RoomConfiguration): OnlineLobbyResult {
        configuration.validate()?.let { return OnlineLobbyResult.Failure(it) }
        return lobbyCall {
            val userId = requireUserId()
            val host = profileRepository.loadProfile(userId).successOrNull
            repeat(Constants.Online.MAX_ROOM_CREATE_ATTEMPTS) {
                val roomCode = RoomCredentials.generateCode(random)
                val now = System.currentTimeMillis()
                // The password hash has to exist before the room, because the join rule
                // reads it and a room without its secret would be unjoinable.
                val hash = RoomCredentials.hashPassword(roomCode, configuration.password)
                if (hash != null) {
                    secretRef(roomCode).child(RoomCodec.Keys.PASSWORD_HASH).setValue(hash).await()
                }
                val created = roomRef(roomCode).runTransactionSuspend { current ->
                    if (current.value != null) return@runTransactionSuspend Transaction.abort()
                    current.value = codec.encodeNewRoom(configuration, host, userId, now)
                    Transaction.success(current)
                }
                if (created) {
                    return@lobbyCall OnlineLobbyResult.Success(
                        OnlineSession(roomCode, userId, PlayerId.PLAYER_ONE),
                    )
                }
                if (hash != null) runCatching { secretRef(roomCode).removeValue().await() }
            }
            OnlineLobbyResult.Failure(AppError.ROOM_CODE_UNAVAILABLE)
        }
    }

    override suspend fun joinRoom(roomCode: String, password: String): OnlineLobbyResult {
        val normalized = RoomCredentials.normalizeCode(roomCode)
        if (!RoomCredentials.isValidCode(normalized)) {
            return OnlineLobbyResult.Failure(AppError.ROOM_CODE_INVALID)
        }
        return lobbyCall { seat(normalized, requireUserId(), password) }
    }

    override suspend fun quickMatch(preferRanked: Boolean): OnlineLobbyResult = lobbyCall {
        val userId = requireUserId()
        val candidates = openRooms()
            .filter { it.hostUserId != userId && !it.requiresPassword }
            .sortedByDescending { it.ranked == preferRanked }
        // Rooms can be taken between listing and joining, so walk the list until one sticks.
        for (room in candidates) {
            val result = seat(room.roomCode, userId, password = "")
            if (result is OnlineLobbyResult.Success) return@lobbyCall result
        }
        OnlineLobbyResult.Failure(AppError.ROOM_NO_OPPONENT_FOUND)
    }

    override suspend fun loadOpenRooms(): Outcome<List<OnlineRoom>> =
        dbCall("open-rooms") { Outcome.Success(openRooms()) }

    override suspend fun findResumableSession(userId: String): Outcome<OnlineSession?> =
        dbCall("resumable-session") {
            if (userId.isBlank()) return@dbCall Outcome.Success(null)
            // Two narrow indexed queries beat scanning every room in the database.
            val hosted = roomsRef()
                .orderByChild(RoomCodec.Keys.HOST_USER_ID)
                .equalTo(userId)
                .awaitSnapshot()
            val joined = roomsRef()
                .orderByChild(RoomCodec.Keys.GUEST_USER_ID)
                .equalTo(userId)
                .awaitSnapshot()
            val resumable = (hosted.children + joined.children)
                .mapNotNull { codec.decode(it.key.orEmpty(), it.value) }
                .firstOrNull { it.status.isPlayable }
            Outcome.Success(
                resumable?.let { room ->
                    room.playerFor(userId)?.let { OnlineSession(room.roomCode, userId, it) }
                },
            )
        }

    override fun observeRoom(roomCode: String): Flow<OnlineRoom> =
        roomRef(roomCode).snapshotFlow().mapNotNull { codec.decode(roomCode, it.value) }

    override suspend fun submitAction(
        session: OnlineSession,
        expectedVersion: Long,
        action: GameAction,
    ): Boolean = runCatching {
        roomRef(session.roomCode).runTransactionSuspend { current ->
            val room = decodeMutable(session.roomCode, current)
                ?: return@runTransactionSuspend Transaction.abort()
            if (
                room.status != OnlineRoomStatus.IN_PROGRESS ||
                room.version != expectedVersion ||
                room.playerFor(session.userId) != room.boardState.currentPlayer
            ) {
                return@runTransactionSuspend Transaction.abort()
            }
            // The same engine the offline game uses decides legality, so the two can never
            // disagree about what a legal move is.
            val result = gameEngine.perform(room.boardState, action)
            if (result !is ActionResult.Success) return@runTransactionSuspend Transaction.abort()

            val now = System.currentTimeMillis()
            val winner = result.state.status.winner
            current.child(RoomCodec.Keys.BOARD).value = boardCodec.encodeBoard(result.state)
            current.child(RoomCodec.Keys.VERSION).value = room.version + 1L
            current.child(RoomCodec.Keys.LAST_MOVE_AT).value = now
            current.child(RoomCodec.Keys.CURRENT_TURN_USER_ID).value =
                if (winner != null) "" else room.userFor(result.state.currentPlayer).orEmpty()
            if (winner != null) {
                current.child(RoomCodec.Keys.STATUS).value = OnlineRoomStatus.FINISHED.name
                current.child(RoomCodec.Keys.WINNER_USER_ID).value =
                    room.userFor(winner).orEmpty()
                current.child(RoomCodec.Keys.END_REASON).value = RoomEndReason.NORMAL.name
                current.child(RoomCodec.Keys.BROWSE_KEY).value =
                    codec.browseKey(room.visibility, OnlineRoomStatus.FINISHED)
            }
            Transaction.success(current)
        }
    }.getOrElse {
        AppLog.warn("submit-action", it)
        false
    }

    override suspend fun resign(session: OnlineSession): Outcome<Unit> =
        finishRoom(session, RoomEndReason.RESIGNATION) { room ->
            // Resigning always hands the win to the other seat.
            room.userFor(session.playerId.opponent).orEmpty()
        }

    override suspend fun claimTurnTimeout(session: OnlineSession): Outcome<Unit> =
        finishRoom(session, RoomEndReason.TIMEOUT) { room ->
            val now = System.currentTimeMillis()
            // Only the player who is *not* on the clock may claim, and only once the
            // deadline has passed. The rules re-check this against the server clock.
            val onClock = room.boardState.currentPlayer
            if (onClock == session.playerId || !room.hasTurnExpired(now)) return@finishRoom null
            room.userFor(session.playerId).orEmpty()
        }

    override suspend fun leaveRoom(session: OnlineSession) {
        runCatching {
            roomRef(session.roomCode).runTransactionSuspend { current ->
                val room = decodeMutable(session.roomCode, current)
                    ?: return@runTransactionSuspend Transaction.abort()
                // A match under way is left standing so the player can reconnect to it.
                // Only a room that never started is torn down here.
                if (room.status != OnlineRoomStatus.WAITING) {
                    return@runTransactionSuspend Transaction.success(current)
                }
                when (session.playerId) {
                    PlayerId.PLAYER_ONE -> current.value = null
                    PlayerId.PLAYER_TWO -> {
                        current.child(RoomCodec.Keys.GUEST_USER_ID).value = ""
                        current.child(RoomCodec.Keys.STATUS).value = OnlineRoomStatus.WAITING.name
                    }
                }
                Transaction.success(current)
            }
            if (session.playerId == PlayerId.PLAYER_ONE) {
                runCatching { secretRef(session.roomCode).removeValue().await() }
            }
        }.onFailure { AppLog.warn("leave-room", it) }
    }

    /** Takes the guest seat, or returns the seat the player already holds. */
    private suspend fun seat(
        roomCode: String,
        userId: String,
        password: String,
    ): OnlineLobbyResult {
        val existing = codec.decode(roomCode, roomRef(roomCode).awaitSnapshot().value)
            ?: return OnlineLobbyResult.Failure(AppError.ROOM_NOT_FOUND)
        // Rejoining a match already in progress: keep the seat the player had.
        existing.playerFor(userId)?.let { seat ->
            if (!existing.status.isOver) {
                return OnlineLobbyResult.Success(OnlineSession(roomCode, userId, seat))
            }
        }
        if (existing.status != OnlineRoomStatus.WAITING || !existing.guestUserId.isNullOrBlank()) {
            return OnlineLobbyResult.Failure(AppError.ROOM_FULL)
        }
        val providedHash = RoomCredentials.hashPassword(roomCode, password).orEmpty()
        if (existing.requiresPassword && providedHash.isBlank()) {
            return OnlineLobbyResult.Failure(AppError.ROOM_PASSWORD_WRONG)
        }

        var joined = false
        val committed = roomRef(roomCode).runTransactionSuspend { current ->
            val room = decodeMutable(roomCode, current)
                ?: return@runTransactionSuspend Transaction.abort()
            if (room.status != OnlineRoomStatus.WAITING || !room.guestUserId.isNullOrBlank()) {
                return@runTransactionSuspend Transaction.abort()
            }
            val now = System.currentTimeMillis()
            current.child(RoomCodec.Keys.GUEST_USER_ID).value = userId
            current.child(RoomCodec.Keys.STATUS).value = OnlineRoomStatus.IN_PROGRESS.name
            current.child(RoomCodec.Keys.CURRENT_TURN_USER_ID).value = room.hostUserId
            current.child(RoomCodec.Keys.LAST_MOVE_AT).value = now
            current.child(RoomCodec.Keys.EXPIRES_AT).value =
                now + Constants.Online.ROOM_EXPIRY_MILLIS
            current.child(RoomCodec.Keys.BROWSE_KEY).value =
                codec.browseKey(room.visibility, OnlineRoomStatus.IN_PROGRESS)
            if (room.requiresPassword) {
                current.child(RoomCodec.Keys.PASSWORD_ATTEMPT).value = providedHash
            }
            joined = true
            Transaction.success(current)
        }
        return if (committed && joined) {
            OnlineLobbyResult.Success(OnlineSession(roomCode, userId, PlayerId.PLAYER_TWO))
        } else if (existing.requiresPassword) {
            // The rules reject a join whose supplied hash does not match the stored secret,
            // so a refused commit on a protected room means a wrong password.
            OnlineLobbyResult.Failure(AppError.ROOM_PASSWORD_WRONG)
        } else {
            OnlineLobbyResult.Failure(AppError.ROOM_FULL)
        }
    }

    private suspend fun openRooms(): List<OnlineRoom> {
        val now = System.currentTimeMillis()
        val snapshot = roomsRef()
            .orderByChild(RoomCodec.Keys.BROWSE_KEY)
            .equalTo(codec.browseKey(RoomVisibility.PUBLIC, OnlineRoomStatus.WAITING))
            .limitToLast(Constants.Online.ROOM_BROWSER_PAGE_SIZE)
            .awaitSnapshot()
        return snapshot.children
            .mapNotNull { codec.decode(it.key.orEmpty(), it.value) }
            .filter { it.expiresAt == 0L || it.expiresAt > now }
            .sortedByDescending(OnlineRoom::createdAt)
    }

    /**
     * @param winnerFor returns the winning user id, or null to abort the transaction when
     *   the caller is not entitled to end the match this way.
     */
    private suspend fun finishRoom(
        session: OnlineSession,
        reason: RoomEndReason,
        winnerFor: (OnlineRoom) -> String?,
    ): Outcome<Unit> = dbCall("finish-room-${reason.name.lowercase()}") {
        val committed = roomRef(session.roomCode).runTransactionSuspend { current ->
            val room = decodeMutable(session.roomCode, current)
                ?: return@runTransactionSuspend Transaction.abort()
            if (room.status != OnlineRoomStatus.IN_PROGRESS || !room.isMember(session.userId)) {
                return@runTransactionSuspend Transaction.abort()
            }
            val winner = winnerFor(room) ?: return@runTransactionSuspend Transaction.abort()
            current.child(RoomCodec.Keys.STATUS).value = OnlineRoomStatus.FINISHED.name
            current.child(RoomCodec.Keys.WINNER_USER_ID).value = winner
            current.child(RoomCodec.Keys.END_REASON).value = reason.name
            current.child(RoomCodec.Keys.CURRENT_TURN_USER_ID).value = ""
            current.child(RoomCodec.Keys.VERSION).value = room.version + 1L
            current.child(RoomCodec.Keys.BROWSE_KEY).value =
                codec.browseKey(room.visibility, OnlineRoomStatus.FINISHED)
            Transaction.success(current)
        }
        if (committed) Outcome.Success(Unit) else Outcome.Failure(AppError.UNKNOWN)
    }

    private fun decodeMutable(
        roomCode: String,
        data: com.google.firebase.database.MutableData,
    ): OnlineRoom? = codec.decode(roomCode, data.value)

    private suspend fun requireUserId(): String =
        firebase.auth.currentUser?.uid
            ?: firebase.auth.signInAnonymously().await().user?.uid
            ?: error("anonymous sign-in produced no user")

    private fun roomsRef(): DatabaseReference =
        firebase.database.getReference(Constants.Online.ROOMS_PATH)

    private fun roomRef(roomCode: String): DatabaseReference = roomsRef().child(roomCode)

    private fun secretRef(roomCode: String): DatabaseReference =
        firebase.database.getReference(Constants.Online.ROOM_SECRETS_PATH).child(roomCode)

    private suspend fun lobbyCall(block: suspend () -> OnlineLobbyResult): OnlineLobbyResult {
        if (!firebase.isConfigured) return OnlineLobbyResult.Failure(AppError.SERVICE_UNAVAILABLE)
        return runCatching { block() }.getOrElse { error ->
            AppLog.warn("lobby-call", error)
            OnlineLobbyResult.Failure(
                if (error is FirebaseNetworkException) AppError.NETWORK else AppError.UNKNOWN,
            )
        }
    }

    private suspend fun <T> dbCall(
        operation: String,
        block: suspend () -> Outcome<T>,
    ): Outcome<T> {
        if (!firebase.isConfigured) return Outcome.Failure(AppError.SERVICE_UNAVAILABLE)
        return try {
            block()
        } catch (error: Exception) {
            AppLog.warn(operation, error)
            Outcome.Failure(
                if (error is FirebaseNetworkException) AppError.NETWORK else AppError.UNKNOWN,
            )
        }
    }
}
