package com.hasyame.marvelchampions.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.backup.Backup
import com.hasyame.marvelchampions.data.backup.BackupRepository
import com.hasyame.marvelchampions.data.backup.BackupResult
import com.hasyame.marvelchampions.data.db.MarvelChampionsDatabase
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.photos.PhotoStore
import com.hasyame.marvelchampions.data.settings.AppPreferences
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A backup written by Thwart Web, format 2, imports here losslessly and
 * exports back whole: docs/spec/achievements/sync.md §2, the round-trip
 * fixture each repository keeps of the other platform's export.
 *
 * Two readings of "whole". The document codec must give back every field
 * the file carried, including the ones this build has never heard of (a
 * collection it does not keep, a key on a play, a key on a seat), and the
 * database must hold and export a play field for field, unknown keys
 * included. What this build adds on the way out is only a default the web
 * left unsaid, never a change to what was there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class BackupWebFixtureTest {

    private lateinit var context: Context
    private lateinit var database: MarvelChampionsDatabase
    private lateinit var repository: BackupRepository

    private val fixture: String = javaClass.getResource("/backup-web-v2.json")!!.readText()
    private val json = BackupRepository.DOCUMENT_JSON

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MarvelChampionsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BackupRepository(
            context,
            database,
            AppPreferences(context),
            PhotoStore(context, Dispatchers.Unconfined),
            Dispatchers.Unconfined,
            sessions = com.hasyame.marvelchampions.data.sync.SyncSessionStore(context, com.hasyame.marvelchampions.data.security.SecretStore()),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `the document reads and writes back every field, known or not`() {
        val expected = Json.parseToJsonElement(fixture)
        val backup = json.decodeFromString(Backup.serializer(), fixture)
        val again = json.encodeToJsonElement(backup)

        assertEquals(2, backup.formatVersion)
        assertContains(expected, again, "backup")
        // Stars are now known records. Their original values survive; local
        // sync timestamps may be emitted with the same defaults as other rows.
        assertContains(expected.jsonObject.getValue("favouritePlays"), again.jsonObject.getValue("favouritePlays"), "favouritePlays")
        assertEquals(0L, backup.favouritePlays.single().updatedAt)
        assertEquals(null, backup.favouritePlays.single().deletedAt)
        assertEquals(expected.jsonObject["plays"], again.jsonObject["plays"])
    }

    @Test
    fun `the plays are read as the specification says`() {
        val backup = json.decodeFromString(Backup.serializer(), fixture)
        val (draft, old) = backup.plays

        assertEquals("draft", draft.mode)
        assertEquals(true, draft.roster[0].isOwner)
        assertEquals(null, draft.roster[1].isOwner)
        assertEquals("01001a", draft.ownerSeat?.code)
        assertEquals(JsonPrimitive("blue"), draft.roster[1].extra["seatColour"])
        assertEquals(JsonPrimitive("rain"), draft.extra["weather"])

        // A play without the new fields: no mode, no owner flag, nothing extra.
        assertEquals(null, old.mode)
        assertTrue(old.extra.isEmpty())
        assertEquals(null, old.ownerSeat)
    }

    @Test
    fun `restored into the database and exported again, a play comes back field for field`() = runTest {
        val backup = json.decodeFromString(Backup.serializer(), fixture)
        assertTrue(repository.restore(backup) is BackupResult.Restored)

        val file = File(context.cacheDir, "web-again.json")
        val destination = Uri.fromFile(file)
        assertTrue(repository.export(destination) is BackupResult.Exported)

        val again = Json.parseToJsonElement(file.readText()).jsonObject
        val expected = Json.parseToJsonElement(fixture).jsonObject
        assertEquals(2, again.getValue("formatVersion").jsonPrimitive.content.toInt())
        assertEquals(expected.getValue("plays"), again.getValue("plays"))
        // Dismissed packs merge with the device's own on restore, by design.
        val settings = JsonObject(expected.getValue("settings").jsonObject.filterKeys { it != "dismissedPacks" })
        assertContains(settings, again.getValue("settings"), "settings")
        assertContains(expected.getValue("ownedPacks"), again.getValue("ownedPacks"), "ownedPacks")

        // The database column itself carries the unknown keys.
        val stored = database.playDao().getAllPlays().associateBy { it.id }
        assertEquals(JsonPrimitive("rain"), stored.getValue(backup.plays[0].id).extra["weather"])
    }

    @Test
    fun `a record of a play carries its unknown keys back out, known fields winning`() {
        val body = Json.parseToJsonElement(fixture).jsonObject.getValue("plays").jsonArray[0].jsonObject
        val play = json.decodeFromJsonElement(PlayEntity.serializer(), body)
        assertEquals(body, json.encodeToJsonElement(play).jsonObject)
        // An extra key that later becomes a known field never shadows it.
        val clashing = play.copy(extra = JsonObject(mapOf("won" to JsonPrimitive(false), "weather" to JsonPrimitive("rain"))))
        val out = json.encodeToJsonElement(clashing).jsonObject
        assertEquals(JsonPrimitive(true), out["won"])
        assertEquals(JsonPrimitive("rain"), out["weather"])
    }

    /**
     * An export of this build, format 2, written under `app/build/fixtures`
     * for the web repository to keep as its own round-trip fixture
     * (`web/scripts/fixtures/backup-android-v2.json`): a play with the
     * owner's seat flagged, a draft mode, and a key neither client knows.
     */
    @Test
    fun `an export of this build is written for the web to keep as a fixture`() = runTest {
        val backup = json.decodeFromString(Backup.serializer(), fixture)
        assertTrue(repository.restore(backup.copy(appVersion = "android")) is BackupResult.Restored)
        val out = File("build/fixtures").apply { mkdirs() }.resolve("backup-android-v2.json")
        assertTrue(repository.export(Uri.fromFile(out)) is BackupResult.Exported)
        val written = Json.parseToJsonElement(out.readText()).jsonObject
        assertEquals(2, written.getValue("formatVersion").jsonPrimitive.content.toInt())
        assertEquals(JsonPrimitive("rain"), written.getValue("plays").jsonArray[0].jsonObject["weather"])
    }

    /**
     * Every field of [expected] is in [actual] with the same value; arrays
     * element for element, in order. What [actual] adds is a default the
     * fixture left unsaid, which the other platform ignores in turn.
     */
    private fun assertContains(expected: JsonElement, actual: JsonElement?, path: String) {
        when (expected) {
            is JsonObject -> {
                val o = actual as? JsonObject ?: error("$path: not an object in the export")
                expected.forEach { (key, value) -> assertContains(value, o[key], "$path.$key") }
            }

            is JsonArray -> {
                val a = actual as? JsonArray ?: error("$path: not a list in the export")
                assertEquals("$path: length", expected.size, a.size)
                expected.forEachIndexed { i, value -> assertContains(value, a[i], "$path[$i]") }
            }

            is JsonNull, is JsonPrimitive -> assertEquals(path, expected, actual)
        }
    }
}
