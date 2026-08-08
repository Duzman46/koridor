package com.duzman46.gridbound.game.ai

import com.duzman46.gridbound.game.TestFixtures
import com.duzman46.gridbound.game.ai.search.SearchClock
import com.duzman46.gridbound.game.ai.search.SearchConfig
import java.util.Locale
import kotlin.math.ceil
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test

/**
 * The answer to "the hard bot plays badly", stated as numbers instead of as an opinion.
 *
 * Every threshold below is a one-sided binomial tail against a coin, so each one carries its own
 * sample size, and the unit is the **opening** rather than the game — the two halves of a
 * colour-swapped pair are the same position played by the same two deterministic engines with the
 * labels exchanged, which is one observation and not two. That assertion is deliberate friction. A
 * threshold that fails cannot be repaired by lowering it, because a lower bar has a fatter tail and
 * [assertStronger] rejects any bar a coin clears more than one time in twenty.
 *
 * Every bar is also set below the sweep it is measuring. A tuning pass on `FREEDOM_VALUE` or
 * `RACE_LINEAR_PLIES` is expected to flip individual deterministic games, and a bar with no slack
 * turns that into a red build whose obvious repair is to ask for less — the exact habit this file
 * exists to prevent. Slack is bought with openings, never with a lower threshold.
 *
 * [mirrorMatchIsExactlyLevel] comes first for a reason: it is the only test here that can fail for
 * a reason unrelated to strength. Two behaviourally identical engines play each opening twice with
 * the colours exchanged, so the challenger's score must be exactly half. Any other number means the
 * pairing leaks state between games and every other figure in this file is meaningless.
 *
 * All matches are played once and cached, so the seven threshold tests and the three property tests
 * share one set of games rather than replaying them ten times.
 */
class StrengthTest {
    /**
     * The pairing itself. Not a strength measurement — a proof that the measurements are worth
     * reading.
     */
    @Test
    fun mirrorMatchIsExactlyLevel() {
        val match = mirror
        assertEquals(
            "an engine against a copy of itself must score exactly half\n${match.report()}",
            match.gameCount / 2.0,
            match.score,
            0.0,
        )
    }

    /**
     * The rung the whole "one engine, two budgets" design rests on, and the only pair in the ladder
     * separated by nothing but [SearchConfig] values.
     *
     * The budgets are in the ratio the tiers actually ship at — `EXPERT_SOFT_BUDGET_MILLIS` is
     * three and a half times `HARD_SOFT_BUDGET_MILLIS` — because that is the ladder a player meets.
     * Measured that way EXPERT takes 10 openings of 20 and loses none.
     *
     * **Not a claim that EXPERT's configuration is stronger at an equal budget.** Handed the same
     * node cap the two score 37-27 in games and 9-4 in openings over the whole book, which a
     * sign test puts at p = 0.13: a real edge, and not one this file would accept as evidence. The
     * depth cap is what the extra clock buys, so the honest statement is about the pair of budgets
     * and not about the shape of the configuration alone.
     *
     * What it catches: `HARD_MAX_DEPTH` raised to EXPERT's turns 10-0 into 7-2 and fails both
     * halves of the bar, which is the ladder inversion no other test here would notice. What it
     * does not catch: swapping the two tiers' wall-candidate constants, which moves the result to
     * 9-1 and stays green — those constants are worth about one opening in twenty, and a bar tight
     * enough to see them would have no slack left for a tuning pass.
     */
    @Test
    fun expertBeatsHard() = assertStronger(expertVsHard, minimumOpeningsWon = 8)

    @Test
    fun expertBeatsMediumInSeededMatch() = assertStronger(expertVsMedium, minimumOpeningsWon = 8)

    /**
     * The gate the whole exercise exists for: the new tier against the exact engine the owner is
     * complaining about, frozen in [LegacyHardAI] so it can never quietly improve.
     *
     * The narrowest bar in the file, and for runtime rather than for want of confidence. The frozen
     * engine re-validates every wall candidate twice per node and runs an A* inside a sort
     * comparator, so one of its moves costs more than a whole EXPERT search and this single match is
     * most of [SUITE_BUDGET_MILLIS]. Eight openings buys a bar that survives an opening flipping
     * outright — already more slack than the nine-games-of-ten it replaces — and still leaves the
     * file room to run on a machine that is building the release at the same time.
     */
    @Test
    fun expertBeatsFrozenHard() = assertStronger(expertVsLegacyHard, minimumOpeningsWon = 7)

    @Test
    fun hardBeatsMedium() = assertStronger(hardVsMedium, minimumOpeningsWon = 8)

    /** The bottom of the ladder, which the rungs above are only meaningful relative to. */
    @Test
    fun mediumBeatsEasy() = assertStronger(mediumVsEasy, minimumOpeningsWon = 8)

    /**
     * A tier called expert losing to random play is a bug, not a bad day — but "zero losses of ten"
     * is a bar that any single flipped game turns red, and the repair anybody would reach for is a
     * larger allowance rather than a larger sample. So the allowance is granted in advance and paid
     * for with games: one loss in twenty is still nineteen of twenty, which is far past anything
     * this file calls evidence, and the opening bar under it is asserted like every other rung.
     */
    @Test
    fun expertLosesAtMostOneGameToEasy() {
        val match = expertVsEasy
        assertStronger(match, minimumOpeningsWon = 8)
        assertTrue(
            "${match.losses} losses to random play; at most $MAX_EASY_LOSSES is a bad day\n" +
                match.report(),
            match.losses <= MAX_EASY_LOSSES,
        )
    }

    /**
     * The harness aborts a match the moment `GameEngine` rejects an action, so reaching this
     * assertion at all is the result. It is stated as its own test so that a failure reads as
     * "an engine produced an illegal move" rather than as a threshold that happened to miss.
     */
    @Test
    fun everyActionIsLegal() {
        assertTrue(
            "no game was played, so nothing was refereed",
            allMatches.sumOf { match -> match.games.sumOf { it.plies } } > 0,
        )
    }

    /**
     * A harness where a third of the games are decided on remaining distance is measuring
     * shuffling, not play.
     */
    @Test
    fun fewerThanTenPercentAdjudicated() {
        val games = allMatches.sumOf { it.gameCount }
        val adjudicated = allMatches.sumOf { it.adjudicated }
        assertTrue(
            "$adjudicated of $games games reached the ${StrengthHarness.MAX_PLIES}-ply cap\n" +
                allMatches.joinToString("\n") { it.report() },
            adjudicated * 10 < games,
        )
    }

    /**
     * Every engine in the harness runs on a clock that never advances, so no deadline can stop it
     * and `maxNodes` is the only brake there is. This checks that the brake exists at all: a search
     * that ignored its node cap would run to `maxDepth = 20` and never come back, so the failure
     * this catches is orders of magnitude past the threshold, not near it.
     *
     * Not a latency assertion. Games share cores, so the figure includes whatever the other threads
     * were doing; [expertRespectsTimeBudget] is what measures how long a turn actually takes.
     */
    @Test
    fun noEngineHitsItsWallClockSafetyNet() {
        val slowest = allMatches.maxOf { it.slowestDecisionNanos } / NANOS_TO_MILLIS
        assertTrue(
            "slowest single decision was $slowest ms; nothing here is bounded by time, so a " +
                "decision approaching $SAFETY_NET_MILLIS ms means nothing is bounding it at all",
            slowest < SAFETY_NET_MILLIS,
        )
    }

    /**
     * The one test on a real clock, and the only thing that can catch an abort check that never
     * fires: the node cap is removed and the budget alone has to stop the search.
     *
     * The allowance is four times the hard budget because it also covers the first call's cold JIT
     * and the up-to-1024-node granularity of the deadline poll.
     */
    @Test
    fun expertRespectsTimeBudget() {
        val engine = SearchAI(
            AIActionGenerator(TestFixtures.moves, TestFixtures.walls, TestFixtures.aStar),
            TestFixtures.engine,
            TestFixtures.aStar,
            SearchConfig.EXPERT.copy(softBudgetMillis = 60L, hardBudgetMillis = 100L),
            SearchClock.SYSTEM,
        )
        StrengthHarness.midGamePositions(TIMED_POSITIONS, TIMED_POSITIONS_SEED).forEach { state ->
            val startedAt = System.nanoTime()
            engine.chooseAction(state, state.currentPlayer)
            val elapsed = (System.nanoTime() - startedAt) / NANOS_TO_MILLIS
            assertTrue(
                "a decision took $elapsed ms against a 100 ms hard budget",
                elapsed < TIMED_DECISION_CEILING_MILLIS,
            )
        }
    }

    /**
     * The tuning run: enough games and enough nodes to tell a real evaluation change from noise.
     *
     * Excluded from the default build because it is minutes, not seconds. Enable with
     * `-Dstrength=1` when moving an evaluation weight.
     */
    @Test
    fun heavyExpertVersusFrozenHard() {
        Assume.assumeTrue(System.getProperty(HEAVY_PROPERTY) != null)
        val match = StrengthHarness.play(
            "EXPERT ($HEAVY_NODES nodes) vs frozen HARD, heavy",
            StrengthHarness.expert(HEAVY_NODES),
            StrengthHarness.legacyHard(),
            openings = StrengthHarness.book.size,
        )
        println(match.report())
        assertStronger(match, ceil(match.openingCount * HEAVY_MIN_SHARE).toInt())
    }

    /**
     * Asserts that [match] is strong evidence, in that order: first that the bar is worth clearing,
     * then that it was cleared.
     *
     * The first assertion is what makes the second one honest. Without it a red test could always
     * be turned green by asking for less.
     *
     * **The trials are openings, not games.** Two deterministic engines play a colour-swapped pair
     * as one experiment: it is the same position with the labels exchanged, which is exactly why
     * [mirrorMatchIsExactlyLevel] is 6 of 12 by construction rather than on average. Counting the
     * two halves as independent coin flips squares the evidence out of thin air — an 8-of-8 sweep
     * reported as `p = 0.0039` when four openings cannot support better than `2^-4 = 0.0625`, which
     * is not evidence at all by this file's own [MAX_FALSE_POSITIVE].
     *
     * **The bar is a sign test with the ties discarded, pre-registered.** A pair the challenger
     * splits is no information about who is stronger, and there are a lot of them once both sides
     * search; requiring every opening to be *won* would make a real advantage unmeasurable. So the
     * bar is a pair of counts, `W` won and at most `L` lost, and the chance a coin clears it is
     * bounded above by the tail of `W` successes in `W + L` trials. That bound holds for any number
     * of level pairs: with `d` decided pairs the event needs `W` wins when `d <= W + L` and needs
     * all but `L` of them when `d > W + L`, and neither is likelier than the quoted figure.
     *
     * `L` is what gives a threshold slack in the direction that matters. A tuning pass that flips
     * one opening outright costs a win *and* adds a loss, so a bar with `L = 0` is a bar one
     * evaluation change turns red.
     */
    private fun assertStronger(
        match: MatchResult,
        minimumOpeningsWon: Int,
        maximumOpeningsLost: Int = MAX_OPENINGS_LOST,
    ) {
        val tail = StrengthHarness.binomialTailAtLeast(
            minimumOpeningsWon,
            minimumOpeningsWon + maximumOpeningsLost,
        )
        // Locale.ROOT because a p-value is a number the reader compares against another one, and a
        // decimal comma in a pasted failure message reads as a thousands separator.
        val printed = "%.4f".format(Locale.ROOT, tail)
        assertTrue(
            "$minimumOpeningsWon won and $maximumOpeningsLost lost is not evidence of anything: " +
                "a coin reaches it with probability $printed",
            tail <= MAX_FALSE_POSITIVE,
        )
        assertTrue(
            "openings ${match.openingsWon}-${match.openingsLost} of ${match.openingCount}, " +
                "needed at least $minimumOpeningsWon won and at most $maximumOpeningsLost lost " +
                "(one-sided p = $printed)\n${match.report()}",
            match.openingsWon >= minimumOpeningsWon &&
                match.openingsLost <= maximumOpeningsLost,
        )
    }

    /** JUnit needs the class fixtures below to be public statics, so this companion cannot be. */
    companion object {
        const val NANOS_TO_MILLIS = 1_000_000L

        /** A threshold a coin clears more often than this is not a measurement. */
        const val MAX_FALSE_POSITIVE = 0.05

        const val SAFETY_NET_MILLIS = 5_000L
        const val SUITE_BUDGET_MILLIS = 45_000L

        const val TIMED_POSITIONS = 20
        const val TIMED_POSITIONS_SEED = 20260809L
        const val TIMED_DECISION_CEILING_MILLIS = 400L

        const val HEAVY_PROPERTY = "strength"
        const val HEAVY_NODES = 20_000L
        const val HEAVY_MIN_SHARE = 0.75

        internal val mirror: MatchResult by lazy {
            StrengthHarness.play(
                "EXPERT vs an identical EXPERT",
                StrengthHarness.expert(MIRROR_NODES),
                StrengthHarness.expert(MIRROR_NODES),
                openings = 6,
            )
        }

        /**
         * The two shipped search tiers against each other, at the budget ratio they ship at.
         *
         * `maxNodes` stands in for the clock everywhere in this file, so the ratio is the whole
         * translation: EXPERT's soft budget is three and a half times HARD's, and it gets three and
         * a half times the nodes. Making the budgets equal instead measures the shape of the two
         * configurations rather than the ladder a player meets, and the engine does not clearly win
         * that comparison — see [StrengthTest.expertBeatsHard].
         */
        internal val expertVsHard: MatchResult by lazy {
            StrengthHarness.play(
                "EXPERT vs HARD, at the shipped budget ratio",
                StrengthHarness.expert(TIER_LADDER_NODES * EXPERT_BUDGET_NUMERATOR / 2),
                StrengthHarness.hard(TIER_LADDER_NODES),
                openings = 20,
            )
        }

        internal val expertVsMedium: MatchResult by lazy {
            StrengthHarness.play(
                "EXPERT vs MEDIUM",
                StrengthHarness.expert(EXPERT_VS_MEDIUM_NODES),
                StrengthHarness.medium(),
                openings = 12,
            )
        }

        internal val expertVsLegacyHard: MatchResult by lazy {
            StrengthHarness.play(
                "EXPERT vs the frozen shipped HARD",
                StrengthHarness.expert(EXPERT_VS_LEGACY_NODES),
                StrengthHarness.legacyHard(),
                openings = 8,
            )
        }

        internal val hardVsMedium: MatchResult by lazy {
            StrengthHarness.play(
                "HARD vs MEDIUM",
                StrengthHarness.hard(HARD_VS_MEDIUM_NODES),
                StrengthHarness.medium(),
                openings = 10,
            )
        }

        internal val mediumVsEasy: MatchResult by lazy {
            StrengthHarness.play(
                "MEDIUM vs EASY",
                StrengthHarness.medium(),
                StrengthHarness.easy(EASY_SEED),
                openings = 10,
            )
        }

        internal val expertVsEasy: MatchResult by lazy {
            StrengthHarness.play(
                "EXPERT vs EASY",
                StrengthHarness.expert(EXPERT_VS_EASY_NODES),
                StrengthHarness.easy(EASY_SEED),
                openings = 10,
            )
        }

        internal val allMatches: List<MatchResult> by lazy {
            listOf(
                mirror,
                expertVsHard,
                expertVsMedium,
                expertVsLegacyHard,
                hardVsMedium,
                mediumVsEasy,
                expertVsEasy,
            )
        }

        const val MIRROR_NODES = 1_000L

        /** What HARD gets in the tier ladder; EXPERT gets the shipped multiple of it. */
        const val TIER_LADDER_NODES = 800L

        /** `EXPERT_SOFT_BUDGET_MILLIS / HARD_SOFT_BUDGET_MILLIS`, over a denominator of two. */
        const val EXPERT_BUDGET_NUMERATOR = 7

        const val EXPERT_VS_MEDIUM_NODES = 1_000L
        const val EXPERT_VS_LEGACY_NODES = 2_000L
        const val HARD_VS_MEDIUM_NODES = 800L
        const val EXPERT_VS_EASY_NODES = 800L
        const val EASY_SEED = 11

        /** One flipped game against random play is a bad day; two is a tier that is not expert. */
        const val MAX_EASY_LOSSES = 1

        /**
         * How many openings a threshold may lose and still be believed.
         *
         * Zero is what a bar looks like when it has never been asked to survive a tuning pass, and
         * the significance it buys is not worth what it costs the next person to move a weight.
         */
        const val MAX_OPENINGS_LOST = 1

        var startedAtNanos = 0L

        @BeforeClass
        @JvmStatic
        fun startSuiteClock() {
            startedAtNanos = System.nanoTime()
        }

        /**
         * The file's own runtime, asserted as a class fixture rather than as a test.
         *
         * A test method cannot measure the file it is in — whichever one ran first would pay for
         * every cached match and the rest would measure nothing. This runs once, after everything,
         * and reports the figure whether it passes or not.
         */
        @AfterClass
        @JvmStatic
        fun assertSuiteFinishesUnderBudget() {
            val elapsed = (System.nanoTime() - startedAtNanos) / NANOS_TO_MILLIS
            println("strength suite: $elapsed ms")
            assertTrue(
                "the strength suite took $elapsed ms against a $SUITE_BUDGET_MILLIS ms budget",
                elapsed < SUITE_BUDGET_MILLIS,
            )
        }
    }
}
