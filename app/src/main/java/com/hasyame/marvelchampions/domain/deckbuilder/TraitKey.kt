package com.hasyame.marvelchampions.domain.deckbuilder

/**
 * A trait as a language-independent key: `guardian`, `x-men`, `shield`,
 * `deadpool corps`.
 *
 * Ported from the web client's `scripts/lib/synergy.mjs`, which is the
 * contract between the two: `traitKey` and `traitKeys` there, `normalize`
 * and `split` here, and the shared fixture checks that they agree. The same
 * function is applied to a card's traits and to a condition's, which is what
 * makes them comparable; display goes through the translated `traits`
 * string on the card, never through this.
 */
object TraitKey {

    /**
     * Lowercase, the dots taken out (`S.H.I.E.L.D.` and `[[S.H.I.E.L.D.]]`
     * are both `shield`), hyphens kept (`x-men`), spaces collapsed.
     */
    fun normalize(trait: String): String =
        trait.lowercase().replace(".", "").replace(WHITESPACE, " ").trim()

    /**
     * A MarvelCDB traits string as keys: "Avenger. S.H.I.E.L.D. Spy." gives
     * `avenger`, `shield`, `spy`. Traits are separated by a dot and a space
     * and the string ends with a dot; the dots inside S.H.I.E.L.D. are
     * neither, which is why the split is not on every dot.
     */
    fun split(printed: String?): List<String> =
        printed.orEmpty()
            .split(TRAIT_SEPARATOR)
            .map(::normalize)
            .filter { it.isNotEmpty() }

    private val WHITESPACE = Regex("""\s+""")
    private val TRAIT_SEPARATOR = Regex("""\.\s+|\.$""")
}
