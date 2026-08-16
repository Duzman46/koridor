package com.duzman46.gridbound.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.duzman46.gridbound.domain.models.AppSettings
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

/**
 * The record and the settings, on this handset.
 *
 * Takes the store rather than reaching for it, so the whole of this class — including
 * [claimStatisticsFor], which decides whether a real player's history survives — can be exercised
 * against a real DataStore over a temporary file instead of against a stand-in that only behaves
 * the way its author expected. The constructor Hilt uses hands it the one shared delegate; nothing
 * else in the app may construct a second one over the same file.
 */
@Singleton
class DefaultGameRepository(
    private val store: DataStore<Preferences>,
) : GameRepository {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context.gridboundDataStore)

    private val preferences: Flow<Preferences> = store.data.catch { error ->
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
            soundEnabled = values[Keys.soundEnabled] ?: true,
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
    override suspend fun setSoundEnabled(enabled: Boolean) = update(Keys.soundEnabled, enabled)

    override suspend fun setNotificationsEnabled(enabled: Boolean) =
        update(Keys.notificationsEnabled, enabled)
    override suspend fun setHapticsEnabled(enabled: Boolean) = update(Keys.hapticsEnabled, enabled)
    override suspend fun setMatchMessagesEnabled(enabled: Boolean) =
        update(Keys.matchMessagesEnabled, enabled)
    override suspend fun setDifficulty(difficulty: Difficulty) = update(Keys.difficulty, difficulty.name)

    /**
     * Says whose the local record is, and clears it when that is somebody new.
     *
     * Three cases, and the third is the one that shipped wrong.
     *
     * **The same id twice** changes nothing, which is what makes linking safe: an anonymous user
     * who signs up keeps their id, so everything they played stays theirs.
     *
     * **A different id** is a different player, and everything in [Keys.record] goes. Their record
     * starts empty, including the tutorial flag — see [Keys.record] for why that one is in the list.
     *
     * **No owner at all** used to mean one thing and now means two, and telling them apart is the
     * whole of the fix. The owner key did not exist in versionCode 5, so *every* handset in closed
     * testing arrives here with no owner the first time somebody signs in. Keeping the record and
     * stamping it for whoever that is was defensible on a fresh install, where the games were
     * played by the person now signing in, and indefensible on a store from the old build, where
     * the counters were a merged pile that no longer said whose they were. [Keys.legacyRecord] is
     * what distinguishes them, and it exists only because [LegacyRecordQuarantine] wrote it.
     *
     * On a store carrying that marker the record has already had the contaminated part taken out
     * of it, at process start, before a single statistic could be read or a badge offered. What is
     * left is a count of matches somebody on this handset genuinely played, and there is exactly
     * one player on a handset, so it is adopted rather than destroyed — the owner's instruction
     * was that nobody loses what they actually played.
     *
     * The quarantine is emphatically *not* re-run here, and that is the one thing in this method
     * it would be easiest to get wrong. The migration's sweep is only true at the instant it runs:
     * a guest with no identity can play the machine for a week before they ever sign in, and by
     * the time this is called the ladder may hold wins that are entirely honest. Sweeping again
     * here would delete exactly those.
     *
     * The marker is left in place afterwards. It is the only thing on disk that still says this
     * player's totals came out of the era when a record belonged to the handset and not to a
     * person, and the complaint about the old code was that it left no signal at all.
     */
    override suspend fun claimStatisticsFor(userId: String) {
        if (userId.isBlank()) return
        store.edit { values ->
            when (values[Keys.statisticsOwner]) {
                // Already theirs. Nothing to decide and nothing to write.
                userId -> return@edit

                // Nobody has claimed this store yet, and both of the ways that can happen end
                // the same way — the record is adopted, not destroyed.
                //
                // With [Keys.legacyRecord] set it is a record from before the split, and the
                // quarantine has already taken the contaminated part out of it at process
                // start; what is left is matches somebody on this handset really played, and
                // the marker stays put as the note saying where those totals came from.
                // Without it, the store was created by this build and the games in it were
                // played by this install since the last claim.
                //
                // The difference is recorded rather than acted on, and deliberately so: by the
                // time a claim arrives the ladder may hold wins earned honestly after the
                // quarantine — a guest can play the machine for a week before ever signing in —
                // so there is nothing here that it would be safe to sweep a second time.
                null -> Unit

                // Somebody else's. Typed one at a time: `remove` is generic over the key's value
                // type, so a list of mixed keys cannot be handed to it as a method reference.
                else -> Keys.record.forEach { key -> values.remove(key) }
            }
            values[Keys.statisticsOwner] = userId
        }
    }

    override suspend fun recordCompletedGame(
        mode: GameMode,
        difficulty: Difficulty,
        winner: PlayerId,
        localPlayer: PlayerId,
        turns: Int,
    ) {
        val tally = tallyOf(mode, winner, localPlayer)
        store.edit { values ->
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
        store.edit { it[key] = value }
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
