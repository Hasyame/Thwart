package com.hasyame.marvelchampions.data.db

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.domain.deckbuilder.IdentityTraits
import com.hasyame.marvelchampions.domain.model.CardFilter
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.search.CardQueryBuilder
import com.hasyame.marvelchampions.domain.search.SearchNormalizer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs the statements [CardQueryBuilder] produces against a real database.
 *
 * The builder's own tests check the SQL text; only executing it proves the
 * syntax is valid and the bindings line up.
 */
@RunWith(RobolectricTestRunner::class)
class CardQueryExecutionTest {

    private lateinit var database: MarvelChampionsDatabase
    private lateinit var dao: CardDao

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MarvelChampionsDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.cardDao()
        dao.insertAll(
            listOf(
                card("01001a", "Spider-Man", "hero", "hero", "core", cost = null, traits = "Avenger."),
                card("01021", "Frappe Gamma", "event", "aggression", "core", cost = 3, traits = "Attaque."),
                card("14001", "Groot", "hero", "hero", "gmw", cost = null, traits = "Gardien."),
                card("14020", "Coup de Racine", "event", "justice", "gmw", cost = 1, traits = "Attaque."),
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

@Test
    fun `an untranslated card appears in French search rather than vanishing`() = runTest {
        // MarvelCDB has not translated every pack, and matching the locale
        // exactly hid those cards from search altogether — which reads as a
        // broken database rather than a missing translation.
        dao.insertAll(
            listOf(
                card("45001", "Untranslated Ally", "ally", "leadership", "fne", 2, "Avenger."),
            ),
        )

        assertEquals(5, run(CardFilter()).size)
        assertEquals(
            listOf("45001"),
            run(CardFilter(query = "untranslated")).map { it.code },
        )
    }

    @Test
    fun `a translated card is returned once, not once per language`() = runTest {
        // The fallback row is used only when no translated row exists. If that
        // ever stops holding, every card in the database doubles.
        dao.insertAll(
            listOf(
                englishCard("01021", "Gamma Slam", "event", "aggression", "core"),
            ),
        )

        val results = run(CardFilter())
        assertEquals(4, results.size)
        assertEquals(
            "the French row wins for a card that has one",
            "Frappe Gamma",
            results.first { it.code == "01021" }.name,
        )
    }

    private suspend fun run(
        filter: CardFilter,
        owned: Set<String> = emptySet(),
    ): List<CardEntity> {
        val query = CardQueryBuilder.build(filter, CardLocale.FRENCH, owned)
        return dao.queryCards(SimpleSQLiteQuery(query.sql, query.args.toTypedArray()))
    }

    @Test
    fun `an empty filter returns everything in the locale`() = runTest {
        assertEquals(4, run(CardFilter()).size)
    }

    @Test
    fun `text search and a pack filter combine`() = runTest {
        val results = run(CardFilter(query = "coup", packCodes = setOf("gmw")))

        assertEquals(listOf("14020"), results.map { it.code })
    }

    @Test
    fun `owned only hides packs the user does not have`() = runTest {
        val results = run(CardFilter(ownedOnly = true), owned = setOf("core"))

        assertEquals(setOf("core"), results.map { it.packCode }.toSet())
    }

    @Test
    fun `owned only with an empty collection returns nothing and does not crash`() = runTest {
        assertTrue(run(CardFilter(ownedOnly = true), owned = emptySet()).isEmpty())
    }

    @Test
    fun `cost bounds filter on the printed cost`() = runTest {
        val results = run(CardFilter(minCost = 1, maxCost = 1))

        assertEquals(listOf("14020"), results.map { it.code })
    }

    @Test
    fun `a trait filter matches accent-insensitively`() = runTest {
        val results = run(CardFilter(traits = setOf("Gardien")))

        assertEquals(listOf("14001"), results.map { it.code })
    }

    @Test
    fun `every filter dimension at once produces valid sql`() = runTest {
        // The point of this one is that it runs at all.
        val results = run(
            CardFilter(
                query = "frappe",
                packCodes = setOf("core"),
                typeCodes = setOf("event"),
                factionCodes = setOf("aggression"),
                traits = setOf("Attaque"),
                minCost = 1,
                maxCost = 5,
                ownedOnly = true,
            ),
            owned = setOf("core"),
        )

        assertEquals(listOf("01021"), results.map { it.code })
    }

    @Test
    fun `the synergy filter keeps what the identity can play and what has never been derived`() = runTest {
        dao.insertAll(
            listOf(
                card("16019", "Rocket Raccoon", "ally", "basic", "gmw", cost = 3, traits = "Gardien.")
                    .copy(synergyTraits = "|guardian|"),
                card("42011", "Elixir", "ally", "protection", "aoa", cost = 3, traits = "X-Men.")
                    .copy(synergyTraits = "|x-force|x-men|"),
                card("41030", "Psi-Bow Attack", "event", "aggression", "aoa", cost = 2, traits = "Attaque.")
                    .copy(synergyTraits = "hero:|psionic|"),
                card("01092", "Helicarrier", "support", "basic", "core", cost = 3, traits = null)
                    .copy(synergyTraits = ""),
                card("99999", "Underived", "support", "basic", "core", cost = 1, traits = null)
                    .copy(synergyTraits = null),
            ),
        )
        // Magik: Mystic and X-Men as a hero, Mutant and Mystic as Illyana.
        val magik = IdentityTraits(heroFaces = setOf("mystic", "x-men"), alterEgoFaces = setOf("mutant", "mystic"))
        val results = run(CardFilter(synergyWith = magik))

        val codes = results.map { it.code }
        assertTrue("Elixir needs X-Men, which Magik has", "42011" in codes)
        assertTrue("no condition at all", "01092" in codes)
        assertTrue("never derived stays visible", "99999" in codes)
        assertTrue("cards without a condition of their own", "01021" in codes)
        assertTrue("Rocket Raccoon needs a Guardian", "16019" !in codes)
        assertTrue("Psi-Bow needs the hero to be Psionic", "41030" !in codes)

        // An identity whose *alter ego* alone is Psionic still cannot play it.
        val alterEgoOnly = IdentityTraits(heroFaces = setOf("x-men"), alterEgoFaces = setOf("psionic"))
        assertTrue("41030" !in run(CardFilter(synergyWith = alterEgoOnly)).map { it.code })
        val psylocke = IdentityTraits(heroFaces = setOf("psionic", "x-men"), alterEgoFaces = setOf("psionic"))
        assertTrue("41030" in run(CardFilter(synergyWith = psylocke)).map { it.code })
    }

    @Test
    fun `the start-up pass finds only rows written before the column existed`() = runTest {
        dao.insertAll(
            listOf(
                card("16019", "Rocket Raccoon", "ally", "basic", "gmw", cost = 3, traits = null)
                    .copy(realText = "Play only if your identity has the [[guardian]] trait.", synergyTraits = null),
                card("01092", "Helicarrier", "support", "basic", "core", cost = 3, traits = null)
                    .copy(synergyTraits = ""),
            ),
        )
        // The four cards of setUp were written with the default, null, too.
        val pending = dao.getUnderivedSynergy()
        assertEquals(5, pending.size)
        pending.forEach { dao.setSynergy(it.code, it.locale, deriveSynergy(it.realText, it.text)) }

        assertEquals("nothing left for the next launch", 0, dao.getUnderivedSynergy().size)
        assertEquals("|guardian|", dao.getCard("16019", "fr")!!.synergyTraits)
        assertEquals("", dao.getCard("01021", "fr")!!.synergyTraits)
    }

private fun englishCard(
        code: String,
        name: String,
        typeCode: String,
        factionCode: String,
        packCode: String,
    ) = card(code, name, typeCode, factionCode, packCode, cost = null, traits = null)
        .copy(locale = "en")

    private fun card(
        code: String,
        name: String,
        typeCode: String,
        factionCode: String,
        packCode: String,
        cost: Int?,
        traits: String?,
    ) = CardEntity(
        code = code,
        locale = "fr",
        name = name,
        realName = name,
        position = 1,
        quantity = 1,
        packCode = packCode,
        packName = packCode,
        packLegacy = false,
        typeCode = typeCode,
        typeName = typeCode,
        factionCode = factionCode,
        factionName = factionCode,
        cost = cost,
        traits = traits,
        searchName = SearchNormalizer.normalize(name),
        searchText = "",
        searchTraits = SearchNormalizer.normalize(traits),
    )
}
