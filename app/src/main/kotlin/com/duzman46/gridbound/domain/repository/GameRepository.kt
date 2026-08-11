package com.duzman46.gridbound.domain.repository

import com.duzman46.gridbound.domain.models.AppSettings
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.domain.models.GameStatistics
import com.duzman46.gridbound.domain.models.ThemeMode
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.PlayerId
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    val settings: Flow<AppSettings>
    val statistics: Flow<GameStatistics>

    /**
     * Device-local tutorial progress. Guests have nowhere else to keep it, and for signed-in
     * players it lets the gate work offline while the cloud copy stays authoritative.
     */
    val tutorialCompleted: Flow<Boolean>

    suspend fun setTutorialCompleted(completed: Boolean)

    /** True once the player has chosen to play without an account on this device. */
    val guestModeAccepted: Flow<Boolean>

    suspend fun setGuestModeAccepted(accepted: Boolean)

    val usernameChosen: Flow<Boolean>

    suspend fun setUsernameChosen(chosen: Boolean)

    /**
     * Badge ids the player has already been shown, or null before the app has ever recorded any.
     *
     * Null is not the same as empty: see [com.duzman46.gridbound.core.Constants.Data.KEY_SEEN_ACHIEVEMENTS].
     */
    val seenAchievements: Flow<Set<String>?>

    suspend fun markAchievementsSeen(ids: Set<String>)

    suspend fun setLanguage(language: AppLanguage)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setHapticsEnabled(enabled: Boolean)
    suspend fun setMatchMessagesEnabled(enabled: Boolean)
    suspend fun setDifficulty(difficulty: Difficulty)

    suspend fun recordCompletedGame(
        mode: GameMode,
        difficulty: Difficulty,
        winner: PlayerId,
        localPlayer: PlayerId,
        turns: Int,
    )
}
