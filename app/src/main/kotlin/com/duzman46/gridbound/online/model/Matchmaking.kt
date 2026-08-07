package com.duzman46.gridbound.online.model

import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.Constants
import kotlin.math.abs

/**
 * One player waiting to be paired.
 *
 * [queuedAt] is stamped by the server and never rewritten, which is what lets two phones
 * reading the same list reach the same conclusion without talking to each other.
 */
data class MatchmakingEntry(
    val userId: String,
    val rating: Int,
    /** False for a guest, whose result cannot move a rating. A pair is ranked only if both are. */
    val ranked: Boolean,
    val queuedAt: Long,
)

/** What a matchmaking session reports while it runs. */
sealed interface MatchmakingState {
    /** In the list, with nobody to pair with yet. */
    data object Searching : MatchmakingState

    /** A room exists and holds both players. */
    data class Paired(val session: OnlineSession) : MatchmakingState

    /** The list could not be joined. The session is over. */
    data class Failed(val error: AppError) : MatchmakingState
}

/**
 * Who pairs with whom, decided identically on every phone reading the list.
 *
 * There is no server in this loop, so agreement has to come from the data: every entry is
 * immutable once written, so two devices looking at the same list compute the same answer.
 * Two conditions make that safe.
 *
 * The first is that a pair must be *mutual* — each the other's nearest by rating. Picking your
 * own nearest and writing is not enough: with ratings 1000, 1005 and 1010 the outer two both
 * consider the middle one nearest, and both would drag them into a match. Mutual choice also
 * settles the three-player chain, where the middle player would otherwise be claimed by one
 * neighbour while claiming the other.
 *
 * The second is that only one of the two writes — the one who queued later, which is the
 * device that has just looked. The other is already watching and does nothing.
 *
 * Neither survives a device reading a stale list, so neither is the last line: the room a
 * pairing lands in is named after the player being claimed, so two devices that both go for
 * the same person write the same node and the transaction there decides between them.
 */
object MatchmakingRules {

    /**
     * The player this device should pair with, or null when it should keep waiting.
     *
     * @param waiting the whole list, including this player's own entry.
     */
    fun partnerFor(userId: String, waiting: List<MatchmakingEntry>): MatchmakingEntry? {
        val self = waiting.firstOrNull { it.userId == userId } ?: return null
        // Ages are measured against this player's own stamp rather than the handset's clock.
        // Both were written by the server, so a phone that believes it is next Tuesday still
        // discounts exactly the entries everyone else does — and claiming one of them would
        // start a match against a device that stopped listening minutes ago.
        val live = waiting.filter {
            self.queuedAt - it.queuedAt < Constants.Online.MATCHMAKING_STALE_MILLIS
        }
        val mine = nearestTo(self, live) ?: return null
        if (nearestTo(mine, live)?.userId != userId) return null
        return mine.takeIf { opens(self, it) }
    }

    /**
     * The closest rating to [entry], with ties broken by a total order both phones share so
     * that "nearest" is never ambiguous.
     */
    private fun nearestTo(
        entry: MatchmakingEntry,
        waiting: List<MatchmakingEntry>,
    ): MatchmakingEntry? = waiting
        .filter { it.userId != entry.userId }
        .minWithOrNull(
            compareBy<MatchmakingEntry> { abs(it.rating - entry.rating) }
                .thenBy(MatchmakingEntry::queuedAt)
                .thenBy(MatchmakingEntry::userId),
        )

    /** True when [self] is the half of the pair that writes the room. */
    private fun opens(self: MatchmakingEntry, other: MatchmakingEntry): Boolean =
        if (self.queuedAt != other.queuedAt) {
            self.queuedAt > other.queuedAt
        } else {
            self.userId > other.userId
        }
}
