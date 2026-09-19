package com.hasyame.marvelchampions.domain.deckbuilder


/**
 * What a deck is made of, counted rather than listed.
 *
 * Everything here counts **copies**, not distinct cards: a deck with three
 * copies of a one-cost ally has three one-cost cards in it, and the question a
 * player is asking — "how often will I draw something cheap?" — is about
 * copies. Counting rows instead would flatter every deck equally and answer
 * nothing.
 */
data class DeckStatistics(
    /** Cost to number of copies at that cost. Cards with no printed cost are excluded. */
    val costCurve: Map<Int, Int> = emptyMap(),
    /** Resource icons printed on the cards, totalled across copies. */
    val resources: ResourceCounts = ResourceCounts(),
    /** Card type name to copies, largest first. */
    val byType: List<Pair<String, Int>> = emptyList(),
    /** Aspect name to copies, largest first. */
    val byAspect: List<Pair<String, Int>> = emptyList(),
    /** Copies with a printed cost, which is what [costCurve] and [averageCost] cover. */
    val costedCards: Int = 0,
    val averageCost: Double = 0.0,
) {
    val hasCostData: Boolean get() = costedCards > 0

    /** The largest column, so a bar chart can scale to it. */
    val tallestCostColumn: Int get() = costCurve.values.maxOrNull() ?: 0
}

/**
 * Resource icons in the deck.
 *
 * These are what pays for cards, so the totals answer a real deckbuilding
 * question: a deck full of energy costs and no energy icons plays badly however
 * good the cards are.
 */
data class ResourceCounts(
    val physical: Int = 0,
    val mental: Int = 0,
    val energy: Int = 0,
    val wild: Int = 0,
) {
    val total: Int get() = physical + mental + energy + wild
}
