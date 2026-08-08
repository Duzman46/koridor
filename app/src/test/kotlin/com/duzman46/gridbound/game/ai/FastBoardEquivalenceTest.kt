package com.duzman46.gridbound.game.ai

import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.TestFixtures
import com.duzman46.gridbound.game.ai.search.FastBoard
import com.duzman46.gridbound.game.ai.search.WallGeometry
import com.duzman46.gridbound.game.ai.search.Zobrist
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.Player
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The gate on [FastBoard]: every answer it gives must be the answer the rules engine gives.
 *
 * [FastBoard] is a hand transcription of `MoveValidator`, `WallValidator`, `BoardGraph` and
 * `AStarPathFinder` into flat integer arrays. A transcription error there is not a weak bot, it is
 * a bot playing a different game — and every strength measurement built on top of it would be a
 * lie. So the comparison is differential and over random legal positions rather than over a
 * handful of hand-picked ones: the failures worth fearing (the jump rule's side-steps, an
 * off-by-one in a wall's row or column span) hide in positions nobody thinks to write down.
 *
 * Positions come from one seeded generator, cached, so a failure is reproducible by index.
 */
class FastBoardEquivalenceTest {

    @Test
    fun pawnMovesMatchMoveValidator() {
        val board = newBoard()
        val buffer = IntArray(FastBoard.MAX_PAWN_MOVES)
        positions.take(PAWN_MOVE_SAMPLE).forEachIndexed { index, state ->
            board.loadFrom(state)
            val count = board.pawnMoves(buffer)
            val actual = (0 until count).map { positionOf(buffer[it]) }.toSet()
            assertEquals(
                "position $index, ${describe(state)}",
                TestFixtures.moves.validMoves(state),
                actual,
            )
            assertEquals("position $index has a duplicate destination", count, actual.size)
        }
    }

    @Test
    fun wallStructuralLegalityMatchesValidator() {
        val board = newBoard()
        positions.take(STRUCTURAL_SAMPLE).forEachIndexed { index, state ->
            board.loadFrom(state)
            for (slot in 0 until WallGeometry.SLOT_COUNT) {
                val wall = WallGeometry.wallOf(slot)
                assertEquals(
                    "position $index, $wall, ${describe(state)}",
                    TestFixtures.walls.isStructurallyValid(state.walls, wall),
                    !conflicts(board, slot),
                )
            }
        }
    }

    @Test
    fun wallLegalityMatchesWallValidator() {
        assertWallLegalityMatches(LEGALITY_SAMPLE, useAnchorFilter = true)
    }

    @Test
    fun wallLegalityMatchesWithAnchorFilterDisabled() {
        assertWallLegalityMatches(LEGALITY_UNFILTERED_SAMPLE, useAnchorFilter = false)
    }

    /**
     * A wall can complete a separating barrier by turning at its own centre post, using only half
     * of itself — so a filter that looks at the two *end* posts alone declares this position safe
     * and lets the search play an illegal wall.
     *
     * `Wall(7,0,VERTICAL)` seals the column 0/1 boundary at rows 7 and 8. `Wall(6,0,HORIZONTAL)`
     * then seals the row 6/7 boundary at columns 0 and 1, which leaves cells (7,0) and (8,0) with
     * no way out at all. Its end posts are (7,0), on the boundary, and (7,2), untouched; the second
     * anchor is its centre post (7,1), which the vertical wall already touches.
     */
    @Test
    fun anchorFilterCatchesHalfWallBarrier() {
        val state = boardState(
            playerOne = Position(7, 0),
            playerTwo = Position(0, 4),
            walls = setOf(Wall(7, 0, WallOrientation.VERTICAL)),
            playerOneWalls = Constants.Board.STARTING_WALLS - 1,
            playerTwoWalls = Constants.Board.STARTING_WALLS,
            current = PlayerId.PLAYER_ONE,
        )
        val candidate = Wall(6, 0, WallOrientation.HORIZONTAL)
        val board = newBoard()
        board.loadFrom(state)

        val actual = board.isWallLegal(WallGeometry.slotOf(candidate), useAnchorFilter = true)

        assertFalse("the half-wall barrier must be rejected", actual)
        assertEquals(TestFixtures.walls.isValid(state, candidate), actual)
    }

    @Test
    fun goalFieldMatchesAStar() {
        val board = newBoard()
        val field = IntArray(WallGeometry.CELL_COUNT)
        positions.take(GOAL_FIELD_SAMPLE).forEachIndexed { index, state ->
            board.loadFrom(state)
            PlayerId.entries.forEach { playerId ->
                val player = playerId.ordinal
                board.goalField(player, field)
                val expected = TestFixtures.aStar.distance(
                    state.player(playerId).position,
                    playerId.goalRow,
                    state.walls,
                )
                assertEquals(
                    "position $index, $playerId, ${describe(state)}",
                    expected,
                    field[board.pawn[player]],
                )
            }
        }
    }

    /**
     * Make and unmake must be exact inverses of each other, not merely close: the search shares one
     * board across the whole tree, so a single unrestored bit corrupts every sibling that follows.
     */
    @Test
    fun makeUnmakeIsExactlyReversible() {
        val board = newBoard()
        val random = Random(REVERSIBILITY_SEED)
        val buffer = IntArray(FastBoard.MAX_PAWN_MOVES)
        positions.take(REVERSIBILITY_SAMPLE).forEachIndexed { index, state ->
            board.loadFrom(state)
            val snapshot = Snapshot(board)
            val undo = ArrayList<() -> Unit>(REVERSIBILITY_DEPTH)
            repeat(REVERSIBILITY_DEPTH) {
                val slot = if (random.nextBoolean()) randomLegalSlot(board, random) else -1
                if (slot >= 0) {
                    board.makeWall(slot)
                    undo += { board.unmakeWall(slot) }
                } else {
                    val pawnCount = board.pawnMoves(buffer)
                    if (pawnCount > 0) {
                        val from = board.pawn[board.side]
                        val target = buffer[random.nextInt(pawnCount)]
                        board.makePawn(target)
                        undo += { board.unmakePawn(target, from) }
                    }
                }
            }
            undo.asReversed().forEach { it() }
            snapshot.assertMatches(board, "position $index, ${describe(state)}")
        }
    }

    /**
     * The incrementally maintained hash must equal the one the same position computes from
     * scratch.
     *
     * [makeUnmakeIsExactlyReversible] structurally cannot see this. A make and an unmake that are
     * wrong in mutually cancelling ways — a key xored into neither, or the wrong key xored into
     * both — are exact inverses of each other all the same, so the snapshot comes back intact while
     * every hash the tree searched under was wrong. And a wrong hash is not a lossy hash: it is the
     * transposition table's whole key, compared in full before an entry is returned, so two
     * positions that collide under it are not scored approximately, they are scored as each other.
     *
     * Only the forward direction is asserted. A make that files the right hash and an unmake that
     * does not is what the reversibility test catches, and the two together cover the pair.
     */
    @Test
    fun incrementalHashMatchesRecomputation() {
        val board = newBoard()
        val reference = newBoard()
        val random = Random(HASH_SEED)
        val buffer = IntArray(FastBoard.MAX_PAWN_MOVES)
        positions.take(HASH_SAMPLE).forEachIndexed { index, state ->
            board.loadFrom(state)
            repeat(HASH_DEPTH) { ply ->
                val slot = if (random.nextBoolean()) randomLegalSlot(board, random) else -1
                if (slot >= 0) {
                    board.makeWall(slot)
                } else {
                    val pawnCount = board.pawnMoves(buffer)
                    if (pawnCount == 0) return@repeat
                    board.makePawn(buffer[random.nextInt(pawnCount)])
                }
                val reached = stateOf(board)
                reference.loadFrom(reached)
                assertEquals(
                    "position $index after ${ply + 1} plies, ${describe(reached)}",
                    reference.hash,
                    board.hash,
                )
            }
        }
    }

    private fun assertWallLegalityMatches(sample: Int, useAnchorFilter: Boolean) {
        val board = newBoard()
        positions.take(sample).forEachIndexed { index, state ->
            board.loadFrom(state)
            for (slot in 0 until WallGeometry.SLOT_COUNT) {
                val wall = WallGeometry.wallOf(slot)
                assertEquals(
                    "position $index, $wall, filter=$useAnchorFilter, ${describe(state)}",
                    TestFixtures.walls.isValid(state, wall),
                    board.isWallLegal(slot, useAnchorFilter),
                )
            }
        }
    }

    private fun conflicts(board: FastBoard, slot: Int): Boolean {
        val mask = WallGeometry.conflictMask[slot]
        return mask[0] and board.occupied[0] != 0L || mask[1] and board.occupied[1] != 0L
    }

    /** Every mutable word of the board, so "unchanged" means unchanged and not merely plausible. */
    private class Snapshot(board: FastBoard) {
        private val open = board.open.copyOf()
        private val occupied = board.occupied.copyOf()
        private val postTouch = board.postTouch.copyOf()
        private val pawn = board.pawn.copyOf()
        private val reserve = board.reserve.copyOf()
        private val side = board.side
        private val hash = board.hash

        fun assertMatches(board: FastBoard, message: String) {
            assertArrayEquals("$message: open", open, board.open)
            assertArrayEquals("$message: occupied", occupied, board.occupied)
            assertArrayEquals("$message: postTouch", postTouch, board.postTouch)
            assertArrayEquals("$message: pawn", pawn, board.pawn)
            assertArrayEquals("$message: reserve", reserve, board.reserve)
            assertEquals("$message: side", side, board.side)
            assertEquals("$message: hash", hash, board.hash)
        }
    }

    private companion object {
        const val POSITION_SEED = 20260808L
        const val REVERSIBILITY_SEED = 20260809L
        const val HASH_SEED = 20260810L
        const val PAWN_MOVE_SAMPLE = 3_000
        const val STRUCTURAL_SAMPLE = 300
        const val LEGALITY_SAMPLE = 400
        const val LEGALITY_UNFILTERED_SAMPLE = 150
        const val GOAL_FIELD_SAMPLE = 1_000
        const val REVERSIBILITY_SAMPLE = 500
        const val REVERSIBILITY_DEPTH = 12
        const val HASH_SAMPLE = 400
        const val HASH_DEPTH = 10
        const val MAX_GENERATED_WALLS = 20

        /** How many random slots a reversibility step draws before it moves a pawn instead. */
        const val SLOT_DRAWS = 16

        /** How many rejected wall draws one position tolerates before it settles for fewer. */
        const val PLACEMENT_ATTEMPTS = 120

        /**
         * Generated once and shared: each test takes a prefix, so a failure at index N is the same
         * position in every run and generation is not paid for six times.
         */
        val positions: List<BoardState> = generatePositions(PAWN_MOVE_SAMPLE)

        fun newBoard(): FastBoard = FastBoard(Zobrist(Constants.Ai.SEARCH_ZOBRIST_SEED))

        /** A legal slot for the side to move, or -1 if the draws found none. */
        fun randomLegalSlot(board: FastBoard, random: Random): Int {
            repeat(SLOT_DRAWS) {
                val slot = random.nextInt(WallGeometry.SLOT_COUNT)
                if (board.isWallLegal(slot, useAnchorFilter = false)) return slot
            }
            return -1
        }

        fun positionOf(cell: Int): Position =
            Position(WallGeometry.rowOf(cell), WallGeometry.columnOf(cell))

        /**
         * The rules-engine position a [FastBoard] is currently standing on.
         *
         * Every field it reads is one the board maintains directly, so feeding this back through
         * `loadFrom` recomputes the hash over the same walls, pawns, reserves and side without
         * consulting any of the incremental xors — which is what makes the comparison differential
         * rather than circular.
         */
        fun stateOf(board: FastBoard): BoardState = boardState(
            playerOne = positionOf(board.pawn[0]),
            playerTwo = positionOf(board.pawn[1]),
            walls = (0 until WallGeometry.SLOT_COUNT)
                .filter { board.occupied[it ushr 6] and (1L shl (it and 63)) != 0L }
                .map(WallGeometry::wallOf)
                .toSet(),
            playerOneWalls = board.reserve[0],
            playerTwoWalls = board.reserve[1],
            current = PlayerId.entries[board.side],
        )

        fun describe(state: BoardState): String =
            "one=${state.player(PlayerId.PLAYER_ONE).position} " +
                "two=${state.player(PlayerId.PLAYER_TWO).position} " +
                "current=${state.currentPlayer} walls=${state.walls}"

        /**
         * Random *legal* positions: pawns first, then walls placed one at a time through the real
         * `WallValidator`, so nothing the tests compare against was ever reachable only in theory.
         * Each wall is charged to a player who still has one, which is what makes the reserve
         * condition in `isWallLegal` reachable at all.
         */
        fun generatePositions(count: Int): List<BoardState> {
            val random = Random(POSITION_SEED)
            return List(count) { generatePosition(random) }
        }

        fun generatePosition(random: Random): BoardState {
            val one = randomCell(random)
            var two = randomCell(random)
            while (two == one) two = randomCell(random)
            val current = PlayerId.entries[random.nextInt(PlayerId.entries.size)]
            var state = boardState(
                playerOne = one,
                playerTwo = two,
                walls = emptySet(),
                playerOneWalls = Constants.Board.STARTING_WALLS,
                playerTwoWalls = Constants.Board.STARTING_WALLS,
                current = current,
            )
            val target = random.nextInt(MAX_GENERATED_WALLS + 1)
            var placed = 0
            var attempts = 0
            while (placed < target && attempts < PLACEMENT_ATTEMPTS) {
                attempts++
                val owner = chooseOwner(state, random) ?: break
                val wall = Wall(
                    row = random.nextInt(Constants.Board.WALL_GRID_SIZE),
                    column = random.nextInt(Constants.Board.WALL_GRID_SIZE),
                    orientation =
                        WallOrientation.entries[random.nextInt(WallOrientation.entries.size)],
                )
                if (!TestFixtures.walls.isValid(state.copy(currentPlayer = owner), wall)) continue
                state = state.copy(
                    walls = state.walls + wall,
                    players = state.players + (owner to state.player(owner).useWall()),
                )
                placed++
            }
            return state.copy(currentPlayer = current)
        }

        fun chooseOwner(state: BoardState, random: Random): PlayerId? {
            val available = PlayerId.entries.filter { state.player(it).wallsRemaining > 0 }
            return available.getOrNull(random.nextInt(available.size.coerceAtLeast(1)))
        }

        fun randomCell(random: Random): Position =
            Position(random.nextInt(Constants.Board.SIZE), random.nextInt(Constants.Board.SIZE))

        fun boardState(
            playerOne: Position,
            playerTwo: Position,
            walls: Set<Wall>,
            playerOneWalls: Int,
            playerTwoWalls: Int,
            current: PlayerId,
        ): BoardState = BoardState.initial().copy(
            players = mapOf(
                PlayerId.PLAYER_ONE to Player(PlayerId.PLAYER_ONE, playerOne, playerOneWalls),
                PlayerId.PLAYER_TWO to Player(PlayerId.PLAYER_TWO, playerTwo, playerTwoWalls),
            ),
            walls = walls,
            currentPlayer = current,
        )
    }
}
