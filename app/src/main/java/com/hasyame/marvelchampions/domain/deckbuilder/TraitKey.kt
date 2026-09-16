package com.hasyame.marvelchampions.domain.deckbuilder

/**
 * A trait as a key: the English word, lowercased, without its printed full
 * stop. `guardian`, `x-men`, `s.h.i.e.l.d`, `deadpool corps`.
 *
 * The same key on both sides of the sync, whatever language the cards are
 * read in, so a French card and an English identity agree. Display goes
 * through the translated `traits` string, never through this.
 */
object TraitKey {

    /** `[[X-MEN]]` and `X-Men.` both become `x-men`. */
    fun normalize(trait: String): String =
        trait.trim().trimEnd('.').trim().lowercase()

    /**
     * The traits printed on a card, as keys.
     *
     * Traits are printed as `Avenger. Gamma.`: split on the full stop *and* the
     * space, not the full stop alone, because `S.H.I.E.L.D.` is one trait with
     * five of them. Splitting on the dot alone is what stopped Maria Hill's
     * deck_options allowance ever matching a S.H.I.E.L.D. support.
     */
    fun split(printed: String?): List<String> {
        if (printed.isNullOrBlank()) {
            return emptyList()
        }
        return printed.split(". ")
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
    }
}
