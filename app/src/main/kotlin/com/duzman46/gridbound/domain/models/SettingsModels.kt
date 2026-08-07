package com.duzman46.gridbound.domain.models

import com.duzman46.gridbound.game.models.Difficulty

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    /**
     * Whether an online rival's canned messages reach this player, and whether this player
     * can send any.
     *
     * On by default, because eight pleasantries and six faces are what most people want out
     * of playing a stranger. Off is the whole defence a closed vocabulary allows: nobody can
     * be insulted with it, but somebody determined enough can still be tiresome, and the only
     * answer to tiresome is to stop hearing it.
     */
    val matchMessagesEnabled: Boolean = true,
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
