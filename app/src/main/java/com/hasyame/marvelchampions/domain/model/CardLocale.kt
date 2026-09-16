package com.hasyame.marvelchampions.domain.model

/**
 * The language card data is stored and displayed in, independent of the UI
 * language.
 *
 * MarvelCDB serves translations from a **locale subdomain**; the `_locale` query
 * parameter and the `Accept-Language` header are both ignored. See
 * `docs/DATA_SOURCES.md`.
 */
enum class CardLocale(val code: String, val host: String) {
    ENGLISH("en", "marvelcdb.com"),
    FRENCH("fr", "fr.marvelcdb.com"),
    ;

    /**
     * The language to fall back on when this one has no translation.
     *
     * There are exactly two, so "the other one" is well defined, and the search
     * query relies on that: with a third language it would return the same card
     * more than once.
     */
    fun fallback(): CardLocale = if (this == FRENCH) ENGLISH else FRENCH

    companion object {
        fun fromCode(code: String): CardLocale? = entries.firstOrNull { it.code == code }

        /**
         * The language to read cards in when nobody has chosen one: the
         * device's, when the cards exist in it, and English otherwise.
         *
         * This used to be French for everybody, the author's own. On an
         * English phone that put "Plumage Adaptatif" under an English screen,
         * and the first search ran against names the player had never seen.
         * The choice in Settings still overrides this, and is kept.
         */
        fun forSystem(language: String): CardLocale =
            fromCode(language.lowercase().substringBefore('-').substringBefore('_')) ?: ENGLISH
    }
}
