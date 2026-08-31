package com.hasyame.marvelchampions.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.backup.Backup
import com.hasyame.marvelchampions.data.backup.BackupRepository
import com.hasyame.marvelchampions.data.backup.BackupResult
import com.hasyame.marvelchampions.data.backup.BackupSettings
import com.hasyame.marvelchampions.data.db.MarvelChampionsDatabase
import com.hasyame.marvelchampions.data.db.entity.CampaignEventEntity
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import com.hasyame.marvelchampions.data.db.entity.ExcludedModularSetEntity
import com.hasyame.marvelchampions.data.db.entity.ExcludedScenarioEntity
import com.hasyame.marvelchampions.data.db.entity.FavouriteCardEntity
import com.hasyame.marvelchampions.data.db.entity.OwnedPackEntity
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.RandomizerHistoryEntity
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import com.hasyame.marvelchampions.data.photos.PhotoStore
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.model.ThemeChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Out to a file and back in, with nothing lost on the way.
 *
 * A backup is the only copy of everything a player has built — this app has no
 * account and no server — so a field that quietly fails to travel is data that
 * disappears at the exact moment somebody is depending on it. That has already
 * happened once: `excluded_scenarios` has existed since 1.28.0 and was exported
 * nowhere, so every restore since then has handed back a collection with the
 * scenarios the player had ticked off silently ticked on again.
 *
 * Set `MCC_BACKUP_FILE` to a real export to run this against one. Nothing real
 * is committed here: a genuine backup holds somebody's play history, the places
 * they play and the names of cards, none of which belongs in a repository.
 */
@RunWith(RobolectricTestRunner::class)
// The real application sweeps unreferenced photographs moments after it starts,
// which would race a test that writes files. A plain one leaves them alone.
@Config(application = android.app.Application::class)
class BackupRoundTripTest {

    private lateinit var context: Context
    private lateinit var database: MarvelChampionsDatabase
    private lateinit var preferences: AppPreferences
    private lateinit var repository: BackupRepository

    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            MarvelChampionsDatabase::class.java,
        ).allowMainThreadQueries().build()
        preferences = AppPreferences(context)
        repository = BackupRepository(
            context,
            database,
            preferences,
            PhotoStore(context, Dispatchers.Unconfined),
            Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `everything the player made comes back`() = runTest {
        givenAFullDatabase()
        val file = destination("round-trip.json")

        assertTrue(repository.export(file) is BackupResult.Exported)
        andThenEverythingIsThrownAway()

        val backup = repository.peek(file).getOrThrow()
        assertTrue(repository.restore(backup) is BackupResult.Restored)

        assertEquals(
            mapOf("core" to 1, "mts" to 2),
            database.ownedPackDao().getOwned().associate { it.packCode to it.quantity },
        )
        assertEquals(
            listOf("bomb_scare"),
            database.excludedModularSetDao().getExcludedCodes(),
        )
        // The gap this change closes. Before it, this list came back empty and
        // said nothing about having done so.
        assertEquals(
            listOf("wrecking_crew"),
            database.excludedScenarioDao().getExcludedCodes(),
        )
        assertEquals(listOf("01001"), database.favouriteDao().getAll().map { it.cardCode })
        assertEquals(listOf("local-1"), database.savedDeckDao().getDecks().map { it.id })
        assertEquals(listOf("run-1"), database.campaignDao().getRuns().map { it.id })
        assertEquals(
            listOf("event-1", "event-2"),
            database.campaignDao().getEvents("run-1").map { it.id },
        )
        assertEquals(listOf("play-1"), database.playDao().getAllPlays().map { it.id })
        assertEquals(
            listOf("draw-1"),
            database.randomizerHistoryDao().getHistory().map { it.id },
        )

        // Contents, not counts. A restore that returned the right number of
        // plays with the notes blanked would pass a count.
        val play = database.playDao().getPlay("play-1")
        assertNotNull(play)
        assertEquals("close one", play!!.notes)
        assertEquals("Chez Marc", play.location)
        assertEquals(2, play.players)
        assertEquals(now, play.playedAt)
    }

    @Test
    fun `the language and the theme come back too`() = runTest {
        givenAFullDatabase()
        val file = destination("settings.json")
        assertTrue(repository.export(file) is BackupResult.Exported)

        // The reinstall: a fresh device, on the defaults.
        preferences.setCardLocale(CardLocale.ENGLISH)
        preferences.setThemeChoice(ThemeChoice.LIGHT)
        preferences.setPlayLocation("")
        preferences.setTrackEncounter(false)

        repository.restore(repository.peek(file).getOrThrow())

        // Before this change a restore returned every deck and every campaign
        // and then showed them in the wrong language.
        assertEquals(CardLocale.FRENCH, preferences.cardLocale.first())
        assertEquals(ThemeChoice.DARK, preferences.themeChoice.first())
        assertEquals("Chez Marc", preferences.playLocation.first())
        assertTrue(preferences.trackEncounter.first())
        assertEquals(setOf("gob", "sm"), preferences.dismissedPacks.first())
    }

    @Test
    fun `a pack turned down on either device stays turned down`() = runTest {
        givenAFullDatabase()
        val file = destination("dismissed.json")
        repository.export(file)

        preferences.setDismissedPacks(setOf("aoa"))
        repository.restore(repository.peek(file).getOrThrow())

        // A union rather than a replacement. The alternative is a restore
        // re-offering a pack the player has already said no to on this device.
        assertEquals(setOf("aoa", "gob", "sm"), preferences.dismissedPacks.first())
    }

    @Test
    fun `the file carries the two new sections under the names it should`() = runTest {
        givenAFullDatabase()
        val file = destination("shape.json")
        repository.export(file)

        val text = File(file.path!!).readText()
        assertTrue("excludedScenarios missing", text.contains("\"excludedScenarios\""))
        assertTrue("settings missing", text.contains("\"settings\""))
        // The version is deliberately not bumped. An older build ignores keys it
        // does not know, so it can still read this file and merely loses the two
        // sections, which beats refusing the backup outright.
        assertTrue(text.contains("\"formatVersion\": 1"))
    }

    @Test
    fun `a backup written before either section existed still restores`() = runTest {
        preferences.setCardLocale(CardLocale.ENGLISH)
        preferences.setPlayLocation("Home")

        // Exactly what 1.38.0 wrote: no excludedScenarios, no settings.
        val file = destination("old.json")
        File(file.path!!).writeText(
            """
            {
              "formatVersion": 1,
              "createdAt": $now,
              "appVersion": "1.38.0",
              "ownedPacks": [{ "packCode": "core", "quantity": 1 }],
              "plays": []
            }
            """.trimIndent(),
        )

        val backup = repository.peek(file).getOrThrow()
        assertEquals(emptyList<ExcludedScenarioEntity>(), backup.excludedScenarios)
        assertEquals(null, backup.settings)

        assertTrue(repository.restore(backup) is BackupResult.Restored)
        assertEquals(listOf("core"), database.ownedPackDao().getOwnedCodes())
        // No settings in the file means nothing to put back, not a reset to
        // defaults the player never chose.
        assertEquals(CardLocale.ENGLISH, preferences.cardLocale.first())
        assertEquals("Home", preferences.playLocation.first())
    }

    @Test
    fun `a deleted row does not travel in the backup`() = runTest {
        givenAFullDatabase()
        database.playDao().delete("play-1", now)

        val file = destination("tombstone.json")
        repository.export(file)

        // Export reads through the same filtered queries the screens do, so a
        // tombstone stays a local fact. A backup is a snapshot of what the
        // player has, not a change feed.
        assertTrue(repository.peek(file).getOrThrow().plays.isEmpty())
    }

    /**
     * The same round trip, against a real export from a real phone.
     *
     * Skipped unless `MCC_BACKUP_FILE` names one. The synthetic case above is
     * tidy by construction; a file written by the app after two years of use
     * has null columns, empty strings and rows written by versions that no
     * longer exist, and it is the one that finds the surprises.
     */
    @Test
    fun `a real export restores whole`() = runTest {
        val path = System.getenv("MCC_BACKUP_FILE")
        assumeTrue("set MCC_BACKUP_FILE to a real export to run this", !path.isNullOrBlank())
        val source = Uri.fromFile(File(path!!))

        val backup = repository.peek(source).getOrThrow()
        assertTrue("nothing to restore in $path", backup.plays.isNotEmpty())

        assertTrue(repository.restore(backup) is BackupResult.Restored)

        // Read back out and compare to what went in, field by field, rather
        // than counting rows.
        val exported = destination("real-round-trip.json")
        repository.export(exported)
        val again = repository.peek(exported).getOrThrow()

        assertEquals(backup.ownedPacks.toSet(), again.ownedPacks.toSet())
        assertEquals(backup.excludedModularSets.toSet(), again.excludedModularSets.toSet())
        assertEquals(backup.excludedScenarios.toSet(), again.excludedScenarios.toSet())
        assertEquals(backup.decks.toSet(), again.decks.toSet())
        assertEquals(backup.campaignRuns.toSet(), again.campaignRuns.toSet())
        assertEquals(backup.campaignEvents.toSet(), again.campaignEvents.toSet())
        assertEquals(backup.plays.toSet(), again.plays.toSet())
        assertEquals(backup.randomizerHistory.toSet(), again.randomizerHistory.toSet())
        assertEquals(backup.favouriteCards.toSet(), again.favouriteCards.toSet())
    }

    // --------------------------------------------------------------- setup ---

    private fun destination(name: String) =
        Uri.fromFile(File(context.cacheDir, name).apply { parentFile?.mkdirs() })

    /** A row in every table the backup carries, plus the settings. */
    private suspend fun givenAFullDatabase() {
        database.ownedPackDao().upsertAll(
            listOf(OwnedPackEntity("core", 1), OwnedPackEntity("mts", 2)),
        )
        database.excludedModularSetDao().exclude(ExcludedModularSetEntity("bomb_scare"))
        database.excludedScenarioDao().exclude(ExcludedScenarioEntity("wrecking_crew"))
        database.favouriteDao().add(FavouriteCardEntity("01001", addedAt = now))
        database.savedDeckDao().upsert(
            SavedDeckEntity(
                id = "local-1",
                marvelCdbId = 0,
                kind = "LOCAL",
                url = "",
                name = "Spider-Man",
                heroCode = "01001a",
                heroName = "Spider-Man",
                aspects = "justice",
                slots = "01002=3",
                ignoreDeckLimitSlots = "",
                descriptionMd = null,
                version = null,
                tags = null,
                rawJson = "",
                lastSyncedAt = now,
            ),
        )
        database.campaignDao().insertRun(
            CampaignRunEntity(
                id = "run-1",
                templateId = "mts",
                templateName = "The Rise of Red Skull",
                name = "Tuesdays",
                difficulty = "standard",
                createdAt = now,
                templateJson = """{"id":"mts"}""",
            ),
        )
        database.campaignDao().appendEvents(
            listOf(
                CampaignEventEntity("event-1", "run-1", now, """{"type":"setup"}"""),
                CampaignEventEntity("event-2", "run-1", now + 1, """{"type":"manual"}"""),
            ),
        )
        database.playDao().insert(
            PlayEntity(
                id = "play-1",
                playedAt = now,
                scenarioCode = "01097",
                scenarioName = "Rhino",
                difficulty = "standard",
                heroCode = "01001a",
                heroName = "Spider-Man",
                aspects = "justice",
                players = 2,
                won = true,
                notes = "close one",
                location = "Chez Marc",
            ),
        )
        database.randomizerHistoryDao().insert(
            RandomizerHistoryEntity(
                id = "draw-1",
                createdAt = now,
                scenarioCode = "01097",
                difficulty = "standard",
                playerCount = 1,
                heroes = "01001a:justice",
                modularSetCodes = "bomb_scare",
            ),
        )

        preferences.restore(
            BackupSettings(
                cardLocale = CardLocale.FRENCH.code,
                themeChoice = ThemeChoice.DARK.code,
                playLocation = "Chez Marc",
                trackEncounter = true,
                dismissedPacks = listOf("gob", "sm"),
            ),
        )
    }

    /** The phone that broke: the database is there, and it is empty. */
    private suspend fun andThenEverythingIsThrownAway() {
        database.ownedPackDao().clear()
        database.excludedModularSetDao().clear()
        database.excludedScenarioDao().clear()
        database.favouriteDao().deleteAll()
        database.savedDeckDao().deleteAll()
        database.campaignDao().deleteAllRuns()
        database.playDao().deleteAll()
        database.randomizerHistoryDao().clear()
        assertTrue(database.playDao().getAllPlays().isEmpty())
    }
}
