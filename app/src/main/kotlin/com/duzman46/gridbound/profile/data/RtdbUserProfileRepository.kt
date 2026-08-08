package com.duzman46.gridbound.profile.data

import com.duzman46.gridbound.auth.domain.AuthUser
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.core.UsernameRules
import com.duzman46.gridbound.data.firebase.await
import com.duzman46.gridbound.data.firebase.awaitSnapshot
import com.duzman46.gridbound.data.firebase.runTransactionSuspend
import com.duzman46.gridbound.data.firebase.snapshotFlow
import com.duzman46.gridbound.online.data.FirebaseProvider
import com.duzman46.gridbound.profile.domain.UserProfile
import com.duzman46.gridbound.profile.domain.UserProfileRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.Transaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
class RtdbUserProfileRepository @Inject constructor(
    private val firebase: FirebaseProvider,
    private val codec: ProfileCodec,
) : UserProfileRepository {

    override fun observeProfile(userId: String): Flow<UserProfile?> {
        if (!firebase.isConfigured || userId.isBlank()) return flowOf(null)
        return userRef(userId).snapshotFlow()
            .map(codec::decode)
            .catch { error ->
                AppLog.warn("observe-profile", error)
                emit(null)
            }
    }

    override suspend fun loadProfile(userId: String): Outcome<UserProfile> = dbCall("load-profile") {
        val profile = codec.decode(userRef(userId).awaitSnapshot())
            ?: return@dbCall Outcome.Failure(AppError.UNKNOWN)
        Outcome.Success(profile)
    }

    override suspend fun ensureProfile(user: AuthUser): Outcome<UserProfile> =
        dbCall("ensure-profile") {
            val now = System.currentTimeMillis()
            val existing = codec.decode(userRef(user.userId).awaitSnapshot())
            if (existing != null) {
                // Account type changes when a guest links a credential; keep it in step.
                userRef(user.userId).updateChildren(
                    mapOf(
                        ProfileCodec.Keys.LAST_LOGIN_AT to now,
                        ProfileCodec.Keys.ACCOUNT_TYPE to user.accountType.name,
                    ),
                ).await()
                storePrivateEmail(user)
                claimBoardRating(user.userId)
                return@dbCall Outcome.Success(
                    existing.copy(
                        lastLoginAt = now,
                        accountType = user.accountType,
                        email = user.email,
                    ),
                )
            }
            createProfile(user, now)
        }

    override suspend fun isUsernameAvailable(username: String): Outcome<Boolean> {
        val validated = UsernameRules.validate(username)
        if (validated is Outcome.Failure) return validated
        return dbCall("username-available") {
            val normalized = UsernameRules.normalize((validated as Outcome.Success).value)
            val owner = usernameRef(normalized).awaitSnapshot().getValue(String::class.java)
            Outcome.Success(owner == null)
        }
    }

    override suspend fun changeUsername(userId: String, username: String): Outcome<String> {
        val validated = UsernameRules.validate(username)
        if (validated is Outcome.Failure) return validated
        val desired = (validated as Outcome.Success).value
        val normalized = UsernameRules.normalize(desired)
        return dbCall("change-username") {
            val previous = userRef(userId).child(ProfileCodec.Keys.NORMALIZED_USERNAME)
                .awaitSnapshot().getValue(String::class.java)
            if (previous == normalized) {
                // Same name, different casing: no index change is needed.
                writeUsername(userId, desired, normalized)
                return@dbCall Outcome.Success(desired)
            }
            if (!claimUsername(userId, normalized)) {
                return@dbCall Outcome.Failure(AppError.USERNAME_TAKEN)
            }
            writeUsername(userId, desired, normalized)
            releaseUsername(userId, previous)
            // The moment the account has a name of its own is the moment it is allowed on the
            // board, and for a player who has just linked a credential it is this line that
            // puts them there. See [claimBoardRating].
            claimBoardRating(userId)
            Outcome.Success(desired)
        }
    }

    override suspend fun updateAvatar(userId: String, avatarId: String): Outcome<Unit> =
        updateField(userId, ProfileCodec.Keys.AVATAR_ID, avatarId)

    override suspend fun updatePreferredLanguage(userId: String, languageTag: String): Outcome<Unit> =
        updateField(userId, ProfileCodec.Keys.PREFERRED_LANGUAGE, languageTag)

    override suspend fun setTutorialCompleted(userId: String, completed: Boolean): Outcome<Unit> =
        updateField(userId, ProfileCodec.Keys.TUTORIAL_COMPLETED, completed)

    override suspend fun deleteAccountData(userId: String): Outcome<Unit> = dbCall("delete-profile") {
        val normalized = userRef(userId).child(ProfileCodec.Keys.NORMALIZED_USERNAME)
            .awaitSnapshot().getValue(String::class.java)
        releaseUsername(userId, normalized)
        privateRef(userId).removeValue().await()
        userRef(userId).removeValue().await()
        Outcome.Success(Unit)
    }

    /**
     * Names the account so it can exist before anybody has been asked anything. A player
     * with a real account is made to replace this the first time they reach the entry gate;
     * a guest keeps it, which is the point of playing as one.
     */
    private suspend fun createProfile(user: AuthUser, now: Long): Outcome<UserProfile> {
        // Six random digits collide about once in a million; each attempt draws again.
        repeat(Constants.Backend.MAX_USERNAME_ATTEMPTS) {
            val candidate = UsernameRules.generatedName()
            val normalized = UsernameRules.normalize(candidate)
            if (!claimUsername(user.userId, normalized)) return@repeat
            val profile = UserProfile(
                userId = user.userId,
                username = candidate,
                normalizedUsername = normalized,
                avatarId = Constants.Profile.DEFAULT_AVATAR_ID,
                email = user.email,
                accountType = user.accountType,
                createdAt = now,
                lastLoginAt = now,
            )
            userRef(user.userId).setValue(codec.encodeNewProfile(profile)).await()
            storePrivateEmail(user)
            // No board claim here. Every profile is created under a name this method invented,
            // and [claimBoardRating] would refuse it; the claim belongs to changeUsername,
            // which is where a name stops being invented.
            return Outcome.Success(profile)
        }
        AppLog.warn("create-profile-username-exhausted")
        return Outcome.Failure(AppError.USERNAME_TAKEN)
    }

    /**
     * Reserves the name atomically. Two devices racing for the same name means exactly one
     * transaction commits; the loser sees the node already owned by someone else.
     */
    private suspend fun claimUsername(userId: String, normalized: String): Boolean =
        usernameRef(normalized).runTransactionSuspend { current ->
            val owner = current.getValue(String::class.java)
            if (owner != null && owner != userId) {
                Transaction.abort()
            } else {
                current.value = userId
                Transaction.success(current)
            }
        }

    private suspend fun releaseUsername(userId: String, normalized: String?) {
        if (normalized.isNullOrBlank()) return
        runCatching {
            usernameRef(normalized).runTransactionSuspend { current ->
                // Only drop the index entry when it still points at this user.
                if (current.getValue(String::class.java) == userId) current.value = null
                Transaction.success(current)
            }
        }.onFailure { AppLog.warn("release-username", it) }
    }

    private suspend fun writeUsername(userId: String, username: String, normalized: String) {
        userRef(userId).updateChildren(
            mapOf(
                ProfileCodec.Keys.USERNAME to username,
                ProfileCodec.Keys.NORMALIZED_USERNAME to normalized,
            ),
        ).await()
    }

    /**
     * Puts a named account into the leaderboard index, with whatever rating it already holds.
     *
     * This is the whole of how a guest gets onto the board once they stop being one: the key
     * the board is ordered by is written here and nowhere else on the device, carrying the
     * rating they played for, because the value is read back off the profile rather than
     * assumed to be the starting one.
     *
     * ## Why linking is not the moment
     *
     * It used to be. A credential makes an account real, `ensureProfile` runs, and the claim
     * went with it — several seconds before its owner reaches the form that names it. In that
     * window they were on the all-time board as `guest_######`, a name nobody chose and nobody
     * meant to publish, and killing the app there left them on it indefinitely. Navigating to
     * the naming gate on link closes the part a player sits and watches; it cannot close the
     * part where the app is not running. So the claim waits for the name instead: a profile
     * with no name of its own has no business on a public board, however it arrived at one,
     * and `worker/src/sweep.ts` holds the same rule so that no other hand puts them there.
     *
     * Both facts are read from one snapshot, as late as possible, because the rules insist the
     * copy equals the rating it mirrors.
     *
     * Best-effort on purpose. A rated match landing in the moment between the read and the
     * write is refused; that must cost a sign-in nothing, and it costs nothing, because the
     * next sign-in writes it again and the server writes it after every rated match in between.
     */
    private suspend fun claimBoardRating(userId: String) {
        runCatching {
            val profile = codec.decode(userRef(userId).awaitSnapshot()) ?: return@runCatching
            if (profile.isGuest || !profile.hasChosenName) return@runCatching
            userRef(userId).child(ProfileCodec.Keys.LEADERBOARD_RATING)
                .setValue(profile.rating).await()
        }.onFailure { AppLog.warn("claim-board-rating", it) }
    }

    private suspend fun storePrivateEmail(user: AuthUser) {
        val email = user.email
        runCatching {
            if (email.isNullOrBlank()) {
                privateRef(user.userId).child(ProfileCodec.Keys.EMAIL).removeValue().await()
            } else {
                privateRef(user.userId).child(ProfileCodec.Keys.EMAIL).setValue(email).await()
            }
        }.onFailure { AppLog.warn("store-private-email", it) }
    }

    private suspend fun updateField(userId: String, key: String, value: Any): Outcome<Unit> =
        dbCall("update-$key") {
            userRef(userId).child(key).setValue(value).await()
            Outcome.Success(Unit)
        }

    private fun userRef(userId: String): DatabaseReference =
        firebase.database.getReference(Constants.Backend.USERS_PATH).child(userId)

    private fun privateRef(userId: String): DatabaseReference =
        firebase.database.getReference(Constants.Backend.USERS_PRIVATE_PATH).child(userId)

    private fun usernameRef(normalized: String): DatabaseReference =
        firebase.database.getReference(Constants.Backend.USERNAMES_PATH).child(normalized)

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
