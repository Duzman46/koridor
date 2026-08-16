package com.duzman46.gridbound.presentation.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.data.firebase.toDatabaseAppError
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.data.SettingsManager
import com.duzman46.gridbound.data.StatisticsManager
import com.duzman46.gridbound.di.ApplicationScope
import com.duzman46.gridbound.game.ai.AIEngineFactory
import com.duzman46.gridbound.game.animation.AnimationManager
import com.duzman46.gridbound.game.audio.SoundEffect
import com.duzman46.gridbound.game.audio.HapticsManager
import com.duzman46.gridbound.game.audio.SoundManager
import com.duzman46.gridbound.game.engine.GameManager
import com.duzman46.gridbound.game.models.ActionResult
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.GameStatus
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation
import com.duzman46.gridbound.match.domain.MatchEndReason
import com.duzman46.gridbound.match.domain.MatchReport
import com.duzman46.gridbound.match.domain.MatchRepository
import com.duzman46.gridbound.online.domain.OnlineGameRepository
import com.duzman46.gridbound.online.model.MatchMessage
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineRoomStatus
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.online.model.RoomEndReason
import com.duzman46.gridbound.profile.domain.UserProfileRepository
import com.duzman46.gridbound.util.enumValueOrDefault
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

@HiltViewModel
class GameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameManager: GameManager,
    private val aiEngineFactory: AIEngineFactory,
    private val settingsManager: SettingsManager,
    private val statisticsManager: StatisticsManager,
    private val soundManager: SoundManager,
    private val hapticsManager: HapticsManager,
    private val animationManager: AnimationManager,
    private val onlineRepository: OnlineGameRepository,
    private val matchRepository: MatchRepository,
    private val profileRepository: UserProfileRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {
    private val mode = enumValueOrDefault(savedStateHandle.get<String>("mode"), GameMode.VS_AI)
    private val difficulty = enumValueOrDefault(savedStateHandle.get<String>("difficulty"), Difficulty.MEDIUM)
    private val roomCode = savedStateHandle.get<String>("roomCode").orEmpty()

    private val onlineSession = if (mode == GameMode.ONLINE) {
        val userId = savedStateHandle.get<String>("userId").orEmpty()
        val playerId = runCatching {
            PlayerId.valueOf(savedStateHandle.get<String>("playerId").orEmpty())
        }.getOrNull()
        playerId?.takeIf { roomCode.isNotBlank() && userId.isNotBlank() }?.let {
            OnlineSession(roomCode, userId, it)
        }
    } else {
        null
    }

    /**
     * The seat this device plays. Online the lobby has already settled it; anywhere else it is
     * the colour the player picked, because seat one is blue and seat one opens.
     */
    private val localPlayer = onlineSession?.playerId
        ?: enumValueOrDefault(savedStateHandle.get<String>("seat"), PlayerId.PLAYER_ONE)
    private val _uiState = MutableStateFlow(
        GameUiState(
            boardState = gameManager.restart(),
            mode = mode,
            difficulty = difficulty,
            localPlayer = localPlayer,
            localUserId = onlineSession?.userId.orEmpty(),
        ),
    )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()
    private var aiJob: Job? = null
    private var idleWatchJob: Job? = null
    private var turnClockJob: Job? = null
    private var recordedWinner: PlayerId? = null
    private var onlineVersion = -1L

    /** When the turn began whose last-seconds warning has already sounded. */
    private var warnedTurnAt = 0L

    /** Guards against filing the same online match twice from this device. */
    private var reportedMatchId: String? = null

    /** The opponent whose profile read is already in flight; see [resolveOpponent]. */
    private var resolvingOpponentId: String? = null

    /** When this device last said something; see [sendMessage]. */
    private var lastMessageAt = 0L

    /**
     * True once this device has given its online seat up, whichever way it did it.
     *
     * [leaveGame] and [onCleared] both release it and both can run for the same departure —
     * the exit dialog resigns, and the navigation it then performs clears this view model.
     * The second write would be refused by the server anyway, since the room is no longer in
     * progress; this is so it is never sent.
     */
    private var released = false

    init {
        viewModelScope.launch {
            settingsManager.settings.collectLatest { settings ->
                _uiState.update {
                    it.copy(
                        soundEnabled = settings.soundEnabled,
                        hapticsEnabled = settings.hapticsEnabled,
                        matchMessagesEnabled = settings.matchMessagesEnabled,
                    )
                }
            }
        }
        if (mode == GameMode.VS_AI) {
            viewModelScope.launch { settingsManager.setDifficulty(difficulty) }
            // A player who chose red holds seat two, so the bot opens and has to be told to.
            finishOrRunAi(_uiState.value.boardState)
        } else if (mode == GameMode.ONLINE) {
            observeOnlineRoom()
        }
    }

    fun onTileTapped(position: Position) {
        val state = _uiState.value
        if (!state.acceptsHumanInput || state.wallMode || state.boardState.status != GameStatus.IN_PROGRESS) return
        val currentPosition = state.boardState.player(state.boardState.currentPlayer).position
        when {
            position == currentPosition -> {
                val selected = !state.pawnSelected
                _uiState.update {
                    it.copy(
                        pawnSelected = selected,
                        validMoves = if (selected) gameManager.validMoves() else emptySet(),
                    )
                }
            }

            state.pawnSelected && position in state.validMoves -> performAction(GameAction.MovePawn(position))
            else -> feedback(SoundEffect.ERROR)
        }
    }

    fun toggleWallMode() {
        val state = _uiState.value
        if (!state.acceptsHumanInput || state.boardState.status != GameStatus.IN_PROGRESS) return
        if (state.boardState.player(state.boardState.currentPlayer).wallsRemaining <= 0) {
            feedback(SoundEffect.ERROR)
            return
        }
        val enabled = !state.wallMode
        _uiState.update {
            it.copy(
                wallMode = enabled,
                pawnSelected = false,
                validMoves = emptySet(),
                validWalls = if (enabled) it.validWalls else emptySet(),
                pendingWall = null,
                invalidWallPreview = null,
            )
        }
        if (enabled) {
            viewModelScope.launch {
                val walls = withContext(Dispatchers.Default) { gameManager.validWalls() }
                if (_uiState.value.wallMode) _uiState.update { it.copy(validWalls = walls) }
            }
        }
    }

    fun setWallOrientation(orientation: WallOrientation) {
        _uiState.update {
            it.copy(
                wallOrientation = orientation,
                pendingWall = null,
                invalidWallPreview = null,
            )
        }
    }

    fun onWallTapped(wall: Wall) {
        val state = _uiState.value
        if (!state.acceptsHumanInput || !state.wallMode) return
        if (wall in state.validWalls) {
            _uiState.update { it.copy(pendingWall = wall, invalidWallPreview = null) }
            feedback(SoundEffect.MOVE)
        } else {
            _uiState.update { it.copy(pendingWall = null, invalidWallPreview = wall) }
            feedback(SoundEffect.ERROR)
            viewModelScope.launch {
                delay(animationManager.invalidPreviewMillis)
                if (_uiState.value.invalidWallPreview == wall) {
                    _uiState.update { it.copy(invalidWallPreview = null) }
                }
            }
        }
    }

    fun confirmPendingWall() {
        val state = _uiState.value
        val wall = state.pendingWall ?: return
        if (!state.acceptsHumanInput || !state.wallMode || wall !in state.validWalls) {
            _uiState.update { it.copy(pendingWall = null) }
            feedback(SoundEffect.ERROR)
            return
        }
        performAction(GameAction.PlaceWall(wall))
    }

    fun cancelPendingWall() {
        _uiState.update { it.copy(pendingWall = null, invalidWallPreview = null) }
    }

    /**
     * Walks out of the match. Online that costs it: a rival left staring at a board nobody is
     * going to move again is the same abandonment as a resignation, so it is written as one
     * and they are told and rated exactly as they would have been.
     *
     * A room that is not being played — one still waiting for an opponent — costs nothing and
     * is only released.
     */
    fun leaveGame(onFinished: () -> Unit) {
        stopAiTurn()
        val session = onlineSession
        if (session == null) {
            onFinished()
            return
        }
        val forfeits = _uiState.value.leavingForfeits
        released = true
        viewModelScope.launch {
            // Bounded, because [onFinished] is what takes the player off this screen and it is
            // the only thing that does. The write goes through a Firebase transaction, and a
            // transaction on a handset that has lost the network does not fail — it waits for a
            // connection that may not come back. The player who asked to leave would sit on a
            // board they had already left, indefinitely, with the door held by a promise. The
            // seat is not abandoned by giving up on the wait: the server's onDisconnect handler
            // and the ten-minute idle sweep both still close the room behind them.
            runCatching {
                withTimeout(LEAVE_TIMEOUT_MILLIS) {
                    if (forfeits) {
                        onlineRepository.resign(session)
                    } else {
                        onlineRepository.leaveRoom(session)
                    }
                }
            }
            onFinished()
        }
    }

    /**
     * Gives the seat up when the board is torn down without anybody having pressed the exit.
     *
     * The exit dialog is not the only way off this screen, and it was the only one that wrote
     * anything. Accepting a rematch — or a friend's invitation — from the request bar navigates
     * straight onto the new board and pops this one with it, which clears this view model; so
     * did every other pop that does not pass through [leaveGame]. Nothing told the room, and the
     * opponent was left in front of a position nobody was ever going to move again until the
     * ten-minute idle sweep noticed and decided it for them. That is the same abandonment
     * [leaveGame] calls a resignation, so it is written as one: they are told, and rated,
     * exactly as they would have been.
     *
     * Guarded twice, because most teardowns owe the room nothing. [GameUiState.leavingForfeits]
     * is false the moment a match has ended, so the ordinary pop from the board to the victory
     * screen costs no write at all; and [released] is what stops the exit dialog's own
     * resignation being filed a second time by the navigation it performs.
     *
     * On the application scope for the reason [announceResult] is: the navigation that clears
     * this view model is the navigation that cancels [viewModelScope], and work started there
     * would be cancelled before it left the handset. Bounded for the reason [leaveGame] is — a
     * transaction on a handset with no network waits rather than failing — so that nothing is
     * left holding the process scope open indefinitely.
     */
    override fun onCleared() {
        val session = onlineSession ?: return
        if (released || !_uiState.value.leavingForfeits) return
        released = true
        applicationScope.launch {
            runCatching {
                withTimeout(LEAVE_TIMEOUT_MILLIS) { onlineRepository.resign(session) }
            }
        }
    }

    fun restart() {
        if (mode == GameMode.ONLINE) {
            feedback(SoundEffect.ERROR)
            return
        }
        stopAiTurn()
        recordedWinner = null
        val prior = _uiState.value
        _uiState.value = GameUiState(
            boardState = gameManager.restart(),
            mode = mode,
            difficulty = difficulty,
            localPlayer = localPlayer,
            soundEnabled = prior.soundEnabled,
            hapticsEnabled = prior.hapticsEnabled,
            matchMessagesEnabled = prior.matchMessagesEnabled,
        )
        finishOrRunAi(_uiState.value.boardState)
    }

    fun undo() {
        if (mode == GameMode.ONLINE) {
            feedback(SoundEffect.ERROR)
            return
        }
        stopAiTurn()
        val restored = gameManager.undo(undoSteps(_uiState.value.boardState)) ?: run {
            feedback(SoundEffect.ERROR)
            return
        }
        recordedWinner = null
        updateBoard(restored)
    }

    /**
     * How many moves an undo has to take back to hand the turn to [localPlayer] again.
     *
     * Against a bot that is your move and its reply together, or your move alone if the reply
     * has not landed yet. An undo that stopped on the bot's turn would leave the board waiting
     * for a move nobody is going to make — which is exactly the opening position when the bot
     * holds seat one, and where there is nothing of yours to take back at all.
     */
    private fun undoSteps(state: BoardState): Int =
        if (mode == GameMode.VS_AI && state.currentPlayer == localPlayer) 2 else 1

    private fun performAction(action: GameAction) {
        if (mode == GameMode.ONLINE) {
            submitOnlineAction(action)
            return
        }
        when (val result = gameManager.perform(action)) {
            is ActionResult.Invalid -> feedback(SoundEffect.ERROR)
            is ActionResult.Success -> {
                updateBoard(result.state, (action as? GameAction.PlaceWall)?.wall)
                feedback(if (action is GameAction.PlaceWall) SoundEffect.WALL else SoundEffect.MOVE)
                finishOrRunAi(result.state)
            }
        }
    }

    private fun finishOrRunAi(state: BoardState) {
        val winner = state.status.winner
        if (winner != null) {
            announceResult(winner, state.turnNumber, state.turnNumber)
        } else if (mode == GameMode.VS_AI && state.currentPlayer != localPlayer) {
            runAiTurn()
        }
    }

    /**
     * Settles the local end of a match: the statistics it counts towards, and the chime that
     * says which way it went.
     *
     * Guarded, because online the result arrives on a room that republishes itself afterwards
     * and a losing chime is not something to hear twice.
     *
     * The record goes on the application scope, for the same reason [RematchViewModel.onCleared]
     * does: the state change that brings us here is the one the victory screen watches, and the
     * navigation it triggers pops the board with `inclusive = true` — which clears this view
     * model and cancels [viewModelScope]. DataStore applies its transform through
     * `withContext(callerContext)`, so a caller cancelled in that window does not fail loudly,
     * it simply never writes: the match is played, won, and silently never counted. Nothing
     * about the recording belongs to this screen, so it must not die with it.
     */
    private fun announceResult(winner: PlayerId, turnsPlayed: Int, winTurns: Int?) {
        if (recordedWinner == winner) return
        recordedWinner = winner
        applicationScope.launch {
            statisticsManager.recordGame(
                mode = mode,
                difficulty = difficulty,
                winner = winner,
                localPlayer = localPlayer,
                turnsPlayed = turnsPlayed,
                winTurns = winTurns,
            )
        }
        val lost = when (mode) {
            GameMode.VS_AI, GameMode.ONLINE -> winner != localPlayer
            GameMode.LOCAL_TWO_PLAYER -> false
        }
        feedback(if (lost) SoundEffect.LOSS else SoundEffect.WIN)
    }

    /**
     * Stops the bot thinking, as far as it can be stopped.
     *
     * Cancelling the job is only half of it: the search runs on `Dispatchers.Default` in a loop
     * that never suspends, so cancellation has nothing to act on until it finishes. Without the
     * second half, restarting a match against a thinking bot waits out the search nobody wants
     * before the new one can start, and pressing again stacks them.
     */
    private fun stopAiTurn() {
        aiJob?.cancel()
        aiEngineFactory.forDifficulty(difficulty).abandonSearch()
    }

    private fun runAiTurn() {
        stopAiTurn()
        aiJob = viewModelScope.launch {
            _uiState.update { it.copy(isAiThinking = true, wallMode = false, validWalls = emptySet()) }
            val snapshot = gameManager.state
            val action = withContext(Dispatchers.Default) {
                // `withContext` tests for cancellation before it dispatches; this tests again
                // after, which is the window that matters. Beyond this line the thread is
                // committed: the search is one non-suspending loop behind a monitor, so a
                // coroutine cancelled from here on runs to its full budget — 1,200 ms at
                // EXPERT — before anything can observe that nobody wants the answer. Rapid
                // restarts are exactly how that queue forms.
                ensureActive()
                aiEngineFactory.forDifficulty(difficulty)
                    .chooseAction(snapshot, localPlayer.opponent)
            }
            if (gameManager.state != snapshot) return@launch
            when (val result = gameManager.perform(action)) {
                is ActionResult.Invalid -> {
                    _uiState.update { it.copy(isAiThinking = false) }
                    feedback(SoundEffect.ERROR)
                }

                is ActionResult.Success -> {
                    updateBoard(
                        result.state,
                        (action as? GameAction.PlaceWall)?.wall,
                        isAiThinking = false,
                    )
                    feedback(if (action is GameAction.PlaceWall) SoundEffect.WALL else SoundEffect.MOVE)
                    finishOrRunAi(result.state)
                }
            }
        }
    }

    private fun updateBoard(
        boardState: BoardState,
        recentlyPlacedWall: Wall? = null,
        isAiThinking: Boolean = false,
    ) {
        _uiState.update {
            it.copy(
                boardState = boardState,
                pawnSelected = false,
                validMoves = emptySet(),
                wallMode = false,
                validWalls = emptySet(),
                pendingWall = null,
                invalidWallPreview = null,
                recentlyPlacedWall = recentlyPlacedWall,
                isAiThinking = isAiThinking,
                isOnlineSyncing = false,
                canUndo = mode != GameMode.ONLINE && gameManager.canUndo(undoSteps(boardState)),
            )
        }
    }

    private fun submitOnlineAction(action: GameAction) {
        val session = onlineSession ?: run {
            feedback(SoundEffect.ERROR)
            return
        }
        val state = _uiState.value
        if (!state.acceptsHumanInput) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isOnlineSyncing = true,
                    pawnSelected = false,
                    validMoves = emptySet(),
                    wallMode = false,
                    validWalls = emptySet(),
                    pendingWall = null,
                )
            }
            // Bounded, for the reason [leaveGame] is bounded and then some. `submitAction`
            // resolves to a Firebase transaction, and a transaction on a handset that has lost
            // the network does not fail — it waits for a connection that may not come back.
            // Unbounded, that left the board with [GameUiState.acceptsHumanInput] false, which
            // is to say refusing every tap, while the move clock the player could still see ran
            // down and handed the match to the opponent on time. Nothing said why, or that
            // anything was wrong at all.
            //
            // Giving up on the wait does not throw the move away: Firebase keeps the write
            // queued and applies it when the connection returns, and the room listener delivers
            // whatever the server settled on. That is also what makes the message safe to show
            // early — a move that was merely slow arrives a moment later as an ordinary room
            // update, which clears both the message and the syncing state on its way past.
            val accepted = try {
                withTimeout(SUBMIT_TIMEOUT_MILLIS) {
                    onlineRepository.submitAction(session, onlineVersion, action)
                }
            } catch (expired: TimeoutCancellationException) {
                // Caught by its own type rather than as a CancellationException, so that a
                // scope genuinely being cancelled — the player leaving the board mid-move —
                // still unwinds instead of being reported to a screen that has gone.
                AppLog.warn("submit-action-timeout", expired)
                _uiState.update {
                    it.copy(
                        isOnlineSyncing = false,
                        // No new string: the honest reading of a write that never reached the
                        // server is the connection, and AppError already owns that sentence in
                        // all ten languages.
                        onlineMessage = AppError.NETWORK.message,
                    )
                }
                feedback(SoundEffect.ERROR)
                return@launch
            }
            if (!accepted) {
                // The room moved on underneath us; the listener will deliver the truth.
                _uiState.update {
                    it.copy(
                        isOnlineSyncing = false,
                        onlineMessage = UiText.Res(R.string.game_reconnecting),
                    )
                }
                feedback(SoundEffect.ERROR)
            }
        }
    }

    private fun observeOnlineRoom() {
        val session = onlineSession
        if (session == null) {
            _uiState.update { it.copy(onlineMessage = UiText.Res(R.string.error_service_unavailable)) }
            return
        }
        viewModelScope.launch {
            onlineRepository.observeRoom(session.roomCode)
                .catch { error ->
                    // The repository logs why the listener closed, under the tag "room-listener";
                    // what belongs here is only what the player is told. Every failure used to be
                    // reported as error_network, so a player with a perfect connection whose
                    // token had expired was sent to check their wifi, and a refused read looked
                    // like a dead router. toDatabaseAppError carries the database's own reason.
                    // No new string: AppError already owns one in all ten languages.
                    _uiState.update {
                        it.copy(
                            isOnlineConnected = false,
                            isOnlineSyncing = false,
                            onlineMessage = error.toDatabaseAppError().message,
                        )
                    }
                }
                .collect { room ->
                    // Null is the room having been removed, which only the sweep does and only
                    // to a room nobody played. Nothing is left to synchronise against.
                    if (room == null) {
                        _uiState.update {
                            it.copy(
                                isOnlineConnected = false,
                                isOnlineSyncing = false,
                                onlineMessage = UiText.Res(R.string.room_error_not_found),
                            )
                        }
                    } else {
                        onRoomUpdate(session, room)
                    }
                }
        }
    }

    private fun onRoomUpdate(session: OnlineSession, room: OnlineRoom) {
        if (room.playerFor(session.userId) != session.playerId) {
            _uiState.update {
                it.copy(
                    isOnlineConnected = false,
                    onlineMessage = UiText.Res(R.string.room_error_not_found),
                )
            }
            return
        }
        val previous = _uiState.value.boardState
        val recentWall = (room.boardState.walls - previous.walls).singleOrNull()
        val versionChanged = onlineVersion >= 0L && room.version > onlineVersion
        onlineVersion = room.version
        gameManager.synchronize(room.boardState)
        // A room can end without the board ending: a resignation, someone walking out, a clock
        // running down. Those writes leave the position exactly as it stood, so the result
        // exists only at the room level and has to be read from there.
        val roomWinner = room.winnerUserId
            ?.takeIf { room.status == OnlineRoomStatus.FINISHED }
            ?.let(room::playerFor)
        _uiState.update {
            it.copy(
                boardState = room.boardState,
                pawnSelected = false,
                validMoves = emptySet(),
                wallMode = false,
                validWalls = emptySet(),
                pendingWall = null,
                invalidWallPreview = null,
                recentlyPlacedWall = recentWall,
                isOnlineConnected = room.status.isPlayable || room.status == OnlineRoomStatus.FINISHED,
                isOnlineSyncing = false,
                turnDeadlineAt = if (room.timing.hasTurnLimit && room.status.isPlayable) {
                    room.lastMoveAt + room.timing.turnDurationSeconds * 1_000L
                } else {
                    null
                },
                onlineWinner = roomWinner,
                onlineEndReason = room.endReason.takeIf { roomWinner != null },
                chat = room.chat,
                // Held on to once seen, so a room whose guest seat has just emptied still
                // remembers who was in it — which is who a rematch would be offered to.
                opponentUserId = room.userFor(session.playerId.opponent)
                    ?.takeIf(String::isNotBlank)
                    ?: it.opponentUserId,
                onlineMessage = when (room.status) {
                    // The guest seat empties when an opponent backs out before the first
                    // move; the room stays open so they can come back.
                    OnlineRoomStatus.WAITING -> UiText.Res(R.string.game_opponent_left)
                    OnlineRoomStatus.CANCELLED, OnlineRoomStatus.EXPIRED ->
                        UiText.Res(R.string.room_error_not_found)

                    else -> null
                },
                canUndo = false,
            )
        }
        if (versionChanged) {
            feedback(if (recentWall != null) SoundEffect.WALL else SoundEffect.MOVE)
        }
        resolveOpponent(session, room)
        reportFinishedMatch(room)
        watchForIdleForfeit(session, room)
        watchTurnClock(session, room)
        _uiState.value.winner?.let {
            announceResult(it, room.boardState.turnNumber, boardWinTurns(room))
        }
    }

    /**
     * How many turns a finished online match is entitled to report, which is none unless the
     * board itself decided it.
     *
     * The same distinction [onRoomUpdate] already draws for the winner, applied to the count.
     * A room can end without the position moving — a resignation, a walk-out, a clock running
     * down — and those writes leave [OnlineRoom.boardState] exactly as it stood, so its turn
     * number is a measure of how far the game got, not of how quickly it was won. Reporting it
     * anyway turned an opponent who resigned on turn two into a two-move victory: it went
     * straight into the handset's `fastestWinTurns` and unlocked the "Lightning" badge — the
     * gold one, permanently — for a game nobody played. A player could farm it by finding
     * somebody willing to resign twice.
     *
     * Null rather than zero, and the distinction is the whole point: zero was overloaded to mean
     * both "no time to record" and "no turns played", so suppressing the Lightning badge also
     * stopped a resigned match counting towards the lifetime turn total and the Marathon badge.
     * The two questions are now asked separately — how long did this run, and how fast was this
     * won — and only the second one can be unanswerable.
     */
    private fun boardWinTurns(room: OnlineRoom): Int? =
        if (room.boardState.status.winner != null) room.boardState.turnNumber else null

    /**
     * Puts a name and a face on the other seat, so the rival is a player rather than "the
     * opponent" and there is something to tap to find out who they are.
     *
     * Read once per opponent, not once per room update: the room republishes itself on every
     * move, and a profile a match cannot change is not worth a request a turn. The state is
     * only filled in when the read succeeds — an unnamed rival leaves the banner exactly as it
     * would have been, rather than offering a tap onto a profile that would fail to open.
     */
    private fun resolveOpponent(session: OnlineSession, room: OnlineRoom) {
        val opponentId = room.userFor(session.playerId.opponent).orEmpty()
        if (opponentId.isBlank()) return
        if (opponentId == _uiState.value.onlineOpponent?.userId) return
        if (opponentId == resolvingOpponentId) return
        resolvingOpponentId = opponentId
        viewModelScope.launch {
            val profile = profileRepository.loadProfile(opponentId).successOrNull
            if (profile == null) {
                // Released so the next room update asks again: a name lost to one dropped
                // request should not leave the rival anonymous for the rest of the match.
                resolvingOpponentId = null
                return@launch
            }
            _uiState.update {
                it.copy(
                    onlineOpponent = OnlineOpponent(
                        userId = opponentId,
                        username = profile.username,
                        avatarId = profile.avatarId,
                    ),
                )
            }
        }
    }

    /**
     * Ends a match that has been walked away from, without anyone having to press anything.
     *
     * Restarted on every room update, so the wait always measures from the last real move.
     * It is not a claim: whichever seat is on the clock loses, so a player who leaves their
     * own phone open on the board forfeits exactly as they would by closing the app.
     *
     * The device clock only decides *when to try*. The database re-checks the span against
     * the server's own clock, so a phone running fast merely gets refused — hence the retry
     * rather than a single shot.
     */
    private fun watchForIdleForfeit(session: OnlineSession, room: OnlineRoom) {
        idleWatchJob?.cancel()
        if (room.status != OnlineRoomStatus.IN_PROGRESS) return
        idleWatchJob = viewModelScope.launch {
            while (isActive) {
                val due = room.lastMoveAt + Constants.Online.IDLE_FORFEIT_MILLIS
                val wait = due - System.currentTimeMillis()
                if (wait > 0L) {
                    delay(wait)
                } else {
                    if (onlineRepository.resolveIdleMatch(session) is Outcome.Success) return@launch
                    delay(IDLE_FORFEIT_RETRY_MILLIS)
                }
            }
        }
    }

    /**
     * Runs the move clock: a warning as its last seconds start, and the end of the match when
     * it reaches zero. Nobody is asked to press anything — a clock that has run out has
     * decided the match, and a win that has to be claimed is one the winner can sleep through.
     *
     * Both devices run this and both fire. The room settles once, so whichever write lands
     * second finds a finished match and is thrown away.
     *
     * The device clock only chooses *when to ask*. The database re-checks the deadline against
     * the server's own clock, so a phone running fast is simply refused — hence the retry
     * rather than a single shot.
     */
    private fun watchTurnClock(session: OnlineSession, room: OnlineRoom) {
        turnClockJob?.cancel()
        if (room.status != OnlineRoomStatus.IN_PROGRESS || !room.timing.hasTurnLimit) return
        val deadline = room.lastMoveAt + room.timing.turnDurationSeconds * 1_000L
        turnClockJob = viewModelScope.launch {
            warnBeforeDeadline(room, deadline)
            while (isActive) {
                val wait = deadline - System.currentTimeMillis()
                if (wait > 0L) {
                    delay(wait)
                } else {
                    if (onlineRepository.resolveTurnTimeout(session) is Outcome.Success) return@launch
                    delay(TURN_TIMEOUT_RETRY_MILLIS)
                }
            }
        }
    }

    /** Calls out the last seconds of a turn — once, and only to the player who can spend them. */
    private suspend fun warnBeforeDeadline(room: OnlineRoom, deadline: Long) {
        // A call to move is no use to the seat waiting for one. Both still see the bar go red,
        // because it is one clock and both are entitled to watch it run out.
        if (room.boardState.currentPlayer != localPlayer) return
        // Keyed on when the turn began rather than on a flag: the room publishes itself again
        // for reasons that are not moves, and a countdown heard twice is worse than none.
        if (room.lastMoveAt <= warnedTurnAt) return
        // Nothing to warn about on a turn already lost — the result is a moment behind it.
        if (System.currentTimeMillis() >= deadline) return

        delay(
            (deadline - Constants.Online.TURN_WARNING_MILLIS - System.currentTimeMillis())
                .coerceAtLeast(0L),
        )
        warnedTurnAt = room.lastMoveAt
        feedback(SoundEffect.WARNING)
    }

    /**
     * Files the result of a finished online match so the server can rate it.
     *
     * Both devices report; the id is derived from the room and its creation time, so the
     * second write collides with the first and is refused. Rating is never applied here.
     */
    private fun reportFinishedMatch(room: OnlineRoom) {
        if (room.status != OnlineRoomStatus.FINISHED) return
        val guestUid = room.guestUserId
        if (guestUid.isNullOrBlank()) return
        val matchId = MatchReport.matchId(room.roomCode, room.createdAt)
        if (reportedMatchId == matchId) return
        reportedMatchId = matchId

        // Mirror the room exactly. Its write rules already proved the outcome legitimate,
        // and the report is rejected server side if a single field disagrees.
        val report = MatchReport(
            matchId = matchId,
            roomCode = room.roomCode,
            hostUid = room.hostUserId,
            guestUid = guestUid,
            winnerUid = room.winnerUserId?.takeIf(String::isNotBlank),
            endReason = when (room.endReason) {
                RoomEndReason.TIMEOUT -> MatchEndReason.TIMEOUT
                RoomEndReason.RESIGNATION -> MatchEndReason.RESIGNATION
                RoomEndReason.DISCONNECT -> MatchEndReason.DISCONNECT
                else -> MatchEndReason.NORMAL
            },
            ranked = room.ranked,
            turnCount = room.boardState.turnNumber,
            reportedAt = System.currentTimeMillis(),
            reportedBy = onlineSession?.userId.orEmpty(),
        )
        viewModelScope.launch { matchRepository.reportMatch(report) }
    }

    /**
     * Says one of the fixed [MatchMessage] values to the rival.
     *
     * The gap check is the same one the database rules apply, kept here so that a double tap
     * is answered by the app rather than by a write the server throws away — and so the
     * player hears why nothing happened.
     */
    fun sendMessage(message: MatchMessage) {
        val session = onlineSession ?: return
        if (!_uiState.value.canSendMessage) return
        val now = System.currentTimeMillis()
        if (now - lastMessageAt < Constants.Online.CHAT_MIN_INTERVAL_MILLIS) {
            feedback(SoundEffect.ERROR)
            return
        }
        lastMessageAt = now
        viewModelScope.launch { onlineRepository.sendMessage(session, message) }
    }

    /**
     * Silences the rival for the rest of this match, and lets them back in.
     *
     * Deliberately not the setting the settings screen holds. That one is the standing answer
     * to whether this player wants canned messages at all; writing it from the board turned
     * one rival who would not stop into a preference that then had to be hunted down two
     * screens away to undo. This belongs to the match, so it is cleared where it was set and
     * it is gone by the next one.
     */
    fun toggleMatchMute() {
        _uiState.update { it.copy(matchMessagesMuted = !it.matchMessagesMuted) }
    }

    /** Concedes the match. The rules only allow handing the win to the opponent. */
    fun resign() {
        val session = onlineSession ?: return
        if (_uiState.value.boardState.status != GameStatus.IN_PROGRESS) return
        viewModelScope.launch { onlineRepository.resign(session) }
    }

    private fun feedback(effect: SoundEffect) {
        val state = _uiState.value
        soundManager.play(effect, state.soundEnabled)
        _events.tryEmit(GameEvent.Feedback(effect, state.hapticsEnabled))
    }

    /**
     * The vibration for one event, played from the screen rather than from here.
     *
     * It stays an event rather than becoming a direct call because a vibration belongs to a
     * screen the player is looking at: a board left behind mid-animation should not still be
     * buzzing in somebody's pocket.
     */
    fun vibrate(effect: SoundEffect) = hapticsManager.play(effect)

    private companion object {
        /**
         * How long leaving an online match waits for the server before going anyway.
         *
         * Long enough for a healthy write on a slow connection, short enough that a player who
         * has just confirmed they want to leave is never held on the board they left.
         */
        const val LEAVE_TIMEOUT_MILLIS = 4_000L

        /**
         * How long a move waits for the server before the board is handed back to the player.
         *
         * Longer than the exit's wait, because a move is worth waiting for and a leave is only
         * worth confirming — but not much longer. It is spent out of a turn clock that can be
         * as short as thirty seconds, and every second of it is a second the player cannot
         * touch the board. Erring short is the cheap mistake: a move that was merely slow lands
         * anyway and arrives back as a room update, which clears the warning it caused.
         */
        const val SUBMIT_TIMEOUT_MILLIS = 8_000L

        /** How long to wait before trying again when the server refuses an idle forfeit. */
        const val IDLE_FORFEIT_RETRY_MILLIS = 30_000L

        /**
         * The same, for the move clock. Far shorter, because the only reason the server says
         * no is a handset a second or two ahead of it, and a turn clock can be thirty seconds
         * long — waiting out an idle-length interval would leave the match hanging.
         */
        const val TURN_TIMEOUT_RETRY_MILLIS = 3_000L
    }
}
