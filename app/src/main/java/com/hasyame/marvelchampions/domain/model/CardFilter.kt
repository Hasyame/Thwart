package com.hasyame.marvelchampions.domain.model

import com.hasyame.marvelchampions.domain.deckbuilder.IdentityTraits

/**
 * The filters applied to a card search. Every field is additive; an empty
 * collection means "do not filter on this".
 */
data class CardFilter(
    val query: String = "",
    val packCodes: Set<String> = emptySet(),
    val typeCodes: Set<String> = emptySet(),
    val factionCodes: Set<String> = emptySet(),
    val traits: Set<String> = emptySet(),
    val minCost: Int? = null,
    val maxCost: Int? = null,
    /** Restrict to packs the user owns. Off by default, so a new user sees everything. */
    val ownedOnly: Boolean = false,
    /** Show only starred cards. */
    val favouritesOnly: Boolean = false,
    /**
     * When set, only cards the identity can play: those with no condition, and
     * those whose condition one of these faces answers. The deck editor's
     * "hide cards without synergy" box; null leaves every card in.
     */
    val synergyWith: IdentityTraits? = null,
    /** Ordering. Not part of [isEmpty]: a sort is not a filter. */
    val sort: CardSort = CardSort.SET,
) {
    val isEmpty: Boolean
        get() = query.isBlank() &&
            packCodes.isEmpty() &&
            typeCodes.isEmpty() &&
            factionCodes.isEmpty() &&
            traits.isEmpty() &&
            minCost == null &&
            maxCost == null &&
            !ownedOnly

    /** How many filters are active, for the badge on the filter button. */
    val activeCount: Int
        get() = listOf(
            packCodes.isNotEmpty(),
            typeCodes.isNotEmpty(),
            factionCodes.isNotEmpty(),
            traits.isNotEmpty(),
            minCost != null || maxCost != null,
            ownedOnly,
            favouritesOnly,
        ).count { it }
}
