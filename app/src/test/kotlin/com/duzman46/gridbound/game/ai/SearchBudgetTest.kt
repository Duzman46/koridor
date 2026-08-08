package com.duzman46.gridbound.game.ai

import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.TestFixtures
import com.duzman46.gridbound.game.ai.search.SearchClock
import com.duzman46.gridbound.game.ai.search.SearchConfig
import com.duzman46.gridbound.game.models.ActionResult
import com.duzman46.gridbound.game.models.BoardState
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * EXPERT at the configuration it actually ships with, which is the one nothing else tests.
 *
 * Every other construction of EXPERT in this module overrides `maxNodes` down to a few thousand, or
 * `maxDepth` down to four, or both, and hands the engine a clock that never advances. That is the
 * right trade for a strength match — a stopped clock is what makes a seeded result reproducible —
 * but it leaves three parts of the search unreachable. Late move reductions need `LMR_MIN_DEPTH`
 * plies and a fifth move at a node before they fire at all. The soft-deadline break and the
 * stability early exit are both gated on a clock that moves. None of the three has ever run in a
 * test.
 *
 * The fix is not a real clock; it is a *deterministic* one. [SteppingClock] advances a fixed amount
 * on every read, so `softBudgetMillis = 700` becomes a fixed number of polls rather than a race
 * against whatever else the machine is doing. The search then reads the shipped budgets, takes the
 * shipped branches and stops at the same point on every machine, which is exactly the property the
 * stopped clock buys elsewhere — without switching the budgets off.
 *
 * [expertOnARealClock] is the same thing without the pretence, gated behind `-Dstrength=1` because
 * it costs the budget it is measuring.
 */
class SearchBudgetTest {
    /**
     * The shipped configuration, unmodified, stopping where the shipped configuration says.
     *
     * Two bounds, and the floor is the interesting one. The ceiling only says the hard deadline is
     * reachable; the floor says the search did not stop on `maxDepth`, on a mate score, or on the
     * stability exit before `MIN_THINK_MILLIS` — that a tier called *Uzman* spent its budget rather
     * than replying instantly, which is the one thing the adaptive-spend logic exists to guarantee
     * and the one thing a node-capped test can never observe.
     */
    @Test
    fun expertSpendsItsShippedBudget() {
        positions.forEach { state ->
            val clock = SteppingClock(STEP_MILLIS)
            val action = engine(SearchConfig.EXPERT, clock).chooseAction(state, state.currentPlayer)

            assertTrue(
                "the shipped configuration produced $action, which the rules engine refuses",
                TestFixtures.engine.perform(state, action) is ActionResult.Success,
            )
            assertTrue(
                "the search stopped after ${clock.elapsedMillis} ms of its own clock, below the " +
                    "${Constants.Ai.MIN_THINK_MILLIS} ms floor",
                clock.elapsedMillis >= Constants.Ai.MIN_THINK_MILLIS,
            )
            assertTrue(
                "the search ran ${clock.elapsedMillis} ms past a " +
                    "${SearchConfig.EXPERT.hardBudgetMillis} ms hard budget",
                clock.elapsedMillis <= SearchConfig.EXPERT.hardBudgetMillis + STEP_MILLIS,
            )
        }
    }

    /**
     * The soft-deadline break is load-bearing, not decoration.
     *
     * Iterative deepening costs roughly four times as much per iteration, so one begun past the
     * soft deadline is an iteration whose work is thrown away — which is only true if the check
     * ends the loop. Quartering the soft budget must therefore end the search sooner, and on the
     * same clock it must never end it later: the two searches are identical up to the point where
     * one of them breaks.
     *
     * Stated over the whole sample rather than per position because a single position can stop for
     * another reason first — a mate score, or three stable iterations past the think floor — and
     * that would be the search behaving correctly, not the break failing to fire.
     */
    @Test
    fun aSmallerSoftBudgetStopsTheSearchSooner() {
        val impatient = SearchConfig.EXPERT.copy(
            softBudgetMillis = SearchConfig.EXPERT.softBudgetMillis / IMPATIENT_DIVISOR,
        )
        var shipped = 0L
        var shortened = 0L
        positions.forEach { state ->
            val shippedClock = SteppingClock(STEP_MILLIS)
            val shortenedClock = SteppingClock(STEP_MILLIS)
            engine(SearchConfig.EXPERT, shippedClock).chooseAction(state, state.currentPlayer)
            engine(impatient, shortenedClock).chooseAction(state, state.currentPlayer)

            assertTrue(
                "a ${impatient.softBudgetMillis} ms soft budget thought for " +
                    "${shortenedClock.elapsedMillis} ms, longer than the " +
                    "${SearchConfig.EXPERT.softBudgetMillis} ms one's " +
                    "${shippedClock.elapsedMillis} ms",
                shortenedClock.elapsedMillis <= shippedClock.elapsedMillis,
            )
            shipped += shippedClock.elapsedMillis
            shortened += shortenedClock.elapsedMillis
        }
        assertTrue(
            "both budgets thought for the same $shipped ms in total, so nothing in the search is " +
                "reading the soft deadline",
            shortened < shipped,
        )
    }

    /**
     * The same positions on `System.nanoTime()`, which is the only way to find out whether 700 ms
     * of real budget is 700 ms of real search.
     *
     * Gated because it costs what it measures — a second a position, against a file that otherwise
     * runs in a couple. The ceiling is generous on purpose: it is a canary for an abort check that
     * never fires, not a latency measurement, and the first call also pays for a cold JIT.
     */
    @Test
    fun expertOnARealClock() {
        Assume.assumeTrue(System.getProperty(HEAVY_PROPERTY) != null)
        positions.forEach { state ->
            val startedAt = System.nanoTime()
            val action = engine(SearchConfig.EXPERT, SearchClock.SYSTEM)
                .chooseAction(state, state.currentPlayer)
            val elapsed = (System.nanoTime() - startedAt) / NANOS_TO_MILLIS

            assertTrue(
                "the shipped configuration produced $action, which the rules engine refuses",
                TestFixtures.engine.perform(state, action) is ActionResult.Success,
            )
            assertTrue(
                "a decision took $elapsed ms against a " +
                    "${SearchConfig.EXPERT.hardBudgetMillis} ms hard budget",
                elapsed < SearchConfig.EXPERT.hardBudgetMillis * REAL_CLOCK_SLACK,
            )
        }
    }

    private fun engine(config: SearchConfig, clock: SearchClock): SearchAI = SearchAI(
        AIActionGenerator(TestFixtures.moves, TestFixtures.walls, TestFixtures.aStar),
        TestFixtures.engine,
        TestFixtures.aStar,
        config,
        clock,
    )

    /**
     * A clock that advances a fixed step on every read.
     *
     * The step turns a budget in milliseconds into a count of polls, and the search polls at fixed
     * points — once before the first iteration, once per `SEARCH_NODE_POLL_MASK + 1` nodes and
     * once at the end of each iteration that does not break first. So the budget still governs
     * when the search stops, but where it stops is a function of the tree and not of the machine.
     */
    private class SteppingClock(stepMillis: Long) : SearchClock {
        private val stepNanos = stepMillis * NANOS_TO_MILLIS
        private var reads = 0L

        /** What the search itself saw pass: the span between its first read and its last. */
        val elapsedMillis: Long
            get() = (reads - 1).coerceAtLeast(0L) * stepNanos / NANOS_TO_MILLIS

        override fun nanoTime(): Long = reads++ * stepNanos
    }

    private companion object {
        const val NANOS_TO_MILLIS = 1_000_000L

        /**
         * Sized so the shipped soft budget buys roughly seventy polls, a search of the same order
         * as the hundred thousand nodes the design's own arithmetic expects from 700 ms on a
         * mid-range handset. Smaller would be more faithful and slower; this file is not the place
         * to spend seconds.
         */
        const val STEP_MILLIS = 10L

        const val IMPATIENT_DIVISOR = 4

        /** Covers a cold JIT and the up-to-1024-node granularity of the deadline poll. */
        const val REAL_CLOCK_SLACK = 3

        const val HEAVY_PROPERTY = "strength"
        const val POSITION_COUNT = 5
        const val POSITION_SEED = 20260811L

        /**
         * Mid-game rather than opening positions: both sides still hold walls, so neither the
         * proven-race score nor an empty wall generator can end the search before the budget does.
         */
        val positions: List<BoardState> =
            StrengthHarness.midGamePositions(POSITION_COUNT, POSITION_SEED)
    }
}
