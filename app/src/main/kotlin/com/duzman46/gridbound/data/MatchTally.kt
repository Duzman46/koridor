package com.duzman46.gridbound.data

import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.PlayerId

/**
 * What one finished match adds to a player's own record.
 *
 * Pulled out of the data store's edit block so the three rules that were wrong in there can be
 * read and tested without a device:
 *
 * - **A local two-player match settles nothing of yours.** Somebody won, but both players were
 *   on this phone, so there is no "you" for the result to belong to.
 * - **An online match is not a match against the machine.** Every online route carries
 *   [com.duzman46.gridbound.game.models.Difficulty.MEDIUM] because a route argument has to say
 *   something, and the difficulty tallies were counting it — so a player who had never met the
 *   bot could be holding a pile of wins over it.
 * - **A streak is a run of your own results**, which is the same two rules again: local matches
 *   neither extend nor break one.
 */
data class MatchTally(
    /** Whether this match settles a win or a loss for the player. */
    val countsAsResult: Boolean,
    val won: Boolean,
    val online: Boolean,
) {
    /** Only a match against the bot belongs in the per-difficulty tallies. */
    val touchesDifficulty: Boolean get() = countsAsResult && !online
}

fun tallyOf(mode: GameMode, winner: PlayerId, localPlayer: PlayerId): MatchTally = when (mode) {
    GameMode.LOCAL_TWO_PLAYER -> MatchTally(countsAsResult = false, won = false, online = false)
    else -> MatchTally(
        countsAsResult = true,
        won = winner == localPlayer,
        online = mode == GameMode.ONLINE,
    )
}
