package com.hasyame.marvelchampions.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.db.MarvelChampionsDatabase
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.db.entity.OwnedPackEntity
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbApi
import com.hasyame.marvelchampions.data.marvelcdb.dto.CardDto
import com.hasyame.marvelchampions.data.marvelcdb.dto.PackDto
import com.hasyame.marvelchampions.data.repository.DeckFolderRepository
import com.hasyame.marvelchampions.data.security.SecretStore
import com.hasyame.marvelchampions.data.seed.SetNameOverrides
import com.hasyame.marvelchampions.data.sync.AutoSync
import com.hasyame.marvelchampions.data.sync.SyncSessionStore
import com.hasyame.marvelchampions.domain.deckbuilder.DeckValidator
import com.hasyame.marvelchampions.domain.draft.DraftEngine
import com.hasyame.marvelchampions.domain.draft.DraftPhase
import com.hasyame.marvelchampions.domain.draft.DraftPlayer
import com.hasyame.marvelchampions.domain.draft.DraftSettings
import com.hasyame.marvelchampions.domain.draft.DraftState
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.search.SearchNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

/**
 * The draft against a real database: the shelf built from owned packs and
 * card rows, the session written and read back, and the decks saved at the
 * end through the ordinary deck path.
 */
@RunWith(RobolectricTestRunner::class)
class DraftRepositoryTest {

    private lateinit var database: MarvelChampionsDatabase
    private lateinit var decks: DeckRepository
    private lateinit var drafts: DraftRepository

    private class OfflineApi : MarvelCdbApi {
        override suspend fun getAllCards(encounter: Int): List<CardDto> = emptyList()
        override suspend fun getPackCards(packCode: String, encounter: Int): List<CardDto> = emptyList()
        override suspend fun getCard(code: String): CardDto = error("not used")
        override suspend fun getPacks(): List<PackDto> = emptyList()
        override suspend fun getAllCardsAt(url: String): List<CardDto> = emptyList()
        override suspend fun getPacksAt(url: String): List<PackDto> = emptyList()
        override suspend fun getDeckRaw(url: String): Response<ResponseBody> = error("not used")
    }

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MarvelChampionsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val json = Json { ignoreUnknownKeys = true }
        val sessions = SyncSessionStore(context, SecretStore())
        sessions.signOut()
        val autoSync = AutoSync(context, sessions)
        val collection = CollectionRepository(
            packDao = database.packDao(),
            ownedPackDao = database.ownedPackDao(),
            excludedModularSetDao = database.excludedModularSetDao(),
            excludedScenarioDao = database.excludedScenarioDao(),
            cardDao = database.cardDao(),
            syncStateDao = database.syncStateDao(),
            setNameOverrides = SetNameOverrides(context, json, Dispatchers.Unconfined),
            autoSync = autoSync,
        )
        decks = DeckRepository(
            api = OfflineApi(),
            savedDeckDao = database.savedDeckDao(),
            syncStateDao = database.syncStateDao(),
            cardDao = database.cardDao(),
            collectionRepository = collection,
            autoSync = autoSync,
            folders = DeckFolderRepository(database, autoSync, Dispatchers.Unconfined),
            json = json,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val builder = DeckBuilderRepository(database.cardDao(), collection, json, Dispatchers.Unconfined)
        drafts = DraftRepository(
            sessionDao = database.draftSessionDao(),
            cardDao = database.cardDao(),
            ownedPackDao = database.ownedPackDao(),
            packDao = database.packDao(),
            deckRepository = decks,
            builderRepository = builder,
            json = json,
            ioDispatcher = Dispatchers.Unconfined,
        )

        // Spider-Man's set: the identity, the alter ego, five signature cards
        // and an obligation, in the core set.
        val cards = mutableListOf(
            card("01001a", "Spider-Man", "hero", "hero", "core", set = "spider_man", traits = "Avenger."),
            card("01001b", "Peter Parker", "alter_ego", "hero", "core", set = "spider_man", traits = "Genius.").copy(linkedToCode = null),
            card("01006", "Spider-Man Obligation", "obligation", "encounter", "core", set = "spider_man"),
        )
        (1..5).forEach { cards += card("0100${it + 1}x", "Signature $it", "event", "hero", "core", set = "spider_man", quantity = 1) }
        // Justice and basic cards, three copies each, some reprinted in a second pack.
        (1..14).forEach { cards += card("j$it", "Justice $it", "event", "justice", "core", quantity = 3) }
        (1..6).forEach { cards += card("b$it", "Basic $it", "support", "basic", "core", quantity = 3) }
        cards += card("j1r", "Justice 1", "event", "justice", "reprint", quantity = 3).copy(duplicateOfCode = "j1")
        cards += card("rocket", "Rocket Raccoon", "ally", "basic", "core", quantity = 1)
            .copy(isUnique = true, deckLimit = 1, synergyTraits = "|guardian|")
        // A hero-faction card of somebody else's, never on the shelf.
        cards += card("99001", "Web-Shooter", "upgrade", "hero", "core", set = "somebody_else", quantity = 2)
        database.cardDao().insertAll(cards)
        database.cardDao().insertAll(cards.map { it.copy(locale = "fr", name = "FR " + it.name) })
        database.ownedPackDao().upsert(OwnedPackEntity(packCode = "core", quantity = 1))
        database.ownedPackDao().upsert(OwnedPackEntity(packCode = "reprint", quantity = 2))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `the shelf counts owned packs and reprints, and holds only player cards`() = runTest {
        val context = drafts.context(draft(), CardLocale.ENGLISH)

        assertEquals("three in core, three in each of two reprint packs", 9, context.initialStock["j1"])
        assertEquals(3, context.initialStock["b1"])
        assertNull("no reprint entry of its own", context.pool["j1r"])
        assertNull("another hero's card", context.pool["99001"])
        assertNull("the obligation", context.pool["01006"])
        assertNull("a signature card", context.pool["01002x"])
        assertEquals(5, context.rules.getValue("01001a").requiredCards.size)
        assertEquals(setOf("avenger"), context.identities.getValue("01001a").heroFaces)
        assertEquals("guardian", context.pool.getValue("rocket").condition!!.anyOfTraits.single())
    }

    @Test
    fun `the session is written down and read back whole`() = runTest {
        val state = draft().copy(phase = DraftPhase.PICK, pickCount = 3, offer = listOf("j1", "b2"))
        drafts.save(state)
        assertEquals(state, drafts.currentSession())
        assertEquals(state, drafts.observeSession().first())
        drafts.clear()
        assertNull(drafts.currentSession())
    }

    @Test
    fun `a finished draft becomes a legal, named, synced deck and the session is gone`() = runTest {
        var state = draft()
        val context = drafts.context(state, CardLocale.ENGLISH)
        state = DraftEngine.start(state.copy(stock = context.initialStock), context)
        while (state.phase != DraftPhase.FINISH) {
            state = DraftEngine.pick(state, state.offer.first(), context)
        }
        drafts.save(state)

        val outcome = drafts.finish(state, context, CardLocale.ENGLISH)

        val saved = outcome as DraftOutcome.Saved
        val deck = decks.getDeck(saved.deckIds.single())
        assertNotNull(deck)
        assertEquals("DRAFT-SPIDERMAN-JUSTICE-01", deck!!.name)
        assertEquals("01001a", deck.heroCode)
        assertEquals("justice", deck.aspects)
        val slots = DeckRepository.parseSlots(deck.slots)
        assertEquals(40, slots.values.sum())
        assertEquals("every signature card is in", 1, slots["01002x"])
        val validation = DeckValidator.validate(context.rules.getValue("01001a"), listOf("justice"), slots, context.cardInfo)
        assertTrue(validation.problems.toString(), validation.isLegal)
        assertTrue("marked for sync", database.syncStateDao().get("saved_decks", deck.id)!!.dirty)
        assertNull("the table is cleared", drafts.currentSession())
    }

    @Test
    fun `a second draft of the same identity gets the next number`() = runTest {
        decks.createLocalDeck("DRAFT-SPIDERMAN-JUSTICE-01", "01001a", "Spider-Man", listOf("justice"))
        var state = draft()
        val context = drafts.context(state, CardLocale.ENGLISH)
        state = DraftEngine.start(state.copy(stock = context.initialStock), context)
        while (state.phase != DraftPhase.FINISH) {
            state = DraftEngine.pick(state, state.offer.first(), context)
        }
        val saved = drafts.finish(state, context, CardLocale.ENGLISH) as DraftOutcome.Saved
        assertEquals("DRAFT-SPIDERMAN-JUSTICE-02", decks.getDeck(saved.deckIds.single())!!.name)
    }

    @Test
    fun `temporary collection persists without changing the saved shelf`() = runTest {
        val original = drafts.collection()
        val session = draft().copy(collection = original.mapValues { it.value * 2 }, sealedOpened = listOf(3))
        drafts.save(session)
        assertEquals(session, drafts.currentSession())
        assertEquals(original, drafts.collection())
        val normal = drafts.context(draft(), CardLocale.ENGLISH)
        val temporary = drafts.context(session, CardLocale.ENGLISH)
        normal.initialStock.forEach { (code, count) ->
            assertEquals(count * 2, temporary.initialStock[code])
        }
    }

    @Test
    fun `a deck the validator refuses is not saved, and said so`() = runTest {
        // A player stopped short: 39 cards is not a deck.
        var state = draft()
        val context = drafts.context(state, CardLocale.ENGLISH)
        state = DraftEngine.start(state.copy(stock = context.initialStock), context)
        repeat(34) { state = DraftEngine.pick(state, state.offer.first(), context) }
        state = DraftEngine.skipCurrent(state, context)
        assertEquals(DraftPhase.FINISH, state.phase)

        val outcome = drafts.finish(state, context, CardLocale.ENGLISH)

        assertTrue(outcome is DraftOutcome.Illegal)
        assertEquals("nothing written", 0, decks.getDecks().size)
    }

    @Test
    fun `the shelf is named in the card language, with English where there is none`() = runTest {
        val context = drafts.context(draft(), CardLocale.FRENCH)
        assertEquals("FR Justice 1", context.pool.getValue("j1").name)
    }

    private fun draft(): DraftState {
        val rules = mapOf("01001a" to (1..5).associate { "0100${it + 1}x" to 1 })
        return DraftState(
            settings = DraftSettings(players = 1),
            players = listOf(
                DraftPlayer(
                    index = 0,
                    heroCode = "01001a",
                    heroName = "Spider-Man",
                    heroSetCode = "spider_man",
                    aspects = listOf("justice"),
                    deckSize = 40,
                    signature = rules.getValue("01001a"),
                ),
            ),
            phase = DraftPhase.IDENTITY,
            seed = 11L,
        )
    }

    private fun card(
        code: String,
        name: String,
        type: String,
        faction: String,
        pack: String,
        set: String? = null,
        quantity: Int = 1,
        traits: String? = null,
    ) = CardEntity(
        code = code,
        locale = "en",
        name = name,
        realName = name,
        position = 1,
        quantity = quantity,
        packCode = pack,
        packName = pack,
        packLegacy = false,
        cardSetCode = set,
        typeCode = type,
        typeName = type,
        factionCode = faction,
        factionName = faction,
        traits = traits,
        realTraits = traits,
        deckLimit = 3,
        searchName = SearchNormalizer.normalize(name),
        searchText = "",
        searchTraits = SearchNormalizer.normalize(traits),
        synergyTraits = "",
    )
}
