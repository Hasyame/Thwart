package com.hasyame.marvelchampions.domain.draft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The collection a draft is played from.
 *
 * The rule that matters beyond the counting: a pack added for the evening is
 * on the table, so its identities can be drafted and not merely its cards
 * lent to everybody else's decks. That was the difference between adding a
 * friend's hero pack and being able to play the hero in it.
 */
class SessionCollectionTest {

    private val packs = listOf(
        DraftPack("core", "Core Set", "CORE", owned = 1),
        DraftPack("wsp", "Wasp", "HERO_PACK", owned = 1),
        DraftPack("trors", "The Rise of Red Skull", "CAMPAIGN_BOX", owned = 0),
        DraftPack("mojo", "MojoMania", "SCENARIO_PACK", owned = 0),
    )

    /** The saved collection, adjusted for one evening: a box brought, a box left home. */
    private val session = mapOf("core" to 1, "wsp" to 0, "trors" to 2)

    @Test
    fun `a pack the draft has is on the table, whatever the saved collection says`() {
        assertTrue(SessionCollection.holds(session, "trors"))
        assertTrue(SessionCollection.holds(session, "core"))
        // Owned, but left at home tonight.
        assertFalse(SessionCollection.holds(session, "wsp"))
        assertFalse(SessionCollection.holds(session, "mojo"))
        // No adjustment at all: nothing is on the table until the draft's own
        // collection is read, which is what opening a draft does.
        assertFalse(SessionCollection.holds(null, "core"))
    }

    @Test
    fun `the summary counts the table and what was changed for it`() {
        assertEquals(2, SessionCollection.onTable(packs, session))
        // Wasp down to none and Red Skull up to two; the Core Set as owned.
        assertEquals(2, SessionCollection.changed(packs, session))
    }

    @Test
    fun `a collection that matches the saved one has changed nothing`() {
        val same = mapOf("core" to 1, "wsp" to 1, "trors" to 0, "mojo" to 0)

        assertEquals(0, SessionCollection.changed(packs, same))
        assertEquals(2, SessionCollection.onTable(packs, same))
    }

    @Test
    fun `the list is what is on the table, plus what is owned`() {
        val shown = SessionCollection.shown(packs, session, query = "", everyPack = false)

        // The Core Set and Red Skull are on the table, Wasp is owned even
        // though it is out tonight; MojoMania is neither.
        assertEquals(listOf("core", "wsp", "trors"), shown.map { it.code })
    }

    @Test
    fun `the switch shows every pack, in the order they were released`() {
        val shown = SessionCollection.shown(packs, session, query = "", everyPack = true)

        assertEquals(packs.map { it.code }, shown.map { it.code })
    }

    @Test
    fun `a search looks through every pack, accents aside`() {
        // Not on the table and not owned: only a search finds it.
        assertEquals(listOf("mojo"), SessionCollection.shown(packs, session, "mojo", everyPack = false).map { it.code })
        assertEquals(listOf("mojo"), SessionCollection.shown(packs, session, "  MOJOMANIA ", everyPack = false).map { it.code })
        assertEquals(emptyList<String>(), SessionCollection.shown(packs, session, "galaxy", everyPack = false).map { it.code })
    }

    @Test
    fun `copies are the draft's own count, never the saved one`() {
        assertEquals(2, SessionCollection.copies(session, packs[2]))
        assertEquals(0, SessionCollection.copies(session, packs[1]))
        assertEquals(0, SessionCollection.copies(null, packs[0]))
    }
}
