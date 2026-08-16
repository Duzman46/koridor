package com.duzman46.gridbound.data

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.PlayerId
import java.io.File
import java.lang.reflect.Field
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The real repository, over a real DataStore, on a real file.
 *
 * Everything here is deliberately end-to-end rather than against a stand-in. The two things under
 * test — the one-shot migration and the ownership claim — are the two places in the app where a
 * mistake is unrecoverable: they run once on a handset that already holds a player's record, and
 * once they have run there is nothing left to compare against. A fake repository that behaves the
 * way its author expected proves nothing about either, because the whole risk lives in what
 * DataStore actually persists between one launch and the next.
 *
 * Each test therefore opens a store over a temporary file, closes it, and opens it again where a
 * second launch is what is being checked. Closing matters: DataStore refuses to have two stores
 * alive over one file, which is the same rule that makes the app's single shared delegate
 * necessary, so the scope is cancelled and joined before the file is reopened.
 */
class DefaultGameRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val storeFile: File get() = File(folder.root, "gridbound_preferences.preferences_pb")

    /** One launch: open the store, do something with it, shut it down again. */
    private fun <T> launch(
        withMigrations: Boolean = true,
        block: suspend (DataStore<Preferences>) -> T,
    ): T {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        val store = PreferenceDataStoreFactory.create(
            migrations = if (withMigrations) gridboundMigrations() else emptyList(),
            scope = scope,
            produceFile = { storeFile },
        )
        return try {
            runBlocking { block(store) }
        } finally {
            // Releases the file. Without this the next open throws "There are multiple DataStores
            // active for the same file", which would hide every reopen test behind one error.
            runBlocking { job.cancelAndJoin() }
        }
    }

    /** One launch of the app proper: the real repository over the real store. */
    private fun <T> launchApp(
        block: suspend (DefaultGameRepository, DataStore<Preferences>) -> T,
    ): T = launch { store -> block(DefaultGameRepository(store), store) }

    /**
     * Writes the store as versionCode 5 left it: counters, no schema version, no owner, and a
     * difficulty ladder that has been fed by online matches as well as bot ones.
     *
     * Written with the migrations switched off, because seeding through them would be seeding
     * through the thing under test.
     */
    private fun seedShippedStore() = launch(withMigrations = false) { store ->
        store.edit { values ->
            values[Keys.totalGames] = 40
            values[Keys.totalWins] = 25
            values[Keys.totalLosses] = 11
            values[Keys.localGames] = 4
            values[Keys.totalTurns] = 1_200
            values[Keys.currentStreak] = 3
            values[Keys.bestStreak] = 9
            values[Keys.fastestWinTurns] = 19
            // The contaminated ladder: some of these were people, not the machine.
            values[Keys.easyWins] = 2
            values[Keys.mediumWins] = 18
            values[Keys.mediumLosses] = 7
            values[Keys.hardWins] = 5
            // Settings and progress that have nothing to do with the split.
            values[Keys.language] = AppLanguage.TURKISH.name
            values[Keys.tutorialCompleted] = true
        }
    }

    // ---------------------------------------------------------------- the migration

    @Test
    fun `a shipped store keeps every match the player actually played`() {
        seedShippedStore()

        val statistics = launchApp { repository, _ -> repository.statistics.first() }

        assertEquals("total games are matches that really happened", 40, statistics.totalGames)
        assertEquals(25, statistics.totalWins)
        assertEquals(11, statistics.totalLosses)
        assertEquals(4, statistics.localGames)
        assertEquals(1_200, statistics.totalTurns)
        assertEquals("a streak is a run of real results", 3, statistics.currentStreak)
        assertEquals(9, statistics.bestStreak)
        assertEquals("the fastest win was a real win", 19, statistics.fastestWinTurns)
    }

    @Test
    fun `a shipped store loses the whole bot ladder, so the bot badges re-lock`() {
        seedShippedStore()

        val statistics = launchApp { repository, _ -> repository.statistics.first() }

        assertEquals(
            "no difficulty may keep a win that might have been against a person",
            emptyList<Int>(),
            statistics.winsByDifficulty.values.filter { it != 0 },
        )
        assertEquals(
            emptyList<Int>(),
            statistics.lossesByDifficulty.values.filter { it != 0 },
        )
        // botGames is the ladder summed, and it is what the bot badges are answered against.
        assertEquals("a player who never met the machine has beaten it 0 times", 0, statistics.botGames)
    }

    @Test
    fun `a shipped store is stamped with the schema version and marked as a legacy record`() {
        seedShippedStore()

        val values = launch { store -> store.data.first() }

        assertEquals(GRIDBOUND_SCHEMA_VERSION, values[Keys.schemaVersion])
        assertEquals(true, values[Keys.legacyRecord])
        assertEquals(
            "settings are none of the migration's business",
            AppLanguage.TURKISH.name,
            values[Keys.language],
        )
    }

    @Test
    fun `a shipped store keeps the badge shelf absent so the honest catch-up still runs`() {
        seedShippedStore()

        val seen = launchApp { repository, _ -> repository.seenAchievements.first() }

        // Absent, not empty. Absent is what makes AchievementAlertViewModel grant the badges this
        // record still legitimately earns without firing weeks of news at once. Seeding it here
        // would silence the first badge a returning player genuinely wins.
        assertNull("the seen set must stay absent", seen)
    }

    @Test
    fun `nothing is invented for the online counters the old build never kept`() {
        seedShippedStore()

        val statistics = launchApp { repository, _ -> repository.statistics.first() }

        assertEquals("an online history cannot be guessed back out of the ladder", 0, statistics.onlineGames)
        assertEquals(0, statistics.onlineWins)
    }

    @Test
    fun `a fresh install is never mistaken for a store that predates the split`() {
        val values = launch { store -> store.data.first() }

        assertEquals(
            "a brand-new store is version 2 the moment it exists",
            GRIDBOUND_SCHEMA_VERSION,
            values[Keys.schemaVersion],
        )
        assertNull("nothing was played before this install, so nothing is quarantined", values[Keys.legacyRecord])
    }

    @Test
    fun `a store that only ever held a language choice is not called a legacy record`() {
        launch(withMigrations = false) { store ->
            store.edit { values -> values[Keys.language] = AppLanguage.GERMAN.name }
        }

        val values = launch { store -> store.data.first() }

        assertEquals(GRIDBOUND_SCHEMA_VERSION, values[Keys.schemaVersion])
        assertNull(
            "there is no record here to have a provenance",
            values[Keys.legacyRecord],
        )
        assertEquals(AppLanguage.GERMAN.name, values[Keys.language])
    }

    @Test
    fun `a store that already counts the two apart keeps its ladder`() {
        launch(withMigrations = false) { store ->
            store.edit { values ->
                values[Keys.totalGames] = 6
                values[Keys.mediumWins] = 4
                // Only a build that already separates a bot match from an online one could have
                // written this, so its ladder was never fed by anybody but the machine.
                values[Keys.onlineGames] = 2
            }
        }

        val (statistics, values) = launchApp { repository, store ->
            repository.statistics.first() to store.data.first()
        }

        assertEquals(
            "an honest ladder must survive a store that merely lacks the version marker",
            4,
            statistics.winsByDifficulty[Difficulty.MEDIUM],
        )
        assertNull(values[Keys.legacyRecord])
        assertEquals(GRIDBOUND_SCHEMA_VERSION, values[Keys.schemaVersion])
    }

    @Test
    fun `the migration runs once and a second launch changes nothing`() {
        seedShippedStore()

        val afterFirst = launch { store -> store.data.first().asMap() }
        val afterSecond = launch { store -> store.data.first().asMap() }

        assertEquals("running twice must be the same as running once", afterFirst, afterSecond)
    }

    @Test
    fun `a bot win earned after the quarantine survives every later launch`() {
        seedShippedStore()

        launchApp { repository, _ ->
            repository.recordCompletedGame(
                mode = GameMode.VS_AI,
                difficulty = Difficulty.MEDIUM,
                winner = PlayerId.PLAYER_ONE,
                localPlayer = PlayerId.PLAYER_ONE,
                turnsPlayed = 30,
                winTurns = 30,
            )
        }

        val statistics = launchApp { repository, _ -> repository.statistics.first() }

        assertEquals(
            "the sweep is only true at the moment it runs; it must never run again",
            1,
            statistics.winsByDifficulty[Difficulty.MEDIUM],
        )
        assertEquals(41, statistics.totalGames)
    }

    // ---------------------------------------------------------------- the ownership claim

    @Test
    fun `a record survives a claim by its own owner`() {
        val statistics = launchApp { repository, _ ->
            repository.claimStatisticsFor(OWNER)
            repository.recordCompletedGame(
                mode = GameMode.VS_AI,
                difficulty = Difficulty.HARD,
                winner = PlayerId.PLAYER_ONE,
                localPlayer = PlayerId.PLAYER_ONE,
                turnsPlayed = 24,
                winTurns = 24,
            )
            // The same id again is what a link, a relaunch and a token refresh all look like.
            repository.claimStatisticsFor(OWNER)
            repository.statistics.first()
        }

        assertEquals("a player must not lose their record to their own sign-in", 1, statistics.totalGames)
        assertEquals(1, statistics.totalWins)
        assertEquals(1, statistics.winsByDifficulty[Difficulty.HARD])
        assertEquals(24, statistics.fastestWinTurns)
    }

    @Test
    fun `a claim by somebody else starts from nothing`() {
        val (statistics, values) = launchApp { repository, store ->
            repository.claimStatisticsFor(OWNER)
            repository.recordCompletedGame(
                mode = GameMode.VS_AI,
                difficulty = Difficulty.EASY,
                winner = PlayerId.PLAYER_ONE,
                localPlayer = PlayerId.PLAYER_ONE,
                turnsPlayed = 30,
                winTurns = 30,
            )
            repository.markAchievementsSeen(setOf("FIRST_WIN"))
            repository.setLanguage(AppLanguage.TURKISH)

            repository.claimStatisticsFor(OTHER)
            repository.statistics.first() to store.data.first()
        }

        assertEquals("the next player's record starts empty", 0, statistics.totalGames)
        assertEquals(0, statistics.totalWins)
        assertEquals(0, statistics.winsByDifficulty[Difficulty.EASY])
        assertNull("and their badge shelf has never been looked at", values[Keys.seenAchievements])
        assertEquals("the new owner is on the store", OTHER, values[Keys.statisticsOwner])
        assertEquals(
            "a handset setting is not part of anybody's record",
            AppLanguage.TURKISH.name,
            values[Keys.language],
        )
        assertEquals(
            "and the store's shape is not part of it either",
            GRIDBOUND_SCHEMA_VERSION,
            values[Keys.schemaVersion],
        )
    }

    @Test
    fun `the tutorial flag follows the player, not the handset`() {
        val values = launchApp { repository, store ->
            repository.claimStatisticsFor(OWNER)
            repository.setTutorialCompleted(true)

            repository.claimStatisticsFor(OTHER)
            store.data.first()
        }

        // SCHOLAR is a claim about a person, and the onboarding gate reads the same flag: left
        // standing it would both hand the next account a badge and skip the lesson for somebody
        // who has never seen a board.
        assertNull("a new account has not done the tutorial", values[Keys.tutorialCompleted])
    }

    @Test
    fun `claiming a shipped record adopts what survived the quarantine`() {
        seedShippedStore()

        val (statistics, values) = launchApp { repository, store ->
            // No owner has ever been written: this is the first sign-in on a handset that has
            // been in closed testing for weeks.
            repository.claimStatisticsFor(OWNER)
            repository.statistics.first() to store.data.first()
        }

        assertEquals("nobody loses what they actually played", 40, statistics.totalGames)
        assertEquals(25, statistics.totalWins)
        assertEquals(9, statistics.bestStreak)
        assertEquals("and the contaminated part stays gone", 0, statistics.botGames)
        assertEquals(OWNER, values[Keys.statisticsOwner])
        assertEquals(
            "the marker is the only thing left saying where these totals came from",
            true,
            values[Keys.legacyRecord],
        )
    }

    @Test
    fun `signing in on a new install does not wipe the games that install just played`() {
        val statistics = launchApp { repository, _ ->
            // A fresh store: the migration stamped a version and marked nothing.
            repository.recordCompletedGame(
                mode = GameMode.VS_AI,
                difficulty = Difficulty.EXPERT,
                winner = PlayerId.PLAYER_ONE,
                localPlayer = PlayerId.PLAYER_ONE,
                turnsPlayed = 28,
                winTurns = 28,
            )
            repository.recordCompletedGame(
                mode = GameMode.ONLINE,
                difficulty = Difficulty.MEDIUM,
                winner = PlayerId.PLAYER_TWO,
                localPlayer = PlayerId.PLAYER_ONE,
                turnsPlayed = 40,
                winTurns = 40,
            )
            repository.claimStatisticsFor(OWNER)
            repository.statistics.first()
        }

        assertEquals("a guest who signs up keeps everything they played", 2, statistics.totalGames)
        assertEquals(1, statistics.totalWins)
        assertEquals(1, statistics.winsByDifficulty[Difficulty.EXPERT])
        assertEquals(1, statistics.onlineGames)
    }

    @Test
    fun `a legacy record is adopted once and then belongs to its owner`() {
        seedShippedStore()

        val (statistics, values) = launchApp { repository, store ->
            repository.claimStatisticsFor(OWNER)
            // The next identity is a different person, and the legacy note goes with the record
            // it described.
            repository.claimStatisticsFor(OTHER)
            repository.statistics.first() to store.data.first()
        }

        assertEquals(0, statistics.totalGames)
        assertNull("nothing may still claim to predate the split", values[Keys.legacyRecord])
        assertEquals(OTHER, values[Keys.statisticsOwner])
    }

    @Test
    fun `a blank id claims nothing`() {
        val values = launchApp { repository, store ->
            repository.claimStatisticsFor(OWNER)
            repository.claimStatisticsFor("   ")
            store.data.first()
        }

        assertEquals("an absent identity must not take the record over", OWNER, values[Keys.statisticsOwner])
    }

    // ---------------------------------------------------------------- what the split is for

    @Test
    fun `an online match is never filed as a match against the machine`() {
        val statistics = launchApp { repository, _ ->
            // MEDIUM is what the online route carries in its arguments; it means nothing here.
            repository.recordCompletedGame(
                mode = GameMode.ONLINE,
                difficulty = Difficulty.MEDIUM,
                winner = PlayerId.PLAYER_ONE,
                localPlayer = PlayerId.PLAYER_ONE,
                turnsPlayed = 34,
                winTurns = 34,
            )
            repository.statistics.first()
        }

        assertEquals(1, statistics.onlineGames)
        assertEquals(1, statistics.onlineWins)
        assertEquals("the ladder belongs to the bot alone", 0, statistics.botGames)
        assertEquals(0, statistics.winsByDifficulty[Difficulty.MEDIUM])
    }

    @Test
    fun `the store the app ships with is readable by the repository at all`() {
        // A guard on the plumbing itself: if the factory, the file or the delegate stopped
        // agreeing, every assertion above would pass vacuously against an empty store.
        val values = launch { store -> store.data.first() }
        assertNotNull(values[Keys.schemaVersion])
        assertTrue(storeFile.exists())
    }

    companion object {
        private const val OWNER = "uid-the-player"
        private const val OTHER = "uid-somebody-else"

        private var restoreSdkInt: Int? = null

        /**
         * Tells the test JVM it is running on the oldest Android this app supports.
         *
         * Not decoration, and not a shortcut. DataStore replaces the store file by moving a
         * scratch file over it, and it picks how from `Build.VERSION.SDK_INT`: at 26 and above it
         * uses `Files.move`, which replaces an existing destination, and below that it falls back
         * to `File.renameTo`, which on Windows refuses to overwrite anything. A JVM unit test has
         * no Android, so that field reads 0 and every write after the first one fails — on a code
         * path this app can never take, because its minSdk *is* 26. Left alone, the platform the
         * test runs on would decide the outcome of a test about a player's records.
         *
         * The field is `static final`, so `Field.setInt` refuses it and this goes through Unsafe.
         * It is put back afterwards; nothing else in the suite reads it, and it is not left
         * changed for whatever runs next in the same JVM.
         */
        @BeforeClass
        @JvmStatic
        fun useTheFileMoveTheAppItselfGets() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return
            restoreSdkInt = Build.VERSION.SDK_INT
            writeSdkInt(Build.VERSION_CODES.O)
            check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                "Could not make the test JVM report API 26; DataStore would take a code path the app never does."
            }
        }

        @AfterClass
        @JvmStatic
        fun putTheApiLevelBack() {
            restoreSdkInt?.let(::writeSdkInt)
            restoreSdkInt = null
        }

        private fun writeSdkInt(value: Int) {
            val field = Build.VERSION::class.java.getDeclaredField("SDK_INT")
            val unsafeType = Class.forName("sun.misc.Unsafe")
            val unsafe = unsafeType.getDeclaredField("theUnsafe")
                .apply { isAccessible = true }
                .get(null)
            val base = unsafeType.getMethod("staticFieldBase", Field::class.java)
                .invoke(unsafe, field)
            val offset = unsafeType.getMethod("staticFieldOffset", Field::class.java)
                .invoke(unsafe, field) as Long
            unsafeType
                .getMethod(
                    "putInt",
                    Any::class.java,
                    java.lang.Long.TYPE,
                    Integer.TYPE,
                )
                .invoke(unsafe, base, offset, value)
        }
    }
}
