package com.duzman46.gridbound.presentation.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.data.SettingsManager
import com.duzman46.gridbound.data.StatisticsManager
import com.duzman46.gridbound.game.ai.AIEngineFactory
import com.duzman46.gridbound.game.animation.AnimationManager
import com.duzman46.gridbound.game.audio.SoundEffect
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
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineRoomStatus
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.online.model.RoomEndReason
import com.duzman46.gridbound.util.enumValueOrDefault
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class GameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameManager: GameManager,
    private val aiEngineFactory: AIEngineFactory,
    private val settingsManager: SettingsManager,
    private val statisticsManager: StatisticsManager,
    private val soundManager: SoundManager,
    private val animationManager: AnimationManager,
    private val onlineRepository: OnlineGameRepository,
    private val matchRepository: MatchRepository,
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
    private val _uiState = MutableStateFlow(
        GameUiState(
            boardState = gameManager.restart(),
            mode = mode,
            difficulty = difficulty,
            localPlayer = onlineSession?.playerId,
        ),
    )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()
    private var aiJob: Job? = null
    private var recordedWinner: PlayerId? = null
    private var onlineVersion = -1L

    /** Guards against filing the same online match twice from this device. */
    private var reportedMatchId: String? = null

    init {
        viewModelScope.launch {
            settingsManager.settings.collectLatest { settings ->
                _uiState.update {
                    it.copy(
                        soundEnabled = settings.soundEnabled,
                        hapticsEnabled = settings.hapticsEnabled,
                        boardTheme = settings.boardTheme,
                    )
                }
            }
        }
        if (mode == GameMode.VS_AI) {
            viewModelScope.launch { settingsManager.setDifficulty(difficulty) }
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

    fun leaveGame(onFinished: () -> Unit) {
        aiJob?.cancel()
        val session = onlineSession
        if (session == null) {
            onFinished()
            return
        }
        viewModelScope.launch {
            runCatching { onlineRepository.leaveRoom(session) }
            onFinished()
        }
    }

    fun restart() {
        if (mode == GameMode.ONLINE) {
            feedback(SoundEffect.ERROR)
            return
        }
        aiJob?.cancel()
        recordedWinner = null
        val prior = _uiState.value
        _uiState.value = GameUiState(
            boardState = gameManager.restart(),
            mode = mode,
            difficulty = difficulty,
            soundEnabled = prior.soundEnabled,
            hapticsEnabled = prior.hapticsEnabled,
        )
    }

    fun undo() {
        if (mode == GameMode.ONLINE) {
            feedback(SoundEffect.ERROR)
            return
        }
        aiJob?.cancel()
        val state = _uiState.value
        val steps = if (
            mode == GameMode.VS_AI &&
            state.boardState.currentPlayer == PlayerId.PLAYER_ONE &&
            gameManager.canUndo(2)
        ) 2 else 1
        val restored = gameManager.undo(steps) ?: run {
            feedback(SoundEffect.ERROR)
            return
        }
        recordedWinner = null
        updateBoard(restored)
    }

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
            recordWinner(winner, state.turnNumber)
            val humanLost = when (mode) {
                GameMode.VS_AI -> winner == PlayerId.PLAYER_TWO
                GameMode.ONLINE -> winner != onlineSession?.playerId
                GameMode.LOCAL_TWO_PLAYER -> false
            }
            feedback(if (humanLost) SoundEffect.LOSS else SoundEffect.WIN)
        } else if (mode == GameMode.VS_AI && state.currentPlayer == PlayerId.PLAYER_TWO) {
            runAiTurn()
        }
    }

    private fun runAiTurn() {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _uiState.update { it.copy(isAiThinking = true, wallMode = false, validWalls = emptySet()) }
            val snapshot = gameManager.state
            val action = withContext(Dispatchers.Default) {
                aiEngineFactory.forDifficulty(difficulty).chooseAction(snapshot, PlayerId.PLAYER_TWO)
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
                canUndo = mode != GameMode.ONLINE && gameManager.canUndo(),
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
            val accepted = onlineRepository.submitAction(session, onlineVersion, action)
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
                    AppLog.warn("observe-room", error)
                    _uiState.update {
                        it.copy(
                            isOnlineConnected = false,
                            isOnlineSyncing = false,
                            onlineMessage = UiText.Res(R.string.error_network),
                        )
                    }
                }
                .collect { room -> onRoomUpdate(session, room) }
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
                isRanked = room.ranked,
                turnDeadlineAt = if (room.timing.hasTurnLimit && room.status.isPlayable) {
                    room.lastMoveAt + room.timing.turnDurationSeconds * 1_000L
                } else {
                    null
                },
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
        reportFinishedMatch(room)
        finishOrRunAi(room.boardState)
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

    /** Concedes the match. The rules only allow handing the win to the opponent. */
    fun resign() {
        val session = onlineSession ?: return
        if (_uiState.value.boardState.status != GameStatus.IN_PROGRESS) return
        viewModelScope.launch { onlineRepository.resign(session) }
    }

    /**
     * Ends a match whose opponent let the clock run out. The database re-checks the deadline
     * against the server clock, so a tampered device clock cannot claim a win early.
     */
    fun claimTurnTimeout() {
        val session = onlineSession ?: return
        viewModelScope.launch { onlineRepository.claimTurnTimeout(session) }
    }

    private fun feedback(effect: SoundEffect) {
        val state = _uiState.value
        soundManager.play(effect, state.soundEnabled)
        _events.tryEmit(GameEvent.Feedback(effect, state.hapticsEnabled))
    }

    private fun recordWinner(winner: PlayerId, turns: Int) {
        if (recordedWinner == winner) return
        recordedWinner = winner
        viewModelScope.launch {
            statisticsManager.recordGame(
                mode = mode,
                difficulty = difficulty,
                winner = winner,
                localPlayer = onlineSession?.playerId ?: PlayerId.PLAYER_ONE,
                turns = turns,
            )
        }
    }

}
