package com.hasyame.marvelchampions.domain.search

import com.hasyame.marvelchampions.domain.deckbuilder.IdentityTraits
import com.hasyame.marvelchampions.domain.model.CardFilter
import com.hasyame.marvelchampions.domain.model.CardLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardQueryBuilderTest {

    @Test
    fun `an empty filter prefers the locale and falls back to the other`() {
        val query = CardQueryBuilder.build(CardFilter(), CardLocale.FRENCH)

        assertTrue(query.sql.contains("cards.locale = ?"))
        // The fallback row is admitted only where no translated row exists, so
        // an untranslated card is findable without a translated one appearing
        // twice.
        assertTrue(query.sql.contains("NOT EXISTS"))
        // preferred locale, fallback locale, preferred again for the subquery,
        // then limit and offset.
        assertEquals(listOf<Any>("fr", "en", "fr", 200, 0), query.args)
    }

    @Test
    fun `no search text means no fts join`() {
        val query = CardQueryBuilder.build(CardFilter(), CardLocale.FRENCH)

        assertFalse(
            "an empty query must not pay for the FTS join",
            query.sql.contains("cards_fts"),
        )
    }

    @Test
    fun `search text joins fts with a normalised prefix match`() {
        val query = CardQueryBuilder.build(
            CardFilter(query = "Stratégie"),
            CardLocale.FRENCH,
        )

        assertTrue(query.sql.contains("cards_fts MATCH ?"))
        assertEquals("strategie*", query.args.first())
    }

    @Test
    fun `multiple packs become a single IN clause`() {
        val query = CardQueryBuilder.build(
            CardFilter(packCodes = setOf("core", "gmw")),
            CardLocale.ENGLISH,
        )

        assertTrue(query.sql.contains("cards.packCode IN (?,?)"))
        assertTrue(query.args.containsAll(listOf("core", "gmw")))
    }

    @Test
    fun `owned only restricts to the owned pack codes`() {
        val query = CardQueryBuilder.build(
            filter = CardFilter(ownedOnly = true),
            locale = CardLocale.FRENCH,
            ownedPackCodes = setOf("core", "magneto"),
        )

        assertTrue(query.sql.contains("cards.packCode IN (?,?)"))
        assertTrue(query.args.containsAll(listOf("core", "magneto")))
    }

    @Test
    fun `owned only with an empty collection yields no results rather than bad sql`() {
        val query = CardQueryBuilder.build(
            filter = CardFilter(ownedOnly = true),
            locale = CardLocale.FRENCH,
            ownedPackCodes = emptySet(),
        )

        // "IN ()" is a syntax error in SQLite, so this must be a false literal.
        assertFalse(query.sql.contains("IN ()"))
        assertTrue(query.sql.contains(" AND 0"))
    }

    @Test
    fun `cost bounds are inclusive on both ends`() {
        val query = CardQueryBuilder.build(
            CardFilter(minCost = 1, maxCost = 3),
            CardLocale.ENGLISH,
        )

        assertTrue(query.sql.contains("cards.cost >= ?"))
        assertTrue(query.sql.contains("cards.cost <= ?"))
        assertTrue(query.args.containsAll(listOf(1, 3)))
    }

    @Test
    fun `traits are matched against the normalised column`() {
        val query = CardQueryBuilder.build(
            CardFilter(traits = setOf("Héros")),
            CardLocale.FRENCH,
        )

        assertTrue(query.sql.contains("cards.searchTraits LIKE ?"))
        assertTrue(query.args.contains("%heros%"))
    }

    @Test
    fun `every placeholder has exactly one argument`() {
        val query = CardQueryBuilder.build(
            filter = CardFilter(
                query = "spider",
                packCodes = setOf("core", "gmw"),
                typeCodes = setOf("ally"),
                factionCodes = setOf("justice", "leadership"),
                traits = setOf("Avenger"),
                minCost = 1,
                maxCost = 4,
                ownedOnly = true,
            ),
            locale = CardLocale.FRENCH,
            ownedPackCodes = setOf("core"),
        )

        // A mismatch here is the classic cause of a runtime bind error, and it
        // is invisible until the query actually runs.
        assertEquals(query.sql.count { it == '?' }, query.args.size)
    }

    @Test
    fun `results are ordered deterministically`() {
        val query = CardQueryBuilder.build(CardFilter(), CardLocale.FRENCH)

        assertTrue(query.sql.contains("ORDER BY cards.packCode, cards.position"))
    }

    @Test
    fun `the synergy filter binds its keys in the order of its placeholders`() {
        // Magik: Mystic and X-Men on the hero side, Mutant and Mystic on the
        // alter ego. A card that needs Mutant is playable (the alter ego has
        // it); one that needs the *hero* to be Mutant is not.
        val magik = IdentityTraits(
            heroFaces = setOf("mystic", "x-men"),
            alterEgoFaces = setOf("mutant", "mystic"),
        )
        val query = CardQueryBuilder.build(CardFilter(synergyWith = magik), CardLocale.ENGLISH)

        assertTrue(query.sql.contains("cards.synergyTraits = ''"))
        assertTrue("underived rows stay visible", query.sql.contains("cards.synergyTraits IS NULL"))
        // locale x3, then: the hero prefix for NOT LIKE, every face's key, the
        // hero prefix for LIKE, the hero faces' keys, limit and offset.
        assertEquals(
            listOf<Any>(
                "en", "fr", "en",
                "hero:%", "%|mystic|%", "%|x-men|%", "%|mutant|%",
                "hero:%", "%|mystic|%", "%|x-men|%",
                200, 0,
            ),
            query.args,
        )
    }

    @Test
    fun `an identity without traits hides every conditional card`() {
        val query = CardQueryBuilder.build(CardFilter(synergyWith = IdentityTraits.NONE), CardLocale.ENGLISH)
        // "OR (... AND (0))" on both branches: only unconditional cards pass.
        assertTrue(query.sql.contains("AND (0))"))
        assertEquals(listOf<Any>("en", "fr", "en", "hero:%", "hero:%", 200, 0), query.args)
    }
}
