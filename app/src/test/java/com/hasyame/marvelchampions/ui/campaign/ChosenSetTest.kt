package com.hasyame.marvelchampions.ui.campaign

import com.hasyame.marvelchampions.domain.randomizer.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which set of encounter cards a campaign is actually played with.
 *
 * A difficulty is a physical set that came in a box, and a player who owns
 * several had no way to say which one a campaign used: standard campaigns were
 * never asked at all, and expert ones were asked only about the Standard half.
 * Leaving it to the app is now an option, and this is the draw that answers it.
 *
 * Drawn once, when the campaign starts, and written into the run. Re-rolling it
 * per scenario would be a different encounter deck every game.
 */
class ChosenSetTest {

    private val owned = listOf(Difficulty.STANDARD_I, Difficulty.STANDARD_III)

    @Test
    fun `a set the table picked is the set it plays`() {
        assertEquals("standard_iii", chosenSet("standard_iii", owned))
    }

    @Test
    fun `at random draws from the sets the collection can field`() {
        // Repeated, because a draw that happens to return the first entry
        // looks identical to one that always does.
        val drawn = List(50) { chosenSet(RANDOM_SET, owned) }.toSet()

        assertEquals(setOf("standard_i", "standard_iii"), drawn)
    }

    @Test
    fun `a set nobody owns is drawn again rather than kept`() {
        // The screen only offers owned sets, so this means the collection
        // changed underneath: playing on a set that is not in the house is
        // worse than playing on a different one.
        val chosen = chosenSet("standard_ii", owned)

        assertTrue(chosen, chosen in setOf("standard_i", "standard_iii"))
    }

    @Test
    fun `owning none of them records nothing at all`() {
        // Rather than naming a set the table cannot produce. A campaign with
        // no difficulty cards is a campaign played without them.
        assertEquals("", chosenSet(RANDOM_SET, emptyList()))
    }

    @Test
    fun `an expert set is never drawn for a standard campaign`() {
        // Expert is the Expert set shuffled in *with* a Standard one; standard
        // is the Standard set alone. The pools are separate for that reason.
        val expert = Difficulty.entries.filter { it.isExpert }

        assertEquals(listOf(Difficulty.EXPERT_I, Difficulty.EXPERT_II), expert)
        assertTrue(Difficulty.standards.none { it.isExpert })
    }
}
