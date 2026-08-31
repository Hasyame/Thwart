package com.hasyame.marvelchampions.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Schema 17 to 18, run against a real version-17 database.
 *
 * This is the test that matters most in the whole change. A migration that
 * loses a row loses somebody's campaign log, on their phone, with no copy
 * anywhere, and no version of the app that ships afterwards can put it back.
 * The rest of the sync work can be got wrong and fixed next week.
 *
 * The old database is built from `schemas/17.json` rather than from SQL typed
 * out here, because that file is the authoritative record of what actually
 * shipped in 1.38.0. SQL typed by hand would be a second opinion about it, and
 * a test that agrees with the wrong opinion proves nothing.
 */
@RunWith(RobolectricTestRunner::class)
class SyncMigrationTest {

    private lateinit var context: Context
    private lateinit var databaseFile: File

    /**
     * Timestamps far enough apart to tell seeded values from a default and from
     * each other. All in 2024, so a real clock cannot collide with them.
     */
    private val played = 1_704_067_200_000L
    private val created = 1_706_745_600_000L
    private val starred = 1_709_251_200_000L
    private val synced = 1_711_929_600_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseFile = context.getDatabasePath(NAME)
        databaseFile.parentFile?.mkdirs()
        databaseFile.delete()
    }

    @After
    fun tearDown() {
        databaseFile.delete()
    }

    @Test
    fun `every row survives the upgrade`() = runTest {
        givenAVersion17DatabaseWithARowInEveryTable()

        val database = openAndMigrate()

        try {
            assertEquals(listOf("core", "mts"), database.ownedPackDao().getOwnedCodes().sorted())
            assertEquals(listOf("bomb_scare"), database.excludedModularSetDao().getExcludedCodes())
            assertEquals(listOf("wrecking_crew"), database.excludedScenarioDao().getExcludedCodes())
            assertEquals(listOf("01001"), database.favouriteDao().getAll().map { it.cardCode })
            assertEquals(listOf("decklist-1"), database.savedDeckDao().getDecks().map { it.id })
            assertEquals(listOf("run-1"), database.campaignDao().getRuns().map { it.id })
            assertEquals(listOf("play-1"), database.playDao().getAllPlays().map { it.id })
            assertEquals(
                listOf("draw-1"),
                database.randomizerHistoryDao().getHistory().map { it.id },
            )

            // The log, which is the row nobody can replace by playing again.
            assertEquals(listOf("event-1"), database.campaignDao().getEvents("run-1").map { it.id })

            // And the contents, not merely the count: a migration that kept the
            // right number of rows and blanked a column would pass a count.
            val play = database.playDao().getPlay("play-1")
            assertNotNull(play)
            assertEquals("Rhino", play!!.scenarioName)
            assertEquals(played, play.playedAt)
            assertTrue(play.won)

            val paused = database.pausedGameDao().current()
            assertNotNull("the game put down mid-play went missing", paused)
        } finally {
            database.close()
        }
    }

    @Test
    fun `a row that already carries a time is seeded from it`() = runTest {
        givenAVersion17DatabaseWithARowInEveryTable()

        val database = openAndMigrate()

        try {
            // Not zero, and not "now": each row keeps the time it already had,
            // so the first sync can order two devices' histories correctly
            // rather than treating everything written before today as
            // simultaneous.
            assertEquals(played, database.playDao().getPlay("play-1")?.updatedAt)
            assertEquals(created, database.campaignDao().getRun("run-1")?.updatedAt)
            assertEquals(
                created,
                database.randomizerHistoryDao().getHistory().single().updatedAt,
            )
            assertEquals(starred, database.favouriteDao().getAll().single().updatedAt)
            assertEquals(synced, database.savedDeckDao().getDecks().single().updatedAt)
        } finally {
            database.close()
        }
    }

    @Test
    fun `a row with no time of its own is honestly older than everything`() = runTest {
        givenAVersion17DatabaseWithARowInEveryTable()

        val database = openAndMigrate()

        try {
            // The three presence-only tables carry no timestamp to seed from,
            // so they keep zero. That is not a gap: zero means "older than
            // anything", which loses to any dated row on a first merge and wins
            // over nothing, and that matches the truth that nobody recorded
            // when the box was ticked.
            assertEquals(0L, database.ownedPackDao().getOwned().first().updatedAt)
            assertEquals(0L, database.excludedModularSetDao().getExcluded().single().updatedAt)
            assertEquals(0L, database.excludedScenarioDao().getExcluded().single().updatedAt)
        } finally {
            database.close()
        }
    }

    @Test
    fun `nothing arrives already deleted`() = runTest {
        givenAVersion17DatabaseWithARowInEveryTable()

        val database = openAndMigrate()

        try {
            // The reads all filter tombstones, so a migration that set
            // deletedAt would empty the app rather than crash it, and the
            // assertions above would still pass on an empty database if they
            // were counting the wrong thing. Read it directly.
            EIGHT_TABLES.forEach { table ->
                database.openHelper.readableDatabase
                    .query("SELECT COUNT(*) FROM $table WHERE deletedAt IS NOT NULL")
                    .use { cursor ->
                        cursor.moveToFirst()
                        assertEquals(
                            "$table came out of the migration deleted",
                            0,
                            cursor.getInt(0),
                        )
                    }
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `the card cache is left out of it`() = runTest {
        givenAVersion17DatabaseWithARowInEveryTable()

        val database = openAndMigrate()

        try {
            // Cards and packs are a cache of MarvelCDB, rebuilt on any device
            // and never synced. A sync column on them would be an invitation to
            // upload Fantasy Flight's card text to a server.
            listOf("cards", "packs", "pack_translations", "paused_games").forEach { table ->
                assertNull(
                    "$table should not have gained a sync column",
                    columnsOf(database, table).firstOrNull { it == "updatedAt" },
                )
            }
        } finally {
            database.close()
        }
    }

    // ---------------------------------------------------------------- setup --

    /**
     * Writes a version-17 database with a row in every table the migration
     * touches, plus the tables it must leave alone.
     *
     * Built from the exported schema, statement for statement, so this is the
     * database 1.38.0 actually created rather than an approximation of it.
     */
    private fun givenAVersion17DatabaseWithARowInEveryTable() {
        val schema = JSONObject(schemaFile(17).readText()).getJSONObject("database")
        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                database.execSQL(
                    entity.getString("createSql").replace("\${TABLE_NAME}", table),
                )
                entity.optJSONArray("indices")?.let { indices ->
                    for (i in 0 until indices.length()) {
                        database.execSQL(
                            indices.getJSONObject(i).getString("createSql")
                                .replace("\${TABLE_NAME}", table),
                        )
                    }
                }
                // The full-text index over `cards` is kept in step by triggers
                // Room writes rather than by the entity itself.
                entity.optJSONArray("contentSyncTriggers")?.let { triggers ->
                    for (i in 0 until triggers.length()) {
                        database.execSQL(triggers.getString(i))
                    }
                }
            }

            // Room refuses to open a database it cannot recognise. This is the
            // identity of schema 17, taken from the same file as the tables.
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY, identity_hash TEXT)",
            )
            database.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                arrayOf(schema.getString("identityHash")),
            )

            seed(database)
            database.version = 17
        } finally {
            database.close()
        }
    }

    private fun seed(database: SQLiteDatabase) {
        fun insert(table: String, vararg values: Pair<String, Any?>) {
            val row = ContentValues()
            values.forEach { (key, value) ->
                when (value) {
                    null -> row.putNull(key)
                    is Int -> row.put(key, value)
                    is Long -> row.put(key, value)
                    else -> row.put(key, value.toString())
                }
            }
            val inserted = database.insertOrThrow(table, null, row)
            assertTrue("could not seed $table", inserted >= 0)
        }

        insert("owned_packs", "packCode" to "core", "quantity" to 1)
        insert("owned_packs", "packCode" to "mts", "quantity" to 2)
        insert("excluded_modular_sets", "setCode" to "bomb_scare")
        insert("excluded_scenarios", "scenarioCode" to "wrecking_crew")
        insert("favourite_cards", "cardCode" to "01001", "addedAt" to starred)
        insert(
            "saved_decks",
            "id" to "decklist-1",
            "marvelCdbId" to 1L,
            "kind" to "DECKLIST",
            "url" to "",
            "name" to "Spider-Man",
            "heroCode" to "01001a",
            "heroName" to "Spider-Man",
            "aspects" to "justice",
            "slots" to "01002=3",
            "ignoreDeckLimitSlots" to "",
            "descriptionMd" to null,
            "version" to null,
            "tags" to null,
            "rawJson" to "{}",
            "lastSyncedAt" to synced,
            "locallyEdited" to 0,
        )
        insert(
            "campaign_runs",
            "id" to "run-1",
            "templateId" to "mts",
            "templateName" to "The Rise of Red Skull",
            "name" to "Tuesdays",
            "difficulty" to "standard",
            "standardSet" to "",
            "createdAt" to created,
            "finished" to 0,
            "templateJson" to """{"id":"mts"}""",
            "timerAccumulatedMillis" to 0L,
            "timerRunningSince" to null,
            "timerScenarioId" to null,
        )
        insert(
            "campaign_events",
            "id" to "event-1",
            "runId" to "run-1",
            "timestamp" to created,
            "payload" to """{"type":"setup"}""",
        )
        insert(
            "plays",
            "id" to "play-1",
            "playedAt" to played,
            "scenarioCode" to "01097",
            "scenarioName" to "Rhino",
            "difficulty" to "standard",
            "standardSet" to "",
            "heroCode" to "01001a",
            "heroName" to "Spider-Man",
            "aspects" to "justice",
            "otherHeroes" to "",
            "roster" to "[]",
            "players" to 1,
            "won" to 1,
            "elapsedMillis" to 2_400_000L,
            "notes" to "close one",
            "location" to "Home",
            "victoryPoints" to 0,
            "campaignRunId" to null,
            "reportedToBgg" to 0,
            "photos" to "",
        )
        insert(
            "randomizer_history",
            "id" to "draw-1",
            "createdAt" to created,
            "scenarioCode" to "01097",
            "difficulty" to "standard",
            "playerCount" to 1,
            "heroes" to "01001a:justice",
            "modularSetCodes" to "bomb_scare",
            "beaten" to 0,
        )
        // Device data, which must come through untouched and unsynced.
        insert(
            "paused_games",
            "id" to "paused-1",
            "savedAt" to played,
            "scenarioCode" to "01097",
            "scenarioName" to "Rhino",
            "difficulty" to "standard",
            "heroes" to "01001a|Spider-Man",
            "modularSetCodes" to "bomb_scare",
            "elapsedMillis" to 600_000L,
            "phase" to "VILLAIN",
            "villainStep" to "REVEAL",
            "heroLives" to "01001a|8",
            "villainLife" to 14,
            "villainStage" to 1,
            "photos" to "",
            "campaignRunId" to "",
        )
    }

    /** Opens at 18 through the real Room builder, so the real migration runs. */
    private fun openAndMigrate(): MarvelChampionsDatabase =
        Room.databaseBuilder(context, MarvelChampionsDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
            .also {
                // Room migrates lazily, on the first query.
                it.openHelper.writableDatabase
            }

    private fun columnsOf(database: MarvelChampionsDatabase, table: String): List<String> =
        database.openHelper.readableDatabase.query("PRAGMA table_info($table)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
        }

    private companion object {
        const val NAME = "sync-migration-test.db"

        /** The eight tables that gained the two columns. */
        val EIGHT_TABLES = listOf(
            "owned_packs",
            "excluded_modular_sets",
            "excluded_scenarios",
            "favourite_cards",
            "saved_decks",
            "campaign_runs",
            "plays",
            "randomizer_history",
        )

        /**
         * The exported schema for one version.
         *
         * Found by walking up from wherever the test happens to be running,
         * rather than assuming a working directory: this file is committed and
         * has to be found on a laptop and on CI alike.
         */
        fun schemaFile(version: Int): File {
            val relative = "app/schemas/" +
                "com.hasyame.marvelchampions.data.db.MarvelChampionsDatabase/$version.json"
            var directory: File? = File("").absoluteFile
            while (directory != null) {
                val candidate = File(directory, relative)
                if (candidate.isFile) {
                    return candidate
                }
                val inModule = File(directory, relative.removePrefix("app/"))
                if (inModule.isFile) {
                    return inModule
                }
                directory = directory.parentFile
            }
            error("could not find $relative from ${File("").absolutePath}")
        }
    }
}
