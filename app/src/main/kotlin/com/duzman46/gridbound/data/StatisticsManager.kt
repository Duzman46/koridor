package com.duzman46.gridbound.data

import com.duzman46.gridbound.domain.models.GameStatistics
import com.duzman46.gridbound.domain.repository.GameRepository
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.PlayerId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class StatisticsManager @Inject constructor(
    private val repository: GameRepository,
) {
    val statistics: Flow<GameStatistics> = repository.statistics

    suspend fun recordGame(
        mode: GameMode,
        difficulty: Difficulty,
        winner: PlayerId,
        localPlayer: PlayerId,
        turns: Int,
    ) {
        repository.recordCompletedGame(mode, difficulty, winner, localPlayer, turns)
    }
}
