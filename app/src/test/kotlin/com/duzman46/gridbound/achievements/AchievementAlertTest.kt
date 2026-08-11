package com.duzman46.gridbound.achievements

import com.duzman46.gridbound.achievements.domain.Achievement
import com.duzman46.gridbound.data.StatisticsManager
import com.duzman46.gridbound.domain.models.GameStatistics
import com.duzman46.gridbound.presentation.achievements.AchievementAlertViewModel
import com.duzman46.gridbound.session.FakeAuthRepository
import com.duzman46.gridbound.session.FakeGameRepository
import com.duzman46.gridbound.session.FakeSocialRepository
import com.duzman46.gridbound.session.FakeUserProfileRepository
import com.duzman46.gridbound.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * When the badge notice speaks, and — more importantly — when it keeps quiet.
 *
 * The interesting case is the update itself. Badges are worked out from statistics that already
 * exist, so the first launch after this feature ships would otherwise congratulate a player on a
 * dozen matches they played weeks ago.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AchievementAlertTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val game = FakeGameRepository()
    private val auth = FakeAuthRepository()
    private val profiles = FakeUserProfileRepository()
    private val social = FakeSocialRepository()
    private var managerScope: CoroutineScope? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        managerScope?.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `an install that predates the shelf is awarded everything and told nothing`() =
        runTest(dispatcher) {
            game.setStatistics(GameStatistics(totalGames = 30, totalWins = 12))
            val model = alert()
            advanceUntilIdle()

            assertEquals(emptyList<Achievement>(), model.fresh.value)
            val seen = game.seenBadges
            assertTrue("nothing was recorded as seen", seen != null && seen.isNotEmpty())
            assertTrue(Achievement.FIRST_WIN.name in seen!!)
            assertTrue(Achievement.REGULAR.name in seen)
        }

    @Test
    fun `a badge won after that is announced once`() = runTest(dispatcher) {
        game.setStatistics(GameStatistics())
        val model = alert()
        advanceUntilIdle()
        // The catch-up pass has run and recorded an empty shelf, so this player is new rather
        // than pre-existing and the first badge is genuinely news.
        assertEquals(emptySet<String>(), game.seenBadges)

        game.setStatistics(GameStatistics(totalGames = 1, totalWins = 1))
        advanceUntilIdle()

        assertTrue(Achievement.FIRST_WIN in model.fresh.value)
        assertTrue(Achievement.FIRST_STEP in model.fresh.value)

        // Marked seen as it was announced, so nothing repeats when the statistics next change.
        model.dismiss()
        game.setStatistics(GameStatistics(totalGames = 2, totalWins = 1))
        advanceUntilIdle()
        assertEquals(emptyList<Achievement>(), model.fresh.value)
    }

    private suspend fun TestScope.alert(): AchievementAlertViewModel {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        managerScope = scope
        val session = SessionManager(auth, profiles, social, game, scope)
        advanceUntilIdle()
        return AchievementAlertViewModel(StatisticsManager(game), session, game)
    }
}
