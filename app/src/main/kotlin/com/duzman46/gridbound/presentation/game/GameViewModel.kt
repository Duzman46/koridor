package com.duzman46.gridbound.presentation.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.data.SettingsManager
import com.duzman46.gridbound.data.StatisticsManager
import com.duzman46.gridbound.domain.models.LocalizedText
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
import com.duzman46.gridbound.online.domain.OnlineGameRepository
import com.duzman46.gridbound.online.model.OnlineRoomStatus
import com.duzman46.gridbound.online.model.OnlineSession
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
    private var onlineRevision = -1L

    init {
        viewModelScope.launch {
            settingsManager.settings.collectLatest { settings ->
                _uiState.update {
                    it.copy(soundEnabled = settings.soundEnabled, hapticsEnabled = settings.hapticsEnabled)
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
            val accepted = onlineRepository.submitAction(session, onlineRevision, action)
            if (!accepted) {
                _uiState.update {
                    it.copy(
                        isOnlineSyncing = false,
                        onlineMessage = LocalizedText(
                            "Hamle gönderilemedi; oyun yeniden eşitleniyor.",
                            "The move could not be sent; the game is resynchronizing.",
                        ),
                    )
                }
                feedback(SoundEffect.ERROR)
            }
        }
    }

    private fun observeOnlineRoom() {
        val session = onlineSession
        if (session == null) {
            _uiState.update {
                it.copy(onlineMessage = LocalizedText("Çevrimiçi oturum bilgisi eksik.", "Online session information is missing."))
            }
            return
        }
        viewModelScope.launch {
            onlineRepository.observeRoom(session.roomCode)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isOnlineConnected = false,
                            isOnlineSyncing = false,
                            onlineMessage = error.localizedMessage?.let { LocalizedText(it, it) }
                                ?: LocalizedText("Oda bağlantısı kesildi.", "The room connection was lost."),
                        )
                    }
                }
                .collect { room ->
                    if (room.playerFor(session.userId) != session.playerId) {
                        _uiState.update {
                            it.copy(
                                isOnlineConnected = false,
                                onlineMessage = LocalizedText("Oda üyeliği doğrulanamadı.", "Room membership could not be verified."),
                            )
                        }
                        return@collect
                    }
                    val previous = _uiState.value.boardState
                    val recentWall = (room.boardState.walls - previous.walls).singleOrNull()
                    val revisionChanged = onlineRevision >= 0L && room.revision > onlineRevision
                    onlineRevision = room.revision
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
                            isOnlineConnected = room.status == OnlineRoomStatus.ACTIVE || room.status == OnlineRoomStatus.FINISHED,
                            isOnlineSyncing = false,
                            onlineMessage = when (room.status) {
                                OnlineRoomStatus.ABANDONED -> LocalizedText("Rakip odadan ayrıldı.", "Your opponent left the room.")
                                OnlineRoomStatus.WAITING -> LocalizedText("Rakip yeniden bağlanıyor…", "Your opponent is reconnecting…")
                                else -> null
                            },
                            canUndo = false,
                        )
                    }
                    if (revisionChanged) {
                        feedback(if (recentWall != null) SoundEffect.WALL else SoundEffect.MOVE)
                    }
                    finishOrRunAi(room.boardState)
                }
        }
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
