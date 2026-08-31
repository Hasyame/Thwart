package com.hasyame.marvelchampions.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.db.entity.CampaignEventEntity
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import com.hasyame.marvelchampions.data.db.entity.ExcludedModularSetEntity
import com.hasyame.marvelchampions.data.db.entity.ExcludedScenarioEntity
import com.hasyame.marvelchampions.data.db.entity.FavouriteCardEntity
import com.hasyame.marvelchampions.data.db.entity.OwnedPackEntity
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.RandomizerHistoryEntity
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import com.hasyame.marvelchampions.data.repository.PlayStats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * A deleted row is invisible to every read there is.
 *
 * The hazard this exists for: a delete no longer removes anything, so a read
 * that forgets `deletedAt IS NULL` shows the user a game they threw away, and
 * it does it silently — no crash, no log line, just the row back on the screen.
 * There are more reads than anyone counts on a first look, which is why they
 * are gone through here one at a time rather than sampled.
 *
 * The last test is the one that will still be useful in a year: it reads the
 * DAO sources and refuses a SELECT against a user table that does not filter.
 * A read added next winter cannot quietly skip the clause.
 */
@RunWith(RobolectricTestRunner::class)
class SoftDeleteTest {

    private lateinit var database: MarvelChampionsDatabase

    /** One fixed instant, so a test reads about the change and not the clock. */
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MarvelChampionsDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a pack given away is not in the collection`() = runTest {
        val dao = database.ownedPackDao()
        dao.upsert(OwnedPackEntity("core", quantity = 1))
        dao.upsert(OwnedPackEntity("mts", quantity = 1))

        dao.remove("core", now)

        assertEquals(listOf("mts"), dao.getOwnedCodes())
        assertEquals(listOf("mts"), dao.getOwned().map { it.packCode })
        assertEquals(listOf("mts"), dao.observeOwned().first().map { it.packCode })
        assertEquals(1, dao.countOwned())
        // Gone from view, still on disk. That row is the only thing that can
        // tell a second device the pack was given away rather than never owned.
        assertEquals(2, rowCount("owned_packs"))
    }

    @Test
    fun `a pack bought again comes back`() = runTest {
        val dao = database.ownedPackDao()
        dao.upsert(OwnedPackEntity("core", quantity = 1))
        dao.remove("core", now)

        dao.upsert(OwnedPackEntity("core", quantity = 2, updatedAt = now + 1))

        // The insert writes over the tombstone rather than colliding with it,
        // which is what every one of these tables relies on: a row deleted and
        // recreated has to become visible again, not stay deleted with a newer
        // timestamp on it.
        assertEquals(listOf("core"), dao.getOwnedCodes())
        assertEquals(2, dao.getOwned().single().quantity)
        assertEquals(1, rowCount("owned_packs"))
    }

    @Test
    fun `a set ticked off again after being put back is excluded`() = runTest {
        val dao = database.excludedModularSetDao()
        dao.exclude(ExcludedModularSetEntity("bomb_scare"))
        dao.include("bomb_scare", now)

        dao.exclude(ExcludedModularSetEntity("bomb_scare", updatedAt = now + 1))

        assertEquals(listOf("bomb_scare"), dao.getExcludedCodes())
    }

    @Test
    fun `a modular set ticked back on is not excluded`() = runTest {
        val dao = database.excludedModularSetDao()
        dao.exclude(ExcludedModularSetEntity("bomb_scare"))

        dao.include("bomb_scare", now)

        assertTrue(dao.getExcludedCodes().isEmpty())
        assertTrue(dao.getExcluded().isEmpty())
        assertTrue(dao.observeExcluded().first().isEmpty())
        assertEquals(1, rowCount("excluded_modular_sets"))
    }

    @Test
    fun `a scenario ticked back on is not excluded`() = runTest {
        val dao = database.excludedScenarioDao()
        dao.exclude(ExcludedScenarioEntity("wrecking_crew"))

        dao.include("wrecking_crew", now)

        assertTrue(dao.getExcludedCodes().isEmpty())
        assertTrue(dao.getExcluded().isEmpty())
        assertTrue(dao.observeExcluded().first().isEmpty())
        assertEquals(1, rowCount("excluded_scenarios"))
    }

    @Test
    fun `an unstarred card is not a favourite`() = runTest {
        val dao = database.favouriteDao()
        dao.add(FavouriteCardEntity("01001", addedAt = now))

        dao.remove("01001", now)

        assertTrue(dao.getAll().isEmpty())
        assertTrue(dao.observeCodes().first().isEmpty())
        assertEquals(1, rowCount("favourite_cards"))
    }

    @Test
    fun `starring a card again brings it back`() = runTest {
        val dao = database.favouriteDao()
        dao.add(FavouriteCardEntity("01001", addedAt = now))
        dao.remove("01001", now)

        // Writing over the tombstone rather than leaving it, which is what the
        // repository relies on: the row has to become visible again, not stay
        // deleted with a newer timestamp.
        dao.add(FavouriteCardEntity("01001", addedAt = now + 1, updatedAt = now + 1))

        assertEquals(listOf("01001"), dao.observeCodes().first())
    }

    @Test
    fun `a deleted deck is gone from every list it was in`() = runTest {
        val dao = database.savedDeckDao()
        dao.upsert(deck("local-1"))
        dao.upsert(deck("local-2"))

        dao.delete("local-1", now)

        assertNull(dao.getDeck("local-1"))
        assertNull(dao.observeDeck("local-1").first())
        assertEquals(listOf("local-2"), dao.getDecks().map { it.id })
        assertEquals(listOf("local-2"), dao.observeDecks().first().map { it.id })
        assertEquals(1, dao.count())
        assertEquals(2, rowCount("saved_decks"))
    }

    @Test
    fun `a deleted campaign is gone, and so is its log`() = runTest {
        val dao = database.campaignDao()
        dao.insertRun(run("run-1"))
        dao.insertRun(run("run-2"))
        dao.appendEvent(CampaignEventEntity("event-1", "run-1", now, "{}"))
        dao.appendEvent(CampaignEventEntity("event-2", "run-2", now, "{}"))

        dao.deleteRun("run-1", now)

        assertNull(dao.getRun("run-1"))
        assertNull(dao.observeRun("run-1").first())
        assertEquals(listOf("run-2"), dao.getRuns().map { it.id })
        assertEquals(listOf("run-2"), dao.observeRuns().first().map { it.id })

        // The events are still there — a tombstoned run fires no cascade — and
        // nothing can read them. That is what stops them being pulled back from
        // a server as orphans belonging to a campaign that no longer exists.
        assertTrue(dao.getEvents("run-1").isEmpty())
        assertTrue(dao.observeEvents("run-1").first().isEmpty())
        assertEquals(0, dao.countEvents("run-1"))
        assertEquals(2, rowCount("campaign_events"))

        // And the campaign that was not deleted is untouched.
        assertEquals(listOf("event-2"), dao.getEvents("run-2").map { it.id })
    }

    @Test
    fun `a deleted play is gone from the history and from every statistic`() = runTest {
        val dao = database.playDao()
        dao.insert(play("keep", won = true))
        dao.insert(play("drop", won = false))

        dao.delete("drop", now)

        assertNull(dao.getPlay("drop"))
        assertEquals(listOf("keep"), dao.observePlays().first().map { it.id })
        assertEquals(listOf("keep"), dao.getAllPlays().map { it.id })

        // The statistics are their own set of queries, and a win rate quietly
        // counting a deleted game is the kind of wrong nobody notices.
        assertEquals(1, dao.observeStatsRows().first().size)
        assertEquals(1, PlayStats.heroes(dao.observeStatsRows().first()).single().played)
        assertEquals(1, dao.observeByScenario().first().sumOf { it.played })
        assertEquals(1, dao.observeByDifficulty().first().sumOf { it.played })
        assertEquals(1, dao.observeBySoloOrGroup().first().sumOf { it.played })
    }

    @Test
    fun `a deleted play still names its photographs`() = runTest {
        val dao = database.playDao()
        dao.insert(play("drop", won = false).copy(photos = "table.jpg"))

        dao.delete("drop", now)

        // The one read that deliberately still sees a tombstone. The sweep that
        // removes unreferenced photographs uses it, and a file deleted the
        // moment the row is tombstoned would make the delete unrecoverable
        // while the row itself is still recoverable.
        assertEquals(listOf("table.jpg"), dao.photoLists())
    }

    @Test
    fun `a deleted draw is gone from the history and from the beaten filter`() = runTest {
        val dao = database.randomizerHistoryDao()
        dao.insert(draw("keep"))
        dao.insert(draw("drop"))
        dao.setBeaten("drop", true, now)

        dao.delete("drop", now)

        assertEquals(listOf("keep"), dao.getHistory().map { it.id })
        assertEquals(listOf("keep"), dao.observeHistory().first().map { it.id })
        // "Exclude what I have beaten" must not still be excluding a scenario
        // whose only record of being beaten has been thrown away.
        assertTrue(dao.observeBeatenScenarios().first().isEmpty())
    }

    @Test
    fun `a tombstoned row cannot be edited back into life`() = runTest {
        val plays = database.playDao()
        plays.insert(play("drop", won = false))
        plays.delete("drop", now)

        plays.markReported("drop", now + 1)

        // The targeted updates guard on the tombstone as well. Without that, a
        // BoardGameGeek report or a "beaten" tick landing after a delete would
        // rewrite a row nobody can see, and the change would be pushed.
        assertEquals(0, valueOf("SELECT reportedToBgg FROM plays WHERE id = 'drop'"))
    }

    // ------------------------------------------------------------- the guard --

    /**
     * No read anywhere selects from a user table without filtering tombstones.
     *
     * The behavioural tests above cover the reads that exist today. This one
     * covers the read somebody adds next winter: the sources are searched for a
     * SELECT touching a user table, and every one has to carry the clause.
     *
     * Read from the source rather than from the annotations, because Room's
     * `@Query` is kept only to the class file and cannot be looked up at
     * runtime. Blunt, but it fails for the right reason and the message says
     * which query.
     */
    @Test
    fun `every select against a user table filters tombstones`() {
        val offenders = daoSources().flatMap { file ->
            queriesIn(file.readText())
                .filter { it.startsWith("SELECT") }
                .filter { sql -> USER_TABLES.any { sql.reads(it) } }
                .filterNot { it.contains("deletedAt IS NULL") }
                .filterNot { sql -> ALLOWED.any { sql.contains(it) } }
                .map { "${file.name}: $it" }
        }

        assertEquals(
            "these reads would show the user rows they have deleted",
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * And the guard can fail.
     *
     * A test that has never been seen to fail is a test nobody should trust,
     * and this one is a text search over source code — exactly the kind that
     * can rot into always passing when a file moves or a format changes.
     */
    @Test
    fun `the guard catches a read that forgets the clause`() {
        val queries = queriesIn(
            """
            @Query("SELECT * FROM plays ORDER BY playedAt DESC")
            fun observePlays(): Flow<List<PlayEntity>>
            """.trimIndent(),
        )

        assertEquals(1, queries.size)
        assertTrue(queries.single().reads("plays"))
        assertTrue(!queries.single().contains("deletedAt IS NULL"))
    }

    @Test
    fun `the guard is reading real files`() {
        // If the DAO folder ever moves, the search above would find nothing and
        // pass by saying nothing. Nine DAOs are expected; a new one is a
        // deliberate change to this number.
        assertEquals(9, daoSources().size)
    }

    // --------------------------------------------------------------- helpers --

    private fun rowCount(table: String): Int = valueOf("SELECT COUNT(*) FROM $table")

    private fun valueOf(sql: String): Int =
        database.openHelper.readableDatabase.query(sql).use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun run(id: String) = CampaignRunEntity(
        id = id,
        templateId = "mts",
        templateName = "The Rise of Red Skull",
        difficulty = "standard",
        createdAt = now,
        templateJson = "{}",
    )

    private fun play(id: String, won: Boolean) = PlayEntity(
        id = id,
        playedAt = now,
        scenarioCode = "01097",
        scenarioName = "Rhino",
        difficulty = "standard",
        heroCode = "01001a",
        heroName = "Spider-Man",
        aspects = "justice",
        won = won,
    )

    private fun draw(id: String) = RandomizerHistoryEntity(
        id = id,
        createdAt = now,
        scenarioCode = "01097",
        difficulty = "standard",
        playerCount = 1,
        heroes = "01001a:justice",
        modularSetCodes = "",
    )

    private fun deck(id: String) = SavedDeckEntity(
        id = id,
        marvelCdbId = 0,
        kind = "LOCAL",
        url = "",
        name = id,
        heroCode = "01001a",
        heroName = "Spider-Man",
        aspects = "justice",
        slots = "",
        ignoreDeckLimitSlots = "",
        descriptionMd = null,
        version = null,
        tags = null,
        rawJson = "",
        lastSyncedAt = now,
    )

    private companion object {
        /** The nine tables that belong to the user and will sync. */
        val USER_TABLES = listOf(
            "owned_packs",
            "excluded_modular_sets",
            "excluded_scenarios",
            "favourite_cards",
            "saved_decks",
            "campaign_runs",
            "campaign_events",
            "plays",
            "randomizer_history",
        )

        /**
         * The reads that see a tombstone on purpose.
         *
         * Only one, and it is the photo sweep: see the test above for why the
         * files outlive the row.
         */
        val ALLOWED = listOf("SELECT photos FROM plays")

        /** Every `@Query` in one DAO source, whitespace flattened. */
        fun queriesIn(source: String): List<String> =
            Regex("""@Query\(\s*(""\"(.*?)""\"|"(.*?)")""", RegexOption.DOT_MATCHES_ALL)
                .findAll(source)
                .map { match ->
                    (match.groupValues[2].ifEmpty { match.groupValues[3] })
                        .replace(Regex("""\s+"""), " ")
                        .trim()
                }
                .toList()

        /** True when this statement reads [table], as a source or in a join. */
        fun String.reads(table: String): Boolean =
            Regex("""\b(FROM|JOIN)\s+`?$table`?\b""").containsMatchIn(this)

        fun daoSources(): List<File> {
            val relative =
                "app/src/main/java/com/hasyame/marvelchampions/data/db/dao"
            var directory: File? = File("").absoluteFile
            while (directory != null) {
                for (candidate in listOf(relative, relative.removePrefix("app/"))) {
                    val folder = File(directory, candidate)
                    if (folder.isDirectory) {
                        return folder.listFiles { file -> file.extension == "kt" }
                            .orEmpty()
                            .sortedBy { it.name }
                    }
                }
                directory = directory.parentFile
            }
            error("could not find the DAO sources from ${File("").absolutePath}")
        }
    }
}
