package com.duzman46.gridbound.game.ai.search

import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.PlayerId

/**
 * The position the search actually moves through: flat integer arrays, mutated in place, with an
 * exact inverse for every mutation.
 *
 * It exists because `GameEngine.perform` rebuilds an immutable [BoardState] per node — including
 * `history = state.history + record`, which copies the whole game history at every interior node —
 * and because `BoardGraph.canTraverse` scans the wall `Set` linearly for each of the four steps out
 * of each cell. Neither is a defect in the rules engine; both are fatal at a hundred thousand nodes
 * a move.
 *
 * `history` and `turnNumber` are not represented here at all. Nothing in `RuleEngine`,
 * `MoveValidator`, `WallValidator`, `VictoryChecker` or `TurnManager` reads `history`, and only
 * `TurnManager` reads `turnNumber`, to increment it — so a board without them is still
 * observation-equivalent for legality and for the outcome.
 *
 * This class is a transcription, not a reimplementation. Where it and the rules engine disagree,
 * the rules engine is right by definition; `FastBoardEquivalenceTest` is the gate that says they
 * do not disagree.
 */
internal class FastBoard(private val zobrist: Zobrist) {
    /**
     * Per cell, which of the four steps out of it are both on the board and unblocked. Board-edge
     * steps are cleared once at construction and never set again, so the hot path needs no bounds
     * check anywhere.
     */
    val open = IntArray(WallGeometry.CELL_COUNT)

    /** The placed wall slots as a 128-bit set, split over two words. */
    val occupied = LongArray(2)

    /** How many placed wall segments touch each post. Drives the anchor filter in [isWallLegal]. */
    val postTouch = IntArray(WallGeometry.POST_COUNT)

    val pawn = IntArray(2)
    val reserve = IntArray(2)
    var side = 0
    var hash = 0L

    /**
     * Breadth-first queue and visit stamps, shared by every traversal on this board.
     *
     * Sharing them is safe only because no traversal ever spans a recursive search call: leaf
     * evaluation, wall legality and candidate scoring each run to completion before the search
     * recurses. A future edit that starts a BFS, recurses, and resumes it would silently read
     * another node's queue.
     */
    private val queue = IntArray(WallGeometry.CELL_COUNT)
    private val visitStamp = IntArray(WallGeometry.CELL_COUNT)
    private var visitToken = 0

    /**
     * Loads a rules-engine position. The only place a [BoardState] is read.
     *
     * Walls are replayed through the same edge-clearing the search uses, rather than copied from
     * some other representation, so a position that arrived from the UI and a position the search
     * built itself are byte-identical.
     */
    fun loadFrom(state: BoardState) {
        WallGeometry.initialOpen.copyInto(open)
        occupied[0] = 0L
        occupied[1] = 0L
        postTouch.fill(0)
        state.walls.forEach { wall -> placeWall(WallGeometry.slotOf(wall)) }
        pawn[0] = cellOf(state, PlayerId.PLAYER_ONE)
        pawn[1] = cellOf(state, PlayerId.PLAYER_TWO)
        reserve[0] = state.player(PlayerId.PLAYER_ONE).wallsRemaining
        reserve[1] = state.player(PlayerId.PLAYER_TWO).wallsRemaining
        side = state.currentPlayer.ordinal
        hash = computeHash()
    }

    fun makePawn(cell: Int) {
        val mover = side
        hash = hash xor zobrist.pawn[mover][pawn[mover]] xor zobrist.pawn[mover][cell]
        pawn[mover] = cell
        side = 1 - mover
        hash = hash xor zobrist.side
    }

    /** @param from the cell the pawn stood on before [makePawn] moved it to [cell]. */
    fun unmakePawn(cell: Int, from: Int) {
        val mover = 1 - side
        side = mover
        hash = hash xor zobrist.side
        hash = hash xor zobrist.pawn[mover][cell] xor zobrist.pawn[mover][from]
        pawn[mover] = from
    }

    fun makeWall(slot: Int) {
        val builder = side
        placeWall(slot)
        hash = hash xor zobrist.wall[slot]
        hash = hash xor zobrist.reserve[builder][reserve[builder]]
        reserve[builder]--
        hash = hash xor zobrist.reserve[builder][reserve[builder]]
        side = 1 - builder
        hash = hash xor zobrist.side
    }

    fun unmakeWall(slot: Int) {
        val builder = 1 - side
        side = builder
        hash = hash xor zobrist.side
        hash = hash xor zobrist.reserve[builder][reserve[builder]]
        reserve[builder]++
        hash = hash xor zobrist.reserve[builder][reserve[builder]]
        hash = hash xor zobrist.wall[slot]
        removeWall(slot)
    }

    /**
     * The mover's legal destinations, as cells, into [out] — which must hold [MAX_PAWN_MOVES].
     *
     * A literal transcription of `MoveValidator.validMoves`, and worth reading against it rather
     * than against the rules of Quoridor. Two details are the whole difficulty:
     *
     * - the two side-steps are tried **only** when the straight jump is unavailable, never
     *   alongside it;
     * - both the jump and the side-steps are traversability-checked **from the opponent's cell**,
     *   not from the mover's.
     *
     * "The square behind is off the board" and "the square behind is walled off" collapse into one
     * test here, because [open] already has board-edge steps cleared — which is exactly what
     * `Position.offsetOrNull` returning null does in the original.
     *
     * @return how many entries of [out] are filled.
     */
    fun pawnMoves(out: IntArray): Int {
        val from = pawn[side]
        val opponent = pawn[1 - side]
        var count = 0
        for (direction in 0 until WallGeometry.DIRECTION_COUNT) {
            if (open[from] and WallGeometry.directionBit[direction] == 0) continue
            val adjacent = from + WallGeometry.directionStep[direction]
            if (adjacent != opponent) {
                count = emit(out, count, adjacent)
                continue
            }
            if (open[adjacent] and WallGeometry.directionBit[direction] != 0) {
                count = emit(out, count, adjacent + WallGeometry.directionStep[direction])
            } else {
                for (sideStep in WallGeometry.perpendicular[direction]) {
                    if (open[adjacent] and WallGeometry.directionBit[sideStep] != 0) {
                        count = emit(out, count, adjacent + WallGeometry.directionStep[sideStep])
                    }
                }
            }
        }
        return count
    }

    /**
     * Whether the side to move may place the wall in [slot], by the same three conditions
     * `WallValidator.isValid` applies: a wall in stock, no structural clash, and neither pawn cut
     * off from its goal row.
     *
     * The cut-off test is the expensive one — two breadth-first searches — and [useAnchorFilter]
     * skips it whenever it provably cannot fail. Call a post **anchored** if it lies on the outer
     * lattice boundary or already has a wall segment touching it. A new wall can only complete a
     * separating barrier through a maximal sub-path of its own one or two segments, and both ends
     * of that sub-path must be anchored; those ends are two of `endA`, `centre`, `endB`. So fewer
     * than two anchored posts means no barrier is possible.
     *
     * It is specifically **not** enough to test the two *end* posts: a wall can complete a barrier
     * using only half of itself, turning at its own centre. With `Wall(7,0,VERTICAL)` already
     * placed, `Wall(6,0,HORIZONTAL)` seals cells (7,0) and (8,0) off entirely, yet its end posts
     * are (7,0) — on the boundary — and (7,2), which is untouched. Its *centre* post (7,1) is the
     * second anchor. `FastBoardEquivalenceTest.anchorFilterCatchesHalfWallBarrier` pins that case.
     *
     * In the opening no wall has two anchored posts at all — a horizontal wall spans post columns
     * `c..c+2` with `c <= 7`, so it can never reach both 0 and 9 — so early in the game the filter
     * removes the cut-off search entirely.
     */
    fun isWallLegal(slot: Int, useAnchorFilter: Boolean): Boolean {
        if (reserve[side] <= 0) return false
        val mask = WallGeometry.conflictMask[slot]
        if (mask[0] and occupied[0] != 0L || mask[1] and occupied[1] != 0L) return false
        if (useAnchorFilter && anchoredPostCount(slot) < 2) return true
        clearWallEdges(slot)
        val legal = canReachGoal(0) && canReachGoal(1)
        restoreWallEdges(slot)
        return legal
    }

    /**
     * Fills [out] with every cell's shortest distance to [player]'s goal row, by one breadth-first
     * search seeded with the whole goal row. Unreachable cells hold [UNREACHABLE_DISTANCE].
     *
     * One field answers the distance of every pawn move, of every wall candidate's endpoint and of
     * the evaluation, so a node pays for two searches however many moves it goes on to order.
     * Pawns do not block, matching `AStarPathFinder` and `BFSValidator`, both of which consult
     * only the walls.
     */
    fun goalField(player: Int, out: IntArray) {
        out.fill(UNREACHABLE_DISTANCE)
        var head = 0
        var tail = 0
        val row = WallGeometry.goalRow[player]
        for (column in 0 until WallGeometry.SIZE) {
            val cell = WallGeometry.cell(row, column)
            out[cell] = 0
            queue[tail++] = cell
        }
        while (head < tail) {
            val cell = queue[head++]
            val next = out[cell] + 1
            val bits = open[cell]
            for (direction in 0 until WallGeometry.DIRECTION_COUNT) {
                if (bits and WallGeometry.directionBit[direction] == 0) continue
                val target = cell + WallGeometry.directionStep[direction]
                if (out[target] > next) {
                    out[target] = next
                    queue[tail++] = target
                }
            }
        }
    }

    /** The player standing on their goal row, or -1. Transcribed from `VictoryChecker`. */
    fun winnerOrNull(): Int = when {
        WallGeometry.rowOf(pawn[0]) == WallGeometry.goalRow[0] -> 0
        WallGeometry.rowOf(pawn[1]) == WallGeometry.goalRow[1] -> 1
        else -> -1
    }

    private fun cellOf(state: BoardState, playerId: PlayerId): Int {
        val position = state.player(playerId).position
        return WallGeometry.cell(position.row, position.column)
    }

    private fun computeHash(): Long {
        var value = zobrist.pawn[0][pawn[0]] xor zobrist.pawn[1][pawn[1]] xor
            zobrist.reserve[0][reserve[0]] xor zobrist.reserve[1][reserve[1]]
        for (slot in 0 until WallGeometry.SLOT_COUNT) {
            if (occupied[slot ushr 6] and (1L shl (slot and 63)) != 0L) {
                value = value xor zobrist.wall[slot]
            }
        }
        return if (side == 1) value xor zobrist.side else value
    }

    private fun placeWall(slot: Int) {
        clearWallEdges(slot)
        occupied[slot ushr 6] = occupied[slot ushr 6] or (1L shl (slot and 63))
        postTouch[WallGeometry.postEndA[slot]]++
        postTouch[WallGeometry.postCentre[slot]]++
        postTouch[WallGeometry.postEndB[slot]]++
    }

    private fun removeWall(slot: Int) {
        restoreWallEdges(slot)
        occupied[slot ushr 6] = occupied[slot ushr 6] and (1L shl (slot and 63)).inv()
        postTouch[WallGeometry.postEndA[slot]]--
        postTouch[WallGeometry.postCentre[slot]]--
        postTouch[WallGeometry.postEndB[slot]]--
    }

    private fun clearWallEdges(slot: Int) {
        val base = slot * WallGeometry.EDGES_PER_WALL
        for (index in base until base + WallGeometry.EDGES_PER_WALL) {
            open[WallGeometry.edgeCell[index]] =
                open[WallGeometry.edgeCell[index]] and WallGeometry.edgeBit[index].inv()
        }
    }

    /**
     * Sets the four steps back, unconditionally — no reference counting.
     *
     * Safe because no two coexisting walls ever block the same step. A horizontal wall clears only
     * vertical-transition bits and a vertical wall only horizontal ones, so the orientations cannot
     * overlap; and two horizontal walls share a blocked step only if they share a row with column
     * spans `{c, c+1}` that intersect, which forces `|c1 - c2| <= 1` — exactly the pair
     * `WallValidator.isStructurallyValid` refuses. Symmetrically for vertical.
     */
    private fun restoreWallEdges(slot: Int) {
        val base = slot * WallGeometry.EDGES_PER_WALL
        for (index in base until base + WallGeometry.EDGES_PER_WALL) {
            open[WallGeometry.edgeCell[index]] =
                open[WallGeometry.edgeCell[index]] or WallGeometry.edgeBit[index]
        }
    }

    private fun anchoredPostCount(slot: Int): Int {
        var count = 0
        if (isAnchored(WallGeometry.postEndA[slot])) count++
        if (isAnchored(WallGeometry.postCentre[slot])) count++
        if (isAnchored(WallGeometry.postEndB[slot])) count++
        return count
    }

    private fun isAnchored(post: Int): Boolean =
        WallGeometry.boundaryPost[post] || postTouch[post] > 0

    private fun canReachGoal(player: Int): Boolean {
        val goal = WallGeometry.goalRow[player]
        val start = pawn[player]
        if (WallGeometry.rowOf(start) == goal) return true
        visitToken++
        visitStamp[start] = visitToken
        var head = 0
        var tail = 0
        queue[tail++] = start
        while (head < tail) {
            val cell = queue[head++]
            val bits = open[cell]
            for (direction in 0 until WallGeometry.DIRECTION_COUNT) {
                if (bits and WallGeometry.directionBit[direction] == 0) continue
                val target = cell + WallGeometry.directionStep[direction]
                if (visitStamp[target] == visitToken) continue
                if (WallGeometry.rowOf(target) == goal) return true
                visitStamp[target] = visitToken
                queue[tail++] = target
            }
        }
        return false
    }

    /** Mirrors the `LinkedHashSet` the rules engine collects moves into. */
    private fun emit(out: IntArray, count: Int, cell: Int): Int {
        for (index in 0 until count) if (out[index] == cell) return count
        out[count] = cell
        return count + 1
    }

    companion object {
        /** The most destinations a pawn can have: three plain steps plus two side-steps. */
        const val MAX_PAWN_MOVES = 5

        /**
         * Stands in for "no path". Far above any real distance (80), far below anything that
         * could overflow when the evaluation doubles it.
         */
        const val UNREACHABLE_DISTANCE = 1_000
    }
}
