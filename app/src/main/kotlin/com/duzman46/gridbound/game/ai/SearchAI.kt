package com.duzman46.gridbound.game.ai

import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.ai.search.FastBoard
import com.duzman46.gridbound.game.ai.search.SearchClock
import com.duzman46.gridbound.game.ai.search.SearchConfig
import com.duzman46.gridbound.game.ai.search.TranspositionTable
import com.duzman46.gridbound.game.ai.search.WallGeometry
import com.duzman46.gridbound.game.ai.search.Zobrist
import com.duzman46.gridbound.game.engine.GameEngine
import com.duzman46.gridbound.game.models.ActionResult
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.pathfinding.AStarPathFinder
import kotlin.math.abs

/** Wall placements are encoded above the 81 pawn destinations, so one Int carries any move. */
private const val WALL_MOVE_BASE = 128

/** One past the largest move code, and the stride of the history table. */
private const val MOVE_CODES = 256

/** Distinguishable from every real move and small enough for the table's 9-bit move field. */
private const val NO_MOVE = 511

/** Five pawn destinations plus the largest wall cap any tier configures, with room to spare. */
private const val MAX_MOVES_PER_PLY = 32

/**
 * The HARD and EXPERT engine: iterative-deepening negamax with alpha-beta over [FastBoard],
 * differing between the two tiers only by its injected [SearchConfig].
 *
 * One engine and two budgets rather than two engines is a deliberate choice. Two evaluation
 * functions are two chances for the difficulty ladder to invert under maintenance — which is the
 * complaint this work started from — whereas "harder" meaning "searches deeper" is a property that
 * survives any later refactor.
 *
 * The class itself is only a shell: it owns the lock, the lazy allocation and the safety net.
 * Everything that thinks lives in [Searcher], which is allocated on the first call so a player who
 * never selects a searching tier never pays for its tables.
 *
 * @param actionGenerator used solely by [safeFallback]; the search generates its own moves.
 * @param gameEngine used solely to re-validate the chosen action; the search never runs through it.
 * @param pathFinder used solely by [safeFallback].
 */
class SearchAI(
    private val actionGenerator: AIActionGenerator,
    private val gameEngine: GameEngine,
    private val pathFinder: AStarPathFinder,
    private val config: SearchConfig,
    private val clock: SearchClock,
) : AIEngine {
    private var searcher: Searcher? = null

    /**
     * Set from whichever thread abandons the search and read inside it, so it has to be volatile —
     * without that the searching thread is entitled to keep a stale copy in a register for the
     * whole of its budget, which is exactly the loop this is meant to break.
     */
    @Volatile
    private var abandoned = false

    /**
     * How many times the search has been abandoned, ever.
     *
     * This is the half of the guard that [abandoned] cannot supply. A caller reads it *before*
     * it queues on the monitor and compares once it is in; a caller retired while it waited sees
     * a different number and gives up the turn instead of taking it.
     *
     * Without it the flag defeats itself. [chooseAction] clears [abandoned] on the way in, which
     * is right for the caller that was asked for and wrong for one that was retired while
     * blocked: it wipes the very signal meant to stop it, then spends its whole budget — 1,200 ms
     * at EXPERT — on a move the screen already moved past. Restarts pressed in a burst queue on
     * this monitor, so k of them serialised k full searches and the one the player is actually
     * waiting for started last. A count rather than a flag because it must survive an arbitrary
     * number of abandonments between the read and the entry.
     *
     * Incremented without an atomic because every caller of [abandonSearch] is the main thread —
     * `GameViewModel.stopAiTurn`, and nothing else. A lost increment could only fail to retire a
     * search that should have been retired, which is the behaviour this replaces; it can never
     * retire one that was wanted.
     */
    @Volatile
    private var abandonments = 0

    /**
     * The lock is mandatory, not defensive. `GameViewModel.runAiTurn` cancels the previous AI job
     * and relaunches immediately, and cancelling a coroutine does not interrupt a non-suspending
     * CPU loop — so two `Dispatchers.Default` threads can arrive here at once and would otherwise
     * mutate one board. The stale caller retires on [abandonSearch] or, failing that, its own
     * deadline.
     *
     * The flag is cleared here rather than in [abandonSearch] so the ordering cannot invert: the
     * stale search holds this lock, so a caller that abandons it and immediately asks for a new
     * move waits at the door until the old one has seen the flag and let go.
     *
     * The result is re-validated through [GameEngine] before it is returned. [Searcher] is a
     * transcription of the rules engine and a divergence in it should cost strength, never
     * legality; this line is what guarantees the difference.
     *
     * The ticket read on the first line is taken outside the lock on purpose — it has to be, as
     * it is the value at the moment this caller joined the queue, and everything after it is
     * spent waiting. See [abandonments].
     */
    override fun chooseAction(state: BoardState, playerId: PlayerId): GameAction {
        require(state.currentPlayer == playerId)
        val ticket = abandonments
        synchronized(this) {
            // Retired while queued: somebody asked for this search to stop after it was asked
            // for and before it got in. Returning at once is what collapses the queue; the
            // caller is a cancelled coroutine and its answer is discarded either way, so the
            // cheap legal move costs nothing and the budget it does not spend is the budget the
            // search the player is waiting for gets to start with.
            if (abandonments != ticket) return safeFallback(state, playerId)
            abandoned = false
            val engine = searcher ?: Searcher(config, clock) { abandoned }.also { searcher = it }
            val action = decode(engine.bestMove(state))
            if (action != null && gameEngine.perform(state, action) is ActionResult.Success) {
                return action
            }
            return safeFallback(state, playerId)
        }
    }

    /**
     * Deliberately not `@Synchronized`: taking the lock would mean waiting for the very search
     * this is trying to stop.
     *
     * The order of the two writes is the one that matters. [abandonments] is what a caller still
     * queued on the monitor compares against, and it is bumped first so that a caller which is
     * about to enter cannot read the old count and then find the flag already cleared by itself.
     */
    override fun abandonSearch() {
        abandonments++
        abandoned = true
    }

    private fun decode(move: Int): GameAction? = when {
        move >= WALL_MOVE_BASE && move < WALL_MOVE_BASE + WallGeometry.SLOT_COUNT ->
            GameAction.PlaceWall(WallGeometry.wallOf(move - WALL_MOVE_BASE))

        move in 0 until WallGeometry.CELL_COUNT ->
            GameAction.MovePawn(Position(WallGeometry.rowOf(move), WallGeometry.columnOf(move)))

        else -> null
    }

    /** The shortest legal step towards goal, straight out of the rules engine's own generator. */
    private fun safeFallback(state: BoardState, playerId: PlayerId): GameAction =
        actionGenerator.pawnActions(state)
            .minByOrNull { pathFinder.distance(it.target, playerId.goalRow, state.walls) }
            ?: actionGenerator.allValidActions(state).first()
}

/**
 * The search proper: one mutable board, one transposition table and a fixed set of scratch buffers,
 * all reused for the life of the instance.
 *
 * Everything is written against the integer encodings in [WallGeometry]: a move is a destination
 * cell (0..80) or [WALL_MOVE_BASE] plus a wall slot (128..255), and every score is from the point
 * of view of the side to move. The single negamax path is what makes the transposition table's
 * bound flags mean anything — separate maximising and minimising branches sharing one alpha/beta
 * pair is exactly where the previous engine filed fail-high bounds as exact scores.
 */
private class Searcher(
    private val config: SearchConfig,
    private val clock: SearchClock,
    private val abandoned: () -> Boolean,
) {
    private val board = FastBoard(Zobrist(config.zobristSeed))
    private val table = TranspositionTable(config.ttSizeLog2)

    private val moves = Array(Constants.Ai.SEARCH_MAX_PLY) { IntArray(MAX_MOVES_PER_PLY) }
    private val orderKeys = Array(Constants.Ai.SEARCH_MAX_PLY) { IntArray(MAX_MOVES_PER_PLY) }
    private val killers = Array(Constants.Ai.SEARCH_MAX_PLY) { IntArray(2) { NO_MOVE } }
    private val history = IntArray(2 * MOVE_CODES)

    /**
     * The two goal-distance fields of the node currently being expanded, and one spare for the
     * "after this wall" measurement.
     *
     * Sharing them across the whole tree is safe only because nothing reads them across a recursive
     * call: a node fills them, generates and scores its moves, and is finished with them before the
     * first child is searched.
     */
    private val fieldMe = IntArray(WallGeometry.CELL_COUNT)
    private val fieldYou = IntArray(WallGeometry.CELL_COUNT)
    private val fieldAfter = IntArray(WallGeometry.CELL_COUNT)

    private val pawnBuffer = IntArray(FastBoard.MAX_PAWN_MOVES)

    /** Wall candidates before they are filtered and capped; [seen] makes the union free. */
    private val candidates = IntArray(WallGeometry.SLOT_COUNT)
    private val candidateKeys = IntArray(WallGeometry.SLOT_COUNT)
    private val seen = LongArray(2)
    private val onOpponentPath = LongArray(2)
    private val onOwnPath = LongArray(2)

    /** The opponent's distance at each ply, kept so late-move reductions can consult it. */
    private val nodeOpponentDistance = IntArray(Constants.Ai.SEARCH_MAX_PLY)

    private val rootMoves = IntArray(MAX_MOVES_PER_PLY)
    private val rootScores = IntArray(MAX_MOVES_PER_PLY)
    private var rootCount = 0
    private var rootBest = NO_MOVE
    private var rootCompleted = 0

    private var nodes = 0L
    private var aborted = false
    private var deadline = 0L

    /**
     * Searches [state] and returns the chosen move, or [NO_MOVE] if the position offers none.
     *
     * Iterative deepening is not merely a way to respect a clock here: each iteration reorders the
     * root by the previous iteration's scores, which is most of what makes the alpha-beta window
     * close early at the next depth.
     */
    fun bestMove(state: BoardState): Int {
        board.loadFrom(state)
        table.newGeneration()
        decayHistory()
        clearKillers()
        aborted = false
        nodes = 0L
        val start = clock.nanoTime()
        deadline = start + config.hardBudgetMillis * MILLIS_TO_NANOS
        val softDeadline = start + config.softBudgetMillis * MILLIS_TO_NANOS

        rootCount = generateRoot()
        if (rootCount == 0) return NO_MOVE
        winningRootMove()?.let { return it }

        var best = rootMoves[0]
        var previousScore = 0
        var stable = 0
        for (depth in 1..config.maxDepth) {
            val previousBest = best
            val score = searchRoot(depth)
            // A root child that was fully searched before the abort is still knowledge; throwing
            // the whole iteration away is what let the previous engine spend its entire budget for
            // nothing.
            if (rootCompleted > 0) best = rootBest
            if (aborted) break
            stable = if (best == previousBest &&
                abs(score - previousScore) < Constants.Ai.STABLE_SCORE_WINDOW
            ) {
                stable + 1
            } else {
                0
            }
            previousScore = score
            if (abs(score) >= Constants.Ai.SEARCH_MATE_THRESHOLD) break
            // Several iterations that changed neither the move nor the assessment mean the budget
            // is being spent to confirm what is already known — but a reply that lands instantly
            // reads as careless from a tier called "expert", so there is a floor under it too.
            if (stable >= Constants.Ai.STABLE_ITERATIONS_TO_STOP &&
                clock.nanoTime() - start >= Constants.Ai.MIN_THINK_MILLIS * MILLIS_TO_NANOS
            ) {
                break
            }
            // Each iteration costs roughly four times the last, so one begun past the soft deadline
            // is one that will be thrown away.
            if (clock.nanoTime() >= softDeadline) break
            orderRootByScore()
        }
        return best
    }

    private fun searchRoot(depth: Int): Int {
        var alpha = -Constants.Ai.SEARCH_WIN_SCORE
        val beta = Constants.Ai.SEARCH_WIN_SCORE
        rootCompleted = 0
        var best = -Constants.Ai.SEARCH_WIN_SCORE
        for (index in 0 until rootCount) rootScores[index] = -Constants.Ai.SEARCH_WIN_SCORE
        for (index in 0 until rootCount) {
            val move = rootMoves[index]
            val from = board.pawn[board.side]
            makeMove(move)
            val score = -negamax(depth - 1, 1, -beta, -alpha)
            unmakeMove(move, from)
            if (aborted) break
            rootScores[index] = score
            rootCompleted++
            if (score > best) {
                best = score
                rootBest = move
            }
            if (score > alpha) alpha = score
        }
        return best
    }

    private fun negamax(depth: Int, ply: Int, alphaIn: Int, betaIn: Int): Int {
        if (aborted) return 0
        nodes++
        if (nodes >= config.maxNodes) {
            aborted = true
            return 0
        }
        // The clock and the abandon flag share one poll: a syscall and a volatile read are both
        // things not to do in an inner loop that runs a hundred thousand times a turn. At roughly
        // ten microseconds a node, 1024 nodes is about ten milliseconds of latency on giving up —
        // far below anything a player perceives, and three orders below the budget it replaces.
        if (nodes and Constants.Ai.SEARCH_NODE_POLL_MASK.toLong() == 0L &&
            (clock.nanoTime() >= deadline || abandoned())
        ) {
            aborted = true
            return 0
        }

        val winner = board.winnerOrNull()
        if (winner >= 0) {
            val win = Constants.Ai.SEARCH_WIN_SCORE - ply
            return if (winner == board.side) win else -win
        }

        // Mate-distance pruning: no line from here can be better than mating at once, so a window
        // that already promises more can be closed without a move being tried.
        var alpha = maxOf(alphaIn, -Constants.Ai.SEARCH_WIN_SCORE + ply)
        val beta = minOf(betaIn, Constants.Ai.SEARCH_WIN_SCORE - ply - 1)
        if (alpha >= beta) return alpha
        if (depth <= 0 || ply >= Constants.Ai.SEARCH_MAX_PLY - 1) return evaluate(ply)

        // The move set a node generates is ply-dependent — measured wall gains and up to
        // shallowWallCandidates at ply 1-2, static keys and deepWallCandidates from ply 3 on —
        // while the key covers only the position, so a score filed by a wide node can be returned
        // to a narrow one and the reverse. That is the ordinary price of forward pruning beside a
        // transposition table rather than a fault, and it is worth saying why here instead of
        // leaving it to be rediscovered as a bug.
        //
        // Nothing unsound reaches the board. The stored move is an ordering hint only: orderKey
        // raises it for a move this node generated and never sees it otherwise, so the table
        // cannot introduce a move the node did not produce for itself. The stored score is
        // depth-gated, so what it stands in for is never a deeper search than the one that
        // produced it. And the root files nothing, so the widest regime never enters the table at
        // all and the mixing is between two neighbouring ones.
        //
        // What is left is a value approximated under a different pruning context, which is already
        // true of every entry: reductions consult nodeOpponentDistance and the killers of that
        // ply, so the tree under a node has always depended on where the node sits. A search with
        // forward pruning is not computing a well-defined function of the position, and a key that
        // pretended otherwise — one spare bit for the regime — would halve the effective table to
        // make the arithmetic tidier and the play no better.
        var ttMove = NO_MOVE
        val entry = table.find(board.hash)
        if (entry >= 0) {
            // The move is worth having at any stored depth — it costs nothing and it is the single
            // best ordering hint available. The *score* is only usable at sufficient depth.
            ttMove = table.moveAt(entry)
            if (table.depthAt(entry) >= depth) {
                val stored = fromTable(table.scoreAt(entry), ply)
                when (table.flagAt(entry)) {
                    TranspositionTable.FLAG_EXACT -> return stored
                    TranspositionTable.FLAG_LOWER -> if (stored >= beta) return stored
                    TranspositionTable.FLAG_UPPER -> if (stored <= alpha) return stored
                }
            }
        }

        val mover = board.side
        val count = generateMoves(ply, ttMove)
        if (count == 0) return evaluate(ply)

        var best = -Constants.Ai.SEARCH_WIN_SCORE
        var bestMove = NO_MOVE
        var index = 0
        while (index < count) {
            selectNext(ply, index, count)
            val move = moves[ply][index]
            val from = board.pawn[board.side]
            makeMove(move)
            // Principal variation search: the first move gets the real window because ordering says
            // it is probably best, and every later move only has to be *disproved*, which a null
            // window does with a far smaller tree. A move that beats alpha anyway is re-searched
            // properly, so nothing is lost when the ordering was wrong.
            val score = if (index == 0) {
                -negamax(depth - 1, ply + 1, -beta, -alpha)
            } else {
                val reduction = reductionFor(move, ply, depth, index, ttMove)
                var trial = -negamax(depth - 1 - reduction, ply + 1, -alpha - 1, -alpha)
                if (trial > alpha && reduction > 0) {
                    trial = -negamax(depth - 1, ply + 1, -alpha - 1, -alpha)
                }
                if (trial > alpha && trial < beta) {
                    trial = -negamax(depth - 1, ply + 1, -beta, -alpha)
                }
                trial
            }
            unmakeMove(move, from)
            if (aborted) return 0
            if (score > best) {
                best = score
                bestMove = move
            }
            if (score > alpha) alpha = score
            if (alpha >= beta) {
                recordCutoff(ply, depth, mover, index)
                break
            }
            index++
        }

        // The flag describes the window the node was *entered* with. Filing it against the raised
        // alpha would turn every fail-low into a claimed exact score, which is precisely the fault
        // audited in the previous engine.
        val flag = when {
            best <= alphaIn -> TranspositionTable.FLAG_UPPER
            best >= beta -> TranspositionTable.FLAG_LOWER
            else -> TranspositionTable.FLAG_EXACT
        }
        table.store(board.hash, depth, flag, toTable(best, ply), bestMove)
        return best
    }

    /**
     * How much shallower a late move may be tried first.
     *
     * Walls only: in a tempo race the straight advance is never a quiet move, so reducing it would
     * be reducing the main line. The table's move and both killers are exempt because they are the
     * moves most likely to be the refutation, and the whole thing switches off when the opponent is
     * within two steps of home, where a missed wall is the game. Every fail-high re-searches at
     * full depth, so a reduction can delay finding a tactic by an iteration but cannot lose it.
     */
    private fun reductionFor(move: Int, ply: Int, depth: Int, index: Int, ttMove: Int): Int {
        val reducible = config.useLateMoveReductions &&
            depth >= Constants.Ai.LMR_MIN_DEPTH &&
            index >= Constants.Ai.LMR_MIN_MOVE_INDEX &&
            move >= WALL_MOVE_BASE &&
            move != ttMove &&
            move != killers[ply][0] &&
            move != killers[ply][1] &&
            nodeOpponentDistance[ply] > Constants.Ai.LMR_SAFE_OPPONENT_DISTANCE
        return if (reducible) 1 else 0
    }

    // ---- evaluation ----

    /**
     * The position from the side to move's point of view, in hundredths of a ply of race advantage.
     *
     * The whole evaluation is built on one exact identity rather than on a tuned tempo weight: with
     * me to move I reach my goal on ply `2*dMe - 1` and the opponent on ply `2*dYou`, so
     * `race = 2*dYou - 2*dMe + 1` is the true margin in plies, and it is always odd. Everything the
     * bot needs to know about walls follows from it. A wall costing the opponent one step leaves
     * `2*(dYou+1) - 2*dMe - 1`, the same number — tempo-neutral, and a wall poorer. A two-step wall
     * gains exactly two plies. "A wall must cost two steps or it is a losing trade" is therefore
     * not a rule this engine was given; it is what the arithmetic says.
     */
    private fun evaluate(ply: Int): Int {
        val me = board.side
        val you = 1 - me
        board.goalField(me, fieldMe)
        board.goalField(you, fieldYou)
        val dMe = fieldMe[board.pawn[me]]
        val dYou = fieldYou[board.pawn[you]]
        val race = 2 * dYou - 2 * dMe + 1
        val reserveMe = board.reserve[me]
        val reserveYou = board.reserve[you]

        // Sound because `BoardGraph.canTraverse` consults only the walls: with the opponent holding
        // none, my distance can never grow again, so a large enough margin is a decided game. The
        // margin is two full steps, which absorbs up to two tempi lost to pawn contact on the way.
        if (reserveYou == 0 && race >= Constants.Ai.PROVEN_RACE_MARGIN) {
            return Constants.Ai.SEARCH_PROVEN_WIN_SCORE - 2 * dMe - ply
        }
        if (reserveMe == 0 && race <= -Constants.Ai.PROVEN_RACE_MARGIN) {
            return -(Constants.Ai.SEARCH_PROVEN_WIN_SCORE - 2 * dYou - ply)
        }

        var score = raceTerm(race)
        if (reserveMe == 0 && reserveYou == 0) return score
        score += wallValue(reserveMe, reserveYou, dYou) - wallValue(reserveYou, reserveMe, dMe)
        score += centreValue(you, dYou) - centreValue(me, dMe)
        score += (
            progressDirections(board.pawn[me], fieldMe, dMe) -
                progressDirections(board.pawn[you], fieldYou, dYou)
            ) * Constants.Ai.FREEDOM_VALUE
        return score.coerceIn(-Constants.Ai.SEARCH_EVAL_CLAMP, Constants.Ai.SEARCH_EVAL_CLAMP)
    }

    /**
     * The race margin, tapered past four full steps.
     *
     * Without the taper the bot falls in love with building a twenty-step labyrinth while its own
     * position rots: past a certain margin another ply of delay is not worth another wall.
     */
    private fun raceTerm(race: Int): Int {
        val magnitude = abs(race)
        val value = if (magnitude <= Constants.Ai.RACE_LINEAR_PLIES) {
            magnitude * Constants.Ai.RACE_PLY_VALUE
        } else {
            Constants.Ai.RACE_LINEAR_PLIES * Constants.Ai.RACE_PLY_VALUE +
                (magnitude - Constants.Ai.RACE_LINEAR_PLIES) * Constants.Ai.RACE_PLY_VALUE /
                Constants.Ai.RACE_TAPER_DIVISOR
        }
        return if (race >= 0) value else -value
    }

    /**
     * What the walls still in hand are worth — an option, not progress.
     *
     * Priced strictly below one step (200) so a wall that delays the opponent a single step is
     * refused, and strictly above zero so the bot does not dump them on neutral placements. The
     * surplus term outweighs the base because in the middlegame the wall *difference* decides who
     * gets the last word, and it is clipped because past three spare walls you already have every
     * answer. The relevance taper states the real maxim: a wall is worth less against a pawn that
     * is nearly home, not merely "later in the game".
     */
    private fun wallValue(walls: Int, opponentWalls: Int, opponentDistance: Int): Int {
        if (walls == 0) return 0
        val raw = walls * Constants.Ai.WALL_BASE_VALUE +
            (walls - opponentWalls)
                .coerceIn(-Constants.Ai.WALL_SURPLUS_CAP, Constants.Ai.WALL_SURPLUS_CAP) *
            Constants.Ai.WALL_SURPLUS_VALUE +
            if (opponentWalls == 0) Constants.Ai.WALL_LAST_VALUE else 0
        return raw * minOf(opponentDistance, Constants.Ai.WALL_RELEVANCE_DISTANCE) /
            Constants.Ai.WALL_RELEVANCE_DISTANCE
    }

    /**
     * The cost of standing away from the centre file, at most a quarter of a step by construction.
     *
     * Sub-step on purpose: it may only break a tie, and the ties it breaks are the first half-dozen
     * moves, where nothing else in the evaluation distinguishes anything and the previous engine
     * broke them in favour of throwing a wall away.
     */
    private fun centreValue(player: Int, distance: Int): Int {
        val column = WallGeometry.columnOf(board.pawn[player])
        return Constants.Ai.CENTRE_VALUE * abs(column - CENTRE_COLUMN) *
            minOf(distance, Constants.Ai.CENTRE_RELEVANCE_DISTANCE) /
            Constants.Ai.CENTRE_RELEVANCE_DISTANCE
    }

    /**
     * How many of a pawn's open neighbours actually take it closer to goal.
     *
     * On an open board this is 1 for both players, so the term is dormant through the opening. It
     * wakes up when walls make one route a single-file corridor and the other redundant — the
     * difference between a wall costing the opponent one step and costing them six.
     */
    private fun progressDirections(cell: Int, field: IntArray, distance: Int): Int {
        var count = 0
        val bits = board.open[cell]
        for (direction in 0 until WallGeometry.DIRECTION_COUNT) {
            if (bits and WallGeometry.directionBit[direction] == 0) continue
            if (field[cell + WallGeometry.directionStep[direction]] < distance) count++
        }
        return count
    }

    // ---- move generation and ordering ----

    private fun generateRoot(): Int {
        val entry = table.find(board.hash)
        val ttMove = if (entry >= 0) table.moveAt(entry) else NO_MOVE
        val count = generateMoves(0, ttMove)
        for (index in 0 until count) selectNext(0, index, count)
        moves[0].copyInto(rootMoves, 0, 0, count)
        return count
    }

    /** The move that ends the game now, if the root has one — no search can improve on it. */
    private fun winningRootMove(): Int? {
        val goal = WallGeometry.goalRow[board.side]
        for (index in 0 until rootCount) {
            val move = rootMoves[index]
            if (move < WALL_MOVE_BASE && WallGeometry.rowOf(move) == goal) return move
        }
        return null
    }

    /** Reorders the root by the last completed iteration's scores, best first. */
    private fun orderRootByScore() {
        for (index in 1 until rootCount) {
            val move = rootMoves[index]
            val score = rootScores[index]
            var slot = index
            while (slot > 0 && rootScores[slot - 1] < score) {
                rootMoves[slot] = rootMoves[slot - 1]
                rootScores[slot] = rootScores[slot - 1]
                slot--
            }
            rootMoves[slot] = move
            rootScores[slot] = score
        }
    }

    private fun generateMoves(ply: Int, ttMove: Int): Int {
        val me = board.side
        val you = 1 - me
        board.goalField(me, fieldMe)
        board.goalField(you, fieldYou)
        val distanceMe = fieldMe[board.pawn[me]]
        val distanceYou = fieldYou[board.pawn[you]]
        nodeOpponentDistance[ply] = distanceYou

        val out = moves[ply]
        val keys = orderKeys[ply]
        var count = 0
        val goal = WallGeometry.goalRow[me]
        val pawnCount = board.pawnMoves(pawnBuffer)
        for (index in 0 until pawnCount) {
            val target = pawnBuffer[index]
            val base = if (WallGeometry.rowOf(target) == goal) {
                Constants.Ai.ORDER_WINNING_MOVE
            } else {
                pawnKey(target, distanceMe)
            }
            out[count] = target
            keys[count] = orderKey(target, base, me, ply, ttMove)
            count++
        }

        if (!wallsAreWorthGenerating(me, you, distanceMe, distanceYou)) return count
        return count + appendWalls(ply, ttMove, me, you, distanceMe, distanceYou, count)
    }

    /**
     * Two Quoridor facts stated as generation rules rather than left to the weights.
     *
     * The second is the important one: if the opponent has no walls left and I am ahead in the foot
     * race, my distance can never grow, so every wall I place hands over a free tempo for nothing.
     * That is why chasing with walls loses to somebody who simply runs.
     */
    private fun wallsAreWorthGenerating(
        me: Int,
        you: Int,
        distanceMe: Int,
        distanceYou: Int,
    ): Boolean {
        if (board.reserve[me] == 0) return false
        val race = 2 * distanceYou - 2 * distanceMe + 1
        return !(board.reserve[you] == 0 && race >= Constants.Ai.RUN_RACE_MARGIN)
    }

    private fun pawnKey(target: Int, distanceMe: Int): Int {
        val reached = fieldMe[target]
        val jump = if (reached <= distanceMe - 2) Constants.Ai.ORDER_JUMP_BONUS else 0
        return Constants.Ai.ORDER_PAWN_BASE -
            Constants.Ai.ORDER_PAWN_DISTANCE_STEP * reached + jump
    }

    /**
     * The final ordering key: the best of the move's own merit and its status as a remembered
     * refutation, plus a bounded history term.
     *
     * `maxOf` rather than a chain of overrides because a move that wins on the spot must stay first
     * even when it is also the table's move, and a killer must not demote a pawn advance below a
     * wall. The history term is clipped on both sides so it can nudge an order but never invert the
     * ranks above.
     */
    private fun orderKey(move: Int, base: Int, mover: Int, ply: Int, ttMove: Int): Int {
        val remembered = when (move) {
            ttMove -> Constants.Ai.ORDER_TT_MOVE
            killers[ply][0] -> Constants.Ai.ORDER_KILLER_PRIMARY
            killers[ply][1] -> Constants.Ai.ORDER_KILLER_SECONDARY
            else -> Int.MIN_VALUE
        }
        val learned = history[mover * MOVE_CODES + move]
            .coerceIn(-Constants.Ai.ORDER_HISTORY_CAP, Constants.Ai.ORDER_HISTORY_CAP)
        return maxOf(base, remembered) + learned
    }

    /**
     * Collects, filters, scores and caps the wall candidates, appending them to the node's move
     * list.
     *
     * @return how many were appended.
     */
    private fun appendWalls(
        ply: Int,
        ttMove: Int,
        me: Int,
        you: Int,
        distanceMe: Int,
        distanceYou: Int,
        offset: Int,
    ): Int {
        seen[0] = 0L
        seen[1] = 0L
        onOpponentPath[0] = 0L
        onOpponentPath[1] = 0L
        onOwnPath[0] = 0L
        onOwnPath[1] = 0L
        var scanned = 0
        scanned = collectPathBlockers(
            board.pawn[you],
            fieldYou,
            Constants.Ai.WALL_PATH_EDGES_OPPONENT,
            onOpponentPath,
            scanned,
        )
        scanned = collectPathBlockers(
            board.pawn[me], fieldMe, Constants.Ai.WALL_PATH_EDGES_OWN, onOwnPath, scanned,
        )
        scanned = collectExtensions(scanned)
        scanned = collectShoulders(board.pawn[you], scanned)

        val scored = if (ply <= Constants.Ai.WALL_SCORED_MAX_PLY) {
            scoreByGain(scanned, me, you, distanceMe, distanceYou)
        } else {
            scoreStatically(scanned, board.pawn[you])
        }

        val cap = when {
            ply == 0 -> config.rootWallCandidates
            ply <= Constants.Ai.WALL_SCORED_MAX_PLY -> config.shallowWallCandidates
            else -> config.deepWallCandidates
        }
        val out = moves[ply]
        val keys = orderKeys[ply]
        val taken = minOf(scored, cap, MAX_MOVES_PER_PLY - offset)
        for (index in 0 until taken) {
            selectBestCandidate(index, scored)
            val move = WALL_MOVE_BASE + candidates[index]
            out[offset + index] = move
            keys[offset + index] = orderKey(
                move,
                Constants.Ai.ORDER_WALL_BASE + candidateKeys[index],
                me,
                ply,
                ttMove,
            )
        }
        return taken
    }

    /**
     * Walks a shortest path downhill through [field] and collects the slots that block each of its
     * first [maxEdges] steps, marking them in [pathMask].
     *
     * Applied to the opponent's field these are the attacking walls; applied to my own they are the
     * prophylactic ones — occupying a slot before the opponent can, which by the adjacency rule
     * kills the two collinear slots beside it as well. The previous generator produced that second
     * class and then discarded it with a `take`, so the bot had no defensive vocabulary at all.
     *
     * Truncated at a handful of edges because a wall six squares along a path is routed around long
     * before the pawn arrives, and the path is recomputed at every node anyway.
     */
    private fun collectPathBlockers(
        start: Int,
        field: IntArray,
        maxEdges: Int,
        pathMask: LongArray,
        scanned: Int,
    ): Int {
        var count = scanned
        var cell = start
        var edges = 0
        while (edges < maxEdges && field[cell] > 0) {
            val next = downhillNeighbour(cell, field)
            if (next < 0) break
            count = addBlockingSlots(cell, next, pathMask, count)
            cell = next
            edges++
        }
        return count
    }

    private fun downhillNeighbour(cell: Int, field: IntArray): Int {
        val bits = board.open[cell]
        val target = field[cell] - 1
        for (direction in 0 until WallGeometry.DIRECTION_COUNT) {
            if (bits and WallGeometry.directionBit[direction] == 0) continue
            val next = cell + WallGeometry.directionStep[direction]
            if (field[next] == target) return next
        }
        return -1
    }

    /** The one or two slots that would block the step between two adjacent cells. */
    private fun addBlockingSlots(from: Int, to: Int, pathMask: LongArray, scanned: Int): Int {
        var count = scanned
        val fromRow = WallGeometry.rowOf(from)
        val fromColumn = WallGeometry.columnOf(from)
        if (fromRow != WallGeometry.rowOf(to)) {
            val row = minOf(fromRow, WallGeometry.rowOf(to))
            for (column in fromColumn - 1..fromColumn) {
                val slot = horizontalSlotOrNull(row, column)
                if (slot >= 0) count = addCandidate(slot, pathMask, count)
            }
        } else {
            val column = minOf(fromColumn, WallGeometry.columnOf(to))
            for (row in fromRow - 1..fromRow) {
                val slot = verticalSlotOrNull(row, column)
                if (slot >= 0) count = addCandidate(slot, pathMask, count)
            }
        }
        return count
    }

    /**
     * Every slot sharing a post with a placed wall.
     *
     * This is where the two-wall staircase lives. The second wall of a trap is frequently *not* on
     * the current shortest path, because the point of the first wall was to move the path onto the
     * square the second one attacks.
     */
    private fun collectExtensions(scanned: Int): Int {
        var count = scanned
        for (word in 0..1) {
            var bits = board.occupied[word]
            while (bits != 0L) {
                val slot = word * Long.SIZE_BITS + bits.countTrailingZeroBits()
                bits = bits and (bits - 1)
                for (neighbour in WallGeometry.slotNeighbours[slot]) {
                    count = addCandidate(neighbour, null, count)
                }
            }
        }
        return count
    }

    /** Slots beside and just ahead of the opponent pawn, where a wall meets it, not trails it. */
    private fun collectShoulders(opponentCell: Int, scanned: Int): Int {
        var count = scanned
        val row = WallGeometry.rowOf(opponentCell)
        val column = WallGeometry.columnOf(opponentCell)
        for (slotRow in row - 2..row + 1) {
            if (slotRow !in 0 until WallGeometry.WALL_GRID_SIZE) continue
            for (slotColumn in column - 1..column) {
                if (slotColumn !in 0 until WallGeometry.WALL_GRID_SIZE) continue
                count = addCandidate(horizontalSlotOrNull(slotRow, slotColumn), null, count)
                count = addCandidate(verticalSlotOrNull(slotRow, slotColumn), null, count)
            }
        }
        return count
    }

    private fun addCandidate(slot: Int, pathMask: LongArray?, scanned: Int): Int {
        if (slot < 0) return scanned
        val word = slot ushr 6
        val bit = 1L shl (slot and 63)
        pathMask?.let { it[word] = it[word] or bit }
        if (seen[word] and bit != 0L) return scanned
        seen[word] = seen[word] or bit
        candidates[scanned] = slot
        return scanned + 1
    }

    /**
     * Prices each surviving candidate by the plies it actually gains, measured through the board.
     *
     * Two searches per candidate is affordable at the root and across the hundred or so nodes of
     * ply 2, and ruinous across the thousands at ply 3 — which is exactly where the line is drawn.
     * A wall that lengthens my own path more than the opponent's is discarded outright; one that
     * gains nothing is discarded too, unless so few candidates survive that the node would be left
     * with no wall vocabulary at all.
     *
     * A free wall on my own route is the exception, and it has to be: its whole value is that it
     * makes the three slots around it illegal for the opponent, which no measurement taken before
     * the opponent replies can see. Filtering on immediate gain alone would delete exactly the
     * class the own-path source was added to produce.
     *
     * @return how many candidates survive, compacted to the front of [candidates].
     */
    private fun scoreByGain(
        scanned: Int,
        me: Int,
        you: Int,
        distanceMe: Int,
        distanceYou: Int,
    ): Int {
        var kept = 0
        var positive = 0
        for (index in 0 until scanned) {
            val slot = candidates[index]
            if (!board.isWallLegal(slot, config.useAnchorFilter)) continue
            board.makeWall(slot)
            board.goalField(you, fieldAfter)
            val afterYou = fieldAfter[board.pawn[you]]
            board.goalField(me, fieldAfter)
            val afterMe = fieldAfter[board.pawn[me]]
            board.unmakeWall(slot)
            val deltaMe = afterMe - distanceMe
            val deltaYou = afterYou - distanceYou
            if (deltaMe > 0 && deltaYou <= deltaMe) continue
            val gain = 2 * deltaYou - 2 * deltaMe
            if (gain > 0) positive++
            candidates[kept] = slot
            candidateKeys[kept] = if (gain == 0 && isMarked(onOwnPath, slot)) {
                PROPHYLACTIC_KEY
            } else {
                gain * GAIN_KEY_SCALE
            }
            kept++
        }
        if (positive < MIN_WALL_CANDIDATES) return kept
        var compacted = 0
        for (index in 0 until kept) {
            if (candidateKeys[index] <= 0) continue
            candidates[compacted] = candidates[index]
            candidateKeys[compacted] = candidateKeys[index]
            compacted++
        }
        return compacted
    }

    /**
     * Prices each surviving candidate by table lookups alone, for the plies where a measurement is
     * unaffordable. The keys say what a strong player looks at first: the opponent's route, the
     * walls already on the board, and the squares the opponent is about to occupy.
     *
     * @return how many candidates survive, compacted to the front of [candidates].
     */
    private fun scoreStatically(scanned: Int, opponentCell: Int): Int {
        var kept = 0
        val opponentRow = WallGeometry.rowOf(opponentCell)
        for (index in 0 until scanned) {
            val slot = candidates[index]
            if (!board.isWallLegal(slot, config.useAnchorFilter)) continue
            var key = 0
            if (isMarked(onOpponentPath, slot)) key += Constants.Ai.STATIC_KEY_OPPONENT_PATH
            if (touchesWall(slot)) key += Constants.Ai.STATIC_KEY_TOUCHES_WALL
            if (abs(WallGeometry.slotRow(slot) - opponentRow) <= NEAR_OPPONENT_ROWS) {
                key += Constants.Ai.STATIC_KEY_NEAR_OPPONENT
            }
            if (isMarked(onOwnPath, slot)) key -= Constants.Ai.STATIC_KEY_OWN_PATH
            candidates[kept] = slot
            candidateKeys[kept] = key
            kept++
        }
        return kept
    }

    private fun isMarked(mask: LongArray, slot: Int): Boolean =
        mask[slot ushr 6] and (1L shl (slot and 63)) != 0L

    private fun touchesWall(slot: Int): Boolean =
        board.postTouch[WallGeometry.postEndA[slot]] > 0 ||
            board.postTouch[WallGeometry.postCentre[slot]] > 0 ||
            board.postTouch[WallGeometry.postEndB[slot]] > 0

    private fun selectBestCandidate(from: Int, count: Int) {
        var best = from
        for (index in from + 1 until count) {
            if (candidateKeys[index] > candidateKeys[best]) best = index
        }
        if (best == from) return
        val slot = candidates[from]
        val key = candidateKeys[from]
        candidates[from] = candidates[best]
        candidateKeys[from] = candidateKeys[best]
        candidates[best] = slot
        candidateKeys[best] = key
    }

    /**
     * Brings the best remaining move to [from] by a single scan.
     *
     * Lazy selection rather than a sort because after a cutoff a node has typically searched one to
     * three of its moves; ordering the rest is work thrown away. It also keeps the distance lookup
     * out of a comparator, where the previous engine ran a full A* on every comparison.
     */
    private fun selectNext(ply: Int, from: Int, count: Int) {
        val list = moves[ply]
        val keys = orderKeys[ply]
        var best = from
        for (index in from + 1 until count) {
            if (keys[index] > keys[best]) best = index
        }
        if (best == from) return
        val move = list[from]
        val key = keys[from]
        list[from] = list[best]
        keys[from] = keys[best]
        list[best] = move
        keys[best] = key
    }

    // ---- bookkeeping ----

    private fun makeMove(move: Int) {
        if (move >= WALL_MOVE_BASE) board.makeWall(move - WALL_MOVE_BASE) else board.makePawn(move)
    }

    private fun unmakeMove(move: Int, from: Int) {
        if (move >= WALL_MOVE_BASE) {
            board.unmakeWall(move - WALL_MOVE_BASE)
        } else {
            board.unmakePawn(move, from)
        }
    }

    /**
     * Records the move that caused a beta cutoff, and penalises the ones tried before it — those
     * are the moves this node demonstrably ordered too early.
     */
    private fun recordCutoff(ply: Int, depth: Int, mover: Int, cutIndex: Int) {
        val move = moves[ply][cutIndex]
        if (killers[ply][0] != move) {
            killers[ply][1] = killers[ply][0]
            killers[ply][0] = move
        }
        history[mover * MOVE_CODES + move] += depth * depth
        for (index in 0 until cutIndex) {
            history[mover * MOVE_CODES + moves[ply][index]] -= depth
        }
    }

    /** Halved rather than cleared, so ordering carries between turns without ossifying. */
    private fun decayHistory() {
        for (index in history.indices) history[index] /= 2
    }

    private fun clearKillers() {
        for (ply in killers.indices) {
            killers[ply][0] = NO_MOVE
            killers[ply][1] = NO_MOVE
        }
    }

    /**
     * Mate scores are stored relative to the node, not to the root: an entry filed six plies deep
     * and probed two plies deep describes a mate at a different distance, and without this the
     * table would report a win that is nearer or further than it is.
     */
    private fun toTable(score: Int, ply: Int): Int = when {
        score > Constants.Ai.SEARCH_MATE_THRESHOLD -> score + ply
        score < -Constants.Ai.SEARCH_MATE_THRESHOLD -> score - ply
        else -> score
    }

    private fun fromTable(score: Int, ply: Int): Int = when {
        score > Constants.Ai.SEARCH_MATE_THRESHOLD -> score - ply
        score < -Constants.Ai.SEARCH_MATE_THRESHOLD -> score + ply
        else -> score
    }

    private fun horizontalSlotOrNull(row: Int, column: Int): Int =
        if (row in 0 until WallGeometry.WALL_GRID_SIZE &&
            column in 0 until WallGeometry.WALL_GRID_SIZE
        ) {
            (row * WallGeometry.WALL_GRID_SIZE + column) * 2
        } else {
            -1
        }

    private fun verticalSlotOrNull(row: Int, column: Int): Int {
        val slot = horizontalSlotOrNull(row, column)
        return if (slot < 0) -1 else slot + 1
    }

    private companion object {
        const val MILLIS_TO_NANOS = 1_000_000L
        const val CENTRE_COLUMN = Constants.Board.SIZE / 2

        /** Turns a gain in plies into an ordering key that dominates the history term. */
        const val GAIN_KEY_SCALE = 1_000

        /** Ranks a free wall on my own route last among walls, but keeps it in the list. */
        const val PROPHYLACTIC_KEY = 1

        /** Below this many gaining walls a node keeps the neutral ones rather than go blind. */
        const val MIN_WALL_CANDIDATES = 4

        const val NEAR_OPPONENT_ROWS = 2
    }
}
