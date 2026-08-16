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

    /**
     * Says whose the local record is, and clears it when that is somebody new.
     *
     * The counters behind [statistics] and [seenAchievements] live on the handset, and for a
     * long time that was the whole of it: they belonged to the device and to nobody in
     * particular. That is a leak. Signing out and coming back as a guest handed the next
     * identity the last one's achievements and win rate.
     *
     * Called with whichever identity the session is carrying. The same id twice changes
     * nothing, which is what makes linking safe -- `linkGuestWithEmail` upgrades an anonymous
     * user in place and keeps its id, so a guest who signs up keeps everything they played,
     * exactly as the sign-up row promises. A *different* id is a different player, and their
     * record starts empty.
     */
    suspend fun claimStatisticsFor(userId: String)

    suspend fun setLanguage(language: AppLanguage)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setHapticsEnabled(enabled: Boolean)
    suspend fun setMatchMessagesEnabled(enabled: Boolean)
    suspend fun setDifficulty(difficulty: Difficulty)

    /**
     * @param turnsPlayed how long the match actually ran, whatever ended it.
     * @param winTurns the turn the board was won on, or null when the board did not decide it —
     *   a resignation, a timeout, a walk-out. The two used to be one parameter, which is how a
     *   rival resigning on turn two came to hand out a badge for winning in under twenty.
     */
    suspend fun recordCompletedGame(
        mode: GameMode,
        difficulty: Difficulty,
        winner: PlayerId,
        localPlayer: PlayerId,
        turnsPlayed: Int,
        winTurns: Int?,
    )
}
