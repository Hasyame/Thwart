package com.hasyame.marvelchampions.domain.play

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which villain stages each difficulty plays.
 *
 * The shapes here are the ones actually printed, taken from the card database
 * rather than invented: Rhino I/II/III, the Wrecking Crew's A/B sides, Kang's
 * I and III, Apocalypse's four, and Tower Defense fielding two villains at once.
 */
class VillainStagesTest {

    private data class Card(val villain: String, val stage: String?)

    private fun select(cards: List<Card>, expert: Boolean) =
        VillainStages.select(cards, expert, { it.villain }, { it.stage })
            .map { "${it.villain} ${it.stage}" }

    private val rhino = listOf(
        Card("Rhino", "I"),
        Card("Rhino", "II"),
        Card("Rhino", "III"),
    )

    @Test
    fun `standard plays the first two stages`() {
        assertEquals(listOf("Rhino I", "Rhino II"), select(rhino, expert = false))
    }

    @Test
    fun `expert plays the last two stages`() {
        assertEquals(listOf("Rhino II", "Rhino III"), select(rhino, expert = true))
    }

    @Test
    fun `each villain is counted on its own`() {
        // Tower Defense fields Corvus Glaive and Proxima Midnight, each printed
        // I, II and III. Treating the six as one list would take the wrong two.
        val towerDefense = listOf(
            Card("Corvus Glaive", "I"),
            Card("Corvus Glaive", "II"),
            Card("Corvus Glaive", "III"),
            Card("Proxima Midnight", "I"),
            Card("Proxima Midnight", "II"),
            Card("Proxima Midnight", "III"),
        )
        assertEquals(
            listOf(
                "Corvus Glaive II", "Corvus Glaive III",
                "Proxima Midnight II", "Proxima Midnight III",
            ),
            select(towerDefense, expert = true),
        )
    }

    @Test
    fun `a villain's two sides are not difficulty stages`() {
        // The Wrecking Crew four are printed A and B, which are the sides of one
        // card. Dropping one would take half the villain off the table.
        val wrecker = listOf(Card("Wrecker", "A"), Card("Wrecker", "B"))
        assertEquals(listOf("Wrecker A", "Wrecker B"), select(wrecker, expert = false))
        assertEquals(listOf("Wrecker A", "Wrecker B"), select(wrecker, expert = true))
    }

    @Test
    fun `Kang is left alone, because the encounter set carries the difficulty`() {
        // Kang is printed I and III, and which you play is decided by taking the
        // Kang set or the Expert Kang set, not by dropping a stage here.
        val kang = listOf(
            Card("Kang (The Conqueror)", "I"),
            Card("Kang (The Conqueror)", "III"),
        )
        assertEquals(2, select(kang, expert = false).size)
        assertEquals(2, select(kang, expert = true).size)
    }

    @Test
    fun `four stages are left alone`() {
        // Apocalypse is printed I to IV and the campaign says which to start on.
        val apocalypse = listOf(
            Card("Apocalypse", "I"),
            Card("Apocalypse", "II"),
            Card("Apocalypse", "III"),
            Card("Apocalypse", "IV"),
        )
        assertEquals(4, select(apocalypse, expert = false).size)
        assertEquals(4, select(apocalypse, expert = true).size)
    }

    @Test
    fun `a single stage survives either difficulty`() {
        val single = listOf(Card("Zola", "II"))
        assertEquals(listOf("Zola II"), select(single, expert = false))
        assertEquals(listOf("Zola II"), select(single, expert = true))
    }

    @Test
    fun `a missing stage label does not remove the card`() {
        val unlabelled = listOf(Card("Someone", null))
        assertEquals(1, select(unlabelled, expert = true).size)
    }
}
