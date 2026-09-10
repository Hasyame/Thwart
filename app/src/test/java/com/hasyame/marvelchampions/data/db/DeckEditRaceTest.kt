package com.hasyame.marvelchampions.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbApi
import com.hasyame.marvelchampions.data.marvelcdb.dto.CardDto
import com.hasyame.marvelchampions.data.marvelcdb.dto.PackDto
import com.hasyame.marvelchampions.data.repository.CollectionRepository
import com.hasyame.marvelchampions.data.repository.DeckRepository
import com.hasyame.marvelchampions.data.security.SecretStore
import com.hasyame.marvelchampions.data.seed.SetNameOverrides
import com.hasyame.marvelchampions.data.sync.AutoSync
import com.hasyame.marvelchampions.data.sync.SyncSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

/**
 * A card added on one device does not undo a card added on another.
 *
 * The screen showing a deck is showing a number it read some time ago. With one
 * device that is the same as the truth; with two it is not, and the gap is
 * where an edit gets eaten. The editor used to add its delta to *its own copy*
 * and send the total — so if a tablet had changed that card in between, the
 * total was arithmetic done on a stale number and it wrote the tablet's change
 * away. No error, no conflict, just a card quietly back to what this phone
 * thought it was.
 *
 * Sending the delta instead makes the two compose, because the sum is worked
 * out against the row as it stands. These tests are the difference between the
 * two, written as the sequence that used to be wrong.
 */
@RunWith(RobolectricTestRunner::class)
class DeckEditRaceTest {

    private lateinit var database: MarvelChampionsDatabase
    private lateinit var decks: DeckRepository

    /** Nothing in these tests reaches MarvelCDB. */
    private class OfflineApi : MarvelCdbApi {
        override suspend fun getAllCards(encounter: Int): List<CardDto> = emptyList()
        override suspend fun getPackCards(packCode: String, encounter: Int): List<CardDto> =
            emptyList()

        override suspend fun getCard(code: String): CardDto = error("not used")
        override suspend fun getPacks(): List<PackDto> = emptyList()
        override suspend fun getAllCardsAt(url: String): List<CardDto> = emptyList()
        override suspend fun getPacksAt(url: String): List<PackDto> = emptyList()
        override suspend fun getDeckRaw(url: String): Response<ResponseBody> = error("not used")
    }

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, MarvelChampionsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val json = Json { ignoreUnknownKeys = true }
        val sessions = SyncSessionStore(context, SecretStore())
        sessions.signOut()
        val autoSync = AutoSync(context, sessions)
        decks = DeckRepository(
            api = OfflineApi(),
            savedDeckDao = database.savedDeckDao(),
            syncStateDao = database.syncStateDao(),
            cardDao = database.cardDao(),
            collectionRepository = CollectionRepository(
                packDao = database.packDao(),
                ownedPackDao = database.ownedPackDao(),
                excludedModularSetDao = database.excludedModularSetDao(),
                excludedScenarioDao = database.excludedScenarioDao(),
                cardDao = database.cardDao(),
                syncStateDao = database.syncStateDao(),
                setNameOverrides = SetNameOverrides(context, json, Dispatchers.Unconfined),
                autoSync = autoSync,
            ),
            autoSync = autoSync,
            json = json,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun deckWith(slots: String) {
        database.savedDeckDao().upsert(
            SavedDeckEntity(
                id = DECK,
                marvelCdbId = 0,
                kind = "LOCAL",
                url = "",
                name = "Under test",
                heroCode = "01001",
                heroName = "Iron Man",
                aspects = "aggression",
                slots = slots,
                ignoreDeckLimitSlots = "",
                descriptionMd = null,
                version = null,
                tags = null,
                rawJson = "",
                lastSyncedAt = 0,
            ),
        )
    }

    private suspend fun quantityOf(code: String): Int =
        DeckRepository.parseSlots(database.savedDeckDao().getDeck(DECK)!!.slots)[code] ?: 0

    @Test
    fun `adding a card adds one`() = runTest {
        deckWith("$CARD=2")

        decks.adjustCardQuantity(DECK, CARD, +1)

        assertEquals(3, quantityOf(CARD))
    }

    @Test
    fun `a change from another device is not written away by the next tap`() = runTest {
        // The screen opened showing two. Then the tablet made it four.
        deckWith("$CARD=2")
        deckWith("$CARD=4")

        // The phone taps +, still showing two. The old code sent 2 + 1 = 3 and
        // the tablet's change was gone; the delta composes with it instead.
        decks.adjustCardQuantity(DECK, CARD, +1)

        assertEquals(5, quantityOf(CARD))
    }

    @Test
    fun `a removal on another device is not undone by the next tap`() = runTest {
        // The worst version of the same thing: the tablet took the card out
        // and the phone's + used to put it back at the phone's old count.
        deckWith("$CARD=2,01005=1")
        deckWith("01005=1")

        decks.adjustCardQuantity(DECK, CARD, +1)

        assertEquals(1, quantityOf(CARD))
    }

    @Test
    fun `removing the last copy takes the card out rather than storing a zero`() = runTest {
        deckWith("$CARD=1,01005=2")

        decks.adjustCardQuantity(DECK, CARD, -1)

        assertEquals(0, quantityOf(CARD))
        assertEquals("01005=2", database.savedDeckDao().getDeck(DECK)!!.slots)
    }

    @Test
    fun `removing below zero does not store a negative`() = runTest {
        deckWith("$CARD=1")

        decks.adjustCardQuantity(DECK, CARD, -3)

        assertEquals(0, quantityOf(CARD))
    }

    @Test
    fun `two taps in a row compose`() = runTest {
        deckWith("$CARD=1")

        decks.adjustCardQuantity(DECK, CARD, +1)
        decks.adjustCardQuantity(DECK, CARD, +1)

        assertEquals(3, quantityOf(CARD))
    }

    private companion object {
        const val DECK = "local-under-test"
        const val CARD = "01023"
    }
}
