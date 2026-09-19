package com.hasyame.marvelchampions.data.achievements

import com.hasyame.marvelchampions.data.db.dao.HeroCardRef
import com.hasyame.marvelchampions.domain.achievements.PlayFact

/** Reads Android set-based hero IDs into the card-based achievements catalogue. */
class HeroAliases(cards: List<HeroCardRef>) {
    private val cardCodes = cards.mapTo(hashSetOf()) { it.code }
    private val aliases = cards.filter { !it.cardSetCode.isNullOrBlank() }
        .groupBy { it.cardSetCode!! }
        .mapNotNull { (set, printings) ->
            printings.map { it.code }.distinct().singleOrNull()?.let { set to it }
        }.toMap()

    /** Never guess between multiple identities or rewrite the persisted game record. */
    fun resolve(fact: PlayFact): PlayFact = fact.copy(seats = fact.seats.map { seat ->
        val code = seat.heroCode
        seat.copy(heroCode = if (code in cardCodes) code else aliases[code] ?: code)
    })
}
