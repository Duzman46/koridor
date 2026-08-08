package com.duzman46.gridbound.game.ai.search

import com.duzman46.gridbound.core.Constants

/**
 * Everything that separates one difficulty tier from another.
 *
 * HARD and EXPERT run the same search over the same evaluation and differ only in the values
 * below. That is deliberate: two evaluation functions are two chances for the ladder to go
 * non-monotone under maintenance, whereas one evaluation and two budgets make "harder" mean
 * "searches deeper", which stays true after any later refactor.
 *
 * @param softBudgetMillis after this, no new iterative-deepening iteration is started. Branching
 *   makes each iteration cost roughly four times the last, so an iteration begun much past halfway
 *   certainly overshoots.
 * @param hardBudgetMillis the abort deadline inside the search. Only the tail needs it: a cold JIT
 *   on the first move of a session, or a handset several times slower than the one this was sized
 *   for.
 * @param maxNodes the stopping condition tests drive the engine by. A wall-clock budget makes one
 *   seed produce different searches on a loaded machine, which would make every measurement noise.
 * @param useLateMoveReductions reduces late wall moves only; a pawn advance is never quiet in a
 *   tempo race.
 * @param useAnchorFilter skips the cut-off search for walls that provably cannot separate anyone.
 *   Switchable so the always-search path stays testable against it.
 */
data class SearchConfig(
    val softBudgetMillis: Long,
    val hardBudgetMillis: Long,
    val maxDepth: Int,
    val maxNodes: Long,
    val rootWallCandidates: Int,
    val shallowWallCandidates: Int,
    val deepWallCandidates: Int,
    val useLateMoveReductions: Boolean,
    val ttSizeLog2: Int,
    val zobristSeed: Long = Constants.Ai.SEARCH_ZOBRIST_SEED,
    val useAnchorFilter: Boolean = true,
) {
    companion object {
        val EXPERT = SearchConfig(
            softBudgetMillis = Constants.Ai.EXPERT_SOFT_BUDGET_MILLIS,
            hardBudgetMillis = Constants.Ai.EXPERT_HARD_BUDGET_MILLIS,
            maxDepth = Constants.Ai.EXPERT_MAX_DEPTH,
            maxNodes = Long.MAX_VALUE,
            rootWallCandidates = Constants.Ai.EXPERT_ROOT_WALL_CANDIDATES,
            shallowWallCandidates = Constants.Ai.EXPERT_SHALLOW_WALL_CANDIDATES,
            deepWallCandidates = Constants.Ai.EXPERT_DEEP_WALL_CANDIDATES,
            useLateMoveReductions = true,
            ttSizeLog2 = Constants.Ai.EXPERT_TT_SIZE_LOG2,
        )

        val HARD = SearchConfig(
            softBudgetMillis = Constants.Ai.HARD_SOFT_BUDGET_MILLIS,
            hardBudgetMillis = Constants.Ai.HARD_HARD_BUDGET_MILLIS,
            maxDepth = Constants.Ai.HARD_MAX_DEPTH,
            maxNodes = Long.MAX_VALUE,
            rootWallCandidates = Constants.Ai.HARD_ROOT_WALL_CANDIDATES,
            shallowWallCandidates = Constants.Ai.HARD_SHALLOW_WALL_CANDIDATES,
            deepWallCandidates = Constants.Ai.HARD_DEEP_WALL_CANDIDATES,
            useLateMoveReductions = false,
            ttSizeLog2 = Constants.Ai.HARD_TT_SIZE_LOG2,
        )
    }
}

/**
 * The clock the search reads.
 *
 * Injected rather than called directly so a test can hand the engine a clock that never advances,
 * leaving `maxNodes` and `maxDepth` as the only stopping conditions — which is what makes a search
 * result bit-identical on every machine.
 */
fun interface SearchClock {
    fun nanoTime(): Long

    companion object {
        val SYSTEM = SearchClock { System.nanoTime() }
    }
}
