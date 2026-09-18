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
 * one code and their copies add up: three Swarm Tactics in Wasp's pack and
 * three in Ant-Man's are six on the shelf. A pack owned twice counts twice.
 * A card in no owned pack is not on the shelf at all.
 *
 * The code a title is filed under is a printing the player **owns**: the
 * original's when its pack is owned, and otherwise the lowest-numbered
 * owned reprint's. Filing a title under an original the player does not
 * have, as this once did, put Ant-Man's Swarm Tactics into a deck built
 * from Wasp's pack alone, and the deck page then called the card missing
 * from a collection it was drafted from.
 */
object DraftStockBuilder {

    fun build(rows: List<StockRow>, ownedPacks: Map<String, Int>): DraftStock {
        val stock = mutableMapOf<String, Int>()
        val pool = mutableMapOf<String, DraftCard>()
        rows.groupBy { it.duplicateOfCode ?: it.code }.forEach { (original, family) ->
            val owned = family.filter { (ownedPacks[it.packCode] ?: 0) > 0 }
            if (owned.isEmpty()) {
                return@forEach
            }
            val representative = owned.firstOrNull { it.code == original } ?: owned.minBy { it.code }
            val code = representative.code
            stock[code] = owned.sumOf { (ownedPacks.getValue(it.packCode)) * it.printedQuantity }
            pool[code] = representative.card.copy(canonicalCode = code, info = representative.card.info.copy(code = code))
        }
        return DraftStock(pool = pool, stock = stock)
    }
}
