package com.duzman46.gridbound.online

import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.online.data.MatchmakingCodec
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a queue entry says the player's rating is.
 *
 * `database.rules.json` refuses any entry whose rating is not exactly `users/{uid}/rating`, so
 * this is not a display value with a sensible default — it is the difference between joining the
 * queue and a refused write, which reaches the player as "something went wrong" the moment they
 * press Quick match.
 *
 * The reported symptom was that it happened *intermittently*, and that is the shape of the bug:
 * the profile read is allowed to fail, the rating was then invented as the starting value, and
 * that guess is correct only until somebody's rating moves. On the owner's own database all four
 * accounts sat at 1020, 980, 964 and 1036 — every one of them a refused write on any press where
 * that read had not landed.
 */
class QueueRatingTest {

    @Test
    fun `the profile's rating is what goes in`() {
        assertEquals(1020, MatchmakingCodec.queueRating(profileRating = 1020, storedRating = 1020))
    }

    @Test
    fun `a failed profile read falls back to the stored rating, never to the starting one`() {
        // The regression. Answering 1000 here is what the rules refuse.
        assertEquals(964, MatchmakingCodec.queueRating(profileRating = null, storedRating = 964))
    }

    @Test
    fun `the starting rating is only for a player who has none anywhere`() {
        assertEquals(
            Constants.Backend.STARTING_RATING,
            MatchmakingCodec.queueRating(profileRating = null, storedRating = null),
        )
    }

    @Test
    fun `a rating the profile carries is never overridden by a stale read`() {
        // Both are the same node, so they can only disagree if one is out of date. The profile
        // is the one that was read for this attempt, so it wins.
        assertEquals(1036, MatchmakingCodec.queueRating(profileRating = 1036, storedRating = 1000))
    }
}
