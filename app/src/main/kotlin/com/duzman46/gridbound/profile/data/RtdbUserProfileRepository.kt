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

    override suspend fun ensureProfile(user: AuthUser, suggestedName: String?): Outcome<UserProfile> =
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
                return@dbCall Outcome.Success(
                    existing.copy(
                        lastLoginAt = now,
                        accountType = user.accountType,
                        email = user.email,
                    ),
                )
            }
            createProfile(user, suggestedName, now)
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
            Outcome.Success(desired)
        }
    }

    override suspend fun updateDisplayName(userId: String, displayName: String): Outcome<Unit> {
        val cleaned = displayName.trim().take(Constants.Profile.DISPLAY_NAME_MAX_LENGTH)
        if (cleaned.isEmpty()) return Outcome.Failure(AppError.USERNAME_BLANK)
        return updateField(userId, ProfileCodec.Keys.DISPLAY_NAME, cleaned)
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

    private suspend fun createProfile(
        user: AuthUser,
        suggestedName: String?,
        now: Long,
    ): Outcome<UserProfile> {
        val seed = UsernameRules.suggestFrom(suggestedName, user.userId)
        // A generated name can still collide, so try a few numbered variants before failing.
        repeat(Constants.Backend.MAX_USERNAME_ATTEMPTS) { attempt ->
            val candidate = if (attempt == 0) seed else nextCandidate(seed, attempt)
            if (UsernameRules.validate(candidate) !is Outcome.Success) return@repeat
            val normalized = UsernameRules.normalize(candidate)
            if (!claimUsername(user.userId, normalized)) return@repeat
            val profile = UserProfile(
                userId = user.userId,
                username = candidate,
                normalizedUsername = normalized,
                displayName = candidate,
                avatarId = Constants.Profile.DEFAULT_AVATAR_ID,
                email = user.email,
                accountType = user.accountType,
                createdAt = now,
                lastLoginAt = now,
            )
            userRef(user.userId).setValue(codec.encodeNewProfile(profile)).await()
            storePrivateEmail(user)
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

    private fun nextCandidate(seed: String, attempt: Int): String {
        val suffix = attempt.toString()
        val room = UsernameRules.MAX_LENGTH - suffix.length
        return seed.take(room) + suffix
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
