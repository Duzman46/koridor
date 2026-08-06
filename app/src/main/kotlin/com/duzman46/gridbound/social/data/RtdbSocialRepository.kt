package com.duzman46.gridbound.social.data

import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.core.UsernameRules
import com.duzman46.gridbound.data.firebase.await
import com.duzman46.gridbound.data.firebase.awaitSnapshot
import com.duzman46.gridbound.data.firebase.snapshotFlow
import com.duzman46.gridbound.online.data.FirebaseProvider
import com.duzman46.gridbound.profile.domain.UserProfile
import com.duzman46.gridbound.profile.domain.UserProfileRepository
import com.duzman46.gridbound.social.domain.Friend
import com.duzman46.gridbound.social.domain.FriendshipAction
import com.duzman46.gridbound.social.domain.FriendshipRules
import com.duzman46.gridbound.social.domain.FriendshipStatus
import com.duzman46.gridbound.social.domain.GameInvite
import com.duzman46.gridbound.social.domain.PresenceState
import com.duzman46.gridbound.social.domain.SocialRepository
import com.duzman46.gridbound.util.enumValueOrDefault
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import javax.inject.Inject
import javax.inject.Singleton
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

    override fun observeInvites(userId: String): Flow<List<GameInvite>> {
        if (!firebase.isConfigured || userId.isBlank()) return flowOf(emptyList())
        return invitesRef(userId).snapshotFlow()
            .map { snapshot ->
                val now = System.currentTimeMillis()
                snapshot.children.mapNotNull(::decodeInvite).filterNot { it.isExpired(now) }
            }
            .catch { error ->
                AppLog.warn("observe-invites", error)
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
            val theirs = readStatus(otherUserId, userId)
            val update = FriendshipRules.apply(action, mine, theirs)
                ?: return@dbCall Outcome.Failure(AppError.UNKNOWN)

            val now = System.currentTimeMillis()
            // Both halves in one update: the pair can never be left disagreeing.
            val payload = buildMap<String, Any?> {
                putAll(statusPayload(userId, otherUserId, update.mine, now))
                putAll(statusPayload(otherUserId, userId, update.theirs, now))
                // Blocking also withdraws any invitations already in flight.
                if (update.mine == FriendshipStatus.BLOCKED) {
                    put("${Constants.Social.INVITES_PATH}/$userId/$otherUserId", null)
                }
            }
            firebase.database.reference.updateChildren(payload).await()
            Outcome.Success(Unit)
        }
    }

    override suspend fun sendInvite(
        fromUserId: String,
        toUserId: String,
        roomCode: String,
    ): Outcome<Unit> = dbCall("send-invite") {
        // Checked here for a clear message, and again by the rules, which are the authority.
        val recipientView = readStatus(toUserId, fromUserId)
        if (!FriendshipRules.canInvite(recipientView)) {
            return@dbCall Outcome.Failure(AppError.NOT_SIGNED_IN)
        }
        val now = System.currentTimeMillis()
        // Keyed by sender, so repeatedly tapping invite refreshes one entry rather than
        // filling the recipient's list.
        invitesRef(toUserId).child(fromUserId).setValue(
            mapOf(
                Keys.FROM_USER_ID to fromUserId,
                Keys.ROOM_CODE to roomCode,
                Keys.CREATED_AT to now,
                Keys.EXPIRES_AT to now + Constants.Social.INVITE_TTL_MILLIS,
            ),
        ).await()
        Outcome.Success(Unit)
    }

    override suspend fun dismissInvite(userId: String, inviteId: String): Outcome<Unit> =
        dbCall("dismiss-invite") {
            invitesRef(userId).child(inviteId).removeValue().await()
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

    override suspend fun deleteSocialData(userId: String): Outcome<Unit> =
        dbCall("delete-social-data") {
            // Remove this player from the lists of everyone they are connected to, so no
            // dangling half-relationship survives the account.
            val friendships = friendshipsRef(userId).awaitSnapshot()
            val payload = buildMap<String, Any?> {
                friendships.children.mapNotNull(DataSnapshot::getKey).forEach { otherId ->
                    put("${Constants.Social.FRIENDSHIPS_PATH}/$otherId/$userId", null)
                    put("${Constants.Social.INVITES_PATH}/$otherId/$userId", null)
                }
                put("${Constants.Social.FRIENDSHIPS_PATH}/$userId", null)
                put("${Constants.Social.INVITES_PATH}/$userId", null)
                put("${Constants.Social.PRESENCE_PATH}/$userId", null)
            }
            firebase.database.reference.updateChildren(payload).await()
            Outcome.Success(Unit)
        }

    private suspend fun readStatus(owner: String, other: String): FriendshipStatus =
        enumValueOrDefault(
            friendshipsRef(owner).child(other).child(Keys.STATUS)
                .awaitSnapshot().getValue(String::class.java),
            FriendshipStatus.NONE,
        )

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
                "$base/${Keys.STATUS}" to status.name,
                "$base/${Keys.UPDATED_AT}" to now,
            )
        }
    }

    private fun decodeInvite(snapshot: DataSnapshot): GameInvite? {
        val inviteId = snapshot.key ?: return null
        val fromUserId = snapshot.child(Keys.FROM_USER_ID).getValue(String::class.java) ?: return null
        val roomCode = snapshot.child(Keys.ROOM_CODE).getValue(String::class.java) ?: return null
        return GameInvite(
            inviteId = inviteId,
            fromUserId = fromUserId,
            fromUsername = snapshot.child(Keys.FROM_USERNAME).getValue(String::class.java).orEmpty(),
            roomCode = roomCode,
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

    private object Keys {
        const val STATUS = "status"
        const val UPDATED_AT = "updatedAt"
        const val ONLINE = "online"
        const val LAST_SEEN = "lastSeen"
        const val FROM_USER_ID = "fromUserId"
        const val FROM_USERNAME = "fromUsername"
        const val ROOM_CODE = "roomCode"
        const val CREATED_AT = "createdAt"
        const val EXPIRES_AT = "expiresAt"
    }
}
