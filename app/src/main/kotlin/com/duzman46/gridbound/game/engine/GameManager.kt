package com.duzman46.gridbound.game.engine

import com.duzman46.gridbound.game.models.ActionResult
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.rules.MoveValidator
import com.duzman46.gridbound.game.rules.WallValidator
import java.util.ArrayDeque
import javax.inject.Inject

class GameManager @Inject constructor(
    private val gameEngine: GameEngine,
    private val moveValidator: MoveValidator,
    private val wallValidator: WallValidator,
) {
    private val undoStack = ArrayDeque<BoardState>()

    /**
     * Volatile because the writers and the readers are not the same thread.
     *
     * Every write happens under this object's monitor on the main thread — a move, an undo, a
     * room update — but the reads do not: `GameViewModel` computes the legal wall set and runs
     * the bot on `Dispatchers.Default`, straight off this field. The monitor gives those readers
     * nothing, because they never take it, so without volatile there is no happens-before edge
     * between the write and the read at all and the worker is entitled to see a board from
     * before the last move. The visible cost of that is a wall preview computed against a
     * position that has already changed, which looks like the rules being wrong rather than like
     * a missing memory barrier.
     */
    @Volatile
    var state: BoardState = BoardState.initial()
        private set

    @Synchronized
    fun restart(): BoardState {
        undoStack.clear()
        state = BoardState.initial()
        return state
    }

    @Synchronized
    fun perform(action: GameAction): ActionResult {
        val before = state
        return when (val result = gameEngine.perform(before, action)) {
            is ActionResult.Invalid -> result
            is ActionResult.Success -> {
                undoStack.addLast(before)
                state = result.state
                result
            }
        }
    }

    @Synchronized
    fun undo(steps: Int = 1): BoardState? {
        require(steps >= 1)
        if (undoStack.size < steps) return null
        repeat(steps) { state = undoStack.removeLast() }
        return state
    }

    fun validMoves(): Set<Position> = moveValidator.validMoves(state)

    fun validWalls(): Set<Wall> = wallValidator.validWalls(state)

    fun canUndo(steps: Int = 1): Boolean = undoStack.size >= steps

    @Synchronized
    fun synchronize(remoteState: BoardState): BoardState {
        if (state != remoteState) {
            undoStack.clear()
            state = remoteState
        }
        return state
    }
}
