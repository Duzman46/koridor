package com.duzman46.gridbound.social.domain

/** The relationship between the signed-in player and someone else, from their point of view. */
enum class FriendshipStatus {
    NONE,
    REQUEST_SENT,
    REQUEST_RECEIVED,
    FRIENDS,
    BLOCKED,
}

/** Something a player can do to a relationship. */
enum class FriendshipAction {
    SEND_REQUEST,
    ACCEPT,
    DECLINE,
    CANCEL,
    REMOVE,
    BLOCK,
    UNBLOCK,
}

/**
 * Both halves of a relationship after an action.
 *
 * Friendship is stored twice, once under each player, so a list can be read without a join.
 * The two entries are always written in a single atomic update, and this type is what makes
 * "always in step" checkable rather than merely intended.
 */
data class FriendshipUpdate(
    val mine: FriendshipStatus,
    val theirs: FriendshipStatus,
)

/**
 * The friendship state machine. Pure and total: every action either produces both sides of
 * the new state or is rejected, so an illegal transition cannot reach the database.
 */
object FriendshipRules {

    fun apply(
        action: FriendshipAction,
        mine: FriendshipStatus,
        theirs: FriendshipStatus,
    ): FriendshipUpdate? {
        // A player I have blocked can never be acted on except by unblocking.
        if (mine == FriendshipStatus.BLOCKED && action != FriendshipAction.UNBLOCK) return null
        // Someone who blocked me must not learn that: every action simply fails.
        if (theirs == FriendshipStatus.BLOCKED && action != FriendshipAction.BLOCK) return null

        return when (action) {
            FriendshipAction.SEND_REQUEST -> when (mine) {
                FriendshipStatus.NONE ->
                    FriendshipUpdate(FriendshipStatus.REQUEST_SENT, FriendshipStatus.REQUEST_RECEIVED)

                // They already asked; treating a second request as acceptance is what the
                // player means and avoids a stuck pair of crossed requests.
                FriendshipStatus.REQUEST_RECEIVED ->
                    FriendshipUpdate(FriendshipStatus.FRIENDS, FriendshipStatus.FRIENDS)

                else -> null
            }

            FriendshipAction.ACCEPT -> when (mine) {
                FriendshipStatus.REQUEST_RECEIVED ->
                    FriendshipUpdate(FriendshipStatus.FRIENDS, FriendshipStatus.FRIENDS)

                else -> null
            }

            FriendshipAction.DECLINE -> when (mine) {
                FriendshipStatus.REQUEST_RECEIVED ->
                    FriendshipUpdate(FriendshipStatus.NONE, FriendshipStatus.NONE)

                else -> null
            }

            FriendshipAction.CANCEL -> when (mine) {
                FriendshipStatus.REQUEST_SENT ->
                    FriendshipUpdate(FriendshipStatus.NONE, FriendshipStatus.NONE)

                else -> null
            }

            FriendshipAction.REMOVE -> when (mine) {
                FriendshipStatus.FRIENDS ->
                    FriendshipUpdate(FriendshipStatus.NONE, FriendshipStatus.NONE)

                else -> null
            }

            // Blocking always works, from any state, and clears whatever they had on me.
            FriendshipAction.BLOCK ->
                if (mine == FriendshipStatus.BLOCKED) null
                else FriendshipUpdate(FriendshipStatus.BLOCKED, FriendshipStatus.NONE)

            FriendshipAction.UNBLOCK -> when (mine) {
                FriendshipStatus.BLOCKED ->
                    FriendshipUpdate(FriendshipStatus.NONE, FriendshipStatus.NONE)

                else -> null
            }
        }
    }

    /** Whether the other player is allowed to reach me with an invite or a request. */
    fun canReceiveFrom(mine: FriendshipStatus): Boolean = mine != FriendshipStatus.BLOCKED

    /** Only confirmed friends may be invited into a room. */
    fun canInvite(mine: FriendshipStatus): Boolean = mine == FriendshipStatus.FRIENDS
}

/** A row in the friends list. */
data class Friend(
    val userId: String,
    val username: String,
    val avatarId: String,
    val rating: Int,
    val status: FriendshipStatus,
    val presence: PresenceState = PresenceState.OFFLINE,
)

/**
 * Coarse presence only. No location, no last-seen timestamp shown to others: a player's
 * habits are not something the game needs to publish.
 */
enum class PresenceState {
    ONLINE,
    OFFLINE,
}

/** An invitation into a specific room. */
data class GameInvite(
    val inviteId: String,
    val fromUserId: String,
    val fromUsername: String,
    val roomCode: String,
    val createdAt: Long,
    val expiresAt: Long,
) {
    fun isExpired(now: Long): Boolean = expiresAt in 1..now
}
