package com.duzman46.gridbound.data

import com.duzman46.gridbound.domain.models.AppSettings
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.domain.repository.GameRepository
import com.duzman46.gridbound.game.models.Difficulty
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class SettingsManager @Inject constructor(
    private val repository: GameRepository,
) {
    val settings: Flow<AppSettings> = repository.settings

    suspend fun setLanguage(language: AppLanguage) = repository.setLanguage(language)
    suspend fun setSoundEnabled(enabled: Boolean) = repository.setSoundEnabled(enabled)
    suspend fun setNotificationsEnabled(enabled: Boolean) =
        repository.setNotificationsEnabled(enabled)
    suspend fun setHapticsEnabled(enabled: Boolean) = repository.setHapticsEnabled(enabled)
    suspend fun setMatchMessagesEnabled(enabled: Boolean) =
        repository.setMatchMessagesEnabled(enabled)
    suspend fun setDifficulty(difficulty: Difficulty) = repository.setDifficulty(difficulty)
}
