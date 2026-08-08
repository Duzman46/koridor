package com.duzman46.gridbound.ui.screens

import com.duzman46.gridbound.social.domain.Friend
import com.duzman46.gridbound.social.domain.FriendshipStatus
import com.duzman46.gridbound.social.domain.PlayerRequest
import com.duzman46.gridbound.social.domain.RequestKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * That an invitation and a friendship from the same player are two rows and not one key.
 *
 * They necessarily coexist: the rules charge friendship for a game invitation, so a friend
 * inviting a friend produces both, about the same user id, in the same list. A LazyColumn
 * keys every `items` block into one space and answers a duplicate by throwing out of the
 * measure pass — so this was not a rendering glitch but the app closing, on the one screen an
 * invitation can be answered from, for as long as the invitation stood.
 */
class FriendsRowKeyTest {

    private val uid = "alice-uid"

    private val request = PlayerRequest(
        fromUserId = uid,
        fromUsername = "alice",
        kind = RequestKind.GAME_INVITE,
        roomCode = "AB3D5F",
    )

    private val friend = Friend(
        userId = uid,
        username = "alice",
        avatarId = "avatar_01",
        rating = 1000,
        status = FriendshipStatus.FRIENDS,
    )

    @Test
    fun `an invitation and a friendship from one player are different rows`() {
        assertNotEquals(FriendsRowKey.invite(request), FriendsRowKey.friend(friend))
    }

    @Test
    fun `a rematch collides with nothing either`() {
        val rematch = request.copy(kind = RequestKind.REMATCH)
        assertNotEquals(FriendsRowKey.invite(rematch), FriendsRowKey.friend(friend))
    }

    @Test
    fun `two different players never share a key`() {
        val other = friend.copy(userId = "bob-uid")
        assertNotEquals(FriendsRowKey.friend(friend), FriendsRowKey.friend(other))
        assertNotEquals(
            FriendsRowKey.invite(request),
            FriendsRowKey.invite(request.copy(fromUserId = "bob-uid")),
        )
    }

    @Test
    fun `a row keeps its identity as the relationship moves between sections`() {
        // The friend sections are a partition of one status field, so a player crossing from
        // "incoming request" to "online friend" is the same row moving, not a new one.
        assertEquals(
            FriendsRowKey.friend(friend),
            FriendsRowKey.friend(friend.copy(status = FriendshipStatus.REQUEST_RECEIVED)),
        )
    }
}
