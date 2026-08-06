package com.duzman46.gridbound.tutorial

import androidx.annotation.StringRes
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameStatus
import com.duzman46.gridbound.game.models.Player
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation

enum class TutorialStepId {
    SELECT_PAWN,
    MOVE,
    JUMP,
    PLACE_VERTICAL_WALL,
    PLACE_HORIZONTAL_WALL,
    BLOCKED_WALL,
    WIN,
}

/** What the player has to do before the step is considered learned. */
sealed interface TutorialGoal {
    /** Tap your own pawn to reveal its moves. */
    data object SelectPawn : TutorialGoal

    data class MoveTo(val target: Position) : TutorialGoal

    data class PlaceWall(val wall: Wall) : TutorialGoal

    /**
     * Attempt a wall that would seal the rival in. The step is passed when the rules engine
     * rejects it, which is exactly the lesson.
     */
    data class AttemptSealingWall(val wall: Wall) : TutorialGoal
}

/**
 * One lesson, played on a real board with the real rules engine.
 *
 * @param board the position the step starts from
 * @param highlightMoves squares the board should light up as the hint
 * @param highlightWalls wall slots the board should light up as the hint
 * @param startInWallMode whether the board opens in wall placement mode
 */
data class TutorialStep(
    val id: TutorialStepId,
    @param:StringRes val titleRes: Int,
    @param:StringRes val messageRes: Int,
    val board: BoardState,
    val goal: TutorialGoal,
    val highlightMoves: Set<Position> = emptySet(),
    val highlightWalls: Set<Wall> = emptySet(),
    val startInWallMode: Boolean = false,
    val startPawnSelected: Boolean = false,
)

/**
 * The scripted curriculum.
 *
 * Every position below is chosen so the real [com.duzman46.gridbound.game.rules.RuleEngine]
 * reaches the intended verdict; TutorialStepsTest asserts that, so a rules change that would
 * break a lesson fails the build rather than the player's first five minutes.
 */
object TutorialSteps {

    /** Sealing demo: this wall already blocks the rival's sideways escape. */
    private val SEAL_SIDE_WALL = Wall(0, 0, WallOrientation.VERTICAL)

    /** Adding this one would close the rival's last route, so the engine must refuse it. */
    private val SEAL_CLOSING_WALL = Wall(1, 0, WallOrientation.HORIZONTAL)

    /**
     * Sits in the channel to the right of column 3, across rows 2 and 3 — it takes the
     * rival's sideways step away without slowing their run down the board, which is the
     * difference between the two orientations.
     */
    private val VERTICAL_TEACHING_WALL = Wall(2, 3, WallOrientation.VERTICAL)

    /** Directly in front of the rival: the orientation that costs them moves. */
    private val HORIZONTAL_TEACHING_WALL = Wall(2, 4, WallOrientation.HORIZONTAL)

    val steps: List<TutorialStep> = listOf(
        TutorialStep(
            id = TutorialStepId.SELECT_PAWN,
            titleRes = R.string.tutorial_step_select_title,
            messageRes = R.string.tutorial_step_select_message,
            board = BoardState.initial(),
            goal = TutorialGoal.SelectPawn,
            highlightMoves = setOf(PlayerId.PLAYER_ONE.startPosition),
        ),
        TutorialStep(
            id = TutorialStepId.MOVE,
            titleRes = R.string.tutorial_step_move_title,
            messageRes = R.string.tutorial_step_move_message,
            board = BoardState.initial(),
            goal = TutorialGoal.MoveTo(Position(7, 4)),
            highlightMoves = setOf(Position(7, 4)),
            startPawnSelected = true,
        ),
        TutorialStep(
            id = TutorialStepId.JUMP,
            titleRes = R.string.tutorial_step_jump_title,
            messageRes = R.string.tutorial_step_jump_message,
            board = board(playerOne = Position(4, 4), playerTwo = Position(3, 4)),
            goal = TutorialGoal.MoveTo(Position(2, 4)),
            highlightMoves = setOf(Position(2, 4)),
            startPawnSelected = true,
        ),
        TutorialStep(
            id = TutorialStepId.PLACE_VERTICAL_WALL,
            titleRes = R.string.tutorial_step_wall_vertical_title,
            messageRes = R.string.tutorial_step_wall_vertical_message,
            board = board(playerOne = Position(6, 4), playerTwo = Position(2, 4)),
            goal = TutorialGoal.PlaceWall(VERTICAL_TEACHING_WALL),
            highlightWalls = setOf(VERTICAL_TEACHING_WALL),
            startInWallMode = true,
        ),
        TutorialStep(
            id = TutorialStepId.PLACE_HORIZONTAL_WALL,
            titleRes = R.string.tutorial_step_wall_horizontal_title,
            messageRes = R.string.tutorial_step_wall_horizontal_message,
            board = board(
                playerOne = Position(6, 4),
                playerTwo = Position(2, 4),
                // Carries the previous lesson's wall over, so the two orientations are seen
                // side by side rather than each on an empty board.
                walls = setOf(VERTICAL_TEACHING_WALL),
                playerOneWallsRemaining = Constants.Board.STARTING_WALLS - 1,
            ),
            goal = TutorialGoal.PlaceWall(HORIZONTAL_TEACHING_WALL),
            highlightWalls = setOf(HORIZONTAL_TEACHING_WALL),
            startInWallMode = true,
        ),
        TutorialStep(
            id = TutorialStepId.BLOCKED_WALL,
            titleRes = R.string.tutorial_step_blocked_title,
            messageRes = R.string.tutorial_step_blocked_message,
            board = board(
                playerOne = Position(6, 4),
                playerTwo = Position(0, 0),
                walls = setOf(SEAL_SIDE_WALL),
                playerTwoWallsRemaining = Constants.Board.STARTING_WALLS - 1,
            ),
            goal = TutorialGoal.AttemptSealingWall(SEAL_CLOSING_WALL),
            highlightWalls = setOf(SEAL_CLOSING_WALL),
            startInWallMode = true,
        ),
        TutorialStep(
            id = TutorialStepId.WIN,
            titleRes = R.string.tutorial_step_win_title,
            messageRes = R.string.tutorial_step_win_message,
            board = board(playerOne = Position(1, 4), playerTwo = Position(6, 6)),
            goal = TutorialGoal.MoveTo(Position(0, 4)),
            highlightMoves = setOf(Position(0, 4)),
            startPawnSelected = true,
        ),
    )

    val size: Int get() = steps.size

    private fun board(
        playerOne: Position,
        playerTwo: Position,
        walls: Set<Wall> = emptySet(),
        playerOneWallsRemaining: Int = Constants.Board.STARTING_WALLS,
        playerTwoWallsRemaining: Int = Constants.Board.STARTING_WALLS,
    ): BoardState = BoardState(
        players = mapOf(
            PlayerId.PLAYER_ONE to Player(PlayerId.PLAYER_ONE, playerOne, playerOneWallsRemaining),
            PlayerId.PLAYER_TWO to Player(PlayerId.PLAYER_TWO, playerTwo, playerTwoWallsRemaining),
        ),
        walls = walls,
        currentPlayer = PlayerId.PLAYER_ONE,
        status = GameStatus.IN_PROGRESS,
        turnNumber = 1,
        history = emptyList(),
    )
}
