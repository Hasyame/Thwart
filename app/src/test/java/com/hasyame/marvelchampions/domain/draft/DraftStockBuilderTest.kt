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
    fun `a reprint stands in for an original that is not owned`() {
        val rows = listOf(
            row("12020", "ant", 3, name = "Swarm Tactics (Ant-Man)"),
            row("13020", "wsp", 3, duplicateOf = "12020", name = "Swarm Tactics (Wasp)"),
        )
        val stock = DraftStockBuilder.build(rows, mapOf("wsp" to 1))
        assertEquals(3, stock.stock["12020"])
        assertEquals("12020", stock.pool.getValue("12020").canonicalCode)
        assertEquals("12020", stock.pool.getValue("12020").info.code)
    }
}
