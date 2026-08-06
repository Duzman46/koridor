package com.duzman46.gridbound.domain.models

import com.duzman46.gridbound.game.board.BoardTheme
import com.duzman46.gridbound.game.models.Difficulty

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /**
     * Off by default. Material You repaints the app in the wallpaper's palette, which on most
     * phones left Koridor looking like a system settings screen with no identity of its own.
     * The option stays in Settings for players who want it; the default is the game's colours.
     */
    val dynamicColor: Boolean = false,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val boardTheme: BoardTheme = BoardTheme.CLASSIC,
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
