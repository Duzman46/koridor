package com.duzman46.gridbound.game.ai

import com.duzman46.gridbound.game.models.Difficulty
import javax.inject.Inject

class AIEngineFactory @Inject constructor(
    private val easyAI: EasyAI,
    private val mediumAI: MediumAI,
    private val hardAI: HardAI,
) {
    fun forDifficulty(difficulty: Difficulty): AIEngine = when (difficulty) {
        Difficulty.EASY -> easyAI
        Difficulty.MEDIUM -> mediumAI
        Difficulty.HARD -> hardAI
    }
}

