package com.duzman46.gridbound.profile.domain

import com.duzman46.gridbound.auth.domain.AccountType
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.UsernameRules

enum class AccountStatus {
    ACTIVE,
    SUSPENDED,
    DELETED,
}

/**
 * A player's public identity and competitive record.
 *
 * The fields under "server-owned" below are writable only by trusted server code; the
 * Realtime Database rules reject any client write that changes them. See
 * database.rules.json and functions/src/index.ts.
 *
 * @param email present only when this profile belongs to the signed-in user. It is stored
 *   under a separate owner-only node and is never readable by other players.
 */
data class UserProfile(
    val userId: String,
    val username: String,
    val normalizedUsername: String,
    val avatarId: String,
    val email: String? = null,
    val accountType: AccountType = AccountType.GUEST,
    val createdAt: Long = 0L,
    val lastLoginAt: Long = 0L,
    val preferredLanguage: String = "",
    val tutorialCompleted: Boolean = false,
    // Server-owned from here down.
    val totalGames: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val rating: Int = Constants.Backend.STARTING_RATING,
    val highestRating: Int = Constants.Backend.STARTING_RATING,
    val currentWinStreak: Int = 0,
    val bestWinStreak: Int = 0,
    val purchasedEntitlements: List<String> = emptyList(),
    val accountStatus: AccountStatus = AccountStatus.ACTIVE,
) {
    val isGuest: Boolean get() = accountType.isGuest

    /**
     * True when the name on this profile was typed by whoever owns it rather than handed out
     * by the app.
     *
     * It is the only durable answer to "has this player ever named themselves?" — a device
     * preference only knows about the install it is on, while the name travels with the
     * account. See [UsernameRules.isGenerated] for what the app hands out, and for why
     * mistaking a chosen name for a generated one is a small and self-correcting error.
     */
    val hasChosenName: Boolean get() = !UsernameRules.isGenerated(username)

    val winRate: Float
        get() {
            val decided = wins + losses
            return if (decided == 0) 0f else wins.toFloat() / decided
        }
}
