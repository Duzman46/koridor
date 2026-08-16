package com.duzman46.gridbound.presentation.game

import android.content.ContextWrapper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.data.SettingsManager
import com.duzman46.gridbound.data.StatisticsManager
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.domain.models.AppSettings
import com.duzman46.gridbound.domain.models.GameStatistics
import com.duzman46.gridbound.domain.models.ThemeMode
import com.duzman46.gridbound.domain.repository.GameRepository
import com.duzman46.gridbound.game.ai.AIActionGenerator
import com.duzman46.gridbound.game.ai.AIEngineFactory
import com.duzman46.gridbound.game.ai.EasyAI
import com.duzman46.gridbound.game.ai.MediumAI
import com.duzman46.gridbound.game.ai.SearchAI
import com.duzman46.gridbound.game.ai.search.SearchClock
import com.duzman46.gridbound.game.ai.search.SearchConfig
import com.duzman46.gridbound.game.animation.AnimationManager
import com.duzman46.gridbound.game.audio.HapticsManager
import com.duzman46.gridbound.game.audio.SoundManager
import com.duzman46.gridbound.game.board.BoardGraph
import com.duzman46.gridbound.game.engine.GameEngine
import com.duzman46.gridbound.game.engine.GameManager
import com.duzman46.gridbound.game.engine.TurnManager
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.pathfinding.AStarPathFinder
import com.duzman46.gridbound.game.pathfinding.BFSValidator
import com.duzman46.gridbound.game.rules.MoveValidator
import com.duzman46.gridbound.game.rules.RuleEngine
import com.duzman46.gridbound.game.rules.VictoryChecker
import com.duzman46.gridbound.game.rules.WallValidator
import com.duzman46.gridbound.match.domain.MatchReport
import com.duzman46.gridbound.match.domain.MatchRepository
import com.duzman46.gridbound.match.domain.RecentMatch
import com.duzman46.gridbound.online.FakeOnlineGameRepository
import com.duzman46.gridbound.online.model.OnlineRoomStatus
import com.duzman46.gridbound.session.FakeUserProfileRepository
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * What a finished match owes the player's record, and whether it still owes it once the screen
 * the match was played on has gone.
 *
 * The two are the same instant. The state change that ends a match is the one the victory screen
 * watches, and the navigation it triggers pops the board with `inclusive = true` — so the board's
 * view model is cleared, and its scope cancelled, while the recording coroutine is still in the
 * middle of asking DataStore to write. DataStore applies its transform through
 * `withContext(callerContext)`, which means a cancelled caller does not fail: it simply never
 * writes. Nothing goes wrong, nothing is logged, and the match is never counted.
 *
 * [RecordingGameRepository] is built to have the same shape: the write only lands if the
 * coroutine that asked for it is still alive when the store gets round to it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = RecordingGameRepository()
    private val online = FakeOnlineGameRepository()
    private val matches = RecordingMatchRepository()

    /** Stands in for the process-lifetime scope Hilt provides; see `CoroutineModule`. */
    private val applicationScope = CoroutineScope(SupervisorJob() + dispatcher)

    /**
     * The real thing a navigation pop reaches for. Clearing a store is what clears the view
     * models in it, so a test that wants the production teardown asks for it the production way
     * rather than reaching into the view model.
     */
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        store.clear()
        applicationScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `a finished match is counted even though the board it was played on is already gone`() =
        runTest(dispatcher) {
            viewModel()
            finishMatch(version = 1L)

            // The pop the victory screen performs, on the same state change that just ended the
            // match. The recording is mid-flight at this point, exactly as on a device.
            store.clear()

            repository.letTheWriteThrough()
            advanceUntilIdle()

            assertEquals(listOf(PlayerId.PLAYER_ONE), repository.recorded)
        }

    @Test
    fun `and the record it leaves is the match, not the number of times the room said so`() =
        runTest(dispatcher) {
            // A finished room republishes itself — a rating update, a rematch request landing,
            // the opponent's client writing anything at all — and every republication arrives
            // here as another finished match.
            repository.letTheWriteThrough()
            viewModel()

            finishMatch(version = 1L)
            finishMatch(version = 2L)
            advanceUntilIdle()

            assertEquals(listOf(PlayerId.PLAYER_ONE), repository.recorded)
        }

    /** Publishes a finished room this device won, the way the database would deliver one. */
    private fun finishMatch(version: Long) {
        online.rooms.value = online.waitingRoom(OnlineRoomStatus.FINISHED).copy(
            guestUserId = GUEST_ID,
            winnerUserId = HOST_ID,
            version = version,
        )
    }

    private fun viewModel(): GameViewModel {
        val boardGraph = BoardGraph()
        val moveValidator = MoveValidator(boardGraph)
        val wallValidator = WallValidator(BFSValidator(boardGraph))
        val pathFinder = AStarPathFinder(boardGraph)
        val actionGenerator = AIActionGenerator(moveValidator, wallValidator, pathFinder)
        val gameEngine = GameEngine(
            RuleEngine(moveValidator, wallValidator),
            VictoryChecker(),
            TurnManager(),
        )
        val model = GameViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "mode" to GameMode.ONLINE.name,
                    "difficulty" to Difficulty.MEDIUM.name,
                    "roomCode" to ROOM_CODE,
                    "userId" to HOST_ID,
                    "playerId" to PlayerId.PLAYER_ONE.name,
                ),
            ),
            gameManager = GameManager(gameEngine, moveValidator, wallValidator),
            aiEngineFactory = AIEngineFactory(
                easyAI = EasyAI(actionGenerator, Random(1)),
                mediumAI = MediumAI(actionGenerator, gameEngine, pathFinder),
                hardAI = searchAI(actionGenerator, gameEngine, pathFinder, SearchConfig.HARD),
                expertAI = searchAI(actionGenerator, gameEngine, pathFinder, SearchConfig.EXPERT),
            ),
            settingsManager = SettingsManager(repository),
            statisticsManager = StatisticsManager(repository),
            soundManager = SoundManager(),
            // Never touched on this path — the board vibrates from the screen, not from here —
            // but the view model holds one, and a JVM test has no Context to give it a vibrator
            // through.
            hapticsManager = HapticsManager(ContextWrapper(null)),
            animationManager = AnimationManager(),
            onlineRepository = online,
            matchRepository = matches,
            profileRepository = FakeUserProfileRepository(),
            applicationScope = applicationScope,
        )
        store.put(STORE_KEY, model)
        return model
    }

    private fun searchAI(
        actionGenerator: AIActionGenerator,
        gameEngine: GameEngine,
        pathFinder: AStarPathFinder,
        config: SearchConfig,
    ) = SearchAI(actionGenerator, gameEngine, pathFinder, config, SearchClock.SYSTEM)

    private companion object {
        const val ROOM_CODE = "AB3D5F"
        const val HOST_ID = "alice-uid"
        const val GUEST_ID = "bob-uid"
        const val STORE_KEY = "game"
    }
}

/**
 * A settings and statistics store whose write can be held open.
 *
 * The gate is the whole point. A repository that recorded synchronously would pass whether the
 * recording coroutine survived the screen or not, because there would be no window in which to
 * kill it — and that window is the defect. Holding the write suspended puts the test inside the
 * moment DataStore is inside when the navigation lands.
 *
 * Sound and haptics are off because a JVM test has no audio device: `SoundManager` would reach
 * for a `ToneGenerator` and log the refusal through `android.util.Log`, which is not mocked here.
 */
private class RecordingGameRepository : GameRepository {
    private val settingsState = MutableStateFlow(
        AppSettings(soundEnabled = false, hapticsEnabled = false),
    )
    private val statisticsState = MutableStateFlow(GameStatistics())
    private val gate = CompletableDeferred<Unit>()

    /** The winner of every match that actually reached the store, in the order they landed. */
    val recorded = mutableListOf<PlayerId>()

    override val settings: Flow<AppSettings> = settingsState
    override val statistics: Flow<GameStatistics> = statisticsState
    override val tutorialCompleted: Flow<Boolean> = MutableStateFlow(true)
    override val guestModeAccepted: Flow<Boolean> = MutableStateFlow(true)
    override val usernameChosen: Flow<Boolean> = MutableStateFlow(true)
    override val seenAchievements: Flow<Set<String>?> = MutableStateFlow(emptySet())

    fun letTheWriteThrough() {
        gate.complete(Unit)
    }

    override suspend fun recordCompletedGame(
        mode: GameMode,
        difficulty: Difficulty,
        winner: PlayerId,
        localPlayer: PlayerId,
        turnsPlayed: Int,
        winTurns: Int?,
    ) {
        gate.await()
        recorded += winner
    }

    override suspend fun setTutorialCompleted(completed: Boolean) = Unit
    override suspend fun setGuestModeAccepted(accepted: Boolean) = Unit
    override suspend fun setUsernameChosen(chosen: Boolean) = Unit
    override suspend fun markAchievementsSeen(ids: Set<String>) = Unit
    override suspend fun claimStatisticsFor(userId: String) = Unit
    override suspend fun setLanguage(language: AppLanguage) = Unit
    override suspend fun setThemeMode(mode: ThemeMode) = Unit
    override suspend fun setSoundEnabled(enabled: Boolean) = Unit
    override suspend fun setNotificationsEnabled(enabled: Boolean) = Unit
    override suspend fun setHapticsEnabled(enabled: Boolean) = Unit
    override suspend fun setMatchMessagesEnabled(enabled: Boolean) = Unit
    override suspend fun setDifficulty(difficulty: Difficulty) = Unit
}

/** Accepts every report; the rating half of a finished match is not what these tests are about. */
private class RecordingMatchRepository : MatchRepository {
    val reports = mutableListOf<MatchReport>()

    override suspend fun reportMatch(report: MatchReport): Outcome<Unit> {
        reports += report
        return Outcome.Success(Unit)
    }

    override suspend fun loadRecentMatches(userId: String): Outcome<List<RecentMatch>> =
        Outcome.Success(emptyList())
}
