package com.hasyame.marvelchampions.domain.draft

/** One printing of a player card, in one pack, as the shelf counts it. */
data class StockRow(
    val code: String,
    /** The original printing this one repeats, or null when it is the original. */
    val duplicateOfCode: String?,
    val packCode: String,
    /** Copies printed in that pack. */
    val printedQuantity: Int,
    val card: DraftCard,
)

/** What is on the shelf before anyone draws: the cards, and how many of each. */
data class DraftStock(
    val pool: Map<String, DraftCard>,
    val stock: Map<String, Int>,
)

/**
 * Counts the collection as physical cards.
 *
 * A reprint is the same card, so every printing of a title is folded onto
 * the original's code and their copies add up: three Swarm Tactics in Wasp's
 * pack and three in Ant-Man's are six on the shelf. A pack owned twice counts
 * twice. A card in no owned pack is not on the shelf at all.
 */
object DraftStockBuilder {

    fun build(rows: List<StockRow>, ownedPacks: Map<String, Int>): DraftStock {
        val stock = mutableMapOf<String, Int>()
        val pool = mutableMapOf<String, DraftCard>()
        rows.forEach { row ->
            val owned = ownedPacks[row.packCode] ?: 0
            if (owned <= 0) {
                return@forEach
            }
            val canonical = row.duplicateOfCode ?: row.code
            stock[canonical] = (stock[canonical] ?: 0) + owned * row.printedQuantity
            // The original printing names the card; a reprint only stands in
            // for it when the original is in no owned pack.
            if (row.duplicateOfCode == null || canonical !in pool) {
                pool[canonical] = row.card.copy(canonicalCode = canonical, info = row.card.info.copy(code = canonical))
            }
        }
        return DraftStock(pool = pool, stock = stock)
    }
}
