package com.duzman46.gridbound.game.ai

import com.duzman46.gridbound.game.TestFixtures
import com.duzman46.gridbound.game.ai.search.SearchClock
import com.duzman46.gridbound.game.ai.search.SearchConfig
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.PlayerId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Giving up on a search nobody is waiting for.
 *
 * `GameViewModel` cancels the AI coroutine when the player restarts or leaves, but a search is a
 * CPU loop that never suspends, so cancellation has nothing to act on: the job keeps running to
 * its own deadline while the lock on `chooseAction` holds the next search at the door. At the
 * expert tier that is over a second per abandoned turn, and pressing restart again while the
 * button looks unresponsive stacks them — which is what an app that has hung looks like.
 *
 * The budgets here are minutes rather than the shipped 0.7 s, so passing cannot be an accident of
 * the search finishing on its own. If the flag is ever dropped, or read non-volatilely, or cleared
 * in the wrong place, this test stops finishing rather than merely reporting a worse number.
 */
class SearchAbandonTest {
    private val generator =
        AIActionGenerator(TestFixtures.moves, TestFixtures.walls, TestFixtures.aStar)

    @Test
    fun `a search that has been abandoned stops long before its deadline`() {
        val searching = CountDownLatch(1)
        // Real time, so the deadline below is a real one, plus a signal on the first poll. That
        // first poll happens inside the engine after it has cleared the flag for this call, which
        // is what makes the ordering here deterministic rather than a race: the test cannot raise
        // the flag before the engine would wipe it.
        val clock = SearchClock { System.nanoTime().also { searching.countDown() } }
        val engine = SearchAI(
            actionGenerator = generator,
            gameEngine = TestFixtures.engine,
            pathFinder = TestFixtures.aStar,
            config = SearchConfig.EXPERT.copy(
                softBudgetMillis = TimeUnit.MINUTES.toMillis(2),
                hardBudgetMillis = TimeUnit.MINUTES.toMillis(5),
                maxNodes = Long.MAX_VALUE,
            ),
            clock = clock,
        )

        val chosen = AtomicReference<GameAction>()
        val thinking = Thread {
            chosen.set(engine.chooseAction(BoardState.initial(), PlayerId.PLAYER_ONE))
        }
        thinking.start()

        assertTrue("the engine never started searching", searching.await(30, TimeUnit.SECONDS))
        engine.abandonSearch()
        thinking.join(TimeUnit.SECONDS.toMillis(20))

        assertTrue("the search ignored being abandoned and ran on", !thinking.isAlive)
        // It still has to answer. Abandoning asks for the best move so far, not for nothing: the
        // caller has already been handed a turn to play and a null here would be a crash.
        assertNotNull(chosen.get())
    }

    /**
     * The flag belongs to one call, not to the engine. Clearing it on entry rather than on the way
     * out is what makes a second search after an abandoned one search properly instead of
     * returning its first legal move — a bot that answers instantly and badly for the rest of the
     * game, which is far harder to notice than one that hangs.
     */
    @Test
    fun `the next search is not affected by the last one being abandoned`() {
        val engine = SearchAI(
            actionGenerator = generator,
            gameEngine = TestFixtures.engine,
            pathFinder = TestFixtures.aStar,
            // A clock that never advances: with the budget unreachable, the node cap is the only
            // stopping condition and the result is identical on every machine.
            config = SearchConfig.EXPERT.copy(maxNodes = 20_000),
            clock = SearchClock { 0L },
        )
        val state = BoardState.initial()
        val expected = engine.chooseAction(state, PlayerId.PLAYER_ONE)

        engine.abandonSearch()

        assertTrue(
            "an abandoned search left the engine crippled for the next move",
            engine.chooseAction(state, PlayerId.PLAYER_ONE) == expected,
        )
    }
}
