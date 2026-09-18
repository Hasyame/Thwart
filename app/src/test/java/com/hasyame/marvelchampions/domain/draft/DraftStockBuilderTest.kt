package com.hasyame.marvelchampions.domain.draft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DraftStockBuilderTest {

    private fun row(code: String, pack: String, printed: Int, duplicateOf: String? = null, name: String = code) =
        StockRow(code, duplicateOf, pack, printed, DraftFixtures.card(code, "justice", name = name))

    @Test
    fun `copies are counted per owned pack, reprints folded onto the original`() {
        val rows = listOf(
            row("12020", "ant", 3, name = "Swarm Tactics"),
            row("13020", "wsp", 3, duplicateOf = "12020", name = "Swarm Tactics"),
            row("01001", "core", 2),
            row("99001", "unowned", 3),
        )
        val stock = DraftStockBuilder.build(rows, mapOf("ant" to 1, "wsp" to 1, "core" to 2))

        assertEquals(6, stock.stock["12020"])
        assertEquals("the reprint has no entry of its own", null, stock.stock["13020"])
        assertEquals("a pack owned twice", 4, stock.stock["01001"])
        assertFalse("99001" in stock.stock)
        assertEquals("Swarm Tactics", stock.pool.getValue("12020").name)
        assertEquals("12020", stock.pool.getValue("12020").info.code)
    }

    @Test
    fun `a title owned only as a reprint is filed under the reprint, which the player has`() {
        val rows = listOf(
            row("12020", "ant", 3, name = "Swarm Tactics (Ant-Man)"),
            row("13020", "wsp", 3, duplicateOf = "12020", name = "Swarm Tactics (Wasp)"),
            row("16099", "gmw", 1, duplicateOf = "12020", name = "Swarm Tactics (Galaxy)"),
        )
        val stock = DraftStockBuilder.build(rows, mapOf("wsp" to 1, "gmw" to 1))
        // The original is not owned, so the deck must not name it: the deck
        // page would call the card missing from the very collection it was
        // drafted from. The lowest owned reprint stands for the title.
        assertFalse("12020" in stock.stock)
        assertEquals(4, stock.stock["13020"])
        assertEquals("13020", stock.pool.getValue("13020").canonicalCode)
        assertEquals("13020", stock.pool.getValue("13020").info.code)
        assertEquals("Swarm Tactics (Wasp)", stock.pool.getValue("13020").name)
        assertFalse("16099" in stock.pool)
    }
}
