package com.hasyame.marvelchampions.ui.decks

import com.hasyame.marvelchampions.core.designsystem.component.cardTypeMark
import com.hasyame.marvelchampions.data.db.entity.CardEntity

/**
 * How a deck list is ordered.
 *
 * Type first by default, because that is how a deck is laid out on a table and
 * how a player checks they have enough of something. Every order falls back to
 * the name so the list never reshuffles between two cards that tie.
 */
enum class DeckSort {
    TYPE,
    COST,
    NAME,
    ;

    val comparator: Comparator<CardEntity>
        get() = when (this) {
            // The mark's own order: allies, events, supports, upgrades,
            // resources, then the obligation nobody chose to include.
            TYPE -> compareBy<CardEntity> { cardTypeMark(it.typeCode).ordinal }
                .thenBy { it.name }

            // Cards with no printed cost sort last rather than as zero: a
            // resource and a one-cost event are not the same thing.
            COST -> compareBy<CardEntity> { it.cost ?: Int.MAX_VALUE }
                .thenBy { it.name }

            NAME -> compareBy { it.name }
        }
}
