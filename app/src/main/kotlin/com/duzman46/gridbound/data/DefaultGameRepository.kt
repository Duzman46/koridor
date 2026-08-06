package com.duzman46.gridbound.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.domain.models.AppSettings
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.domain.models.GameStatistics
import com.duzman46.gridbound.domain.models.ThemeMode
import com.duzman46.gridbound.domain.repository.GameRepository
import com.duzman46.gridbound.game.board.BoardTheme
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
        val dynamicColor = booleanPreferencesKey(Constants.Data.KEY_DYNAMIC_COLOR)
        val soundEnabled = booleanPreferencesKey(Constants.Data.KEY_SOUND_ENABLED)
        val hapticsEnabled = booleanPreferencesKey(Constants.Data.KEY_HAPTICS_ENABLED)
        val difficulty = stringPreferencesKey(Constants.Data.KEY_DIFFICULTY)
        val tutorialCompleted = booleanPreferencesKey(Constants.Tutorial.KEY_COMPLETED)
        val guestModeAccepted = booleanPreferencesKey(Constants.Session.KEY_GUEST_MODE_ACCEPTED)
        val boardTheme = stringPreferencesKey(Constants.Data.KEY_BOARD_THEME)
        val totalGames = intPreferencesKey(Constants.Data.KEY_TOTAL_GAMES)
        val totalWins = intPreferencesKey(Constants.Data.KEY_TOTAL_WINS)
        val totalLosses = intPreferencesKey(Constants.Data.KEY_TOTAL_LOSSES)
        val localGames = intPreferencesKey(Constants.Data.KEY_LOCAL_GAMES)
        val totalTurns = intPreferencesKey(Constants.Data.KEY_TOTAL_TURNS)
        val easyWins = intPreferencesKey(Constants.Data.KEY_EASY_WINS)
        val mediumWins = intPreferencesKey(Constants.Data.KEY_MEDIUM_WINS)
        val hardWins = intPreferencesKey(Constants.Data.KEY_HARD_WINS)
        val easyLosses = intPreferencesKey(Constants.Data.KEY_EASY_LOSSES)
        val mediumLosses = intPreferencesKey(Constants.Data.KEY_MEDIUM_LOSSES)
        val hardLosses = intPreferencesKey(Constants.Data.KEY_HARD_LOSSES)
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
            // Matches AppSettings: the game's own palette wins unless the player asks for
            // Material You. Anyone who already toggled it keeps their stored choice.
            dynamicColor = values[Keys.dynamicColor] ?: false,
            soundEnabled = values[Keys.soundEnabled] ?: true,
            hapticsEnabled = values[Keys.hapticsEnabled] ?: true,
            difficulty = enumValueOrDefault(values[Keys.difficulty], Difficulty.MEDIUM),
            boardTheme = enumValueOrDefault(values[Keys.boardTheme], BoardTheme.CLASSIC),
        )
    }

    override val statistics: Flow<GameStatistics> = preferences.map { values ->
        GameStatistics(
            totalGames = values[Keys.totalGames] ?: 0,
            totalWins = values[Keys.totalWins] ?: 0,
            totalLosses = values[Keys.totalLosses] ?: 0,
            localGames = values[Keys.localGames] ?: 0,
            totalTurns = values[Keys.totalTurns] ?: 0,
            winsByDifficulty = mapOf(
                Difficulty.EASY to (values[Keys.easyWins] ?: 0),
                Difficulty.MEDIUM to (values[Keys.mediumWins] ?: 0),
                Difficulty.HARD to (values[Keys.hardWins] ?: 0),
            ),
            lossesByDifficulty = mapOf(
                Difficulty.EASY to (values[Keys.easyLosses] ?: 0),
                Difficulty.MEDIUM to (values[Keys.mediumLosses] ?: 0),
                Difficulty.HARD to (values[Keys.hardLosses] ?: 0),
            ),
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

    override suspend fun setLanguage(language: AppLanguage) = update(Keys.language, language.name)
    override suspend fun setThemeMode(mode: ThemeMode) = update(Keys.themeMode, mode.name)
    override suspend fun setDynamicColor(enabled: Boolean) = update(Keys.dynamicColor, enabled)
    override suspend fun setSoundEnabled(enabled: Boolean) = update(Keys.soundEnabled, enabled)
    override suspend fun setHapticsEnabled(enabled: Boolean) = update(Keys.hapticsEnabled, enabled)
    override suspend fun setDifficulty(difficulty: Difficulty) = update(Keys.difficulty, difficulty.name)
    override suspend fun setBoardTheme(theme: BoardTheme) = update(Keys.boardTheme, theme.name)

    override suspend fun recordCompletedGame(
        mode: GameMode,
        difficulty: Difficulty,
        winner: PlayerId,
        localPlayer: PlayerId,
        turns: Int,
    ) {
        context.gridboundDataStore.edit { values ->
            values[Keys.totalGames] = (values[Keys.totalGames] ?: 0) + 1
            values[Keys.totalTurns] = (values[Keys.totalTurns] ?: 0) + turns
            if (mode == GameMode.LOCAL_TWO_PLAYER) {
                values[Keys.localGames] = (values[Keys.localGames] ?: 0) + 1
            } else if (winner == localPlayer) {
                values[Keys.totalWins] = (values[Keys.totalWins] ?: 0) + 1
                val key = winKey(difficulty)
                values[key] = (values[key] ?: 0) + 1
            } else {
                values[Keys.totalLosses] = (values[Keys.totalLosses] ?: 0) + 1
                val key = lossKey(difficulty)
                values[key] = (values[key] ?: 0) + 1
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
    }

    private fun lossKey(difficulty: Difficulty): Preferences.Key<Int> = when (difficulty) {
        Difficulty.EASY -> Keys.easyLosses
        Difficulty.MEDIUM -> Keys.mediumLosses
        Difficulty.HARD -> Keys.hardLosses
    }

}
