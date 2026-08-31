package com.hasyame.marvelchampions.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.db.dao.SavedDeckDao
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import com.hasyame.marvelchampions.data.repository.DeckRepository
import com.hasyame.marvelchampions.domain.deeplink.DeckReference
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SavedDeckDaoTest {

    private lateinit var database: MarvelChampionsDatabase
    private lateinit var dao: SavedDeckDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MarvelChampionsDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.savedDeckDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a decklist and a deck with the same number are different rows`() = runTest {
        // MarvelCDB keeps two id spaces; collapsing them would overwrite one
        // deck with another.
        dao.upsert(deck(DeckReference(30000, DeckReference.Kind.DECKLIST), "Cyclops"))
        dao.upsert(deck(DeckReference(30000, DeckReference.Kind.DECK), "Black Widow"))

        assertEquals(2, dao.count())
        assertEquals("Cyclops", dao.getDeck("decklist-30000")?.name)
        assertEquals("Black Widow", dao.getDeck("deck-30000")?.name)
    }

    @Test
    fun `re-importing the same deck replaces it rather than duplicating`() = runTest {
        val reference = DeckReference(1, DeckReference.Kind.DECKLIST)
        dao.upsert(deck(reference, "Old name"))
        dao.upsert(deck(reference, "New name"))

        assertEquals(1, dao.count())
        assertEquals("New name", dao.getDeck("decklist-1")?.name)
    }

    @Test
    fun `deleting removes only the requested deck`() = runTest {
        dao.upsert(deck(DeckReference(1, DeckReference.Kind.DECKLIST), "A"))
        dao.upsert(deck(DeckReference(2, DeckReference.Kind.DECKLIST), "B"))

        dao.delete("decklist-1", 1_700_000_000_000L)

        assertNull(dao.getDeck("decklist-1"))
        assertEquals(1, dao.count())
    }

    @Test
    fun `decks are listed by name`() = runTest {
        dao.upsert(deck(DeckReference(1, DeckReference.Kind.DECKLIST), "Zephyr"))
        dao.upsert(deck(DeckReference(2, DeckReference.Kind.DECKLIST), "Apex"))

        assertEquals(listOf("Apex", "Zephyr"), dao.getDecks().map { it.name })
    }

    @Test
    fun `slots survive the round trip`() = runTest {
        val reference = DeckReference(1, DeckReference.Kind.DECKLIST)
        dao.upsert(deck(reference, "A", slots = "01041=1,01044=3,01043a=2"))

        val stored = requireNotNull(dao.getDeck("decklist-1"))

        assertEquals(
            mapOf("01041" to 1, "01044" to 3, "01043a" to 2),
            DeckRepository.parseSlots(stored.slots),
        )
    }

    @Test
    fun `a deck can carry two aspects`() = runTest {
        dao.upsert(
            deck(DeckReference(1, DeckReference.Kind.DECKLIST), "A", aspects = "pool,leadership"),
        )

        assertEquals(
            listOf("pool", "leadership"),
            DeckRepository.parseAspects(requireNotNull(dao.getDeck("decklist-1")).aspects),
        )
    }

    @Test
    fun `malformed slot entries are skipped rather than crashing`() = runTest {
        assertEquals(mapOf("01041" to 1), DeckRepository.parseSlots("01041=1,broken,=2,x="))
        assertEquals(emptyMap<String, Int>(), DeckRepository.parseSlots(""))
    }

    private fun deck(
        reference: DeckReference,
        name: String,
        slots: String = "01041=1",
        aspects: String = "leadership",
    ) = SavedDeckEntity(
        id = DeckRepository.localId(reference),
        marvelCdbId = reference.id,
        kind = reference.kind.name,
        url = reference.apiUrl,
        name = name,
        heroCode = "01040a",
        heroName = "Black Panther",
        aspects = aspects,
        slots = slots,
        ignoreDeckLimitSlots = "",
        descriptionMd = null,
        version = "1.0",
        tags = null,
        rawJson = "{}",
        lastSyncedAt = 0L,
    )
}
