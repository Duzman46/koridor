package com.duzman46.gridbound.game.ai

import com.duzman46.gridbound.game.TestFixtures
import com.duzman46.gridbound.game.ai.search.SearchClock
import com.duzman46.gridbound.game.ai.search.SearchConfig
import com.duzman46.gridbound.game.models.ActionResult
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.GameStatus
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.pow
import kotlin.random.Random

/** Builds an engine for one game. Called twice per opening so neither side keeps state. */
internal fun interface EngineFactory {
    fun create(): AIEngine
}

/** One finished game, seen from the challenger's side. */
internal data class GameOutcome(
    val openingIndex: Int,
    val openingLabel: String,
    val challengerSeat: PlayerId,
    val winner: PlayerId?,
    val plies: Int,
    val adjudicated: Boolean,
    val slowestDecisionNanos: Long,
    val moves: List<GameAction>,
) {
    val challengerScore: Double = when (winner) {
        challengerSeat -> 1.0
        null -> 0.5
        else -> 0.0
    }

    val challengerLost: Boolean = winner != null && winner != challengerSeat
}

/** A whole paired match, and everything a failure needs in order to be looked at. */
internal class MatchResult(
    private val label: String,
    val games: List<GameOutcome>,
    private val elapsedMillis: Long,
) {
    val gameCount: Int = games.size
    val score: Double = games.sumOf { it.challengerScore }
    val losses: Int = games.count { it.challengerLost }
    val adjudicated: Int = games.count { it.adjudicated }
    val slowestDecisionNanos: Long = games.maxOf { it.slowestDecisionNanos }

    private val pairScores: List<Double> = games.groupBy { it.openingIndex }
        .toSortedMap()
        .map { (_, pair) -> pair.sumOf { it.challengerScore } }

    /**
     * How many openings this match played — the unit every statistic here is computed over.
     *
     * The two halves of a colour-swapped pair are not two observations. They are the same position,
     * the same two deterministic engines and the labels exchanged, which is precisely why a mirror
     * match scores exactly half by construction rather than on average. Counting the games instead
     * would square every tail in the report.
     */
    val openingCount: Int = pairScores.size

    /**
     * Openings where the challenger took more than one of the pair's two points.
     *
     * A level pair counts against the challenger rather than half for it, so the figure understates
     * a real advantage and never overstates one.
     */
    val openingsWon: Int = pairScores.count { it > LEVEL_PAIR }

    /** Openings the challenger took less than half of — the other half of the sign test. */
    val openingsLost: Int = pairScores.count { it < LEVEL_PAIR }

    /**
     * One line, printed as soon as the match ends.
     *
     * A green run should still say what the engine actually scored. A threshold that is met by a
     * hair and one that is met by a mile are different states of the world, and only one of them is
     * worth acting on before the next evaluation change.
     */
    val headline: String
        get() = "$label — openings $openingsWon-$openingsLost of $openingCount, " +
            "$score / $gameCount points " +
            "(losses $losses, adjudicated $adjudicated, ${elapsedMillis} ms)"

    /**
     * The whole match as text, ending with the move list of the first game the challenger lost.
     *
     * A bare score only says that a threshold was missed. This says which opening, which colour and
     * which moves, which is the difference between a number to argue about and a position to look
     * at.
     */
    fun report(): String = buildString {
        append("$headline, seed ${StrengthHarness.SEED}\n")
        append(" opening  seat         winner        plies  adjudicated\n")
        games.forEach { game ->
            append(
                " %-8d %-12s %-13s %-6d %s\n".format(
                    Locale.ROOT,
                    game.openingIndex,
                    game.challengerSeat,
                    game.winner ?: "draw",
                    game.plies,
                    if (game.adjudicated) "yes" else "no",
                ),
            )
        }
        games.firstOrNull { it.challengerLost }?.let { loss ->
            append("first loss — opening ${loss.openingIndex}, ")
            append("challenger as ${loss.challengerSeat}\n")
            append("  from:  ${loss.openingLabel}\n")
            append("  moves: ${loss.moves.joinToString(" ", transform = ::describeAction)}\n")
        }
    }

    private companion object {
        /** Half of an opening's two points: what a pair splits to when neither side is ahead. */
        const val LEVEL_PAIR = 1.0
    }
}

/**
 * Plays seeded matches between two engines so that "stronger" is a measurement rather than an
 * opinion.
 *
 * Four properties make the numbers it prints mean something.
 *
 * **Engines are driven by nodes, never by milliseconds.** A wall-clock budget makes the same seed
 * produce a different search on a loaded machine, so the result would depend on what else the
 * computer was doing. Every searching engine here is handed [STOPPED_CLOCK], a clock that never
 * advances: no deadline can be reached and no elapsed-time early exit can fire, which leaves
 * `maxNodes` as the only stopping condition. That is what makes a match reproducible rather than
 * merely repeatable.
 *
 * **Openings are seeded and shared.** Both engines are deterministic, so without a book "play ten
 * games" plays one game ten times.
 *
 * **Colours are swapped in pairs.** Every opening is played twice, challenger first as
 * `PLAYER_ONE` and then as `PLAYER_TWO`, so a book that happens to favour one seat cancels out.
 * This is also what makes a mirror match a real test: two behaviourally identical engines play the
 * same game twice with the labels exchanged, so the score must be exactly half, not approximately
 * half.
 *
 * **The rules engine referees.** Every action goes through `GameEngine.perform`, and an illegal one
 * aborts the match with the position that produced it rather than being quietly skipped.
 *
 * Engines are built per game rather than per match. A [SearchAI] carries a transposition table and
 * a history table between turns, so one instance reused across the two halves of a colour swap
 * would give the second game knowledge the first did not have — breaking the mirror match's
 * exactness for a reason that has nothing to do with strength.
 */
internal object StrengthHarness {
    /** The one seed behind the book. Printed on every failure so a result can be reproduced. */
    const val SEED = 20260808L

    /** Long enough that no real game reaches it, short enough that a pathology cannot hang it. */
    const val MAX_PLIES = 200

    /** A clock that never advances, so no engine can stop on time rather than on nodes. */
    val STOPPED_CLOCK = SearchClock { 0L }

    private const val MIN_OPENING_PLIES = 2
    private const val MAX_OPENING_PLIES = 6
    private const val OPENING_WALL_PROBABILITY = 0.25

    /** An opening that leaves anyone this close to home has decided the game before it starts. */
    private const val MIN_OPENING_DISTANCE = 3

    /**
     * Larger than any default match needs. The book is drawn sequentially, so a match that asks for
     * the first four openings gets the same four whatever this is set to, and the gated tuning run
     * gets the whole thing.
     */
    private const val BOOK_SIZE = 32

    private const val MIN_MID_GAME_PLIES = 8
    private const val MAX_MID_GAME_PLIES = 28

    /**
     * An hour, which nothing in this harness can approach.
     *
     * It makes a wall-clock stop unreachable twice over: once because the clock never advances, and
     * once because the deadline is past any conceivable run. The redundancy is deliberate — a
     * future edit that hands an engine a real clock should still not produce a time-dependent
     * result silently.
     */
    private const val UNREACHABLE_BUDGET_MILLIS = 3_600_000L

    private val generator =
        AIActionGenerator(TestFixtures.moves, TestFixtures.walls, TestFixtures.aStar)

    /**
     * The shared opening book, built once.
     *
     * Two to six random legal plies with a one-in-four chance of a wall rather than a step, so the
     * openings differ in wall structure and not merely in which file a pawn stands on. Positions
     * that are already decided, already lopsided, or duplicates of an earlier one are redrawn.
     */
    val book: List<BoardState> by lazy { buildBook() }

    fun expert(maxNodes: Long): EngineFactory = searchEngine(SearchConfig.EXPERT, maxNodes)

    fun hard(maxNodes: Long): EngineFactory = searchEngine(SearchConfig.HARD, maxNodes)

    fun medium(): EngineFactory = EngineFactory {
        MediumAI(generator, TestFixtures.engine, TestFixtures.aStar)
    }

    fun easy(seed: Int): EngineFactory = EngineFactory { EasyAI(generator, Random(seed)) }

    /**
     * The frozen baseline at a budget large enough that its shipped depth always completes.
     *
     * Raising its clock does not strengthen it: the shipped engine discards any iteration it fails
     * to finish, so a budget it never exhausts merely removes the machine's speed from the result.
     */
    fun legacyHard(): EngineFactory = EngineFactory {
        LegacyHardAI(
            generator,
            TestFixtures.engine,
            TestFixtures.aStar,
            timeBudgetMillis = UNREACHABLE_BUDGET_MILLIS,
        )
    }

    /**
     * Plays [openings] openings twice each and returns the challenger's result.
     *
     * The games run on a thread pool, and that is safe to the letter rather than by luck: every
     * `BoardState` is immutable, every validator in `TestFixtures` holds nothing but immutable
     * collaborators and allocates its working arrays per call, and each game builds its own
     * engines. Nothing is shared that anything writes to, so a game's result cannot depend on what
     * the other threads are doing — the same property the stopped clock buys within one game.
     *
     * It matters because the frozen baseline is by far the most expensive participant here: it
     * re-validates every wall candidate twice per node and runs an A* inside a sort comparator, so
     * a single one of its moves costs more than a whole EXPERT search. Serially that one match is
     * most of the suite's runtime, and a suite whose runtime depends on how busy the machine is
     * will eventually fail for a reason nobody cares about.
     *
     * @param label names the match in the failure report.
     */
    fun play(
        label: String,
        challenger: EngineFactory,
        defender: EngineFactory,
        openings: Int,
    ): MatchResult {
        require(openings <= book.size) {
            "The book holds ${book.size} openings, $openings asked for"
        }
        val startedAt = System.nanoTime()
        val pending = ArrayList<Callable<GameOutcome>>(openings * 2)
        for (index in 0 until openings) {
            for (seat in PlayerId.entries) {
                pending += Callable {
                    playGame(index, book[index], seat, challenger.create(), defender.create())
                }
            }
        }
        val pool = Executors.newFixedThreadPool(
            minOf(pending.size, Runtime.getRuntime().availableProcessors()),
        )
        val games = try {
            pool.invokeAll(pending).map(::award)
        } finally {
            pool.shutdown()
        }
        val elapsed = (System.nanoTime() - startedAt) / 1_000_000L
        return MatchResult(label, games, elapsed).also { println(it.headline) }
    }

    /** Unwraps a finished game, so a rejected action still surfaces its own message and stack. */
    private fun award(future: Future<GameOutcome>): GameOutcome = try {
        future.get()
    } catch (failure: ExecutionException) {
        throw failure.cause ?: failure
    }

    /**
     * Mid-game positions for the tests that measure one engine rather than a match.
     *
     * Deeper into the game than the opening book, so the search has walls to reason about and the
     * budget is actually under pressure. A walk that ends a game is redrawn.
     */
    fun midGamePositions(count: Int, seed: Long): List<BoardState> {
        val random = Random(seed)
        val positions = ArrayList<BoardState>(count)
        while (positions.size < count) {
            randomWalk(random, random.nextInt(MIN_MID_GAME_PLIES, MAX_MID_GAME_PLIES + 1))
                ?.let { positions += it }
        }
        return positions
    }

    /**
     * `P(X >= successes)` for `X ~ Binomial(trials, 0.5)`: the chance that a coin would have looked
     * this convincing.
     *
     * The trials are openings, never games — see [MatchResult.openingCount] for why the two halves
     * of a colour-swapped pair are one observation. Every threshold in the strength tests is
     * asserted together with this number, which is what stops a failing threshold from being
     * repaired by lowering it: a lower bar has a fatter tail, and the tail is asserted too.
     */
    fun binomialTailAtLeast(successes: Int, trials: Int): Double {
        var ways = 0.0
        for (k in successes..trials) ways += binomialCoefficient(trials, k)
        return ways / 2.0.pow(trials)
    }

    private fun binomialCoefficient(n: Int, k: Int): Double {
        var result = 1.0
        for (step in 0 until k) result = result * (n - step) / (step + 1)
        return result
    }

    private fun searchEngine(config: SearchConfig, maxNodes: Long): EngineFactory = EngineFactory {
        SearchAI(
            generator,
            TestFixtures.engine,
            TestFixtures.aStar,
            config.copy(maxNodes = maxNodes, hardBudgetMillis = UNREACHABLE_BUDGET_MILLIS),
            STOPPED_CLOCK,
        )
    }

    private fun playGame(
        openingIndex: Int,
        opening: BoardState,
        challengerSeat: PlayerId,
        challenger: AIEngine,
        defender: AIEngine,
    ): GameOutcome {
        var state = opening
        val moves = ArrayList<GameAction>()
        var slowestDecisionNanos = 0L
        while (state.status == GameStatus.IN_PROGRESS && moves.size < MAX_PLIES) {
            val mover = state.currentPlayer
            val engine = if (mover == challengerSeat) challenger else defender
            val startedAt = System.nanoTime()
            val action = engine.chooseAction(state, mover)
            slowestDecisionNanos = maxOf(slowestDecisionNanos, System.nanoTime() - startedAt)
            val result = TestFixtures.engine.perform(state, action)
            if (result !is ActionResult.Success) {
                throw AssertionError(
                    "Illegal action from $mover in opening $openingIndex " +
                        "(challenger as $challengerSeat, seed $SEED)\n" +
                        "  action:   ${describeAction(action)}\n" +
                        "  reason:   ${(result as ActionResult.Invalid).reason}\n" +
                        "  position: ${describeState(state)}\n" +
                        "  moves:    ${moves.joinToString(" ", transform = ::describeAction)}",
                )
            }
            state = result.state
            moves += action
        }
        val decided = state.status.winner
        return GameOutcome(
            openingIndex = openingIndex,
            openingLabel = describeState(opening),
            challengerSeat = challengerSeat,
            winner = decided ?: adjudicate(state),
            plies = moves.size,
            adjudicated = decided == null,
            slowestDecisionNanos = slowestDecisionNanos,
            moves = moves,
        )
    }

    /** Whoever has less board left to cross. Equal distances are an honest half point. */
    private fun adjudicate(state: BoardState): PlayerId? {
        val one = remainingDistance(state, PlayerId.PLAYER_ONE)
        val two = remainingDistance(state, PlayerId.PLAYER_TWO)
        return when {
            one < two -> PlayerId.PLAYER_ONE
            two < one -> PlayerId.PLAYER_TWO
            else -> null
        }
    }

    private fun remainingDistance(state: BoardState, playerId: PlayerId): Int =
        TestFixtures.aStar.distance(state.player(playerId).position, playerId.goalRow, state.walls)

    private fun buildBook(): List<BoardState> {
        val random = Random(SEED)
        val openings = ArrayList<BoardState>(BOOK_SIZE)
        while (openings.size < BOOK_SIZE) {
            val plies = random.nextInt(MIN_OPENING_PLIES, MAX_OPENING_PLIES + 1)
            val candidate = randomWalk(random, plies)
            if (candidate == null ||
                PlayerId.entries.any { remainingDistance(candidate, it) < MIN_OPENING_DISTANCE } ||
                openings.any { it.sameSetupAs(candidate) }
            ) {
                continue
            }
            openings += candidate
        }
        return openings
    }

    /** [plies] random legal actions from the initial position, or null if the walk ends a game. */
    private fun randomWalk(random: Random, plies: Int): BoardState? {
        var state = BoardState.initial()
        repeat(plies) {
            val action = randomAction(state, random) ?: return null
            val result = TestFixtures.engine.perform(state, action)
            if (result !is ActionResult.Success) return null
            state = result.state
        }
        return if (state.status == GameStatus.IN_PROGRESS) state else null
    }

    private fun randomAction(state: BoardState, random: Random): GameAction? {
        if (state.player(state.currentPlayer).wallsRemaining > 0 &&
            random.nextDouble() < OPENING_WALL_PROBABILITY
        ) {
            val walls = TestFixtures.walls.validWalls(state)
            if (walls.isNotEmpty()) {
                return GameAction.PlaceWall(walls.elementAt(random.nextInt(walls.size)))
            }
        }
        val steps = TestFixtures.moves.validMoves(state)
        if (steps.isEmpty()) return null
        return GameAction.MovePawn(steps.elementAt(random.nextInt(steps.size)))
    }

    /** Positions match when the search would see the same thing; history and turn count do not. */
    private fun BoardState.sameSetupAs(other: BoardState): Boolean =
        walls == other.walls &&
            currentPlayer == other.currentPlayer &&
            PlayerId.entries.all { player(it) == other.player(it) }
}

private fun describeState(state: BoardState): String {
    val one = state.player(PlayerId.PLAYER_ONE)
    val two = state.player(PlayerId.PLAYER_TWO)
    val walls = state.walls.joinToString(",", transform = ::describeWall)
    return "P1${one.position.short()}/${one.wallsRemaining} " +
        "P2${two.position.short()}/${two.wallsRemaining} " +
        "to-move ${state.currentPlayer} walls[$walls]"
}

private fun describeAction(action: GameAction): String = when (action) {
    is GameAction.MovePawn -> action.target.short()
    is GameAction.PlaceWall -> describeWall(action.wall)
}

private fun describeWall(wall: Wall): String =
    "${if (wall.orientation == WallOrientation.HORIZONTAL) "H" else "V"}${wall.row}${wall.column}"

private fun Position.short(): String = "($row,$column)"
