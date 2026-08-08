package com.duzman46.gridbound.game.ai

import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.TestFixtures
import com.duzman46.gridbound.game.ai.search.SearchClock
import com.duzman46.gridbound.game.ai.search.SearchConfig
import com.duzman46.gridbound.game.models.ActionResult
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.Player
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Eleven hand-set positions, each naming one thing the engine is supposed to know, and one sample
 * that says the search only ever produces moves the rules engine accepts.
 *
 * These are not coverage. Every position here is either a piece of Quoridor knowledge the previous
 * engine demonstrably lacked or a place where a search of this shape is known to go wrong, and each
 * test is written so that failing it means the engine has lost that specific knowledge rather than
 * merely changed its mind. `refusesOneStepWall` and `playsSlotDenial` are the two the whole
 * exercise is about: the first is the audited pathology of throwing walls away, the second a class
 * of move the previous generator structurally could not produce.
 *
 * All of them run on a clock that never advances, so `maxDepth` is the only stopping condition and
 * the answer is identical on every machine and under any load.
 */
class SearchAITacticsTest {
    private val generator =
        AIActionGenerator(TestFixtures.moves, TestFixtures.walls, TestFixtures.aStar)

    @Test
    fun takesImmediateWin() {
        val state = position(
            playerOne = Position(1, 1),
            playerTwo = Position(7, 4),
            current = PlayerId.PLAYER_TWO,
        )

        assertEquals(GameAction.MovePawn(Position(8, 4)), choose(state))
    }

    /**
     * A win available now must beat a win available later. Without ply-relative terminal scores
     * every winning line is worth exactly the same and the engine keeps whichever it happened to
     * try first, which in play looks like a bot that walks in circles in front of its own goal.
     */
    @Test
    fun prefersWinInOneOverWinInThree() {
        val state = position(
            playerOne = Position(1, 4),
            playerTwo = Position(6, 4),
            current = PlayerId.PLAYER_ONE,
        )

        assertEquals(GameAction.MovePawn(Position(0, 4)), choose(state))
    }

    /**
     * The audited pathology, tested head on.
     *
     * The pawns are in different files, so walls against the opponent are genuinely available and
     * cost me nothing — the position is not decided by the generator refusing to produce any. And
     * on an open board every one of those walls delays the opponent by exactly one step, which the
     * race arithmetic prices at zero: after a one-step wall it is the opponent's move, so
     * `2*(dYou+1) - 2*dMe - 1` is the number it was before. The engine must therefore advance and
     * keep the wall. The previous engine ordered every wall above every non-winning pawn move and
     * its evaluation paid it +4 for exactly this trade.
     */
    @Test
    fun refusesOneStepWall() {
        val state = position(
            playerOne = Position(6, 1),
            playerTwo = Position(2, 7),
            current = PlayerId.PLAYER_ONE,
        )

        val action = choose(state)

        assertTrue("expected a pawn move, got $action", action is GameAction.MovePawn)
    }

    /** The other side of the same arithmetic: two steps is a real gain and must be taken. */
    @Test
    fun playsTwoStepWall() {
        val state = position(
            playerOne = Position(7, 2),
            playerTwo = Position(2, 6),
            walls = setOf(Wall(2, 4, WallOrientation.HORIZONTAL)),
            current = PlayerId.PLAYER_ONE,
        )

        val action = choose(state)

        assertTrue("expected a wall, got $action", action is GameAction.PlaceWall)
        assertTrue(
            "expected a wall costing the opponent at least two steps, got $action",
            opponentDelay(state, action as GameAction.PlaceWall) >= 2,
        )
    }

    /**
     * With the opponent out of walls and the race won, every wall placed is a tempo handed over for
     * nothing — my distance cannot grow again, so there is nothing left to defend against. The
     * position is decided at the root, which is why it also has to be answered without searching.
     *
     * "Without searching" is asserted by counting clock reads rather than by timing the call. The
     * search reads its clock exactly once before the first iteration, then once per 1024 nodes and
     * once at the end of every iteration that does not break first. A proven race scores past
     * `SEARCH_MATE_THRESHOLD` at depth 1, so the loop leaves before its first end-of-iteration
     * poll and the total is one. Delete the proven-race branch and the same position runs four
     * iterations over a full wall vocabulary, which reads the clock at least four times. The
     * figure is the same on an idle laptop and on a loaded CI box, which a wall-clock reading of
     * "under 5 ms" is not — one young-gen collection is enough to lose it.
     */
    @Test
    fun runsWhenOpponentHasNoWalls() {
        val state = position(
            playerOne = Position(3, 1),
            playerTwo = Position(2, 7),
            playerOneWalls = 5,
            playerTwoWalls = 0,
            current = PlayerId.PLAYER_ONE,
        )
        val clock = CountingClock()

        val action = engine(TACTICAL_DEPTH, clock = clock).chooseAction(state, PlayerId.PLAYER_ONE)

        assertTrue("expected a pawn move, got $action", action is GameAction.MovePawn)
        assertEquals(
            "the proven race must resolve at depth 1; the search polled its clock ${clock.reads} " +
                "times, so it began a second iteration",
            1,
            clock.reads,
        )
    }

    /**
     * On move one every wall the generator can reach blocks the centre file for both pawns at once,
     * so it is worth nothing and costs a wall. The previous evaluation scored that trade at +4 and
     * played it, which is most of the owner's complaint in a single move.
     */
    @Test
    fun doesNotOpenWithAWall() {
        val action = choose(BoardState.initial())

        assertTrue("expected a pawn move, got $action", action is GameAction.MovePawn)
    }

    /**
     * The opponent is one step from home in a pocket with a single exit, and exactly one wall
     * closes it. Nothing else on the board matters.
     */
    @Test
    fun blocksForcedLoss() {
        val state = position(
            playerOne = Position(8, 4),
            playerTwo = Position(7, 0),
            walls = setOf(Wall(6, 0, WallOrientation.VERTICAL)),
            playerOneWalls = 5,
            playerTwoWalls = 0,
            current = PlayerId.PLAYER_ONE,
        )

        assertEquals(
            GameAction.PlaceWall(Wall(7, 0, WallOrientation.HORIZONTAL)),
            choose(state),
        )
    }

    /**
     * Pawns meet head on and whoever is to move steals a full step by jumping. Most club games turn
     * on this parity moment, and the previous engine's ordering had no representation of it at all.
     */
    @Test
    fun jumpsAtTheMeetingPoint() {
        val state = position(
            playerOne = Position(5, 4),
            playerTwo = Position(4, 4),
            current = PlayerId.PLAYER_ONE,
        )

        assertEquals(GameAction.MovePawn(Position(3, 4)), choose(state))
    }

    /**
     * The prophylactic class, which the previous generator could produce on paper and never in
     * fact: it inserted the opponent's path first and then truncated, so an own-path candidate
     * never reached the search.
     *
     * My route to row 0 runs through the single gap at column 8, and I can reach it either through
     * (2,7) or through (3,8) — two routes of equal length. `Wall(2,7,VERTICAL)` closes both at once
     * and costs me six steps. `Wall(1,7,VERTICAL)` costs me nothing, because the route through
     * (3,8) is untouched, and by the adjacency rule it makes `Wall(2,7,VERTICAL)` illegal. The
     * wall that beats it is not on the opponent's path and gains nothing measurable this ply; its
     * entire value is the move it takes away.
     */
    @Test
    fun playsSlotDenial() {
        val state = position(
            playerOne = Position(3, 7),
            playerTwo = Position(2, 0),
            walls = setOf(
                Wall(1, 0, WallOrientation.HORIZONTAL),
                Wall(1, 2, WallOrientation.HORIZONTAL),
                Wall(1, 4, WallOrientation.HORIZONTAL),
                Wall(1, 6, WallOrientation.HORIZONTAL),
                Wall(4, 7, WallOrientation.VERTICAL),
            ),
            playerOneWalls = 3,
            playerTwoWalls = 3,
            current = PlayerId.PLAYER_ONE,
        )

        assertEquals(
            GameAction.PlaceWall(Wall(1, 7, WallOrientation.VERTICAL)),
            choose(state, DEEP_TACTICAL_DEPTH),
        )
    }

    /**
     * The second wall of a trap. The first one pushed the opponent's route sideways; the wall that
     * now costs them two steps hangs off its post and is on no path the first wall left behind.
     */
    @Test
    fun findsTheSecondWallOfAStaircase() {
        val state = position(
            playerOne = Position(7, 1),
            playerTwo = Position(5, 4),
            walls = setOf(
                Wall(5, 3, WallOrientation.HORIZONTAL),
                Wall(3, 4, WallOrientation.VERTICAL),
            ),
            current = PlayerId.PLAYER_ONE,
        )

        assertEquals(
            GameAction.PlaceWall(Wall(5, 4, WallOrientation.VERTICAL)),
            choose(state),
        )
    }

    /**
     * One wall each, and the race is lost by a single ply. Holding the wall is worth something in
     * the abstract and nothing at all here: spending it is the only move that changes the result.
     */
    @Test
    fun spendsLastWallWhenItWinsTheRace() {
        val state = position(
            playerOne = Position(7, 1),
            playerTwo = Position(2, 6),
            walls = setOf(Wall(2, 4, WallOrientation.HORIZONTAL)),
            playerOneWalls = 1,
            playerTwoWalls = 1,
            current = PlayerId.PLAYER_ONE,
        )

        assertEquals(
            GameAction.PlaceWall(Wall(2, 6, WallOrientation.HORIZONTAL)),
            choose(state),
        )
    }

    /**
     * What the *search* produces is legal — which is a different claim from the one the safety net
     * makes, and the only one worth 200 positions.
     *
     * `SearchAI.chooseAction` returns the searched move only after `GameEngine.perform` has already
     * accepted it, and substitutes a step out of `MoveValidator.validMoves` otherwise. So "the
     * returned action is legal" is true by construction: it would stay green if `FastBoard`
     * generated nothing but illegal walls, and the reader who takes it as evidence that the anchor
     * filter is sound has been told something the assertion does not say. It is kept below because
     * a red one would still mean the net itself is broken, but it is not the point of the test.
     *
     * The two assertions that are about the search:
     *
     * - **Every wall answer came from the search.** The substitute is always a `MovePawn` — each
     *   position here leaves the mover a legal step, asserted — so a returned `PlaceWall` cannot
     *   have come from anywhere else, and those are precisely the answers the wall generator and
     *   the anchor filter are on trial for. The sample has to contain enough of them to be a trial,
     *   which is what [MIN_WALL_ANSWERS] fixes.
     * - **The anchor filter changes nothing but speed.** The same positions are answered again by
     *   an engine that differs only in running the two cut-off searches for every candidate instead
     *   of skipping the ones it can prove safe. Both engines are deterministic, so a sound filter
     *   means identical answers, position by position. An unsound one admits a wall the unfiltered
     *   engine refuses; that changes the tree it searches, and when the engine plays the wall the
     *   rules engine refuses it and the substitute comes back instead. Either way the two answers
     *   part company and this goes red.
     *
     * What it does not prove: that the unfiltered legality test is itself right. Both engines share
     * it, so both would be wrong together. `FastBoardEquivalenceTest` is where that is settled,
     * slot by slot against the real `WallValidator`.
     */
    @Test
    fun searchesOnlyLegalActions() {
        val filtered = engine(TACTICAL_DEPTH)
        val unfiltered = engine(TACTICAL_DEPTH, useAnchorFilter = false)
        var wallAnswers = 0
        randomPositions(LEGALITY_SAMPLE).forEachIndexed { index, state ->
            assertTrue(
                "position $index leaves the mover no step, so a wall answer proves nothing",
                TestFixtures.moves.validMoves(state).isNotEmpty(),
            )
            val action = filtered.chooseAction(state, state.currentPlayer)
            assertTrue(
                "position $index, $action, walls=${state.walls}",
                TestFixtures.engine.perform(state, action) is ActionResult.Success,
            )
            assertEquals(
                "the anchor filter changed the answer at position $index, walls=${state.walls}",
                action,
                unfiltered.chooseAction(state, state.currentPlayer),
            )
            if (action is GameAction.PlaceWall) wallAnswers++
        }
        assertTrue(
            "only $wallAnswers of $LEGALITY_SAMPLE answers were walls, which is too few for the " +
                "wall generator to have been tested at all",
            wallAnswers >= MIN_WALL_ANSWERS,
        )
    }

    private fun choose(state: BoardState, depth: Int = TACTICAL_DEPTH): GameAction =
        engine(depth).chooseAction(state, state.currentPlayer)

    private fun engine(
        depth: Int,
        useAnchorFilter: Boolean = true,
        clock: SearchClock = STOPPED_CLOCK,
    ): SearchAI = SearchAI(
        generator,
        TestFixtures.engine,
        TestFixtures.aStar,
        SearchConfig.EXPERT.copy(maxDepth = depth, useAnchorFilter = useAnchorFilter),
        clock,
    )

    /** How many extra steps [action] costs the player who is not to move, through the real A*. */
    private fun opponentDelay(state: BoardState, action: GameAction.PlaceWall): Int {
        val opponent = state.currentPlayer.opponent
        val position = state.player(opponent).position
        val before = TestFixtures.aStar.distance(position, opponent.goalRow, state.walls)
        val after =
            TestFixtures.aStar.distance(position, opponent.goalRow, state.walls + action.wall)
        return after - before
    }

    /**
     * A clock that never advances, so `maxDepth` stays the only stopping condition, and that says
     * how often it was asked — which is the one machine-independent measure of how much work a
     * search did that is visible from outside [SearchAI].
     */
    private class CountingClock : SearchClock {
        var reads = 0
            private set

        override fun nanoTime(): Long {
            reads++
            return 0L
        }
    }

    private companion object {
        const val TACTICAL_DEPTH = 4
        const val DEEP_TACTICAL_DEPTH = 6
        const val LEGALITY_SAMPLE = 200
        const val LEGALITY_SEED = 20260810L
        const val MAX_GENERATED_WALLS = 14
        const val PLACEMENT_ATTEMPTS = 90

        /**
         * Below this many wall answers the sample is 200 pawn shuffles and says nothing about the
         * wall generator. Set well under the figure the engine actually produces, so a change that
         * merely shifts its taste between two sound moves does not fail it.
         */
        const val MIN_WALL_ANSWERS = 50

        val STOPPED_CLOCK = SearchClock { 0L }

        fun position(
            playerOne: Position,
            playerTwo: Position,
            walls: Set<Wall> = emptySet(),
            playerOneWalls: Int = Constants.Board.STARTING_WALLS,
            playerTwoWalls: Int = Constants.Board.STARTING_WALLS,
            current: PlayerId = PlayerId.PLAYER_ONE,
        ): BoardState = BoardState.initial().copy(
            players = mapOf(
                PlayerId.PLAYER_ONE to Player(PlayerId.PLAYER_ONE, playerOne, playerOneWalls),
                PlayerId.PLAYER_TWO to Player(PlayerId.PLAYER_TWO, playerTwo, playerTwoWalls),
            ),
            walls = walls,
            currentPlayer = current,
        )

        /**
         * Random legal positions with neither pawn already home, so every one of them is a position
         * the engine could genuinely be asked to move in.
         */
        fun randomPositions(count: Int): List<BoardState> {
            val random = Random(LEGALITY_SEED)
            return List(count) { randomPosition(random) }
        }

        fun randomPosition(random: Random): BoardState {
            val one = randomCellOffGoalRow(random, PlayerId.PLAYER_ONE)
            var two = randomCellOffGoalRow(random, PlayerId.PLAYER_TWO)
            while (two == one) two = randomCellOffGoalRow(random, PlayerId.PLAYER_TWO)
            val current = PlayerId.entries[random.nextInt(PlayerId.entries.size)]
            var state = position(playerOne = one, playerTwo = two, current = current)
            val target = random.nextInt(MAX_GENERATED_WALLS + 1)
            var placed = 0
            var attempts = 0
            while (placed < target && attempts < PLACEMENT_ATTEMPTS) {
                attempts++
                val owner = PlayerId.entries[random.nextInt(PlayerId.entries.size)]
                if (state.player(owner).wallsRemaining == 0) continue
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

        fun randomCellOffGoalRow(random: Random, playerId: PlayerId): Position {
            var row = random.nextInt(Constants.Board.SIZE)
            while (row == playerId.goalRow) row = random.nextInt(Constants.Board.SIZE)
            return Position(row, random.nextInt(Constants.Board.SIZE))
        }
    }
}
