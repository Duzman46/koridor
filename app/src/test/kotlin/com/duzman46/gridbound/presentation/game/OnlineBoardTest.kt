package com.duzman46.gridbound.presentation.game

import android.content.ContextWrapper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.duzman46.gridbound.core.AppError
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
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.GameStatus
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
import com.duzman46.gridbound.online.domain.OnlineGameRepository
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineRoomStatus
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.session.FakeUserProfileRepository
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The three things an online board owes the player and the person on the other end of it: a seat
 * that is given up when the board goes, a move that is allowed to fail, and a record that only
 * counts turns somebody actually played.
 *
 * All three are about a match ending in a way nobody pressed a button for, which is why they are
 * asserted through the production teardown — clearing the [ViewModelStore] is what a navigation
 * pop does, and it is the only thing that does it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnlineBoardTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = TurnCountingGameRepository()
    private val online = HesitantOnlineRepository()
    private val matches = AcceptingMatchRepository()
    private val applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
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
    fun `a live board that is popped without anyone pressing exit still gives the seat up`() =
        runTest(dispatcher) {
            viewModel()
            online.rooms.value = liveRoom()
            advanceUntilIdle()

            // Accepting a rematch or an invitation from the request bar navigates straight onto
            // the new board and pops this one with it. Nothing asks the view model first, and
            // until this was written nothing told the room either: the opponent sat in front of
            // a position nobody was going to move again until the idle sweep noticed.
            store.clear()
            advanceUntilIdle()

            assertEquals(listOf(ROOM_CODE), online.resigned)
        }

    @Test
    fun `and a board popped on the way to the victory screen owes it nothing`() =
        runTest(dispatcher) {
            // The ordinary end of every match: the winning move arrives, the screen navigates,
            // and the pop clears this view model. A resignation filed here would be a write
            // against a finished room for every match anybody ever wins.
            viewModel()
            online.rooms.value = finishedRoom(winnerUserId = HOST_ID)
            advanceUntilIdle()

            store.clear()
            advanceUntilIdle()

            assertEquals(emptyList<String>(), online.resigned)
        }

    @Test
    fun `the exit dialog's resignation is not filed twice by the pop it causes`() =
        runTest(dispatcher) {
            val model = viewModel()
            online.rooms.value = liveRoom()
            advanceUntilIdle()

            model.leaveGame {}
            advanceUntilIdle()
            store.clear()
            advanceUntilIdle()

            assertEquals(listOf(ROOM_CODE), online.resigned)
        }

    @Test
    fun `a move the server never answers hands the board back and says why`() =
        runTest(dispatcher) {
            val model = viewModel()
            online.rooms.value = liveRoom()
            advanceUntilIdle()
            online.answerMoves = false

            move(model)
            // Syncing while it waits, which is what stops the board taking a second tap — and,
            // unbounded, what stopped it taking any tap at all for the rest of the match.
            assertTrue(model.uiState.value.isOnlineSyncing)
            assertFalse(model.uiState.value.acceptsHumanInput)

            // A wait that is bounded but not instant: the board is still the server's for a
            // second, because a move is worth waiting for.
            advanceTimeBy(1_000L)
            assertTrue(model.uiState.value.isOnlineSyncing)

            // Generously past it. The assertion is that the wait ends at all and says something
            // when it does, not that it ends on a particular second — the length is a judgement
            // call that belongs in the view model, and this would only pin it in place.
            advanceTimeBy(WELL_PAST_ANY_REASONABLE_WAIT)

            assertFalse(model.uiState.value.isOnlineSyncing)
            // No new string: a write that never reached the server is the connection, and
            // AppError already owns that sentence in all ten languages.
            assertEquals(AppError.NETWORK.message, model.uiState.value.onlineMessage)
        }

    @Test
    fun `a win the board never played reports no turn count`() = runTest(dispatcher) {
        // An opponent resigning leaves the position exactly as it stood, so the room's turn
        // number measures how far the game got and not how fast it was won. Reported as a turn
        // count it became a two-move victory, went into `fastestWinTurns`, and handed out the
        // gold "Lightning" badge for a game nobody played.
        viewModel()
        online.rooms.value = finishedRoom(winnerUserId = HOST_ID)
        advanceUntilIdle()

        assertEquals(listOf<Int?>(null), repository.winTurnsRecorded)
        // …and the match still counts towards the lifetime total: it was played,
        // it simply was not won on the board.
        assertEquals(listOf(2), repository.turnsPlayedRecorded)
    }

    @Test
    fun `and a win on the board reports the turns it took`() = runTest(dispatcher) {
        viewModel()
        online.rooms.value = finishedRoom(winnerUserId = HOST_ID).let { room ->
            room.copy(
                boardState = room.boardState.copy(
                    status = GameStatus.PLAYER_ONE_WON,
                    turnNumber = 17,
                ),
            )
        }
        advanceUntilIdle()

        assertEquals(listOf<Int?>(17), repository.winTurnsRecorded)
        assertEquals(listOf(17), repository.turnsPlayedRecorded)
    }

    /** Selects the pawn and plays whichever move the board itself says is legal. */
    private fun move(model: GameViewModel) {
        val start = model.uiState.value.boardState.player(PlayerId.PLAYER_ONE).position
        model.onTileTapped(start)
        model.onTileTapped(model.uiState.value.validMoves.first())
    }

    private fun liveRoom(): OnlineRoom = online.waitingRoom(OnlineRoomStatus.IN_PROGRESS).copy(
        guestUserId = GUEST_ID,
        currentTurnUserId = HOST_ID,
        version = 1L,
    )

    /**
     * A room the opponent walked out of on the second turn: finished, won, and with a position
     * that never moved. Two turns is the audit's own example, and it is the number that used to
     * become a permanent fastest-win record.
     */
    private fun finishedRoom(winnerUserId: String): OnlineRoom =
        online.waitingRoom(OnlineRoomStatus.FINISHED).let { room ->
            room.copy(
                guestUserId = GUEST_ID,
                currentTurnUserId = "",
                winnerUserId = winnerUserId,
                boardState = room.boardState.copy(turnNumber = 2),
                version = 2L,
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
                hardAI = SearchAI(
                    actionGenerator,
                    gameEngine,
                    pathFinder,
                    SearchConfig.HARD,
                    SearchClock.SYSTEM,
                ),
                expertAI = SearchAI(
                    actionGenerator,
                    gameEngine,
                    pathFinder,
                    SearchConfig.EXPERT,
                    SearchClock.SYSTEM,
                ),
            ),
            settingsManager = SettingsManager(repository),
            statisticsManager = StatisticsManager(repository),
            soundManager = SoundManager(),
            // A JVM test has no Context to give a vibrator through, and nothing on these paths
            // reaches for one: the board vibrates from the screen, not from here.
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

    private companion object {
        const val ROOM_CODE = "AB3D5F"
        const val HOST_ID = "alice-uid"
        const val GUEST_ID = "bob-uid"
        const val STORE_KEY = "game"

        /**
         * Longer than any wait a move could defensibly be given. Virtual time, so it costs the
         * test nothing, and it keeps the assertion about the behaviour rather than the number.
         */
        const val WELL_PAST_ANY_REASONABLE_WAIT = 120_000L
    }
}

/**
 * An online repository that can be asked to stop answering.
 *
 * [answerMoves] is the case that matters and it is not a failure invented for a test: a Firebase
 * transaction on a handset that has lost the network does not fail. It does not call back at all,
 * for as long as you leave it waiting — which is what an unbounded await was waiting for.
 */
private class HesitantOnlineRepository(
    private val fake: FakeOnlineGameRepository = FakeOnlineGameRepository(),
) : OnlineGameRepository by fake {

    val rooms get() = fake.rooms
    fun waitingRoom(status: OnlineRoomStatus) = fake.waitingRoom(status)

    /** Set false for the handset that has walked out of signal mid-move. */
    var answerMoves = true

    /** The room code of every seat this device actually conceded. */
    val resigned = mutableListOf<String>()

    override suspend fun submitAction(
        session: OnlineSession,
        expectedVersion: Long,
        action: GameAction,
    ): Boolean = if (answerMoves) true else awaitCancellation()

    override suspend fun resign(session: OnlineSession): Outcome<Unit> {
        resigned += session.roomCode
        return Outcome.Success(Unit)
    }
}

/** A statistics store that remembers how many turns each finished match was worth. */
private class TurnCountingGameRepository : GameRepository {
    private val settingsState = MutableStateFlow(
        // Off because a JVM test has no audio device: SoundManager would reach for a
        // ToneGenerator and log the refusal through android.util.Log, which is not mocked here.
        AppSettings(soundEnabled = false, hapticsEnabled = false),
    )
    private val statisticsState = MutableStateFlow(GameStatistics())

    /** How long each finished match ran, in the order they landed. */
    val turnsPlayedRecorded = mutableListOf<Int>()

    /**
     * The turn each match was WON on, or null where the board did not decide it.
     *
     * Separate from the list above on purpose: a resignation is a real match of real length that
     * has no winning turn at all, and collapsing the two is what let a rival resigning on turn two
     * hand out a badge for winning in under twenty.
     */
    val winTurnsRecorded = mutableListOf<Int?>()

    override val settings: Flow<AppSettings> = settingsState
    override val statistics: Flow<GameStatistics> = statisticsState
    override val tutorialCompleted: Flow<Boolean> = MutableStateFlow(true)
    override val guestModeAccepted: Flow<Boolean> = MutableStateFlow(true)
    override val usernameChosen: Flow<Boolean> = MutableStateFlow(true)
    override val seenAchievements: Flow<Set<String>?> = MutableStateFlow(emptySet())

    override suspend fun recordCompletedGame(
        mode: GameMode,
        difficulty: Difficulty,
        winner: PlayerId,
        localPlayer: PlayerId,
        turnsPlayed: Int,
        winTurns: Int?,
    ) {
        turnsPlayedRecorded += turnsPlayed
        winTurnsRecorded += winTurns
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
private class AcceptingMatchRepository : MatchRepository {
    override suspend fun reportMatch(report: MatchReport): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun loadRecentMatches(userId: String): Outcome<List<RecentMatch>> =
        Outcome.Success(emptyList())
}
