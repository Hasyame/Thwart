package com.hasyame.marvelchampions.domain.draft

import com.hasyame.marvelchampions.domain.deckbuilder.HeroDeckRules
import java.text.Normalizer

/**
 * The name a drafted deck gets unless the player changes it:
 * `DRAFT-SPIDERMAN-AGGRESSION-01`. The same on both clients, whatever the
 * language, so the aspect is its English code and the identity is folded to
 * capitals without accents, spaces or punctuation.
 */
object DraftNaming {

    private const val PREFIX = "DRAFT"

    /** What stands for the aspect when the identity's rule imposes them all. */
    private const val IMPOSED = "MULTI"

    /**
     * The next free name for the identity and aspects, given the names already
     * in use: the other decks on the device, and the ones of the same draft
     * that were named before this one. Case does not tell two names apart.
     */
    fun defaultName(
        heroName: String,
        aspects: List<String>,
        rules: HeroDeckRules?,
        taken: Collection<String>,
    ): String {
        val base = "$PREFIX-${fold(heroName)}-${aspectPart(aspects, rules)}"
        val used = taken.map { it.uppercase() }.toSet()
        var suffix = 1
        while ("$base-${suffix.toString().padStart(2, '0')}" in used) {
            suffix++
        }
        return "$base-${suffix.toString().padStart(2, '0')}"
    }

    /**
     * `MULTI` when the rule imposes the aspects (Adam Warlock); otherwise the
     * codes chosen, alphabetically, so a two-aspect identity reads the same
     * whichever order the player tapped them in.
     */
    fun aspectPart(aspects: List<String>, rules: HeroDeckRules?): String =
        if (DraftEngine.imposedAspects(rules) != null) IMPOSED else aspects.sorted().joinToString("-") { it.uppercase() }

    /** `Ms. Marvel` becomes `MSMARVEL`, `Nébula` becomes `NEBULA`. */
    fun fold(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .uppercase()
            .filter { it in 'A'..'Z' || it in '0'..'9' }
}
