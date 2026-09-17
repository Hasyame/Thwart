package com.hasyame.marvelchampions.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CardLocaleTest {

    @Test
    fun `a device in a language the cards exist in reads them in it`() {
        assertEquals(CardLocale.FRENCH, CardLocale.forSystem("fr"))
        assertEquals(CardLocale.FRENCH, CardLocale.forSystem("fr-CA"))
        assertEquals(CardLocale.FRENCH, CardLocale.forSystem("FR_BE"))
        assertEquals(CardLocale.ENGLISH, CardLocale.forSystem("en"))
        assertEquals(CardLocale.ENGLISH, CardLocale.forSystem("en-GB"))
    }

    @Test
    fun `any other language reads the cards in English`() {
        // The F-Droid reviewer's finding: a phone in English got French cards.
        // German or Spanish get English, the language most players read
        // MarvelCDB in, never French.
        assertEquals(CardLocale.ENGLISH, CardLocale.forSystem("de"))
        assertEquals(CardLocale.ENGLISH, CardLocale.forSystem("es-ES"))
        assertEquals(CardLocale.ENGLISH, CardLocale.forSystem(""))
    }

    @Test
    fun `each language falls back on the other`() {
        assertEquals(CardLocale.ENGLISH, CardLocale.FRENCH.fallback())
        assertEquals(CardLocale.FRENCH, CardLocale.ENGLISH.fallback())
    }
}
