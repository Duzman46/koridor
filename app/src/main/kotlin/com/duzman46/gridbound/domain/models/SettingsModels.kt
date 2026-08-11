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
    /**
     * How loud the move, wall and result sounds are, from 0 to 100.
     *
     * A level rather than a switch, and the switch it replaced is [soundEnabled] below: silence
     * is nought per cent, so the control is one thing rather than a toggle and a slider that can
     * disagree with each other. An install that had the old switch keeps its answer — see
     * DefaultGameRepository, which reads the switch when there is no level stored yet.
     */
    val soundVolume: Int = DEFAULT_SOUND_VOLUME,
    val musicEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    /** Whether the app may put anything in the notification shade at all. */
    val notificationsEnabled: Boolean = true,
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
) {
    /** Whether anything is audible. Everything that used to ask the old switch still asks this. */
    val soundEnabled: Boolean get() = soundVolume > 0
}

/** Loud enough to be heard over a room, quiet enough not to be the first thing turned off. */
const val DEFAULT_SOUND_VOLUME = 80

data class GameStatistics(
    val totalGames: Int = 0,
    val totalWins: Int = 0,
    val totalLosses: Int = 0,
    val localGames: Int = 0,
    val totalTurns: Int = 0,
    val winsByDifficulty: Map<Difficulty, Int> = Difficulty.entries.associateWith { 0 },
    val lossesByDifficulty: Map<Difficulty, Int> = Difficulty.entries.associateWith { 0 },
    /** Matches played against another person over the network, and how many of them were won. */
    val onlineGames: Int = 0,
    val onlineWins: Int = 0,
    /** Competitive wins in a row: the run standing now, and the longest one ever stood. */
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    /**
     * Turns in the shortest match this player has won, or 0 when they have won none.
     *
     * Zero rather than null because it is a stored counter like the rest, and "no win yet" and
     * "won in no turns" are the same impossible thing — a match cannot end before it starts.
     */
    val fastestWinTurns: Int = 0,
) {
    val winRate: Float
        get() {
            val competitiveGames = totalWins + totalLosses
            return if (competitiveGames == 0) 0f else totalWins.toFloat() / competitiveGames
        }
}
