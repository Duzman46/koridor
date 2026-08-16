package com.duzman46.gridbound.data

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.duzman46.gridbound.core.Constants

/**
 * The shape this build expects the store to be in.
 *
 * There is no version 1 written anywhere, and there never will be: the shipped store — the one
 * on every closed-test handset — carries no version key at all, and that absence *is* version 1.
 * Version 2 is the shape in which a match against the machine and a match against a person are
 * counted separately.
 *
 * A number rather than the absence of some other key, because the absence of a key is the one
 * thing a brand-new install and a three-week-old install have in common. Everything below turns
 * on telling those two apart, so the marker has to be something only a migration ever writes.
 */
internal const val GRIDBOUND_SCHEMA_VERSION = 2

/**
 * Every key the app keeps, in one place.
 *
 * Declared here rather than inside the repository because the migration below has to reach the
 * same keys the repository reads, and two lists of preference names that must agree is exactly
 * the kind of thing that stops agreeing. A ladder key spelled one way in the migration and
 * another way in the reader would leave a contaminated counter alive with nothing to show for it.
 */
internal object Keys {
    val language = stringPreferencesKey(Constants.Data.KEY_LANGUAGE)
    val themeMode = stringPreferencesKey(Constants.Data.KEY_THEME_MODE)
    val soundEnabled = booleanPreferencesKey(Constants.Data.KEY_SOUND_ENABLED)
    val notificationsEnabled = booleanPreferencesKey(Constants.Data.KEY_NOTIFICATIONS_ENABLED)
    val hapticsEnabled = booleanPreferencesKey(Constants.Data.KEY_HAPTICS_ENABLED)
    val matchMessagesEnabled = booleanPreferencesKey(Constants.Data.KEY_MATCH_MESSAGES_ENABLED)
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
    val statisticsOwner = stringPreferencesKey(Constants.Data.KEY_STATISTICS_OWNER)

    /**
     * Which shape of the store this file is in. Written by [LegacyRecordQuarantine] and by
     * nothing else, ever — see [GRIDBOUND_SCHEMA_VERSION] for why it has to be that way.
     */
    val schemaVersion = intPreferencesKey("schema_version")

    /**
     * Set when the counters in this store were earned before the app recorded who earned them,
     * and left set afterwards on purpose.
     *
     * It is the only signal that survives the quarantine. Once the contaminated ladder has been
     * removed and an owner has been stamped there is nothing else on disk to say that this
     * player's totals came out of the era when a record belonged to the handset rather than to a
     * person — and the audit's complaint about the old code was precisely that it left no signal
     * at all. It is cleared with the rest of [record] when a different account takes the device
     * over, because at that point the record it describes is gone too.
     */
    val legacyRecord = booleanPreferencesKey("legacy_record")

    /**
     * The per-difficulty bot ladder: the counters shipped versionCode 5 fed with online results.
     *
     * That build had no online counters and no notion of mode when it tallied, so every online
     * match was filed under whichever [com.duzman46.gridbound.game.models.Difficulty] the route
     * argument happened to be carrying. There is no way to tell one of those from a real win over
     * the machine, which is why the whole ladder goes rather than some estimated part of it.
     */
    val botLadder: List<Preferences.Key<Int>> = listOf(
        easyWins, mediumWins, hardWins, expertWins,
        easyLosses, mediumLosses, hardLosses, expertLosses,
    )

    /**
     * The counters that mean somebody has actually played on this device.
     *
     * Used only to tell a store that predates the split from one that merely holds a language
     * choice: a player who installed, set the app to Portuguese and never finished a match has
     * nothing to quarantine, and marking their store as a legacy record would be a lie.
     */
    val playedRecord: List<Preferences.Key<Int>> = listOf(
        totalGames, totalWins, totalLosses, localGames, totalTurns,
        currentStreak, bestStreak, fastestWinTurns,
    ) + botLadder

    /**
     * Keys that only code written after the split has ever put on disk.
     *
     * None of them existed in versionCode 5: the online counters were added with the split, the
     * seen-badge set with the achievement shelf, and the owner key with the ownership fix. Any one
     * of them therefore proves the store was last written by a build that already counted a bot
     * match separately from an online one — which makes its ladder honest, and makes quarantining
     * it destruction rather than repair. Only a development handset can be in that state today,
     * and getting it wrong there would be indistinguishable from getting it wrong on a tester's.
     */
    val postSplitOnly: List<Preferences.Key<*>> =
        listOf(onlineGames, onlineWins, seenAchievements, statisticsOwner)

    /**
     * Everything `DefaultGameRepository.claimStatisticsFor` clears, in one place so none is
     * forgotten.
     *
     * [tutorialCompleted] is in here, and that is a deliberate change of side. It is one flag
     * read by two things: the SCHOLAR badge, which is a claim about a person, and the onboarding
     * gate, which decides whether this player is shown the lesson. Left behind, it hands the next
     * account a badge they did not earn *and* skips the lesson for someone who has never seen the
     * board — the two failures point the same way, so the flag follows the player. What makes
     * that safe is that a signed-in player's answer is mirrored to their cloud profile, and
     * `SessionState.tutorialCompleted` is the local flag OR the profile's: the player who really
     * did the tutorial gets it back the moment their own profile loads.
     */
    val record: List<Preferences.Key<*>> = listOf(
        totalGames, totalWins, totalLosses, localGames, totalTurns,
        easyWins, mediumWins, hardWins, expertWins,
        easyLosses, mediumLosses, hardLosses, expertLosses,
        onlineGames, onlineWins, currentStreak, bestStreak, fastestWinTurns,
        seenAchievements, tutorialCompleted, legacyRecord,
    )
}

/**
 * The app's single preference store.
 *
 * Declared once and shared, because DataStore throws if two delegates are created for the
 * same file — which is exactly what would happen if the repository and the startup language
 * read each declared their own.
 *
 * The migrations attached here are what makes the ordering safe, and the ordering is the point.
 * DataStore runs them on the first touch of the store in a process — whichever caller that turns
 * out to be — and finishes them before the first value is handed out or the first edit is applied.
 * So the quarantine is always complete before anything can read a statistic, answer a badge or
 * stamp an owner, without a single caller having to know that it needs to wait.
 */
internal val Context.gridboundDataStore by preferencesDataStore(
    name = Constants.Data.SETTINGS_FILE_NAME,
    produceMigrations = { gridboundMigrations() },
)

/**
 * The migrations the store runs, in order.
 *
 * A function rather than a value so the unit tests can hand the same list to a DataStore of their
 * own over a temporary file — the migration is the piece of this app with the least room for a
 * mistake and the least opportunity to notice one, since it runs once per handset and then the
 * evidence is gone.
 */
internal fun gridboundMigrations(): List<DataMigration<Preferences>> =
    listOf(LegacyRecordQuarantine)

/**
 * Quarantines a record earned before online matches were counted separately from bot matches.
 *
 * **What is wrong with those stores.** Closed testing runs versionCode 5. Its `recordCompletedGame`
 * filed every non-local match into the per-difficulty win/loss counters, so an online match landed
 * on the bot ladder under whatever difficulty the navigation argument was carrying. Three things
 * read that ladder and all three are wrong on such a store: `GameStatistics.botGames`, which is
 * the ladder summed; the BOT_MEDIUM / BOT_HARD / BOT_EXPERT badges, which are the ladder compared
 * against a target; and — because a store from that build has no seen-badge set either — the
 * catch-up branch in `AchievementAlertViewModel`, which would mark those false badges seen without
 * saying a word, leaving no trace that they were ever granted.
 *
 * **What is done about it.** The honest part of the record is kept and the contaminated part is
 * removed. Total games, wins, losses, local games, turns, both streaks and the fastest win are
 * counts of matches the player really played, and they are untouched. The ladder is deleted,
 * which re-locks the bot badges, because a real win over the machine and an online win recorded
 * as one are indistinguishable and there is no honest way to split them. Nothing is invented in
 * the other direction either: [Keys.onlineGames] and [Keys.onlineWins] are left absent rather than
 * back-filled from the ladder, since a guessed online history is a different lie about the same
 * matches.
 *
 * **What is deliberately not touched.** [Keys.seenAchievements] stays absent. Absent is what makes
 * `AchievementAlertViewModel` grant the badges this record does still earn — first win, veteran,
 * streaks — silently instead of firing weeks of news at once, and that behaviour is right. After
 * the ladder is gone, the badges that branch grants are only ones the player actually earned.
 *
 * **Irreversible.** The ladder cannot be reconstructed once this has run. That is accepted: the
 * alternative is leaving a badge on a player's shelf that says they beat the machine when they may
 * never have met it, on a shelf that other players can see.
 */
internal object LegacyRecordQuarantine : DataMigration<Preferences> {

    /**
     * Keyed on the version marker and on nothing else.
     *
     * Not on the absence of the owner key, not on the absence of the online counters: a fresh
     * install lacks every one of those too, and a migration that cannot tell "never had a record"
     * from "had one written by the old build" would either quarantine nothing or quarantine
     * everybody. Once the marker is on disk this answers false for the life of the install, which
     * is what makes running twice impossible and makes running once safe.
     */
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[Keys.schemaVersion] == null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val migrated = currentData.toMutablePreferences()
        // Stamped whatever else happens here, including on a store that has never held anything.
        // A fresh install is version 2 from the moment it exists, and that is the whole point:
        // the next launch must be able to say "this store was created after the split" without
        // looking at what is in it.
        migrated[Keys.schemaVersion] = GRIDBOUND_SCHEMA_VERSION
        if (currentData.carriesLegacyRecord()) {
            migrated.removeContaminatedLadder()
            migrated[Keys.legacyRecord] = true
        }
        return migrated.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}

/**
 * Whether this store holds a record that predates the split.
 *
 * Two conditions, and both are needed.
 *
 * It has to hold counters, rather than merely be old: a store with a language choice and nothing
 * else has nothing to quarantine, and calling it a legacy record would put a provenance note on an
 * empty shelf.
 *
 * And it has to hold none of [Keys.postSplitOnly]. The missing version marker is what brings this
 * function to be asked at all, but a store can be missing it for a second reason — being written
 * by a build that has the split and not yet the marker, which is every development handset running
 * the branch this work sits on. Those ladders are honest and deleting one would be the exact
 * mistake this whole file exists to prevent, so the presence of a key only such a build could have
 * written settles it.
 */
internal fun Preferences.carriesLegacyRecord(): Boolean =
    Keys.playedRecord.any { key -> this[key] != null } &&
        Keys.postSplitOnly.none { key -> this[key] != null }

/**
 * Deletes the per-difficulty ladder.
 *
 * Removed rather than set to zero. An absent counter reads as 0 everywhere the repository maps it,
 * so the two are the same to every screen — but absent is also what a store that has never played
 * a bot match looks like, and leaving a row of explicit zeros behind would be a claim about
 * matches that is no truer than the numbers being deleted.
 *
 * Called from exactly one place, the migration. It must not be re-run later: by the time any other
 * code could reach it the player may have added a real win to the ladder, and a second sweep would
 * destroy the very thing the first one was protecting.
 */
private fun MutablePreferences.removeContaminatedLadder() {
    // Typed one at a time: `remove` is generic over the key's value type, so a list of keys
    // cannot be handed to it as a method reference.
    Keys.botLadder.forEach { key -> remove(key) }
}
