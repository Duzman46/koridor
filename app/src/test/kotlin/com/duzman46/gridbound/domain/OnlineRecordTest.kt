package com.duzman46.gridbound.domain

import com.duzman46.gridbound.data.tallyOf
import com.duzman46.gridbound.domain.models.GameStatistics
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.PlayerId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The statistics screen reads online losses that nothing ever stored.
 *
 * `GameStatistics.onlineLosses` is `onlineGames - onlineWins`, and that is only exact while two
 * things hold: every counted online match lands on exactly one of the won/lost branches in
 * `DefaultGameRepository.recordCompletedGame`, and a local match reaches neither. Both are
 * properties of `tallyOf`, so they are checked here against the rule itself rather than against
 * a comment — the day a draw is added, the first test below fails and the derivation has to
 * become a real counter.
 */
class OnlineRecordTest {

    @Test
    fun `every online result is a win or a loss and nothing in between`() {
        val online = GameMode.ONLINE
        val asWinner = tallyOf(online, PlayerId.PLAYER_ONE, PlayerId.PLAYER_ONE)
        val asLoser = tallyOf(online, PlayerId.PLAYER_TWO, PlayerId.PLAYER_ONE)

        assertEquals("an online match always settles a result", true, asWinner.countsAsResult)
        assertEquals("an online match always settles a result", true, asLoser.countsAsResult)
        assertEquals(true, asWinner.won)
        assertEquals(false, asLoser.won)
        // Won and lost are the only two outcomes, so games = wins + losses exactly.
        assertEquals(
            "the two outcomes must be opposites, or a third state exists",
            asWinner.won,
            !asLoser.won,
        )
    }

    @Test
    fun `a local match never reaches the online counters`() {
        val tally = tallyOf(GameMode.LOCAL_TWO_PLAYER, PlayerId.PLAYER_ONE, PlayerId.PLAYER_ONE)
        assertEquals("two people on one phone settle nobody's record", false, tally.countsAsResult)
        assertEquals(false, tally.online)
    }

    @Test
    fun `a bot match counts as a result but not as an online one`() {
        val tally = tallyOf(GameMode.VS_AI, PlayerId.PLAYER_ONE, PlayerId.PLAYER_ONE)
        assertEquals(true, tally.countsAsResult)
        assertEquals("the bot is not the network", false, tally.online)
        assertEquals("and only the bot feeds the difficulty ladder", true, tally.touchesDifficulty)
    }

    @Test
    fun `online losses are the remainder of online games`() {
        val statistics = GameStatistics(onlineGames = 9, onlineWins = 5)
        assertEquals(4, statistics.onlineLosses)
        assertEquals(5f / 9f, statistics.onlineWinRate, 0.0001f)
    }

    @Test
    fun `an untouched record reports no rate rather than dividing by zero`() {
        val statistics = GameStatistics()
        assertEquals(0, statistics.onlineLosses)
        assertEquals(0f, statistics.onlineWinRate, 0f)
        assertEquals(0, statistics.botGames)
    }

    /**
     * A stored pair can only disagree with the derivation by going backwards, which would mean a
     * win was recorded without its game. Clamping is the honest answer to a state that cannot
     * happen: a negative loss count on the screen would be worse than a zero.
     */
    @Test
    fun `losses never go negative`() {
        assertEquals(0, GameStatistics(onlineGames = 2, onlineWins = 5).onlineLosses)
    }

    @Test
    fun `bot games are the sum of both difficulty ladders`() {
        val statistics = GameStatistics(
            winsByDifficulty = mapOf(Difficulty.EASY to 2, Difficulty.HARD to 1),
            lossesByDifficulty = mapOf(Difficulty.EXPERT to 3),
        )
        assertEquals(6, statistics.botGames)
    }
}
