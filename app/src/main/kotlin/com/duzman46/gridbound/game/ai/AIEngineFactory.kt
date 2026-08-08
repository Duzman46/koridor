package com.duzman46.gridbound.game.ai

import com.duzman46.gridbound.di.ExpertEngine
import com.duzman46.gridbound.di.HardEngine
import com.duzman46.gridbound.game.models.Difficulty
import javax.inject.Inject

/**
 * The two top tiers are the same engine at two budgets. That is what keeps the ladder monotone:
 * with one evaluation shared between them, "harder" can only ever mean "searches deeper", whereas
 * two separate evaluations are two chances for a later change to make HARD beat EXPERT.
 */
class AIEngineFactory @Inject constructor(
    private val easyAI: EasyAI,
    private val mediumAI: MediumAI,
    @param:HardEngine private val hardAI: SearchAI,
    @param:ExpertEngine private val expertAI: SearchAI,
) {
    fun forDifficulty(difficulty: Difficulty): AIEngine = when (difficulty) {
        Difficulty.EASY -> easyAI
        Difficulty.MEDIUM -> mediumAI
        Difficulty.HARD -> hardAI
        Difficulty.EXPERT -> expertAI
    }
}
