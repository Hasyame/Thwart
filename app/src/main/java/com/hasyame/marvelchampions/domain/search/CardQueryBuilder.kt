package com.hasyame.marvelchampions.domain.search

import com.hasyame.marvelchampions.domain.deckbuilder.IdentityTraits
import com.hasyame.marvelchampions.domain.deckbuilder.SynergyCondition
import com.hasyame.marvelchampions.domain.model.CardFilter
import com.hasyame.marvelchampions.domain.model.CardLocale

/** A SQL statement and its bind arguments, ready for Room's raw query support. */
data class CardQuery(
    val sql: String,
    val args: List<Any>,
)

/**
 * Builds the card list query.
 *
 * This is a raw query rather than a pile of `@Query` methods because the filters
 * combine freely: seven independent dimensions would otherwise mean either a
 * combinatorial explosion of methods or a single query with a dozen
 * `(:x IS NULL OR ...)` guards that SQLite cannot index well.
 *
 * It lives in `domain` and returns a value rather than executing anything, so
 * the filter logic is testable without a database.
 */
object CardQueryBuilder {

    fun build(
        filter: CardFilter,
        locale: CardLocale,
        ownedPackCodes: Set<String> = emptySet(),
        limit: Int = 200,
        offset: Int = 0,
    ): CardQuery {
        val args = mutableListOf<Any>()
        val where = mutableListOf<String>()

        val matchQuery = SearchNormalizer.toPrefixMatchQuery(filter.query)
        val from = if (matchQuery != null) {
            args += matchQuery
            "cards JOIN cards_fts ON cards_fts.rowid = cards.rowid AND cards_fts MATCH ?"
        } else {
            "cards"
        }

        // A card MarvelCDB has not translated is still a card the player owns.
        // Matching the locale exactly hid it from search entirely, which reads
        // as the database being incomplete rather than the translation being
        // missing. The fallback row is used only when no translated row exists,
        // so a card is never returned twice.
        where += """
            (
                cards.locale = ?
                OR (
                    cards.locale = ?
                    AND NOT EXISTS (
                        SELECT 1 FROM cards translated
                        WHERE translated.code = cards.code AND translated.locale = ?
                    )
                )
            )
        """.trimIndent()
        args += locale.code
        args += locale.fallback().code
        args += locale.code

        filter.packCodes.addInClause(where, args, "cards.packCode")
        filter.typeCodes.addInClause(where, args, "cards.typeCode")
        filter.factionCodes.addInClause(where, args, "cards.factionCode")

        if (filter.ownedOnly) {
            if (ownedPackCodes.isEmpty()) {
                // An empty collection with ownedOnly on means no results, which
                // is correct. Emitting "IN ()" would be a syntax error.
                where += "0"
            } else {
                ownedPackCodes.addInClause(where, args, "cards.packCode")
            }
        }

        if (filter.favouritesOnly) {
            // A subquery rather than a join: favourites are keyed by code and a
            // card has a row per language, so joining would multiply results.
            where += "cards.code IN (SELECT cardCode FROM favourite_cards)"
        }

        filter.minCost?.let {
            where += "cards.cost >= ?"
            args += it
        }
        filter.maxCost?.let {
            where += "cards.cost <= ?"
            args += it
        }

        // Traits are stored as the printed string ("Avenger. Gamma."), so each
        // selected trait is matched against the normalised copy.
        filter.traits.forEach { trait ->
            where += "cards.searchTraits LIKE ?"
            args += "%${SearchNormalizer.normalize(trait)}%"
        }

        filter.synergyWith?.let { identity ->
            where += synergyClause(identity, args)
        }

        args += limit
        args += offset

        val sql = buildString {
            append("SELECT cards.* FROM ")
            append(from)
            append(" WHERE ")
            append(where.joinToString(" AND "))
            // A constant per branch of the enum, never user input, which is
            // what makes interpolating an ORDER BY safe here.
            append(" ORDER BY ").append(filter.sort.orderBy)
            append(" LIMIT ? OFFSET ?")
        }
        return CardQuery(sql, args)
    }

    /**
     * Cards the identity can play: no condition, or one that a face of the
     * identity answers. A "hero only" condition is matched against the hero
     * faces alone. Each key is stored wrapped in bars, so the LIKE cannot
     * match part of a longer trait; a row not yet derived (null) is left in
     * rather than hidden, since hiding it would look like a missing card.
     */
    private fun synergyClause(identity: IdentityTraits, args: MutableList<Any>): String {
        fun anyOf(keys: Set<String>): String {
            if (keys.isEmpty()) {
                return "0"
            }
            keys.forEach { args += "%${SynergyCondition.SEPARATOR}$it${SynergyCondition.SEPARATOR}%" }
            return keys.joinToString(" OR ") { "cards.synergyTraits LIKE ?" }
        }
        // Bound in the order the placeholders appear below.
        val heroPrefix = "${SynergyCondition.HERO_PREFIX}%"
        args += heroPrefix
        val identityWide = anyOf(identity.allFaces)
        args += heroPrefix
        val heroOnly = anyOf(identity.heroFaces)
        return """
            (
                cards.synergyTraits IS NULL
                OR cards.synergyTraits = ''
                OR (cards.synergyTraits NOT LIKE ? AND ($identityWide))
                OR (cards.synergyTraits LIKE ? AND ($heroOnly))
            )
        """.trimIndent()
    }

    private fun Collection<String>.addInClause(
        where: MutableList<String>,
        args: MutableList<Any>,
        column: String,
    ) {
        if (isEmpty()) {
            return
        }
        where += "$column IN (${joinToString(",") { "?" }})"
        args.addAll(this)
    }
}
