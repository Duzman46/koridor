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
    /**
     * Whether the app may put anything in the notification shade.
     *
     * On by default. The one notice this app sends arrives at most once a fortnight and only
     * after a week away, so the cost of it being on for somebody who did not ask is close to
     * nothing — and the switch is one screen away for anybody who would rather it were not.
     */
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
)

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

    /**
     * Online matches lost, worked out rather than stored — and it is exact, not an estimate.
     *
     * `DefaultGameRepository.recordCompletedGame` puts every counted match down exactly one of
     * two branches, won or lost; there is no third. A local match returns before either, so it
     * reaches neither [onlineGames] nor this. The game has no draw, so nothing falls between the
     * two. That makes online games the sum of online wins and online losses, and this the
     * remainder.
     *
     * If a draw is ever added, this stops being true and the counter has to become real.
     */
    val onlineLosses: Int get() = (onlineGames - onlineWins).coerceAtLeast(0)

    /** The share of online matches won. The career figures are this game's, not the bot's. */
    val onlineWinRate: Float
        get() = if (onlineGames == 0) 0f else onlineWins.toFloat() / onlineGames

    /** Matches against the machine, summed from the per-difficulty tallies that only it feeds. */
    val botGames: Int
        get() = winsByDifficulty.values.sum() + lossesByDifficulty.values.sum()
}
