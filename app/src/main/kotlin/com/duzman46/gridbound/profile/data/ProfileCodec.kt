package com.duzman46.gridbound.profile.data

import com.duzman46.gridbound.auth.domain.AccountType
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.data.firebase.bool
import com.duzman46.gridbound.data.firebase.int
import com.duzman46.gridbound.data.firebase.string
import com.duzman46.gridbound.data.firebase.stringOrNull
import com.duzman46.gridbound.profile.domain.AccountStatus
import com.duzman46.gridbound.profile.domain.UserProfile
import com.duzman46.gridbound.util.enumValueOrDefault
import com.google.firebase.database.DataSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Translates between [UserProfile] and the flat map stored at `users/{uid}`.
 *
 * Written by hand rather than with Firebase's reflective mapper so R8 needs no keep rule
 * for the model and so an unexpected payload degrades to defaults instead of throwing.
 */
@Singleton
class ProfileCodec @Inject constructor() {

    fun decode(snapshot: DataSnapshot): UserProfile? {
        val userId = snapshot.key ?: return null
        val username = snapshot.stringOrNull(Keys.USERNAME) ?: return null
        return UserProfile(
            userId = userId,
            username = username,
            normalizedUsername = snapshot.string(Keys.NORMALIZED_USERNAME, username.lowercase()),
            avatarId = snapshot.string(Keys.AVATAR_ID, Constants.Profile.DEFAULT_AVATAR_ID),
            accountType = enumValueOrDefault(
                snapshot.stringOrNull(Keys.ACCOUNT_TYPE),
                AccountType.GUEST,
            ),
            createdAt = snapshot.child(Keys.CREATED_AT).getValue(Long::class.java) ?: 0L,
            lastLoginAt = snapshot.child(Keys.LAST_LOGIN_AT).getValue(Long::class.java) ?: 0L,
            preferredLanguage = snapshot.string(Keys.PREFERRED_LANGUAGE),
            tutorialCompleted = snapshot.bool(Keys.TUTORIAL_COMPLETED),
            totalGames = snapshot.int(Keys.TOTAL_GAMES),
            wins = snapshot.int(Keys.WINS),
            losses = snapshot.int(Keys.LOSSES),
            draws = snapshot.int(Keys.DRAWS),
            rating = snapshot.int(Keys.RATING, Constants.Backend.STARTING_RATING),
            highestRating = snapshot.int(Keys.HIGHEST_RATING, Constants.Backend.STARTING_RATING),
            currentWinStreak = snapshot.int(Keys.CURRENT_WIN_STREAK),
            bestWinStreak = snapshot.int(Keys.BEST_WIN_STREAK),
            purchasedEntitlements = snapshot.child(Keys.PURCHASED_ENTITLEMENTS).children
                .mapNotNull { it.key }
                .filter { it.isNotBlank() },
            accountStatus = enumValueOrDefault(
                snapshot.stringOrNull(Keys.ACCOUNT_STATUS),
                AccountStatus.ACTIVE,
            ),
        )
    }

    /**
     * The full payload written when a profile is first created. Values here must match the
     * initial values the database rules require, otherwise the write is rejected.
     */
    fun encodeNewProfile(profile: UserProfile): Map<String, Any> = mapOf(
        Keys.USERNAME to profile.username,
        Keys.NORMALIZED_USERNAME to profile.normalizedUsername,
        Keys.AVATAR_ID to profile.avatarId,
        Keys.ACCOUNT_TYPE to profile.accountType.name,
        Keys.CREATED_AT to profile.createdAt,
        Keys.LAST_LOGIN_AT to profile.lastLoginAt,
        Keys.PREFERRED_LANGUAGE to profile.preferredLanguage,
        Keys.TUTORIAL_COMPLETED to profile.tutorialCompleted,
        Keys.TOTAL_GAMES to 0,
        Keys.WINS to 0,
        Keys.LOSSES to 0,
        Keys.DRAWS to 0,
        Keys.RATING to Constants.Backend.STARTING_RATING,
        Keys.HIGHEST_RATING to Constants.Backend.STARTING_RATING,
        Keys.CURRENT_WIN_STREAK to 0,
        Keys.BEST_WIN_STREAK to 0,
        Keys.ACCOUNT_STATUS to AccountStatus.ACTIVE.name,
    )

    object Keys {
        const val USERNAME = "username"
        const val NORMALIZED_USERNAME = "normalizedUsername"
        const val AVATAR_ID = "avatarId"
        const val ACCOUNT_TYPE = "accountType"
        const val CREATED_AT = "createdAt"
        const val LAST_LOGIN_AT = "lastLoginAt"
        const val PREFERRED_LANGUAGE = "preferredLanguage"
        const val TUTORIAL_COMPLETED = "tutorialCompleted"
        const val TOTAL_GAMES = "totalGames"
        const val WINS = "wins"
        const val LOSSES = "losses"
        const val DRAWS = "draws"
        const val RATING = "rating"

        /**
         * The leaderboard's ordering key: the same number as [RATING], but written only for
         * an account that has been linked. See RtdbLeaderboardRepository for why the board is
         * ordered by a second copy of a number it already has.
         */
        const val LEADERBOARD_RATING = "leaderboardRating"
        const val HIGHEST_RATING = "highestRating"
        const val CURRENT_WIN_STREAK = "currentWinStreak"
        const val BEST_WIN_STREAK = "bestWinStreak"
        const val PURCHASED_ENTITLEMENTS = "purchasedEntitlements"
        const val ACCOUNT_STATUS = "accountStatus"
        const val EMAIL = "email"
    }
}
