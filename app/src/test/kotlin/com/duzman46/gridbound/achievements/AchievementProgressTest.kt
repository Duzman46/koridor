package com.duzman46.gridbound.achievements

import com.duzman46.gridbound.achievements.domain.Achievement
import com.duzman46.gridbound.achievements.domain.AchievementState
import com.duzman46.gridbound.achievements.domain.SWIFT_WIN_TURNS
import com.duzman46.gridbound.achievements.domain.achievementStates
import com.duzman46.gridbound.data.tallyOf
import com.duzman46.gridbound.domain.models.GameStatistics
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.PlayerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementProgressTest {

    private fun states(
        statistics: GameStatistics = GameStatistics(),
        tutorialCompleted: Boolean = false,
    ): Map<Achievement, AchievementState> =
        achievementStates(statistics, tutorialCompleted).associateBy { it.achievement }

    @Test
    fun `a new player has earned nothing and can see every badge`() {
        val states = states()
        assertEquals(Achievement.entries.size, states.size)
        assertTrue(states.values.none(AchievementState::unlocked))
        assertTrue(states.values.all { it.progress == 0 })
    }

    @Test
    fun `beating the expert bot earns only the expert badge`() {
        val states = states(
            GameStatistics(
                totalGames = 1,
                totalWins = 1,
                winsByDifficulty = mapOf(Difficulty.EXPERT to 1),
            ),
        )
        assertTrue(states.getValue(Achievement.BOT_EXPERT).unlocked)
        assertFalse(states.getValue(Achievement.BOT_HARD).unlocked)
        assertFalse(states.getValue(Achievement.BOT_MEDIUM).unlocked)
        // The first two rungs of the career ladders come with it; the network's do not.
        assertTrue(states.getValue(Achievement.FIRST_WIN).unlocked)
        assertFalse(states.getValue(Achievement.ONLINE_DEBUT).unlocked)
        assertFalse(states.getValue(Achievement.ONLINE_WIN).unlocked)
    }

    /**
     * The bug the extra counters exist to fix: every online route carries MEDIUM, so before this
     * a player who had never opened the bot screen could hold a stack of wins over the machine.
     */
    @Test
    fun `an online match is not a match against the machine`() {
        val tally = tallyOf(GameMode.ONLINE, PlayerId.PLAYER_ONE, PlayerId.PLAYER_ONE)
        assertTrue(tally.countsAsResult)
        assertTrue(tally.won)
        assertTrue(tally.online)
        assertFalse(tally.touchesDifficulty)

        val states = states(GameStatistics(totalGames = 3, totalWins = 3, onlineGames = 3, onlineWins = 3))
        assertTrue(states.getValue(Achievement.ONLINE_WIN).unlocked)
        assertFalse(states.getValue(Achievement.BOT_MEDIUM).unlocked)
    }

    @Test
    fun `a match on one phone settles no result of the player's own`() {
        val tally = tallyOf(GameMode.LOCAL_TWO_PLAYER, PlayerId.PLAYER_TWO, PlayerId.PLAYER_ONE)
        assertFalse(tally.countsAsResult)
        assertFalse(tally.touchesDifficulty)

        val states = states(GameStatistics(totalGames = 1, localGames = 1))
        assertTrue(states.getValue(Achievement.FACE_TO_FACE).unlocked)
        assertTrue(states.getValue(Achievement.FIRST_STEP).unlocked)
        assertFalse(states.getValue(Achievement.FIRST_WIN).unlocked)
    }

    @Test
    fun `a stored zero is no win at all rather than the fastest one`() {
        assertFalse(states(GameStatistics(fastestWinTurns = 0)).getValue(Achievement.SWIFT).unlocked)
        assertTrue(
            states(GameStatistics(fastestWinTurns = SWIFT_WIN_TURNS))
                .getValue(Achievement.SWIFT).unlocked,
        )
        assertFalse(
            states(GameStatistics(fastestWinTurns = SWIFT_WIN_TURNS + 1))
                .getValue(Achievement.SWIFT).unlocked,
        )
    }

    @Test
    fun `the streak badges read the best run rather than the one standing`() {
        val states = states(GameStatistics(currentStreak = 0, bestStreak = 4))
        assertTrue(states.getValue(Achievement.STREAK_THREE).unlocked)
        assertFalse(states.getValue(Achievement.STREAK_FIVE).unlocked)
        assertEquals(4, states.getValue(Achievement.STREAK_FIVE).progress)
        assertEquals(0.8f, states.getValue(Achievement.STREAK_FIVE).fraction, 0.001f)
    }

    @Test
    fun `a counter past its target never prints more than the target`() {
        val states = states(GameStatistics(totalGames = 500, totalWins = 500))
        val veteran = states.getValue(Achievement.VETERAN)
        assertTrue(veteran.unlocked)
        assertEquals(50, veteran.shown)
        assertEquals(1f, veteran.fraction, 0.001f)
    }

    @Test
    fun `the tutorial badge follows the lesson rather than any match`() {
        assertFalse(states().getValue(Achievement.SCHOLAR).unlocked)
        assertTrue(states(tutorialCompleted = true).getValue(Achievement.SCHOLAR).unlocked)
    }

    /** A badge nobody can reach is a badge that should not be in the catalogue. */
    @Test
    fun `every badge has a target that can be reached`() {
        Achievement.entries.forEach { achievement ->
            assertTrue(achievement.name, achievement.target >= 1)
            assertTrue(achievement.name, achievement.quoted >= 1)
        }
    }

    /** Two badges with the same title would look like the same badge twice. */
    @Test
    fun `no two badges share a title`() {
        val titles = Achievement.entries.map(Achievement::titleRes)
        assertEquals(titles.size, titles.toSet().size)
    }
}
