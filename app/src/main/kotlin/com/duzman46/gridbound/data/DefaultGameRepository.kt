package com.duzman46.gridbound.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.domain.models.AppSettings
import com.duzman46.gridbound.domain.models.DEFAULT_SOUND_VOLUME
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.domain.models.GameStatistics
import com.duzman46.gridbound.domain.models.ThemeMode
import com.duzman46.gridbound.domain.repository.GameRepository
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.util.enumValueOrDefault
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@Singleton
class DefaultGameRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : GameRepository {
    private object Keys {
        val language = stringPreferencesKey(Constants.Data.KEY_LANGUAGE)
        val themeMode = stringPreferencesKey(Constants.Data.KEY_THEME_MODE)
        val soundEnabled = booleanPreferencesKey(Constants.Data.KEY_SOUND_ENABLED)
        val soundVolume = intPreferencesKey(Constants.Data.KEY_SOUND_VOLUME)
        val musicEnabled = booleanPreferencesKey(Constants.Data.KEY_MUSIC_ENABLED)
        val notificationsEnabled = booleanPreferencesKey(Constants.Data.KEY_NOTIFICATIONS_ENABLED)
        val hapticsEnabled = booleanPreferencesKey(Constants.Data.KEY_HAPTICS_ENABLED)
        val matchMessagesEnabled =
            booleanPreferencesKey(Constants.Data.KEY_MATCH_MESSAGES_ENABLED)
        val difficulty = stringPreferencesKey(Constants.Data.KEY_DIFFICULTY)
        val tutorialCompleted = booleanPreferencesKey(Constants.Tutorial.KEY_COMPLETED)
        val guestModeAccepted = booleanPreferencesKey(Constants.Session.KEY_GUEST_MODE_ACCEPTED)
        val usernameChosen = booleanPreferencesKey(Constants.Session.KEY_USERNAME_CHOSEN)
        val totalGames = intPreferencesKey(Constants.Data.KEY_TOTAL_GAMES)
        val totalWins = intPreferencesKey(Constants.Data.KEY_TOTAL_WINS)
        val totalLosses = intPreferencesKey(Constants.Data.KEY_TOTAL_LOSSES)
        val localGames = intPreferencesKey(Constants.Data.KEY_LOCAL_GAMES)
        val totalTurns = intPreferencesKey(Constants.Data.KEY_TOTAL_TURNS)
        val easyWins = intPreferencesKey(Constants.Data.KEY_EASY_WINS)
        val mediumWins = intPreferencesKey(Constants.Data.KEY_MEDIUM_WINS)
        val hardWins = intPreferencesKey(Constants.Data.KEY_HARD_WINS)
        val expertWins = intPreferencesKey(Constants.Data.KEY_EXPERT_WINS)
        val easyLosses = intPreferencesKey(Constants.Data.KEY_EASY_LOSSES)
        val mediumLosses = intPreferencesKey(Constants.Data.KEY_MEDIUM_LOSSES)
        val hardLosses = intPreferencesKey(Constants.Data.KEY_HARD_LOSSES)
        val expertLosses = intPreferencesKey(Constants.Data.KEY_EXPERT_LOSSES)
        val onlineGames = intPreferencesKey(Constants.Data.KEY_ONLINE_GAMES)
        val onlineWins = intPreferencesKey(Constants.Data.KEY_ONLINE_WINS)
        val currentStreak = intPreferencesKey(Constants.Data.KEY_CURRENT_STREAK)
        val bestStreak = intPreferencesKey(Constants.Data.KEY_BEST_STREAK)
        val fastestWinTurns = intPreferencesKey(Constants.Data.KEY_FASTEST_WIN_TURNS)
        val seenAchievements = stringSetPreferencesKey(Constants.Data.KEY_SEEN_ACHIEVEMENTS)
    }

    private val preferences: Flow<Preferences> = context.gridboundDataStore.data.catch { error ->
        if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
    }

    override val settings: Flow<AppSettings> = preferences.map { values ->
        AppSettings(
            // SYSTEM, not a specific language: an install with no stored choice must follow
            // the device, falling back to the base (English) resources when the device
            // language is not one of the ten shipped. Defaulting to Turkish here forced every
            // player in the world into Turkish on first launch, and disagreed with both
            // AppSettings() and SettingsBootstrap, which have always defaulted to SYSTEM.
            language = enumValueOrDefault(values[Keys.language], AppLanguage.SYSTEM),
            themeMode = enumValueOrDefault(values[Keys.themeMode], ThemeMode.SYSTEM),
            // The level, or — on an install that only ever saw the old on-off switch — whatever
            // that switch was set to, read as full or silent. Nobody's choice is thrown away by
            // the control changing shape.
            soundVolume = values[Keys.soundVolume]
                ?: if (values[Keys.soundEnabled] == false) 0 else DEFAULT_SOUND_VOLUME,
            musicEnabled = values[Keys.musicEnabled] ?: true,
            notificationsEnabled = values[Keys.notificationsEnabled] ?: true,
            hapticsEnabled = values[Keys.hapticsEnabled] ?: true,
            difficulty = enumValueOrDefault(values[Keys.difficulty], Difficulty.MEDIUM),
            matchMessagesEnabled = values[Keys.matchMessagesEnabled] ?: true,
        )
    }

    override val statistics: Flow<GameStatistics> = preferences.map { values ->
        GameStatistics(
            totalGames = values[Keys.totalGames] ?: 0,
            totalWins = values[Keys.totalWins] ?: 0,
            totalLosses = values[Keys.totalLosses] ?: 0,
            localGames = values[Keys.localGames] ?: 0,
            totalTurns = values[Keys.totalTurns] ?: 0,
            winsByDifficulty = Difficulty.entries.associateWith { values[winKey(it)] ?: 0 },
            lossesByDifficulty = Difficulty.entries.associateWith { values[lossKey(it)] ?: 0 },
            onlineGames = values[Keys.onlineGames] ?: 0,
            onlineWins = values[Keys.onlineWins] ?: 0,
            currentStreak = values[Keys.currentStreak] ?: 0,
            bestStreak = values[Keys.bestStreak] ?: 0,
            fastestWinTurns = values[Keys.fastestWinTurns] ?: 0,
        )
    }

    override val tutorialCompleted: Flow<Boolean> =
        preferences.map { values -> values[Keys.tutorialCompleted] ?: false }

    override suspend fun setTutorialCompleted(completed: Boolean) =
        update(Keys.tutorialCompleted, completed)

    override val guestModeAccepted: Flow<Boolean> =
        preferences.map { values -> values[Keys.guestModeAccepted] ?: false }

    override suspend fun setGuestModeAccepted(accepted: Boolean) =
        update(Keys.guestModeAccepted, accepted)

    override val usernameChosen: Flow<Boolean> =
        preferences.map { values -> values[Keys.usernameChosen] ?: false }

    override suspend fun setUsernameChosen(chosen: Boolean) =
        update(Keys.usernameChosen, chosen)

    // Read as nullable on purpose: absent is "this install predates the shelf" and empty is
    // "a new player who has earned nothing yet", and only one of the two should be told about
    // every badge their record already satisfies.
    override val seenAchievements: Flow<Set<String>?> =
        preferences.map { values -> values[Keys.seenAchievements] }

    override suspend fun markAchievementsSeen(ids: Set<String>) =
        update(Keys.seenAchievements, ids)

    override suspend fun setLanguage(language: AppLanguage) = update(Keys.language, language.name)
    override suspend fun setThemeMode(mode: ThemeMode) = update(Keys.themeMode, mode.name)
    override suspend fun setSoundVolume(percent: Int) =
        update(Keys.soundVolume, percent.coerceIn(0, 100))

    override suspend fun setMusicEnabled(enabled: Boolean) = update(Keys.musicEnabled, enabled)

    override suspend fun setNotificationsEnabled(enabled: Boolean) =
        update(Keys.notificationsEnabled, enabled)
    override suspend fun setHapticsEnabled(enabled: Boolean) = update(Keys.hapticsEnabled, enabled)
    override suspend fun setMatchMessagesEnabled(enabled: Boolean) =
        update(Keys.matchMessagesEnabled, enabled)
    override suspend fun setDifficulty(difficulty: Difficulty) = update(Keys.difficulty, difficulty.name)

    override suspend fun recordCompletedGame(
        mode: GameMode,
        difficulty: Difficulty,
        winner: PlayerId,
        localPlayer: PlayerId,
        turns: Int,
    ) {
        val tally = tallyOf(mode, winner, localPlayer)
        context.gridboundDataStore.edit { values ->
            values[Keys.totalGames] = (values[Keys.totalGames] ?: 0) + 1
            values[Keys.totalTurns] = (values[Keys.totalTurns] ?: 0) + turns
            if (!tally.countsAsResult) {
                values[Keys.localGames] = (values[Keys.localGames] ?: 0) + 1
                return@edit
            }
            if (tally.online) {
                values[Keys.onlineGames] = (values[Keys.onlineGames] ?: 0) + 1
            }
            if (tally.won) {
                values[Keys.totalWins] = (values[Keys.totalWins] ?: 0) + 1
                if (tally.online) values[Keys.onlineWins] = (values[Keys.onlineWins] ?: 0) + 1
                if (tally.touchesDifficulty) {
                    val key = winKey(difficulty)
                    values[key] = (values[key] ?: 0) + 1
                }
                val streak = (values[Keys.currentStreak] ?: 0) + 1
                values[Keys.currentStreak] = streak
                values[Keys.bestStreak] = maxOf(values[Keys.bestStreak] ?: 0, streak)
                val fastest = values[Keys.fastestWinTurns] ?: 0
                // Zero means "no win yet", so the first win always sets the record rather than
                // losing to a stored nothing.
                if (turns > 0 && (fastest == 0 || turns < fastest)) {
                    values[Keys.fastestWinTurns] = turns
                }
            } else {
                values[Keys.totalLosses] = (values[Keys.totalLosses] ?: 0) + 1
                if (tally.touchesDifficulty) {
                    val key = lossKey(difficulty)
                    values[key] = (values[key] ?: 0) + 1
                }
                values[Keys.currentStreak] = 0
            }
        }
    }

    private suspend fun <T> update(key: Preferences.Key<T>, value: T) {
        context.gridboundDataStore.edit { it[key] = value }
    }

    private fun winKey(difficulty: Difficulty): Preferences.Key<Int> = when (difficulty) {
        Difficulty.EASY -> Keys.easyWins
        Difficulty.MEDIUM -> Keys.mediumWins
        Difficulty.HARD -> Keys.hardWins
        Difficulty.EXPERT -> Keys.expertWins
    }

    private fun lossKey(difficulty: Difficulty): Preferences.Key<Int> = when (difficulty) {
        Difficulty.EASY -> Keys.easyLosses
        Difficulty.MEDIUM -> Keys.mediumLosses
        Difficulty.HARD -> Keys.hardLosses
        Difficulty.EXPERT -> Keys.expertLosses
    }
}
