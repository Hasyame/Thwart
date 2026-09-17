package com.hasyame.marvelchampions.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.db.MarvelChampionsDatabase
import com.hasyame.marvelchampions.data.db.entity.DeckFolderEntity
import com.hasyame.marvelchampions.data.db.entity.SyncCollection
import com.hasyame.marvelchampions.data.security.SecretStore
import com.hasyame.marvelchampions.data.sync.AutoSync
import com.hasyame.marvelchampions.data.sync.SyncSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Folders against a real database: a deck in at most one, whole-record
 * writes marked for sync, a soft delete, and the body the web client reads.
 */
@RunWith(RobolectricTestRunner::class)
class DeckFolderRepositoryTest {

    private lateinit var database: MarvelChampionsDatabase
    private lateinit var folders: DeckFolderRepository
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MarvelChampionsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sessions = SyncSessionStore(context, SecretStore())
        sessions.signOut()
        folders = DeckFolderRepository(database, AutoSync(context, sessions), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a deck is in at most one folder, and moving it takes it out of the other`() = runTest {
        val solo = folders.create("Solo")!!
        val duo = folders.create("Duo")!!
        assertNull("a blank name makes nothing", folders.create("   "))

        folders.moveDeck("local-1", solo)
        folders.moveDeck("local-2", solo)
        folders.moveDeck("local-1", duo)

        val shelf = folders.observeFolders().first()
        assertEquals(listOf("Duo", "Solo"), shelf.map { it.name })
        assertEquals(listOf("local-1"), shelf.first { it.id == duo }.deckIds)
        assertEquals(listOf("local-2"), shelf.first { it.id == solo }.deckIds)
        assertEquals("Duo", DeckFolderRepository.folderOf(shelf, "local-1")!!.name)

        folders.moveDeck("local-1", null)
        assertNull(DeckFolderRepository.folderOf(folders.observeFolders().first(), "local-1"))
    }

    @Test
    fun `every change is one folder written whole and marked for sync`() = runTest {
        val id = folders.create("Solo")!!
        assertTrue(database.syncStateDao().get(SyncCollection.DECK_FOLDERS.key, id)!!.dirty)

        val before = database.deckFolderDao().getFolder(id)!!.updatedAt
        Thread.sleep(2)
        folders.rename(id, "Solo games")
        val after = database.deckFolderDao().getFolder(id)!!
        assertEquals("Solo games", after.name)
        assertTrue("a fresh updatedAt is what the merge compares", after.updatedAt > before)
    }

    @Test
    fun `deleting a folder leaves a tombstone and its decks on the shelf`() = runTest {
        val id = folders.create("Solo")!!
        folders.moveDeck("local-1", id)

        folders.delete(id)

        assertEquals(emptyList<DeckFolderEntity>(), folders.observeFolders().first())
        val tombstone = database.syncRecordDao().folder(id)
        assertNotNull("sync still sees it, so the other device learns it is gone", tombstone!!.deletedAt)
    }

    @Test
    fun `the body on the wire is the web's record, nothing more`() {
        val folder = DeckFolderEntity(id = "folder-1", name = "Solo", deckIds = listOf("local-1", "decklist-2"), createdAt = 10, updatedAt = 20)
        val body = json.encodeToJsonElement(DeckFolderEntity.serializer(), folder).jsonObject

        assertEquals(setOf("id", "name", "deckIds", "createdAt", "updatedAt"), body.keys)
        assertEquals(listOf("local-1", "decklist-2"), body["deckIds"]!!.jsonArray.map { it.jsonPrimitive.content })
        // And a body the web wrote reads back the same.
        val fromWeb = json.decodeFromString(DeckFolderEntity.serializer(), """{"id":"folder-9","name":"Duo","deckIds":["a"],"createdAt":1,"updatedAt":2}""")
        assertEquals(listOf("a"), fromWeb.deckIds)
        assertNull(fromWeb.deletedAt)
    }
}
