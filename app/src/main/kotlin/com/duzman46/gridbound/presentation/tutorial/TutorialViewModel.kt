package com.duzman46.gridbound.presentation.tutorial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.game.audio.SoundEffect
import com.duzman46.gridbound.game.audio.SoundManager
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation
import com.duzman46.gridbound.presentation.game.GameUiState
import com.duzman46.gridbound.session.SessionManager
import com.duzman46.gridbound.tutorial.TutorialEngine
import com.duzman46.gridbound.tutorial.TutorialOutcome
import com.duzman46.gridbound.tutorial.TutorialStep
import com.duzman46.gridbound.tutorial.TutorialSteps
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TutorialUiState(
    val stepIndex: Int = 0,
    val stepCount: Int = TutorialSteps.size,
    val board: GameUiState = GameUiState(),
    val hint: UiText? = null,
    val stepSolved: Boolean = false,
    val isFinished: Boolean = false,
) {
    val step: TutorialStep get() = TutorialSteps.steps[stepIndex]
    val isFirstStep: Boolean get() = stepIndex == 0
    val isLastStep: Boolean get() = stepIndex == stepCount - 1
}

/**
 * Runs the interactive tutorial.
 *
 * The board is a real [GameUiState] rendered by the same composable the game uses, and every
 * action is judged by [TutorialEngine] on top of the production rules engine. A step only
 * advances once the player performs the requested action.
 */
@HiltViewModel
class TutorialViewModel @Inject constructor(
    private val tutorialEngine: TutorialEngine,
    private val sessionManager: SessionManager,
    private val soundManager: SoundManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TutorialUiState(board = boardFor(TutorialSteps.steps[0])))
    val uiState: StateFlow<TutorialUiState> = _uiState.asStateFlow()

    fun onTileTapped(position: Position) {
        val state = _uiState.value
        if (state.stepSolved) return
        when (val outcome = tutorialEngine.onTileTapped(state.step, state.board.boardState, position)) {
            is TutorialOutcome.Advance -> solve(outcome.board)
            is TutorialOutcome.Select -> _uiState.update {
                it.copy(
                    board = it.board.copy(
                        pawnSelected = outcome.selected,
                        validMoves = if (outcome.selected) it.step.highlightMoves else emptySet(),
                    ),
                    hint = null,
                )
            }

            else -> hint()
        }
    }

    fun onWallTapped(wall: Wall) {
        val state = _uiState.value
        if (state.stepSolved) return
        when (val outcome = tutorialEngine.onWallTapped(state.step, state.board.boardState, wall)) {
            is TutorialOutcome.Advance -> solve(outcome.board)
            // The rejection is the lesson: show the wall in its invalid styling and pass.
            TutorialOutcome.AdvanceOnRejection -> {
                soundManager.play(SoundEffect.ERROR, state.board.soundEnabled)
                _uiState.update {
                    it.copy(
                        board = it.board.copy(invalidWallPreview = wall, validWalls = emptySet()),
                        hint = null,
                        stepSolved = true,
                    )
                }
            }

            else -> hint()
        }
    }

    fun nextStep() {
        val state = _uiState.value
        if (!state.stepSolved) return
        if (state.isLastStep) {
            finish()
            return
        }
        showStep(state.stepIndex + 1)
    }

    fun previousStep() {
        val state = _uiState.value
        if (state.isFirstStep) return
        showStep(state.stepIndex - 1)
    }

    /** Skipping still counts as done, so the gate never blocks a player who already knows. */
    fun skip() = finish()

    private fun finish() {
        if (_uiState.value.isFinished) return
        _uiState.update { it.copy(isFinished = true, stepSolved = true) }
        viewModelScope.launch { sessionManager.setTutorialCompleted(true) }
    }

    private fun showStep(index: Int) {
        val step = TutorialSteps.steps[index]
        _uiState.update {
            it.copy(
                stepIndex = index,
                board = boardFor(step),
                hint = null,
                stepSolved = false,
            )
        }
    }

    private fun solve(board: com.duzman46.gridbound.game.models.BoardState) {
        val state = _uiState.value
        soundManager.play(SoundEffect.MOVE, state.board.soundEnabled)
        _uiState.update {
            it.copy(
                board = it.board.copy(
                    boardState = board,
                    validMoves = emptySet(),
                    validWalls = emptySet(),
                    pawnSelected = false,
                    pendingWall = null,
                    invalidWallPreview = null,
                ),
                hint = null,
                stepSolved = true,
            )
        }
    }

    private fun hint() {
        val state = _uiState.value
        soundManager.play(SoundEffect.ERROR, state.board.soundEnabled)
        _uiState.update { it.copy(hint = UiText.Res(R.string.tutorial_hint_wrong_move)) }
    }
}

/**
 * Builds the board state for a step. LOCAL_TWO_PLAYER is used so the board accepts taps;
 * the tutorial itself decides what counts as a legal action.
 */
private fun boardFor(step: TutorialStep): GameUiState = GameUiState(
    boardState = step.board,
    mode = GameMode.LOCAL_TWO_PLAYER,
    pawnSelected = step.startPawnSelected,
    validMoves = if (step.startPawnSelected) step.highlightMoves else emptySet(),
    wallMode = step.startInWallMode,
    wallOrientation = step.highlightWalls.firstOrNull()?.orientation ?: WallOrientation.HORIZONTAL,
    validWalls = step.highlightWalls,
)
