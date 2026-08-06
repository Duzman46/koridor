package com.duzman46.gridbound.social.domain

import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.profile.domain.UserProfile
import kotlinx.coroutines.flow.Flow

interface SocialRepository {
    /** Every relationship the player has, keyed by the other player's id. */
    fun observeFriendships(userId: String): Flow<List<Friend>>

    fun observeInvites(userId: String): Flow<List<GameInvite>>

    /** Online state of the given players. Kept to the friends actually on screen. */
    fun observePresence(userIds: Set<String>): Flow<Map<String, PresenceState>>

    /** Exact username lookup; there is no fuzzy search over the whole player base. */
    suspend fun findByUsername(username: String): Outcome<UserProfile?>

    /**
     * Applies a relationship change to both players in one atomic update, so the two sides
     * can never disagree about whether they are friends.
     */
    suspend fun applyFriendshipAction(
        userId: String,
        otherUserId: String,
        action: FriendshipAction,
    ): Outcome<Unit>

    /** Invites a friend into a room. Rejected server side unless they are a friend. */
    suspend fun sendInvite(
        fromUserId: String,
        toUserId: String,
        roomCode: String,
    ): Outcome<Unit>

    suspend fun dismissInvite(userId: String, inviteId: String): Outcome<Unit>

    /**
     * Marks the player online and registers the disconnect handler that clears it, so a
     * crash or a dead network still leaves an accurate state behind.
     */
    fun startPresence(userId: String)

    suspend fun clearPresence(userId: String)

    /** Removes the player's social data. Part of account deletion. */
    suspend fun deleteSocialData(userId: String): Outcome<Unit>
}
