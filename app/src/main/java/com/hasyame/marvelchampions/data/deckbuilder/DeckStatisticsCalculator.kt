package com.hasyame.marvelchampions.data.deckbuilder

import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.domain.deckbuilder.DeckStatistics
import com.hasyame.marvelchampions.domain.deckbuilder.ResourceCounts

object DeckStatisticsCalculator {

    /**
     * Counts a deck.
     *
     * The hero card is not passed in and must not be: it is not part of the
     * deck, has no cost, and would distort both the curve and the aspect split.
     */
    fun calculate(cards: List<Pair<CardEntity, Int>>): DeckStatistics {
        if (cards.isEmpty()) {
            return DeckStatistics()
        }

        val costCurve = sortedMapOf<Int, Int>()
        var physical = 0
        var mental = 0
        var energy = 0
        var wild = 0
        var costedCards = 0
        var costTotal = 0

        val typeCounts = mutableMapOf<String, Int>()
        val aspectCounts = mutableMapOf<String, Int>()

        for ((card, quantity) in cards) {
            // A card printed with a variable cost — "X" or "per hero" — has no
            // single number, so it is left out of the curve rather than
            // counted as whatever placeholder the database happens to hold.
            val cost = card.cost?.takeIf { !card.costStar && !card.costPerHero }
            if (cost != null) {
                costCurve[cost] = (costCurve[cost] ?: 0) + quantity
                costedCards += quantity
                costTotal += cost * quantity
            }

            physical += (card.resourcePhysical ?: 0) * quantity
            mental += (card.resourceMental ?: 0) * quantity
            energy += (card.resourceEnergy ?: 0) * quantity
            wild += (card.resourceWild ?: 0) * quantity

            typeCounts[card.typeName] = (typeCounts[card.typeName] ?: 0) + quantity
            aspectCounts[card.factionName] = (aspectCounts[card.factionName] ?: 0) + quantity
        }

        return DeckStatistics(
            costCurve = costCurve,
            resources = ResourceCounts(physical, mental, energy, wild),
            byType = typeCounts.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key })
                .map { it.key to it.value },
            byAspect = aspectCounts.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key })
                .map { it.key to it.value },
            costedCards = costedCards,
            averageCost = if (costedCards > 0) costTotal.toDouble() / costedCards else 0.0,
        )
    }
}
