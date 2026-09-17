package com.hasyame.marvelchampions.domain.deckbuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraitKeyTest {

    @Test
    fun `a key is the English word, lowercased, without its dots`() {
        assertEquals("guardian", TraitKey.normalize("Guardian."))
        assertEquals("x-men", TraitKey.normalize("X-MEN"))
        assertEquals("shield", TraitKey.normalize("S.H.I.E.L.D."))
        assertEquals("deadpool corps", TraitKey.normalize(" Deadpool  Corps. "))
    }

    @Test
    fun `printed traits split on the full stop and space, so SHIELD stays whole`() {
        assertEquals(listOf("shield", "soldier"), TraitKey.split("S.H.I.E.L.D. Soldier."))
        assertEquals(listOf("avenger", "shield", "spy"), TraitKey.split("Avenger. S.H.I.E.L.D. Spy."))
        assertEquals(listOf("avenger", "gamma"), TraitKey.split("Avenger. Gamma."))
        assertEquals(listOf("deadpool corps", "x-force"), TraitKey.split("Deadpool Corps. X-Force."))
        assertEquals(emptyList<String>(), TraitKey.split(null))
        assertEquals(emptyList<String>(), TraitKey.split("  "))
    }

    @Test
    fun `a deck option trait matches a SHIELD card`() {
        // Maria Hill's allowance for three S.H.I.E.L.D. supports never matched
        // while traits were split on the dot alone.
        val helicarrier = DeckCardInfo(
            code = "50020",
            name = "Helicarrier",
            factionCode = "leadership",
            typeCode = "support",
            cardSetCode = null,
            traits = "S.H.I.E.L.D. Location.",
            deckLimit = 1,
            isUnique = false,
        )
        assertTrue(helicarrier.hasTrait("S.H.I.E.L.D."))
        assertTrue(helicarrier.hasTrait("location"))
        assertFalse(helicarrier.hasTrait("D"))
    }
}
