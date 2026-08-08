package com.duzman46.gridbound.game.ai

import com.duzman46.gridbound.game.engine.GameEngine
import com.duzman46.gridbound.game.models.ActionResult
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.GameStatus
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.pathfinding.AStarPathFinder

/**
 * The HARD bot exactly as it shipped, kept in test sources as the regression baseline.
 *
 * This is the engine the owner is complaining about. It is the yardstick the new EXPERT tier has to
 * clear, so it must never improve: measuring a new bot against a moving baseline measures nothing.
 * Nothing here is to be tidied, restyled or corrected — every defect it has is part of the
 * measurement, including the sort that ranks every wall above every non-winning pawn move, the
 * transposition cache that files fail-high bounds as exact scores, and the A* call inside the sort
 * comparator.
 *
 * Only three kinds of change were made to the copy, none of them behavioural:
 *  - `Constants.Ai.HARD_MAX_DEPTH` and `HARD_TIME_BUDGET_MILLIS` became constructor parameters, so
 *    a harness can drive it without touching production constants.
 *  - The five other `Constants.Ai` values it read no longer exist in production. Their shipped
 *    values are inlined below, which is also what freezes them: retuning `OWN_DISTANCE_WEIGHT` for
 *    `MediumAI` must not silently move the baseline.
 *  - `strategicActions` lost its default argument, so the shipped default is passed explicitly.
 *
 * @param maxDepth the shipped iterative-deepening ceiling.
 * @param timeBudgetMillis the shipped wall-clock budget. A harness raises it so that depth
 *   [maxDepth] always completes and the baseline is the same on a loaded machine as on an idle one.
 */
internal class LegacyHardAI(
    private val actionGenerator: AIActionGenerator,
    private val gameEngine: GameEngine,
    private val pathFinder: AStarPathFinder,
    private val maxDepth: Int = SHIPPED_MAX_DEPTH,
    private val timeBudgetMillis: Long = SHIPPED_TIME_BUDGET_MILLIS,
) : AIEngine {
    private data class CacheKey(val stateHash: Int, val depth: Int, val maximizing: Boolean)
    private data class SearchResult(val score: Int, val action: GameAction?)
    private class SearchTimedOut : RuntimeException()

    override fun chooseAction(state: BoardState, playerId: PlayerId): GameAction {
        require(state.currentPlayer == playerId)
        val fallback = orderedActions(state, playerId).firstOrNull()
            ?: error("A non-terminal game must have at least one valid action")
        if (fallback is GameAction.MovePawn && fallback.target.row == playerId.goalRow) return fallback

        val deadline = System.nanoTime() + timeBudgetMillis * 1_000_000L
        var best = fallback
        for (depth in 1..maxDepth) {
            val cache = HashMap<CacheKey, Int>()
            try {
                val result = search(
                    state = state,
                    depth = depth,
                    alphaStart = Int.MIN_VALUE + 1,
                    betaStart = Int.MAX_VALUE,
                    root = playerId,
                    deadline = deadline,
                    cache = cache,
                )
                result.action?.let { best = it }
            } catch (_: SearchTimedOut) {
                break
            }
        }
        return best
    }

    private fun search(
        state: BoardState,
        depth: Int,
        alphaStart: Int,
        betaStart: Int,
        root: PlayerId,
        deadline: Long,
        cache: MutableMap<CacheKey, Int>,
    ): SearchResult {
        if (System.nanoTime() >= deadline) throw SearchTimedOut()
        if (depth == 0 || state.status != GameStatus.IN_PROGRESS) {
            return SearchResult(evaluate(state, root), null)
        }
        val maximizing = state.currentPlayer == root
        val key = CacheKey(state.hashCode(), depth, maximizing)
        cache[key]?.let { return SearchResult(it, null) }
        val actions = orderedActions(state, root)
        if (actions.isEmpty()) return SearchResult(evaluate(state, root), null)

        var alpha = alphaStart
        var beta = betaStart
        var bestAction: GameAction? = null
        var bestScore = if (maximizing) Int.MIN_VALUE + 1 else Int.MAX_VALUE
        for (action in actions) {
            val next = (gameEngine.perform(state, action) as? ActionResult.Success)?.state ?: continue
            val score = search(next, depth - 1, alpha, beta, root, deadline, cache).score
            if (maximizing) {
                if (score > bestScore) {
                    bestScore = score
                    bestAction = action
                }
                alpha = maxOf(alpha, bestScore)
            } else {
                if (score < bestScore) {
                    bestScore = score
                    bestAction = action
                }
                beta = minOf(beta, bestScore)
            }
            if (beta <= alpha) break
        }
        cache[key] = bestScore
        return SearchResult(bestScore, bestAction)
    }

    private fun orderedActions(state: BoardState, root: PlayerId): List<GameAction> =
        actionGenerator.strategicActions(state, SHIPPED_MAX_WALL_CANDIDATES)
            .sortedWith(
                compareByDescending<GameAction> { action ->
                    action is GameAction.MovePawn && action.target.row == state.currentPlayer.goalRow
                }.thenByDescending { action ->
                    if (action is GameAction.MovePawn) {
                        -pathFinder.distance(action.target, state.currentPlayer.goalRow, state.walls)
                    } else {
                        if (state.currentPlayer == root) 1 else 0
                    }
                },
            )

    private fun evaluate(state: BoardState, root: PlayerId): Int {
        state.status.winner?.let { winner ->
            return if (winner == root) SHIPPED_TERMINAL_SCORE else -SHIPPED_TERMINAL_SCORE
        }
        val own = state.player(root)
        val opponent = state.player(root.opponent)
        val ownDistance = pathFinder.distance(own.position, root.goalRow, state.walls)
        val opponentDistance = pathFinder.distance(opponent.position, root.opponent.goalRow, state.walls)
        var score = opponentDistance * SHIPPED_OPPONENT_DISTANCE_WEIGHT -
            ownDistance * SHIPPED_OWN_DISTANCE_WEIGHT +
            (own.wallsRemaining - opponent.wallsRemaining) * SHIPPED_WALL_COUNT_WEIGHT
        if (ownDistance == 1) score += SHIPPED_IMMEDIATE_THREAT_WEIGHT
        if (opponentDistance == 1) score -= SHIPPED_IMMEDIATE_THREAT_WEIGHT
        return score
    }

    companion object {
        const val SHIPPED_MAX_DEPTH = 3
        const val SHIPPED_TIME_BUDGET_MILLIS = 850L
        private const val SHIPPED_MAX_WALL_CANDIDATES = 10
        private const val SHIPPED_TERMINAL_SCORE = 100_000
        private const val SHIPPED_OWN_DISTANCE_WEIGHT = 18
        private const val SHIPPED_OPPONENT_DISTANCE_WEIGHT = 20
        private const val SHIPPED_WALL_COUNT_WEIGHT = 3
        private const val SHIPPED_IMMEDIATE_THREAT_WEIGHT = 2_000
    }
}
