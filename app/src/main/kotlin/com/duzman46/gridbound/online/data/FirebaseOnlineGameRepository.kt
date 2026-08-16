package com.duzman46.gridbound.online.data

import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.data.firebase.await
import com.duzman46.gridbound.data.firebase.awaitSnapshot
import com.duzman46.gridbound.data.firebase.runTransactionSuspend
import com.duzman46.gridbound.data.firebase.snapshotFlow
import com.duzman46.gridbound.data.firebase.toDatabaseAppError
import com.duzman46.gridbound.data.firebase.warnOnFailure
import com.duzman46.gridbound.game.engine.GameEngine
import com.duzman46.gridbound.game.models.ActionResult
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.online.domain.OnlineGameRepository
import com.duzman46.gridbound.online.model.MatchMessage
import com.duzman46.gridbound.online.model.MatchmakingRules
import com.duzman46.gridbound.online.model.MatchmakingState
import com.duzman46.gridbound.online.model.OnlineLobbyResult
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineRoomStatus
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.online.model.RoomConfiguration
import com.duzman46.gridbound.online.model.RoomEndReason
import com.duzman46.gridbound.online.model.RoomVisibility
import com.duzman46.gridbound.profile.data.ProfileCodec
import com.duzman46.gridbound.profile.domain.UserProfile
import com.duzman46.gridbound.profile.domain.UserProfileRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.withIndex

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
            // Drawn once, outside the retry loop: a host who left the colour to chance is
            // choosing a seat, not re-rolling it every time a room code collides.
            val hostSeat = configuration.hostSeat ?: PlayerId.entries.random(random)
            // The server's clock, not this handset's. `expiresAt` is the one stamp a phone
            // chooses that anything else reads, and a phone half an hour slow publishes a room
            // that is already expired: invisible in every honest browser, deleted by the sweep
            // inside a minute, and the host left watching a waiting panel that never ends.
            val now = serverNow()
            repeat(Constants.Online.MAX_ROOM_CREATE_ATTEMPTS) {
                val roomCode = RoomCredentials.generateCode(random)
                // The password hash has to exist before the room, because the join rule
                // reads it and a room without its secret would be unjoinable.
                val hash = RoomCredentials.hashPassword(roomCode, configuration.password)
                if (hash != null) {
                    secretRef(roomCode).child(RoomCodec.Keys.PASSWORD_HASH).setValue(hash).await()
                }
                val created = roomRef(roomCode).runTransactionSuspend { current ->
                    if (current.value != null) return@runTransactionSuspend Transaction.abort()
                    current.value = codec.encodeNewRoom(configuration, host, userId, hostSeat, now)
                    Transaction.success(current)
                }
                if (created) {
                    // A standing instruction to the server: if this client goes away, take the
                    // room with it. Leaving the screen is handled by the screen, but nothing in
                    // the app runs when it is swiped out of the recents list or the handset
                    // walks into a lift — and until now those rooms sat in the browser for half
                    // an hour, offering a code that opened nothing. Withdrawn by keepRoom the
                    // moment a rival walks in.
                    runCatching {
                        roomRef(roomCode).onDisconnect().removeValue().await()
                        secretRef(roomCode).onDisconnect().removeValue().await()
                    }.warnOnFailure("room-on-disconnect")
                    return@lobbyCall OnlineLobbyResult.Success(
                        OnlineSession(roomCode, userId, hostSeat),
                        hosted = true,
                    )
                }
                // The code was taken by somebody else between the hash and the room, so the
                // hash belongs to a room this player will never own. Best effort, but not a
                // secret worth abandoning in silence: one left behind under a code somebody
                // else now holds is a password check the real host never set.
                if (hash != null) {
                    runCatching { secretRef(roomCode).removeValue().await() }
                        .warnOnFailure("room-secret-abandon")
                }
            }
            OnlineLobbyResult.Failure(AppError.ROOM_CODE_UNAVAILABLE)
        }
    }

    override suspend fun rematchRoom(
        playedRoomCode: String,
        opponentUserId: String,
        playedSeat: PlayerId,
    ): OnlineLobbyResult = lobbyCall {
        val userId = requireUserId()
        val code = RoomCredentials.rematchCode(playedRoomCode, userId, opponentUserId)
        val host = profileRepository.loadProfile(userId).successOrNull
        // The whole of the played room, not one field of it. Only `ranked` used to be taken off
        // it, and the settings that were left behind were quietly replaced by the defaults —
        // see [RoomConfiguration.rematchOf] for what that cost a room with the clock switched
        // off. The read happens either way, so carrying the rest of it is free.
        val played = codec.decode(
            playedRoomCode,
            roomRef(playedRoomCode).awaitSnapshot().value,
        )
        val configuration = RoomConfiguration.rematchOf(played, playedSeat.opponent)
        val now = serverNow()
        val opened = roomRef(code).runTransactionSuspend { current ->
            if (current.value != null) return@runTransactionSuspend Transaction.abort()
            current.value =
                codec.encodeNewRoom(configuration, host, userId, playedSeat.opponent, now)
            Transaction.success(current)
        }
        if (opened) {
            OnlineLobbyResult.Success(
                OnlineSession(code, userId, playedSeat.opponent),
                hosted = true,
            )
        } else {
            // The other player asked first and this is their room. Taking the free seat in it
            // is the same answer their invitation would have given, one tap earlier — and it
            // comes back as `hosted = false`, which is what stops this device sending an
            // invitation of its own to a room the recipient is already sitting in.
            seat(code, userId, "")
        }
    }

    override suspend fun joinRoom(roomCode: String, password: String): OnlineLobbyResult {
        val normalized = RoomCredentials.normalizeCode(roomCode)
        if (!RoomCredentials.isValidCode(normalized)) {
            return OnlineLobbyResult.Failure(AppError.ROOM_CODE_INVALID)
        }
        return lobbyCall { seat(normalized, requireUserId(), password) }
    }

    override fun matchmake(ranked: Boolean): Flow<MatchmakingState> {
        if (!firebase.isConfigured) {
            return flowOf(MatchmakingState.Failed(AppError.SERVICE_UNAVAILABLE))
        }
        return queueSession(ranked).catch { error ->
            AppLog.warn("matchmake", error)
            // The queue is watched through listeners, and a listener the database closed used
            // to arrive here as an unrecognised exception and be reported as "something went
            // wrong" whatever it was. It now carries its own reason; see toDatabaseAppError.
            emit(MatchmakingState.Failed(error.toDatabaseAppError()))
        }
    }

    /**
     * One trip through the waiting list: write the entry, hold it, end in a room.
     *
     * The entry is given up in a `finally`, so cancelling the collection — the player pressing
     * cancel, the lobby leaving the screen — is all it takes to leave the list. onDisconnect
     * covers the case that never reaches a `finally`: a process killed, or a phone that walks
     * out of signal. Between them, a name in this list is always a device that is still there.
     */
    private fun queueSession(ranked: Boolean): Flow<MatchmakingState> = flow {
        val userId = requireUserId()
        val profile = profileRepository.loadProfile(userId).successOrNull
        // Read from the one node the rules compare the entry against, rather than taken off the
        // profile above. The rule insists the rating written here equals users/{uid}/rating
        // exactly, and the profile read is allowed to fail — it returns null on a slow or
        // refused read, and the standing rating was then invented as 1000. That is correct for
        // exactly as long as nobody has played a rated match; the moment a rating moves, every
        // queue write on a failed profile read is refused by the rules, surfaces as a thrown
        // DatabaseException, and reaches the player as "something went wrong" the instant they
        // press. One extra read of one integer buys a number that cannot disagree.
        val rating = ratingOf(userId, profile)
        val entry = queueRef().child(userId)
        // Registered before the entry is written, so a process that dies between the two
        // still leaves nothing behind: the server runs the removal either way.
        entry.onDisconnect().removeValue()
        try {
            emit(MatchmakingState.Searching)
            // Losing the place is not the end of the wait. The worker clears out entries old
            // enough to be from a phone that never came back, and a player who is genuinely
            // still here would otherwise be left watching a list they are no longer in.
            while (true) {
                val session = awaitPairing(userId, profile, ranked, takePlace(entry, rating, ranked))
                if (session != null) {
                    emit(MatchmakingState.Paired(session))
                    return@flow
                }
            }
        } finally {
            entry.onDisconnect().cancel()
            // Not awaited: this usually runs while the caller is being cancelled, and the
            // database client sends it from its own queue whether or not anyone is listening.
            entry.removeValue()
        }
    }

    /**
     * The rating the queue entry has to carry, which is whatever `users/{uid}/rating` holds.
     *
     * The profile is preferred when there is one, because it has already been read. Otherwise
     * the single leaf is fetched, and only a player with no rating at all — nobody with a
     * profile, which is everyone who can reach this screen — falls back to the starting value.
     */
    private suspend fun ratingOf(userId: String, profile: UserProfile?): Int {
        // Fetched only when the profile read came back empty, because the profile carries the
        // same number and this is a second round trip to say so.
        val stored = if (profile != null) {
            null
        } else {
            firebase.database.getReference(Constants.Backend.USERS_PATH)
                .child(userId)
                .child(ProfileCodec.Keys.RATING)
                .awaitSnapshot()
                // One number type in the database, so it comes back Long and is narrowed here
                // rather than asking Firebase to convert on our behalf.
                .getValue(Long::class.java)
                ?.toInt()
        }
        return MatchmakingCodec.queueRating(profileRating = profile?.rating, storedRating = stored)
    }

    /** Writes the entry and returns the stamp the server settled on. */
    private suspend fun takePlace(
        entry: DatabaseReference,
        rating: Int,
        ranked: Boolean,
    ): Long {
        entry.setValue(
            mapOf(
                MatchmakingCodec.Keys.RATING to rating,
                MatchmakingCodec.Keys.RANKED to ranked,
                MatchmakingCodec.Keys.QUEUED_AT to ServerValue.TIMESTAMP,
            ),
        ).await()
        // Read back rather than trust the local estimate of the stamp. Every decision from
        // here on compares one server stamp against another, which is what makes them sound
        // on a handset whose own clock is wrong.
        return entry.child(MatchmakingCodec.Keys.QUEUED_AT)
            .awaitSnapshot()
            .getValue(Long::class.java)
            ?: error("the queue entry carries no timestamp")
    }

    /** A pairing, or the loss of this player's place in the list. */
    private sealed interface Pairing {
        val session: OnlineSession?

        data class Made(override val session: OnlineSession) : Pairing

        data object Lost : Pairing {
            override val session: OnlineSession? = null
        }
    }

    /**
     * Suspends until this player is in a room, or until their place in the list is gone.
     *
     * Three sources, and the first to answer wins. Claiming is this device pairing from the
     * list itself. Then there is every room that names this player and did not exist when they
     * queued, which covers both being claimed by the phone at the other end and being paired
     * by the scheduled worker — neither of which can tell this device anything directly, and
     * neither of which needs to be able to. "Did not exist when they queued" is a comparison of
     * two server stamps, so a match this player walked out of an hour ago is never mistaken for
     * the one just made for them.
     *
     * The third is the entry going missing, which the caller answers by taking a new place.
     */
    private suspend fun awaitPairing(
        userId: String,
        profile: UserProfile?,
        ranked: Boolean,
        queuedAt: Long,
    ): OnlineSession? = merge(
        queueRef().snapshotFlow().withIndex().mapNotNull { (index, snapshot) ->
            when {
                snapshot.hasChild(userId) -> claim(userId, profile, ranked, snapshot)?.let(Pairing::Made)
                // Never on the first snapshot. That one can be served from the local cache
                // before the write that put this player in the list has reached it, and
                // starting over on the strength of it would loop.
                index > 0 -> Pairing.Lost
                else -> null
            }
        },
        pairedRooms(RoomCodec.Keys.HOST_USER_ID, userId, queuedAt).map(Pairing::Made),
        pairedRooms(RoomCodec.Keys.GUEST_USER_ID, userId, queuedAt).map(Pairing::Made),
    ).first().session

    private fun pairedRooms(field: String, userId: String, queuedAt: Long): Flow<OnlineSession> =
        roomsRef().orderByChild(field).equalTo(userId).snapshotFlow().mapNotNull { snapshot ->
            snapshot.children
                .mapNotNull { codec.decode(it.key.orEmpty(), it.value) }
                .filter { it.status.isPlayable && it.createdAt >= queuedAt }
                .firstNotNullOfOrNull { room ->
                    room.playerFor(userId)?.let { OnlineSession(room.roomCode, userId, it) }
                }
        }

    /**
     * Writes the room for the pair [MatchmakingRules] settled on, or returns null.
     *
     * Null covers both "keep waiting" and "somebody else got there first": the room is named
     * after the player being claimed, so two devices that go for the same person aim at one
     * node and only one transaction commits.
     */
    private suspend fun claim(
        userId: String,
        profile: UserProfile?,
        ranked: Boolean,
        snapshot: DataSnapshot,
    ): OnlineSession? {
        val waiting = snapshot.children.mapNotNull(MatchmakingCodec::decode)
        val partner = MatchmakingRules.partnerFor(userId, waiting) ?: return null
        val code = RoomCredentials.meetingCode(partner.userId, partner.queuedAt)
        // Neither player picked a colour, so the seats are drawn here — once, by the phone
        // that writes the room, because the room is where both of them read the answer.
        val hostSeat = PlayerId.entries.random(random)
        val committed = roomRef(code).runTransactionSuspend { current ->
            if (current.value != null) return@runTransactionSuspend Transaction.abort()
            current.value = codec.encodePairedRoom(
                host = profile,
                hostUserId = userId,
                guestUserId = partner.userId,
                hostSeat = hostSeat,
                // A guest's result cannot move a rating, so one guest makes the match casual.
                ranked = ranked && partner.ranked,
                expiresAt = System.currentTimeMillis() + Constants.Online.ROOM_EXPIRY_MILLIS,
            )
            Transaction.success(current)
        }
        return if (committed) OnlineSession(code, userId, hostSeat) else null
    }

    override suspend fun loadOpenRooms(): Outcome<List<OnlineRoom>> =
        dbCall("open-rooms") { Outcome.Success(openRooms()) }

    override suspend fun closeIdleMatches(userId: String): Outcome<Unit> =
        dbCall("close-idle-matches") {
            if (userId.isBlank()) return@dbCall Outcome.Success(Unit)
            // Two narrow indexed queries beat scanning every room in the database.
            val hosted = roomsRef()
                .orderByChild(RoomCodec.Keys.HOST_USER_ID)
                .equalTo(userId)
                .awaitSnapshot()
            val joined = roomsRef()
                .orderByChild(RoomCodec.Keys.GUEST_USER_ID)
                .equalTo(userId)
                .awaitSnapshot()
            val now = System.currentTimeMillis()
            (hosted.children + joined.children)
                .mapNotNull { codec.decode(it.key.orEmpty(), it.value) }
                .filter { it.hasIdled(now) }
                .forEach { room ->
                    val seat = room.playerFor(userId) ?: return@forEach
                    resolveIdleMatch(OnlineSession(room.roomCode, userId, seat))
                }
            Outcome.Success(Unit)
        }

    override fun observeRoom(roomCode: String): Flow<OnlineRoom?> =
        // Mapped rather than filtered: a deleted room arrives as a snapshot with no value, and
        // dropping it left every waiting panel listening to a room that no longer exists.
        roomRef(roomCode).snapshotFlow()
            .map { codec.decode(roomCode, it.value) }
            // Reported once, here, rather than in each of the four screens that watch a room —
            // which is also where every other listener in this app reports itself, from
            // observeFriendships to observeProfile. Why the database closed a listener is a
            // database fact and it is the same fact whoever was watching; what the screens
            // differ on is what to do about it, and that is the half they each still own.
            // Rethrown untouched so those halves still run.
            .catch { error ->
                AppLog.warn("room-listener", error)
                throw error
            }

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

            val winner = result.state.status.winner
            current.child(RoomCodec.Keys.BOARD).value = boardCodec.encodeBoard(result.state)
            current.child(RoomCodec.Keys.VERSION).value = room.version + 1L
            // The server stamps this, not the handset. The rule compares it against the
            // server's own `now`, so a phone whose clock ran even slightly fast had every
            // one of its moves rejected — and the move clock it feeds is what decides
            // timeouts, which no device should be able to influence.
            current.child(RoomCodec.Keys.LAST_MOVE_AT).value = ServerValue.TIMESTAMP
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
    }.warnOnFailure("submit-action").getOrElse { false }

    override suspend fun resign(session: OnlineSession): Outcome<Unit> =
        finishRoom(session, RoomEndReason.RESIGNATION) { room ->
            // Resigning always hands the win to the other seat.
            room.userFor(session.playerId.opponent).orEmpty()
        }

    override suspend fun sendMessage(
        session: OnlineSession,
        message: MatchMessage,
    ): Outcome<Unit> = dbCall("send-message") {
        // Straight at this player's own slot rather than through the room transaction: a
        // message is not a move, it must not bump the version other devices are racing on,
        // and it must not be able to fail because somebody moved while the sheet was open.
        roomRef(session.roomCode)
            .child(RoomCodec.Keys.CHAT)
            .child(session.userId)
            .setValue(codec.encodeMessage(message))
            .await()
        Outcome.Success(Unit)
    }

    override suspend fun resolveTurnTimeout(session: OnlineSession): Outcome<Unit> =
        finishRoom(session, RoomEndReason.TIMEOUT) { room ->
            // Both devices ask, and one of them belongs to the loser. Reading the winner off
            // the room instead of off who is asking is what makes the two agree, and what
            // lets a match end while the player it was won by has their phone in a pocket.
            room.waiting()?.takeIf { room.hasTurnExpired(System.currentTimeMillis()) }
        }

    override suspend fun resolveIdleMatch(session: OnlineSession): Outcome<Unit> =
        finishRoom(session, RoomEndReason.TIMEOUT) { room ->
            room.waiting()?.takeIf { room.hasIdled(System.currentTimeMillis()) }
        }

    /**
     * The member who is *not* on the clock, and so the one a clock running out hands the match
     * to. Null when the room names nobody, which aborts rather than awarding it to no one.
     *
     * Read from `currentTurnUserId` because that is the field the database rules judge these
     * writes by; deriving it from the seats instead would let a disagreement between the two
     * turn into a write the server refuses on every retry.
     */
    private fun OnlineRoom.waiting(): String? = when (currentTurnUserId.orEmpty()) {
        hostUserId -> guestUserId
        guestUserId -> hostUserId
        else -> null
    }?.takeIf(String::isNotBlank)

    override suspend fun leaveRoom(session: OnlineSession) {
        runCatching {
            // Who owns the room is the host, not a seat: a host who chose red holds seat two,
            // and it is still their room to tear down.
            var wasHost = false
            roomRef(session.roomCode).runTransactionSuspend { current ->
                val room = decodeMutable(session.roomCode, current)
                    ?: return@runTransactionSuspend Transaction.abort()
                wasHost = session.userId == room.hostUserId
                // A match under way is left standing: walking out of it is not a resignation,
                // and it is settled by the idle rule once the clock has run long enough.
                // Only a room that never started is torn down here.
                if (room.status != OnlineRoomStatus.WAITING) {
                    return@runTransactionSuspend Transaction.success(current)
                }
                if (wasHost) {
                    current.value = null
                } else {
                    current.child(RoomCodec.Keys.GUEST_USER_ID).value = ""
                    current.child(RoomCodec.Keys.STATUS).value = OnlineRoomStatus.WAITING.name
                }
                Transaction.success(current)
            }
            if (wasHost) {
                // The room is gone and its secret should go with it. A hash left under a code
                // that is free again is the one piece of this teardown that outlives the room,
                // so a failure here is worth a line even though nothing can be done about it.
                runCatching { secretRef(session.roomCode).removeValue().await() }
                    .warnOnFailure("room-secret-remove")
            }
        }.warnOnFailure("leave-room")
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

        // Set once the handler has decided the seat is takeable, which is what tells a server
        // refusal apart from an abort: the write only ever reaches the server with this set,
        // so a failure after it is the rules turning the write down — and the only rule a join
        // can fail on once the seat was free is the password.
        var offered = false
        val committed = runCatching {
            roomRef(roomCode).runTransactionSuspend { current ->
                val room = decodeMutable(roomCode, current)
                    ?: return@runTransactionSuspend Transaction.abort()
                if (room.status != OnlineRoomStatus.WAITING ||
                    !room.guestUserId.isNullOrBlank()
                ) {
                    return@runTransactionSuspend Transaction.abort()
                }
                val now = System.currentTimeMillis()
                current.child(RoomCodec.Keys.GUEST_USER_ID).value = userId
                current.child(RoomCodec.Keys.STATUS).value = OnlineRoomStatus.IN_PROGRESS.name
                // Seat one opens, and until this moment it may have been the empty seat: a
                // host who chose red has been waiting for the player who takes the first turn.
                current.child(RoomCodec.Keys.CURRENT_TURN_USER_ID).value =
                    if (room.hostSeat == PlayerId.PLAYER_ONE) room.hostUserId else userId
                // Server-stamped for the same reason as a move: this starts the opening
                // player's clock, and the joiner's handset must not be able to shorten it.
                current.child(RoomCodec.Keys.LAST_MOVE_AT).value = ServerValue.TIMESTAMP
                current.child(RoomCodec.Keys.EXPIRES_AT).value =
                    now + Constants.Online.ROOM_EXPIRY_MILLIS
                current.child(RoomCodec.Keys.BROWSE_KEY).value =
                    codec.browseKey(room.visibility, OnlineRoomStatus.IN_PROGRESS)
                if (room.requiresPassword) {
                    current.child(RoomCodec.Keys.PASSWORD_ATTEMPT).value = providedHash
                }
                offered = true
                Transaction.success(current)
            }
        }.getOrElse { error ->
            // The player walked away mid-join. Nobody is waiting for an answer, and reporting
            // a full room to a screen that has gone is not an answer anyway.
            if (error is CancellationException) throw error
            // A rules refusal arrives as an exception rather than as `committed == false` —
            // the Realtime Database reports only a handler's own abort that way — so letting
            // it unwind turned every mistyped room password into "something went wrong".
            if (error is FirebaseNetworkException) throw error
            AppLog.warn("join-room", error)
            false
        }
        val refusal = joinRefusal(committed, offered, existing.requiresPassword)
        return if (refusal == null) {
            OnlineLobbyResult.Success(OnlineSession(roomCode, userId, existing.hostSeat.opponent))
        } else {
            OnlineLobbyResult.Failure(refusal)
        }
    }

    private suspend fun openRooms(): List<OnlineRoom> = readOpenRooms(openRoomsQuery().awaitSnapshot())

    override fun observeOpenRooms(): Flow<List<OnlineRoom>> =
        // The same query the one-shot read uses, kept open. Firebase answers a new listener from
        // its own cache first and then from the wire, so attaching it costs no more than the
        // read it replaces and every later change arrives without asking.
        openRoomsQuery().snapshotFlow().map(::readOpenRooms)

    override suspend fun keepRoom(roomCode: String) {
        runCatching {
            roomRef(roomCode).onDisconnect().cancel().await()
            secretRef(roomCode).onDisconnect().cancel().await()
        }.warnOnFailure("room-keep")
    }

    override fun observeConnection(): Flow<Boolean> =
        firebase.database
            .getReference(Constants.Online.CONNECTED_PATH)
            .snapshotFlow()
            .map { it.getValue(Boolean::class.java) == true }
            .catch { emit(false) }

    private fun openRoomsQuery(): Query = roomsRef()
        .orderByChild(RoomCodec.Keys.BROWSE_KEY)
        .equalTo(codec.browseKey(RoomVisibility.PUBLIC, OnlineRoomStatus.WAITING))
        .limitToLast(Constants.Online.ROOM_BROWSER_PAGE_SIZE)

    /**
     * A room's own expiry is checked here rather than in the query, because the window closes
     * while nobody is writing anything: an expired room is still indexed as waiting, and only
     * the passage of time makes it stale. The listener would never fire for that, so a room can
     * still be a few seconds past its end when it is dropped — which is the same tolerance the
     * poll had, and far better than showing it for another fourteen.
     */
    private fun readOpenRooms(snapshot: DataSnapshot): List<OnlineRoom> {
        val now = System.currentTimeMillis()
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

    /**
     * Now, as the server reckons it.
     *
     * Firebase keeps the difference between the two clocks on `/.info/serverTimeOffset` over
     * the same connection the rooms are read on, so this is a cached local read rather than a
     * round trip, and it answers immediately even offline — with whatever the last connection
     * established, which is the best answer there is. Falls back to the handset's own clock,
     * because a room opened with a slightly wrong window beats no room at all.
     *
     * The fallback is logged rather than taken quietly. A phone whose clock is half an hour
     * fast publishes a room that is already expired — invisible in every honest browser,
     * deleted by the sweep within the minute, and the host left watching a wait that never
     * ends. That is a report about rooms that vanish, and the one fact that explains it is
     * whether this read answered.
     */
    private suspend fun serverNow(): Long {
        val offset = runCatching {
            firebase.database
                .getReference(Constants.Online.SERVER_TIME_OFFSET_PATH)
                .snapshotFlow()
                .first()
                .getValue(Long::class.java)
        }.warnOnFailure("server-time-offset").getOrNull() ?: 0L
        return System.currentTimeMillis() + offset
    }

    private fun roomsRef(): DatabaseReference =
        firebase.database.getReference(Constants.Online.ROOMS_PATH)

    private fun roomRef(roomCode: String): DatabaseReference = roomsRef().child(roomCode)

    private fun queueRef(): DatabaseReference =
        firebase.database.getReference(Constants.Online.MATCHMAKING_PATH)

    private fun secretRef(roomCode: String): DatabaseReference =
        firebase.database.getReference(Constants.Online.ROOM_SECRETS_PATH).child(roomCode)

    /** Cancellation is passed on for the reasons set out on [dbCall]. */
    private suspend fun lobbyCall(block: suspend () -> OnlineLobbyResult): OnlineLobbyResult {
        if (!firebase.isConfigured) return OnlineLobbyResult.Failure(AppError.SERVICE_UNAVAILABLE)
        return try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppLog.warn("lobby-call", error)
            OnlineLobbyResult.Failure(error.toDatabaseAppError())
        }
    }

    /**
     * Every database call the lobby and the board make, with its failure turned into something
     * the screen can say.
     *
     * Cancellation is re-thrown before anything else looks at the exception, and that ordering
     * is the whole of it. On the JVM a `CancellationException` is an `Exception` like any other,
     * so a player who backed out of a screen mid-request — the single most ordinary thing anyone
     * does — had the cancellation caught here, written to Crashlytics as a genuine failure, and
     * handed back as [Outcome.Failure] to a caller that had already gone. It filled the
     * dashboard with reports of nothing happening, which is worse than no reports at all because
     * it buries the ones that mean something; and swallowing cancellation breaks the one promise
     * structured concurrency makes, that cancelling a scope ends the work inside it.
     */
    private suspend fun <T> dbCall(
        operation: String,
        block: suspend () -> Outcome<T>,
    ): Outcome<T> {
        if (!firebase.isConfigured) return Outcome.Failure(AppError.SERVICE_UNAVAILABLE)
        return try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppLog.warn(operation, error)
            Outcome.Failure(error.toDatabaseAppError())
        }
    }
}

/**
 * Why a join did not happen, or null when it did.
 *
 * The three answers turn on two different failures that used to look the same. A transaction
 * whose handler stood down reports itself honestly as "not committed"; one the server refused
 * arrives as an exception, and letting that unwind reported every mistyped room password as
 * "something went wrong". Telling them apart is [offered]: the write only ever reaches the
 * server once the handler has decided the seat is takeable, so a failure after that is the
 * rules turning it down — and the only rule a join can still fail on is the password.
 *
 * @param offered whether the transaction handler filled the seat in rather than aborting.
 */
internal fun joinRefusal(
    committed: Boolean,
    offered: Boolean,
    requiresPassword: Boolean,
): AppError? = when {
    committed && offered -> null
    offered && requiresPassword -> AppError.ROOM_PASSWORD_WRONG
    // The handler stood down: the room went, or somebody took the seat between the read and
    // the write. Neither is a wrong password, which is what a protected room used to say to a
    // player whose room had simply filled up.
    else -> AppError.ROOM_FULL
}
