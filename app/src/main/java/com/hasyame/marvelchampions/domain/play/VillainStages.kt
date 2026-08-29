package com.hasyame.marvelchampions.domain.play

/**
 * Which villain stages a difficulty actually puts on the table.
 *
 * A villain printed I, II and III is played I then II on Standard, and II then
 * III on Expert. Galaxy's Most Wanted states it plainly: "Deck Méchant: Drang
 * (I), Drang (II). Retirez Drang (I) et ajoutez Drang (III) pour le mode
 * expert." The tracker used to walk all three whatever the difficulty, so a
 * Standard game asked for a stage that was not in the deck.
 *
 * Kept here rather than in the repository because it is a rule of the game, and
 * because taking the cards through a pair of accessors makes it testable
 * without building card rows that carry ninety other fields.
 */
object VillainStages {

    /**
     * The one stage shape difficulty changes.
     *
     * 38 of the villains in the card database are printed this way, and they
     * are the only ones where Standard and Expert use different stages.
     */
    val CLASSIC = setOf("I", "II", "III")

    private const val FIRST = "I"
    private const val LAST = "III"

    /**
     * Drops the stage this difficulty does not use.
     *
     * Everything else is returned untouched, deliberately. The stage field also
     * carries a villain's two sides (A and B, as the Wrecking Crew four are
     * printed), the four-part shapes some scenarios use, and Kang, whose
     * difficulty lives in which encounter set was taken rather than in the
     * stages. None of those are difficulty tiers, and dropping one of them
     * would break a scenario in order to fix a different one.
     *
     * Grouped by villain, because a scenario can field several: Tower Defense
     * has Corvus Glaive and Proxima Midnight, each printed I, II and III, and
     * treating the six as one list would take the wrong two.
     */
    fun <T> select(
        cards: List<T>,
        expert: Boolean,
        name: (T) -> String,
        stage: (T) -> String?,
    ): List<T> {
        val byVillain = cards.groupBy(name)
        val dropped = if (expert) FIRST else LAST
        return cards.filter { card ->
            val printed = byVillain[name(card)]
                .orEmpty()
                .mapNotNull { stage(it)?.uppercase() }
                .toSet()
            printed != CLASSIC || stage(card)?.uppercase() != dropped
        }
    }
}
