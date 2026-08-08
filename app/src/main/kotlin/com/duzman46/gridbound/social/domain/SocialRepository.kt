package com.duzman46.gridbound.social.domain

import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.profile.domain.UserProfile
import kotlinx.coroutines.flow.Flow

interface SocialRepository {
    /** Every relationship the player has, keyed by the other player's id. */
    fun observeFriendships(userId: String): Flow<List<Friend>>

    /**
     * The live request channel: everything anyone is currently asking this player, in one
     * stream, so a screen that wants to react to any of it subscribes once.
     */
    fun observeRequests(userId: String): Flow<List<PlayerRequest>>

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
        fromUsername: String,
        toUserId: String,
        roomCode: String,
    ): Outcome<Unit>

    /**
     * Asks the player from a finished match to play it again in a room that has already been
     * opened for them.
     *
     * @param roomCode the new room, waiting for them to join.
     * @param playedRoomCode the match the two of them just finished. The server accepts this
     *   between strangers, so it insists on seeing that they really did play it.
     */
    suspend fun sendRematch(
        fromUserId: String,
        fromUsername: String,
        toUserId: String,
        roomCode: String,
        playedRoomCode: String,
    ): Outcome<Unit>

    /**
     * Answers a rematch with no. Sent back down the channel because the asker cannot read the
     * entry they sent, so this is the only thing they can be told.
     */
    suspend fun declineRematch(
        fromUserId: String,
        fromUsername: String,
        toUserId: String,
        roomCode: String,
        playedRoomCode: String,
    ): Outcome<Unit>

    /**
     * Removes one entry from the channel. Either end may do it — the recipient once they have
     * answered, the sender once they have stopped waiting for an answer — because it is the
     * same node either way and nobody but those two can reach it.
     */
    suspend fun clearRequest(recipientId: String, senderId: String): Outcome<Unit>

    /**
     * Reports another player's content to the operator.
     *
     * The app shows two things one player typed to players who have never met them — the
     * username and the room name — which is what Play means by user-generated content, and
     * what obliges the app to carry a way of reporting it. It goes to a node no client can
     * read, so the account being reported cannot see it, cannot answer it and cannot delete
     * it; the operator reads them out of the database console.
     *
     * Keyed by the pair, so reporting the same player twice refreshes one row rather than
     * filling the node with the same complaint.
     *
     * @param roomCode where the content was seen, when it was a room name. Empty otherwise.
     */
    suspend fun reportPlayer(
        reporterId: String,
        subjectId: String,
        reason: ContentReportReason,
        roomCode: String = "",
    ): Outcome<Unit>

    /**
     * Marks the player online and registers the disconnect handler that clears it, so a
     * crash or a dead network still leaves an accurate state behind.
     */
    fun startPresence(userId: String)

    suspend fun clearPresence(userId: String)

    /** Removes the player's social data. Part of account deletion. */
    suspend fun deleteSocialData(userId: String): Outcome<Unit>
}
