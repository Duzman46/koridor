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

/**
 * Why one player is reporting another.
 *
 * A closed list rather than a box to type in, for the same reason the in-match messages are a
 * closed list: free text about another player is itself content nobody is moderating, and the
 * operator does not need a paragraph to act — they need the account and the category.
 *
 * The two fields a player can type and a stranger can read are the username and the room name,
 * so both have a reason of their own. Cheating is the third thing players actually report, and
 * the fourth is there because a list of three would send everything else nowhere.
 */
enum class ContentReportReason {
    OFFENSIVE_NAME,
    OFFENSIVE_ROOM_NAME,
    CHEATING,
    OTHER,
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

/**
 * What an entry on the live request channel is asking for.
 *
 * An invitation and a rematch both reach a player who is somewhere else in the app and are
 * both answered with yes or no, so they travel together rather than on a channel each: one
 * listener, one bar, and no way for a request to arrive somewhere nobody is watching.
 */
enum class RequestKind {
    /** A friend has a room open and wants you in it. */
    GAME_INVITE,

    /** The player from the match that has just ended wants to play it again. */
    REMATCH,

    /**
     * A rematch answered with no.
     *
     * It rides the same channel because it is the only way the asker hears anything at all:
     * the entry they sent lives in the opponent's box, which they are not allowed to read.
     */
    REMATCH_DECLINED,
    ;

    /** True when the entry puts a question to the player, which is what the bar is for. */
    val isAsk: Boolean get() = this != REMATCH_DECLINED
}

/**
 * One entry on the channel a player listens to wherever they are in the app.
 *
 * Stored under the recipient and keyed by the sender, so asking twice refreshes a single entry
 * instead of stacking up on someone's screen.
 */
data class PlayerRequest(
    val fromUserId: String,
    val fromUsername: String,
    val kind: RequestKind,
    /** Where to go on accept. On a decline, the room the asker opened and may now close. */
    val roomCode: String,
    /**
     * The finished match the two of them just played. Empty on an invitation; on a rematch it
     * is what the server checks in place of friendship, because having just played someone is
     * the whole licence to ask them again.
     */
    val playedRoomCode: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
) {
    fun isExpired(now: Long): Boolean = expiresAt in 1..now
}
