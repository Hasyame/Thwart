package com.hasyame.marvelchampions.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.domain.search.SearchNormalizer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CardDaoTest {

    private lateinit var database: MarvelChampionsDatabase
    private lateinit var dao: CardDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MarvelChampionsDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.cardDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `finds an accented french card from an unaccented query`() = runTest {
        dao.insertAll(listOf(card(code = "01064", locale = "fr", name = "Équipe de Surveillance")))

        val results = dao.search(
            matchQuery = requireNotNull(SearchNormalizer.toPrefixMatchQuery("equipe")),
            locale = "fr",
        )

        assertEquals(listOf("01064"), results.map { it.code })
    }

    @Test
    fun `finds a card by a word from its text, not only its name`() = runTest {
        dao.insertAll(
            listOf(
                card(
                    code = "01021",
                    locale = "fr",
                    name = "Frappe Gamma",
                    text = "<b>Action de héros</b> : infligez X dégâts à un ennemi.",
                ),
            ),
        )

        val results = dao.search(
            matchQuery = requireNotNull(SearchNormalizer.toPrefixMatchQuery("degats")),
            locale = "fr",
        )

        assertEquals(listOf("01021"), results.map { it.code })
    }

    @Test
    fun `finds a hyphenated name by one of its parts`() = runTest {
        dao.insertAll(listOf(card(code = "01001a", locale = "en", name = "Spider-Man")))

        val results = dao.search(
            matchQuery = requireNotNull(SearchNormalizer.toPrefixMatchQuery("spider")),
            locale = "en",
        )

        assertEquals(listOf("01001a"), results.map { it.code })
    }

    @Test
    fun `search does not leak across locales`() = runTest {
        dao.insertAll(
            listOf(
                card(code = "01021", locale = "en", name = "Gamma Slam"),
                card(code = "01021", locale = "fr", name = "Frappe Gamma"),
            ),
        )

        val english = dao.search(
            requireNotNull(SearchNormalizer.toPrefixMatchQuery("gamma")),
            locale = "en",
        )
        assertEquals(1, english.size)
        assertEquals("Gamma Slam", english.single().name)
    }

    @Test
    fun `the same code exists once per locale`() = runTest {
        dao.insertAll(
            listOf(
                card(code = "01021", locale = "en", name = "Gamma Slam"),
                card(code = "01021", locale = "fr", name = "Frappe Gamma"),
            ),
        )

        assertEquals(1, dao.countForLocale("en"))
        assertEquals(1, dao.countForLocale("fr"))
        assertEquals("Frappe Gamma", dao.getCard("01021", "fr")?.name)
        assertEquals("Gamma Slam", dao.getCard("01021", "en")?.name)
    }

    @Test
    fun `replacing one locale leaves the other untouched`() = runTest {
        dao.insertAll(
            listOf(
                card(code = "01021", locale = "en", name = "Gamma Slam"),
                card(code = "01021", locale = "fr", name = "Frappe Gamma"),
            ),
        )

        dao.replaceLocale("fr", listOf(card(code = "01099", locale = "fr", name = "Nouvelle Carte")))

        assertEquals(1, dao.countForLocale("en"))
        assertNotNull(dao.getCard("01021", "en"))
        assertNull(dao.getCard("01021", "fr"))
        assertNotNull(dao.getCard("01099", "fr"))
    }

    @Test
    fun `replacing a locale keeps the fts index in step`() = runTest {
        dao.replaceLocale("fr", listOf(card(code = "01064", locale = "fr", name = "Équipe de Surveillance")))
        dao.replaceLocale("fr", listOf(card(code = "01021", locale = "fr", name = "Frappe Gamma")))

        // The removed card must not linger in the external-content FTS table.
        val stale = dao.search(
            requireNotNull(SearchNormalizer.toPrefixMatchQuery("equipe")),
            locale = "fr",
        )
        assertTrue("stale rows found: ${stale.map { it.code }}", stale.isEmpty())

        val current = dao.search(
            requireNotNull(SearchNormalizer.toPrefixMatchQuery("frappe")),
            locale = "fr",
        )
        assertEquals(listOf("01021"), current.map { it.code })
    }

    @Test
    fun `chunked insert handles more rows than the sqlite variable limit`() = runTest {
        val many = (0 until 1500).map { card(code = "c$it", locale = "en", name = "Card $it") }

        dao.replaceLocale("en", many)

        assertEquals(1500, dao.countForLocale("en"))
    }

    @Test
    fun `a card missing a translation still resolves in the asked-for locale`() = runTest {
        // MarvelCDB has not translated every pack. An exact-locale lookup
        // returns nothing for those, which made untranslated cards vanish from
        // decks and campaign lists instead of merely showing in English.
        dao.insertAll(
            listOf(
                card(code = "01064", locale = "en", name = "Surveillance Team"),
                card(code = "01064", locale = "fr", name = "Équipe de Surveillance"),
                card(code = "45001", locale = "en", name = "Untranslated Ally"),
            ),
        )

        assertEquals("Équipe de Surveillance", dao.getCardPreferringLocale("01064", "fr")?.name)
        // Falls back rather than returning nothing at all.
        assertEquals("Untranslated Ally", dao.getCardPreferringLocale("45001", "fr")?.name)
        assertNull(dao.getCard("45001", "fr"))
        assertNull(dao.getCardPreferringLocale("99999", "fr"))
    }

    @Test
    fun `a villain set is only a scenario when it brings a main scheme`() = runTest {
        // The four Wrecking Crew villains are villain sets with no main scheme
        // of their own — they are played inside the Wrecking Crew scenario —
        // and the draw used to offer "Bulldozer" as though you could play it.
        dao.insertAll(
            listOf(
                villainCard("v1", set = "crossbones", type = "villain"),
                villainCard("v2", set = "crossbones", type = "main_scheme"),
                villainCard("v3", set = "bulldozer", type = "villain"),
            ),
        )

        val playable = dao.getPlayableScenarios("en").map { it.code }

        assertEquals(listOf("crossbones"), playable)
    }

    /** A card in a villain set, which is what a scenario is made of. */
    private fun villainCard(code: String, set: String, type: String) =
        card(code = code, locale = "en", name = code).copy(
            typeCode = type,
            typeName = type,
            cardSetCode = set,
            cardSetName = set,
            cardSetTypeNameCode = "villain",
        )

    @Test
    fun `search invalidates when card content changes without a count change`() = runTest {
        dao.insertAll(listOf(card("audit-1", "en", "Before")))
        dao.observeSearchChanges(androidx.sqlite.db.SimpleSQLiteQuery("SELECT 1")).test {
            awaitItem()
            dao.insertAll(listOf(card("audit-1", "en", "After")))
            awaitItem()
            assertEquals(1, dao.countForLocale("en"))
            assertEquals("After", dao.getCard("audit-1", "en")?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search invalidates when a favourite changes`() = runTest {
        dao.observeSearchChanges(androidx.sqlite.db.SimpleSQLiteQuery("SELECT 1")).test {
            awaitItem()
            database.favouriteDao().add(com.hasyame.marvelchampions.data.db.entity.FavouriteCardEntity("audit-1", addedAt = 0))
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a larger search page reaches cards beyond the initial two hundred`() = runTest {
        dao.insertAll((1..205).map { card("audit-$it", "en", "Synthetic $it") })
        val query = com.hasyame.marvelchampions.domain.search.CardQueryBuilder.build(
            filter = com.hasyame.marvelchampions.domain.model.CardFilter(),
            locale = com.hasyame.marvelchampions.domain.model.CardLocale.ENGLISH,
            ownedPackCodes = emptySet(), limit = 401, offset = 0,
        )
        assertEquals(205, dao.queryCards(androidx.sqlite.db.SimpleSQLiteQuery(query.sql, query.args.toTypedArray())).size)
    }

    private fun card(
        code: String,
        locale: String,
        name: String,
        text: String? = null,
        traits: String? = null,
    ) = CardEntity(
        code = code,
        locale = locale,
        name = name,
        realName = name,
        position = 1,
        quantity = 1,
        packCode = "core",
        packName = "Core Set",
        packLegacy = false,
        typeCode = "ally",
        typeName = "Ally",
        factionCode = "hero",
        factionName = "Hero",
        text = text,
        traits = traits,
        searchName = SearchNormalizer.normalize(name),
        searchText = SearchNormalizer.normalize(text),
        searchTraits = SearchNormalizer.normalize(traits),
    )
}
