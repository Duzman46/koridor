package com.duzman46.gridbound.domain.models

import com.duzman46.gridbound.game.models.Difficulty

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class AppLanguage {
    TURKISH,
    ENGLISH,
}

data class LocalizedText(
    val turkish: String,
    val english: String,
) {
    fun value(language: AppLanguage): String = when (language) {
        AppLanguage.TURKISH -> turkish
        AppLanguage.ENGLISH -> english
    }
}

data class AppSettings(
    val language: AppLanguage = AppLanguage.TURKISH,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val difficulty: Difficulty = Difficulty.MEDIUM,
)

data class GameStatistics(
    val totalGames: Int = 0,
    val totalWins: Int = 0,
    val totalLosses: Int = 0,
    val localGames: Int = 0,
    val totalTurns: Int = 0,
    val winsByDifficulty: Map<Difficulty, Int> = Difficulty.entries.associateWith { 0 },
    val lossesByDifficulty: Map<Difficulty, Int> = Difficulty.entries.associateWith { 0 },
) {
    val winRate: Float
        get() {
            val competitiveGames = totalWins + totalLosses
            return if (competitiveGames == 0) 0f else totalWins.toFloat() / competitiveGames
        }
}
