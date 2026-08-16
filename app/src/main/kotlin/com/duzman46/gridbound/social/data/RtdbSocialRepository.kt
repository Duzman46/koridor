package com.duzman46.gridbound.social.data

import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.core.UsernameRules
import com.duzman46.gridbound.data.firebase.await
import com.duzman46.gridbound.data.firebase.awaitSnapshot
import com.duzman46.gridbound.data.firebase.snapshotFlow
import com.duzman46.gridbound.data.firebase.toDatabaseAppError
import com.duzman46.gridbound.online.data.FirebaseProvider
import com.duzman46.gridbound.profile.domain.UserProfile
import com.duzman46.gridbound.profile.domain.UserProfileRepository
import com.duzman46.gridbound.social.domain.ContentReportReason
import com.duzman46.gridbound.social.domain.Friend
import com.duzman46.gridbound.social.domain.FriendshipAction
import com.duzman46.gridbound.social.domain.FriendshipRules
import com.duzman46.gridbound.social.domain.FriendshipStatus
import com.duzman46.gridbound.social.domain.FriendshipUpdate
import com.duzman46.gridbound.social.domain.PlayerRequest
import com.duzman46.gridbound.social.domain.PresenceState
import com.duzman46.gridbound.social.domain.RequestKind
import com.duzman46.gridbound.social.domain.SocialRepository
import com.duzman46.gridbound.util.enumValueOrDefault
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RtdbSocialRepository @Inject constructor(
    private val firebase: FirebaseProvider,
    private val profileRepository: UserProfileRepository,
) : SocialRepository {

    override fun observeFriendships(userId: String): Flow<List<Friend>> {
        if (!firebase.isConfigured || userId.isBlank()) return flowOf(emptyList())
        return friendshipsRef(userId).snapshotFlow()
            .map { snapshot ->
                val entries = snapshot.children.mapNotNull { child ->
                    val otherId = child.key ?: return@mapNotNull null
                    val status = enumValueOrDefault(
                        child.child(Keys.STATUS).getValue(String::class.java),
                        FriendshipStatus.NONE,
                    )
                    if (status == FriendshipStatus.NONE) null else otherId to status
                }
                // Profiles are fetched in parallel; the list is small and explicit, so this
                // costs one read per relationship rather than a scan of the player base.
                coroutineScope {
                    entries.map { (otherId, status) ->
                        async {
                            val profile = profileRepository.loadProfile(otherId).successOrNull
                            Friend(
                                userId = otherId,
                                username = profile?.username ?: otherId.take(6),
                                avatarId = profile?.avatarId ?: Constants.Profile.DEFAULT_AVATAR_ID,
                                rating = profile?.rating ?: Constants.Backend.STARTING_RATING,
                                status = status,
                            )
                        }
                    }.map { it.await() }
                }
            }
            .catch { error ->
                AppLog.warn("observe-friendships", error)
                emit(emptyList())
            }
    }

    override fun observeRequests(userId: String): Flow<List<PlayerRequest>> {
        if (!firebase.isConfigured || userId.isBlank()) return flowOf(emptyList())
        return invitesRef(userId).snapshotFlow()
            .map { snapshot ->
                val now = System.currentTimeMillis()
                snapshot.children.mapNotNull(::decodeRequest).filterNot { it.isExpired(now) }
            }
            .catch { error ->
                AppLog.warn("observe-requests", error)
                emit(emptyList())
            }
    }

    override fun observePresence(userIds: Set<String>): Flow<Map<String, PresenceState>> {
        if (!firebase.isConfigured || userIds.isEmpty()) return flowOf(emptyMap())
        // One listener per friend rather than a listener on the whole presence tree, so the
        // app never subscribes to the online state of players it is not showing.
        val flows = userIds.map { id ->
            presenceRef(id).snapshotFlow().map { snapshot ->
                id to if (snapshot.child(Keys.ONLINE).getValue(Boolean::class.java) == true) {
                    PresenceState.ONLINE
                } else {
                    PresenceState.OFFLINE
                }
            }
        }
        return combine(flows) { pairs -> pairs.toMap() }
            .catch { error ->
                AppLog.warn("observe-presence", error)
                emit(emptyMap())
            }
    }

    override suspend fun findByUsername(username: String): Outcome<UserProfile?> {
        val validated = UsernameRules.validate(username)
        if (validated is Outcome.Failure) return validated
        return dbCall("find-by-username") {
            val normalized = UsernameRules.normalize((validated as Outcome.Success).value)
            val ownerId = firebase.database
                .getReference(Constants.Backend.USERNAMES_PATH)
                .child(normalized)
                .awaitSnapshot()
                .getValue(String::class.java)
                ?: return@dbCall Outcome.Success(null)
            Outcome.Success(profileRepository.loadProfile(ownerId).successOrNull)
        }
    }

    override suspend fun applyFriendshipAction(
        userId: String,
        otherUserId: String,
        action: FriendshipAction,
    ): Outcome<Unit> {
        if (userId == otherUserId) return Outcome.Failure(AppError.UNKNOWN)
        return dbCall("friendship-${action.name.lowercase()}") {
            val mine = readStatus(userId, otherUserId)
            // Only our own half is read. friendships/{them}/{me} is readable by them alone —
            // reading it to find out whether they had blocked us was rejected by the rules,
            // which failed every single friendship action before the write was even tried.
            // The rules already enforce that invariant on write: the update to their half is
            // refused when they have us blocked, and refusing there is also what keeps a
            // block from being detectable.
            val update = FriendshipRules.apply(action, mine, FriendshipStatus.NONE)
                ?: return@dbCall Outcome.Failure(AppError.UNKNOWN)

            val writes = FriendshipWrites.of(
                action = action,
                update = update,
                userId = userId,
                otherUserId = otherUserId,
                now = System.currentTimeMillis(),
            )
            firebase.database.reference.updateChildren(writes.atomic).await()
            // Best effort, and for the same reason deleteSocialData writes the other player's
            // rows one at a time: a player who has us blocked refuses this one, and it must
            // not be able to take the block we have just written down with it.
            if (writes.mirror.isNotEmpty()) {
                runCatching { firebase.database.reference.updateChildren(writes.mirror).await() }
                    .onFailure { AppLog.warn("friendship-mirror", it) }
            }
            Outcome.Success(Unit)
        }
    }

    override suspend fun sendInvite(
        fromUserId: String,
        fromUsername: String,
        toUserId: String,
        roomCode: String,
    ): Outcome<Unit> = dbCall("send-invite") {
        // Our own half, not theirs: friendships/{them}/{me} is unreadable to us, and asking
        // for it made every invite fail. The two halves are written in one atomic update, so
        // our side answers the same question, and the rules re-check theirs on write.
        if (!FriendshipRules.canInvite(readStatus(fromUserId, toUserId))) {
            return@dbCall Outcome.Failure(AppError.NOT_FRIENDS)
        }
        writeRequest(RequestKind.GAME_INVITE, fromUserId, fromUsername, toUserId, roomCode)
        Outcome.Success(Unit)
    }

    override suspend fun sendRematch(
        fromUserId: String,
        fromUsername: String,
        toUserId: String,
        roomCode: String,
        playedRoomCode: String,
    ): Outcome<Unit> = dbCall("send-rematch") {
        // No friendship is asked for, here or in the rules. You have just spent a match with
        // this person; needing to befriend them first to offer them another one would be an
        // obstacle in front of the one thing everybody wants after losing.
        writeRequest(
            kind = RequestKind.REMATCH,
            fromUserId = fromUserId,
            fromUsername = fromUsername,
            toUserId = toUserId,
            roomCode = roomCode,
            playedRoomCode = playedRoomCode,
        )
        Outcome.Success(Unit)
    }

    override suspend fun declineRematch(
        fromUserId: String,
        fromUsername: String,
        toUserId: String,
        roomCode: String,
        playedRoomCode: String,
    ): Outcome<Unit> = dbCall("decline-rematch") {
        writeRequest(
            kind = RequestKind.REMATCH_DECLINED,
            fromUserId = fromUserId,
            fromUsername = fromUsername,
            toUserId = toUserId,
            // Echoed back so the asker can tell this answer from one to a request they have
            // since replaced, and so they know which room they are now free to close.
            roomCode = roomCode,
            playedRoomCode = playedRoomCode,
        )
        Outcome.Success(Unit)
    }

    override suspend fun clearRequest(recipientId: String, senderId: String): Outcome<Unit> =
        dbCall("clear-request") {
            invitesRef(recipientId).child(senderId).removeValue().await()
            Outcome.Success(Unit)
        }

    override suspend fun reportPlayer(
        reporterId: String,
        subjectId: String,
        reason: ContentReportReason,
        roomCode: String,
    ): Outcome<Unit> = dbCall("report-player") {
        if (reporterId.isBlank() || reporterId == subjectId || subjectId.isBlank()) {
            return@dbCall Outcome.Failure(AppError.UNKNOWN)
        }
        // The server stamps it. A report is evidence, and a phone that dated its own would be
        // deciding when the thing it is reporting happened.
        firebase.database
            .getReference(Constants.Social.CONTENT_REPORTS_PATH)
            .child(subjectId)
            .child(reporterId)
            .setValue(
                buildMap {
                    put(Keys.REASON, reason.name)
                    put(Keys.CREATED_AT, ServerValue.TIMESTAMP)
                    if (roomCode.isNotBlank()) put(Keys.ROOM_CODE, roomCode)
                },
            ).await()
        Outcome.Success(Unit)
    }

    override fun startPresence(userId: String) {
        if (!firebase.isConfigured || userId.isBlank()) return
        val reference = presenceRef(userId)
        // Registered with the server first: if the process dies or the network drops, the
        // server still clears the flag, so nobody is left showing as online forever.
        reference.onDisconnect().setValue(
            mapOf(Keys.ONLINE to false, Keys.LAST_SEEN to ServerValue.TIMESTAMP),
        )
        reference.setValue(mapOf(Keys.ONLINE to true, Keys.LAST_SEEN to ServerValue.TIMESTAMP))
    }

    override suspend fun clearPresence(userId: String) {
        if (!firebase.isConfigured || userId.isBlank()) return
        runCatching {
            presenceRef(userId)
                .setValue(mapOf(Keys.ONLINE to false, Keys.LAST_SEEN to ServerValue.TIMESTAMP))
                .await()
        }.onFailure { AppLog.warn("clear-presence", it) }
    }

    /**
     * Every path is written one relationship deep.
     *
     * `friendships/{me}` and `invites/{me}` look like the obvious things to remove, and both
     * are refused: the rules grant a write one level further down, at
     * `friendships/{owner}/{other}` and `invites/{recipient}/{sender}`, and permission in the
     * Realtime Database only ever flows downwards. Asking for the parent asks for a
     * permission nobody was granted, and in a single atomic update it took everything else
     * down with it — which is why an account's friendships and invitations outlived it.
     *
     * ## The one thing this cannot reach
     *
     * The friend list is what names the other players, so every game invitation this account
     * sent is here: friendship is exactly what the rules charge for one, so an invitation and
     * a friendship always come as a pair. A rematch is charged for differently — the finished
     * match is the licence, and the two need never have been friends — so
     * `invites/{opponent}/{me}` can exist with nothing on this side pointing at it.
     *
     * Nothing this account is allowed to read would find it. `invites/$recipient` is readable
     * by that recipient alone, there is no rule below it that opens a single entry to the
     * player who wrote it, and no index of "who did I ask". The write is permitted — the rules
     * let a sender take back their own entry sight unseen — but only by naming the recipient,
     * and that name is the part that cannot be recovered. Reading them off the rooms this
     * account played is not the answer either: a room and an invitation are kept for different
     * lengths of time by different rules, so the overlap is a coincidence rather than a
     * guarantee, and a swept room leaves an entry nobody can name at all.
     *
     * So the promise is kept by the one writer that needs no permission from anybody:
     * `collectDeadInvites` in `worker/src/sweep.ts` clears every entry whose sender no longer
     * has a profile, along with every entry that has expired.
     */
    override suspend fun deleteSocialData(userId: String): Outcome<Unit> =
        dbCall("delete-social-data") {
            val friendships = friendshipsRef(userId).awaitSnapshot()
            // The rows on other players' nodes, each on its own. Someone who blocked this
            // player keeps their block — the rules say so, and rightly — so this one write
            // can be refused, and it must not be able to abort the rest of the deletion.
            friendships.children.forEach { child ->
                val otherId = child.key ?: return@forEach
                removeQuietly(friendshipsRef(otherId).child(userId))
                removeQuietly(invitesRef(otherId).child(userId))
            }
            val payload = buildMap<String, Any?> {
                friendships.children.forEach { child ->
                    child.key?.let { put("${Constants.Social.FRIENDSHIPS_PATH}/$userId/$it", null) }
                }
                invitesRef(userId).awaitSnapshot().children.forEach { child ->
                    child.key?.let { put("${Constants.Social.INVITES_PATH}/$userId/$it", null) }
                }
                put("${Constants.Social.PRESENCE_PATH}/$userId", null)
            }
            firebase.database.reference.updateChildren(payload).await()
            Outcome.Success(Unit)
        }

    private suspend fun removeQuietly(reference: DatabaseReference) {
        runCatching { reference.removeValue().await() }
            .onFailure { AppLog.warn("delete-social-mirror", it) }
    }

    private suspend fun readStatus(owner: String, other: String): FriendshipStatus =
        enumValueOrDefault(
            friendshipsRef(owner).child(other).child(Keys.STATUS)
                .awaitSnapshot().getValue(String::class.java),
            FriendshipStatus.NONE,
        )

    private suspend fun writeRequest(
        kind: RequestKind,
        fromUserId: String,
        fromUsername: String,
        toUserId: String,
        roomCode: String,
        playedRoomCode: String = "",
    ) {
        val now = System.currentTimeMillis()
        // Keyed by sender, so asking again refreshes one entry rather than filling the
        // recipient's channel with the same question.
        invitesRef(toUserId).child(fromUserId).setValue(
            mapOf(
                Keys.KIND to kind.name,
                Keys.FROM_USER_ID to fromUserId,
                Keys.FROM_USERNAME to fromUsername,
                Keys.ROOM_CODE to roomCode,
                Keys.PLAYED_ROOM_CODE to playedRoomCode,
                Keys.CREATED_AT to now,
                Keys.EXPIRES_AT to now + Constants.Social.INVITE_TTL_MILLIS,
            ),
        ).await()
    }

    private fun decodeRequest(snapshot: DataSnapshot): PlayerRequest? {
        // The key is the sender, and the rules refuse an entry whose payload disagrees with
        // it, so the key is the identity worth trusting.
        val fromUserId = snapshot.key ?: return null
        val roomCode = snapshot.child(Keys.ROOM_CODE).getValue(String::class.java) ?: return null
        return PlayerRequest(
            fromUserId = fromUserId,
            fromUsername = snapshot.child(Keys.FROM_USERNAME).getValue(String::class.java).orEmpty(),
            // An entry carrying no kind came from a build that could only invite.
            kind = enumValueOrDefault(
                snapshot.child(Keys.KIND).getValue(String::class.java),
                RequestKind.GAME_INVITE,
            ),
            roomCode = roomCode,
            playedRoomCode = snapshot.child(Keys.PLAYED_ROOM_CODE)
                .getValue(String::class.java)
                .orEmpty(),
            createdAt = snapshot.child(Keys.CREATED_AT).getValue(Long::class.java) ?: 0L,
            expiresAt = snapshot.child(Keys.EXPIRES_AT).getValue(Long::class.java) ?: 0L,
        )
    }

    private fun friendshipsRef(userId: String): DatabaseReference =
        firebase.database.getReference(Constants.Social.FRIENDSHIPS_PATH).child(userId)

    private fun invitesRef(userId: String): DatabaseReference =
        firebase.database.getReference(Constants.Social.INVITES_PATH).child(userId)

    private fun presenceRef(userId: String): DatabaseReference =
        firebase.database.getReference(Constants.Social.PRESENCE_PATH).child(userId)

    /**
     * Cancellation is passed on rather than caught. On the JVM it is an `Exception` like any
     * other, so leaving the generic catch to see it turned a player who tapped away from the
     * friends screen mid-request into a Crashlytics report of a failed write, and left the
     * coroutine that had been told to stop running on to answer nobody.
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

    internal object Keys {
        const val STATUS = "status"
        const val UPDATED_AT = "updatedAt"
        const val REASON = "reason"
        const val ONLINE = "online"
        const val LAST_SEEN = "lastSeen"
        const val KIND = "kind"
        const val FROM_USER_ID = "fromUserId"
        const val FROM_USERNAME = "fromUsername"
        const val ROOM_CODE = "roomCode"
        const val PLAYED_ROOM_CODE = "playedRoomCode"
        const val CREATED_AT = "createdAt"
        const val EXPIRES_AT = "expiresAt"
    }
}

/**
 * The rows one friendship action turns into, split by what is allowed to be refused.
 *
 * @param atomic must land together or not at all.
 * @param mirror the other player's copy, sent on its own and permitted to fail. Empty unless
 *   the action is a block.
 */
internal data class FriendshipWrite(
    val atomic: Map<String, Any?>,
    val mirror: Map<String, Any?>,
)

/**
 * Turns a settled [FriendshipUpdate] into the paths that record it.
 *
 * Every action but one travels as a single all-or-nothing update, and deliberately so: a
 * request aimed at somebody who has blocked you is refused on *their* row, and having that
 * refusal take the whole update with it is what keeps a block from being detectable.
 *
 * Blocking is the exception, because clearing the other player's row is precisely the write
 * their own block refuses. Sent together, being blocked first was all it took to make somebody
 * unblockable: the update failed whole, and the one row that actually stops them reaching you
 * — your own — was never written. So the block goes on its own, and their copy follows as a
 * best-effort mirror, exactly as `deleteSocialData` already treats the same rows.
 */
internal object FriendshipWrites {

    fun of(
        action: FriendshipAction,
        update: FriendshipUpdate,
        userId: String,
        otherUserId: String,
        now: Long,
    ): FriendshipWrite {
        val blocking = action == FriendshipAction.BLOCK
        val theirs = statusPayload(otherUserId, userId, update.theirs, now)
        return FriendshipWrite(
            atomic = buildMap {
                putAll(statusPayload(userId, otherUserId, update.mine, now))
                if (!blocking) putAll(theirs)
                // Blocking also withdraws any invitation already in flight.
                if (update.mine == FriendshipStatus.BLOCKED) {
                    put("${Constants.Social.INVITES_PATH}/$userId/$otherUserId", null)
                }
            },
            mirror = if (blocking) theirs else emptyMap(),
        )
    }

    private fun statusPayload(
        owner: String,
        other: String,
        status: FriendshipStatus,
        now: Long,
    ): Map<String, Any?> {
        val base = "${Constants.Social.FRIENDSHIPS_PATH}/$owner/$other"
        // NONE is stored as absence rather than a value, so a cleared relationship leaves
        // nothing behind to read or pay for.
        return if (status == FriendshipStatus.NONE) {
            mapOf(base to null)
        } else {
            mapOf(
                "$base/${RtdbSocialRepository.Keys.STATUS}" to status.name,
                "$base/${RtdbSocialRepository.Keys.UPDATED_AT}" to now,
            )
        }
    }
}
