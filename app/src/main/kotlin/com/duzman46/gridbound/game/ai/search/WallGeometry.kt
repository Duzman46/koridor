package com.duzman46.gridbound.game.ai.search

import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation

/**
 * The board's geometry, precomputed once, in the integer encodings the whole search is written
 * against:
 *
 * ```
 * cell = row * 9 + column                       0..80
 * slot = (row * 8 + column) * 2 + orientation   0..127   (HORIZONTAL = 0)
 * post = postRow * 10 + postCol                 0..99
 * ```
 *
 * The post lattice is 10x10, not 9x9. A horizontal wall at column 7 reaches post column 9, and a
 * table sized for 81 posts would be an out-of-bounds read on exactly the walls that matter most —
 * the ones against the far edge.
 *
 * Every table is a transcription of an existing rule: [edgeCell] / [edgeBit] of
 * `BoardGraph.isVerticalTransitionBlocked` and `isHorizontalTransitionBlocked`, [conflictMask] of
 * `WallValidator.isStructurallyValid`. Nothing here is derived from the rules of Quoridor as
 * generally stated, so a divergence from the rules engine can only be a transcription error — which
 * is what `FastBoardEquivalenceTest` exists to catch.
 */
internal object WallGeometry {
    const val SIZE = Constants.Board.SIZE
    const val WALL_GRID_SIZE = Constants.Board.WALL_GRID_SIZE
    const val CELL_COUNT = SIZE * SIZE
    const val SLOT_COUNT = WALL_GRID_SIZE * WALL_GRID_SIZE * 2

    /** One more than the board size on each axis: posts sit on cell corners, not in cells. */
    const val POST_STRIDE = SIZE + 1
    const val POST_COUNT = POST_STRIDE * POST_STRIDE

    const val UP = 1
    const val DOWN = 2
    const val LEFT = 4
    const val RIGHT = 8

    const val DIRECTION_COUNT = 4

    /** How many cell/bit pairs one wall clears: two cells on each side of its two unit segments. */
    const val EDGES_PER_WALL = 4

    /** Indexed the same as `Direction.entries`: UP, DOWN, LEFT, RIGHT. */
    val directionBit = intArrayOf(UP, DOWN, LEFT, RIGHT)

    /**
     * The step each direction takes in cell space. LEFT and RIGHT are ±1 and would wrap into the
     * neighbouring row, which is safe only because a board-edge bit is never set in `open[]`: the
     * step is taken solely when the bit says the move is on the board.
     */
    val directionStep = intArrayOf(-SIZE, SIZE, -1, 1)

    /** Transcribed from `MoveValidator.perpendicularDirections`, as direction indices. */
    val perpendicular = arrayOf(
        intArrayOf(2, 3),
        intArrayOf(2, 3),
        intArrayOf(0, 1),
        intArrayOf(0, 1),
    )

    /** Goal rows by `PlayerId.ordinal`. */
    val goalRow =
        intArrayOf(Constants.Board.PLAYER_ONE_GOAL_ROW, Constants.Board.PLAYER_TWO_GOAL_ROW)

    /** `open[]` for an empty board: every interior step set, every board-edge step clear. */
    val initialOpen = IntArray(CELL_COUNT) { cell ->
        val row = cell / SIZE
        val column = cell % SIZE
        var bits = 0
        if (row > 0) bits = bits or UP
        if (row < SIZE - 1) bits = bits or DOWN
        if (column > 0) bits = bits or LEFT
        if (column < SIZE - 1) bits = bits or RIGHT
        bits
    }

    /** Cell of the i-th edge a wall clears, at `slot * EDGES_PER_WALL + i`. */
    val edgeCell = IntArray(SLOT_COUNT * EDGES_PER_WALL)

    /** Direction bit cleared in [edgeCell]`[i]`, at the same index. */
    val edgeBit = IntArray(SLOT_COUNT * EDGES_PER_WALL)

    /**
     * Slots that cannot coexist with this one, as a 128-bit mask split over two words. Word `w`
     * holds slots `w * 64 until w * 64 + 64`.
     */
    val conflictMask = Array(SLOT_COUNT) { LongArray(2) }

    val postEndA = IntArray(SLOT_COUNT)
    val postCentre = IntArray(SLOT_COUNT)
    val postEndB = IntArray(SLOT_COUNT)

    /** A post on the outer lattice boundary. Such a post anchors a barrier for free. */
    val boundaryPost = BooleanArray(POST_COUNT) { post ->
        val row = post / POST_STRIDE
        val column = post % POST_STRIDE
        row == 0 || row == POST_STRIDE - 1 || column == 0 || column == POST_STRIDE - 1
    }

    /**
     * Every other slot sharing at least one post with this one — the slots that extend a placed
     * wall into a longer barrier.
     */
    val slotNeighbours: Array<IntArray>

    init {
        for (slot in 0 until SLOT_COUNT) {
            val row = slotRow(slot)
            val column = slotColumn(slot)
            val base = slot * EDGES_PER_WALL
            if (isHorizontal(slot)) {
                // Blocks the row/row+1 boundary at columns {column, column+1}.
                for (offset in 0..1) {
                    val x = column + offset
                    edgeCell[base + offset * 2] = cell(row, x)
                    edgeBit[base + offset * 2] = DOWN
                    edgeCell[base + offset * 2 + 1] = cell(row + 1, x)
                    edgeBit[base + offset * 2 + 1] = UP
                }
                postEndA[slot] = post(row + 1, column)
                postCentre[slot] = post(row + 1, column + 1)
                postEndB[slot] = post(row + 1, column + 2)
            } else {
                // Blocks the column/column+1 boundary at rows {row, row+1}.
                for (offset in 0..1) {
                    val y = row + offset
                    edgeCell[base + offset * 2] = cell(y, column)
                    edgeBit[base + offset * 2] = RIGHT
                    edgeCell[base + offset * 2 + 1] = cell(y, column + 1)
                    edgeBit[base + offset * 2 + 1] = LEFT
                }
                postEndA[slot] = post(row, column + 1)
                postCentre[slot] = post(row + 1, column + 1)
                postEndB[slot] = post(row + 2, column + 1)
            }
        }

        // Both orientations at one (row, column) share a centre post, which is exactly the pair
        // WallValidator.isStructurallyValid rejects — a cheap self-check on the table above.
        for (index in 0 until SLOT_COUNT / 2) {
            check(postCentre[index * 2] == postCentre[index * 2 + 1])
        }

        for (slot in 0 until SLOT_COUNT) {
            val row = slotRow(slot)
            val column = slotColumn(slot)
            addConflict(slot, slot)
            addConflict(slot, slot xor 1)
            for (delta in intArrayOf(-1, 1)) {
                val other = if (isHorizontal(slot)) {
                    slotOrNull(row, column + delta, WallOrientation.HORIZONTAL)
                } else {
                    slotOrNull(row + delta, column, WallOrientation.VERTICAL)
                }
                if (other >= 0) addConflict(slot, other)
            }
        }

        val slotsByPost = Array(POST_COUNT) { mutableListOf<Int>() }
        for (slot in 0 until SLOT_COUNT) {
            slotsByPost[postEndA[slot]] += slot
            slotsByPost[postCentre[slot]] += slot
            slotsByPost[postEndB[slot]] += slot
        }
        slotNeighbours = Array(SLOT_COUNT) { slot ->
            val neighbours = LinkedHashSet<Int>()
            neighbours += slotsByPost[postEndA[slot]]
            neighbours += slotsByPost[postCentre[slot]]
            neighbours += slotsByPost[postEndB[slot]]
            neighbours -= slot
            neighbours.toIntArray()
        }
    }

    fun cell(row: Int, column: Int): Int = row * SIZE + column

    fun rowOf(cell: Int): Int = cell / SIZE

    fun columnOf(cell: Int): Int = cell % SIZE

    fun post(row: Int, column: Int): Int = row * POST_STRIDE + column

    fun slotRow(slot: Int): Int = (slot ushr 1) / WALL_GRID_SIZE

    fun slotColumn(slot: Int): Int = (slot ushr 1) % WALL_GRID_SIZE

    fun isHorizontal(slot: Int): Boolean = (slot and 1) == WallOrientation.HORIZONTAL.ordinal

    fun slotOf(wall: Wall): Int =
        (wall.row * WALL_GRID_SIZE + wall.column) * 2 + wall.orientation.ordinal

    fun wallOf(slot: Int): Wall = Wall(
        row = slotRow(slot),
        column = slotColumn(slot),
        orientation =
            if (isHorizontal(slot)) WallOrientation.HORIZONTAL else WallOrientation.VERTICAL,
    )

    private fun slotOrNull(row: Int, column: Int, orientation: WallOrientation): Int =
        if (row in 0 until WALL_GRID_SIZE && column in 0 until WALL_GRID_SIZE) {
            (row * WALL_GRID_SIZE + column) * 2 + orientation.ordinal
        } else {
            -1
        }

    private fun addConflict(slot: Int, other: Int) {
        conflictMask[slot][other ushr 6] =
            conflictMask[slot][other ushr 6] or (1L shl (other and 63))
    }
}
