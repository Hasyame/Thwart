package com.hasyame.marvelchampions.domain.deckbuilder

import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.deckbuilder.DeckStatisticsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckStatisticsTest {

    private fun card(
        code: String,
        cost: Int? = null,
        type: String = "Ally",
        aspect: String = "Leadership",
        physical: Int? = null,
        mental: Int? = null,
        energy: Int? = null,
        wild: Int? = null,
        costStar: Boolean = false,
        costPerHero: Boolean = false,
    ) = CardEntity(
        code = code,
        locale = "en",
        name = "Card $code",
        realName = "Card $code",
        position = 1,
        quantity = 1,
        packCode = "core",
        packName = "Core Set",
        packLegacy = false,
        typeCode = type.lowercase(),
        typeName = type,
        factionCode = aspect.lowercase(),
        factionName = aspect,
        cost = cost,
        costStar = costStar,
        costPerHero = costPerHero,
        resourcePhysical = physical,
        resourceMental = mental,
        resourceEnergy = energy,
        resourceWild = wild,
        searchName = "card $code",
        searchText = "",
        searchTraits = "",
    )

    @Test
    fun `an empty deck produces empty statistics rather than dividing by zero`() {
        val stats = DeckStatisticsCalculator.calculate(emptyList())

        assertFalse(stats.hasCostData)
        assertEquals(0.0, stats.averageCost, 0.0)
        assertEquals(0, stats.tallestCostColumn)
        assertTrue(stats.byType.isEmpty())
    }

    @Test
    fun `the curve counts copies, not distinct cards`() {
        // The question a curve answers is "how often will I draw something
        // cheap", which is about copies. Counting rows would flatter every
        // deck equally and answer nothing.
        val stats = DeckStatisticsCalculator.calculate(
            listOf(
                card("a", cost = 1) to 3,
                card("b", cost = 3) to 2,
                card("c", cost = 3) to 1,
            ),
        )

        assertEquals(mapOf(1 to 3, 3 to 3), stats.costCurve)
        assertEquals(6, stats.costedCards)
        assertEquals(3, stats.tallestCostColumn)
        // (1×3 + 3×3) / 6
        assertEquals(2.0, stats.averageCost, 0.001)
    }

    @Test
    fun `cards with a variable cost stay out of the curve`() {
        // "X" and "per hero" costs have no single number. Counting whatever
        // placeholder the database holds would put a phantom column on the
        // chart and drag the average with it.
        val stats = DeckStatisticsCalculator.calculate(
            listOf(
                card("fixed", cost = 2) to 2,
                card("variable", cost = 0, costStar = true) to 3,
                card("perHero", cost = 1, costPerHero = true) to 1,
            ),
        )

        assertEquals(mapOf(2 to 2), stats.costCurve)
        assertEquals(2, stats.costedCards)
        assertEquals(2.0, stats.averageCost, 0.001)
    }

    @Test
    fun `resource icons are totalled across copies`() {
        val stats = DeckStatisticsCalculator.calculate(
            listOf(
                card("a", cost = 1, energy = 1) to 3,
                card("b", cost = 2, physical = 1, wild = 1) to 2,
                card("c", cost = 0) to 2,
            ),
        )

        assertEquals(3, stats.resources.energy)
        assertEquals(2, stats.resources.physical)
        assertEquals(2, stats.resources.wild)
        assertEquals(0, stats.resources.mental)
        assertEquals(7, stats.resources.total)
    }

    @Test
    fun `types and aspects are ordered by how many copies there are`() {
        val stats = DeckStatisticsCalculator.calculate(
            listOf(
                card("a", cost = 1, type = "Event", aspect = "Justice") to 3,
                card("b", cost = 1, type = "Ally", aspect = "Justice") to 1,
                card("c", cost = 1, type = "Upgrade", aspect = "Basic") to 5,
            ),
        )

        assertEquals(
            listOf("Upgrade" to 5, "Event" to 3, "Ally" to 1),
            stats.byType,
        )
        assertEquals(listOf("Basic" to 5, "Justice" to 4), stats.byAspect)
    }

    @Test
    fun `a deck of only variable-cost cards reports no cost data`() {
        // Nothing to average, and the chart must not be drawn at all rather
        // than drawn empty.
        val stats = DeckStatisticsCalculator.calculate(
            listOf(card("x", cost = 0, costStar = true) to 4),
        )

        assertFalse(stats.hasCostData)
        assertEquals(0.0, stats.averageCost, 0.0)
        assertEquals(4, stats.byType.single().second)
    }
}
