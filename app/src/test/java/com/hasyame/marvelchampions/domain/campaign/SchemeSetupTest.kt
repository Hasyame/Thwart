package com.hasyame.marvelchampions.domain.campaign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every card text here is copied from what MarvelCDB actually returns, because
 * the awkward part of this job is that the same heading is written three ways.
 */
class SchemeSetupTest {

    @Test
    fun `reads the setup off a main scheme`() {
        val nebula = "<b>Contents</b>: Nebula (I) and Nebula (II). " +
            "<i>(Nebula (II) and Nebula (III) instead for expert mode.)</i> " +
            "Nebula, Power Stone, Ship Command, and Standard encounter sets.\n" +
            "<b>Setup</b>: Put the Nebula's Ship environment and the Milano support " +
            "into play. Attach the Power Stone to Nebula. Discard the top 2[per_hero] " +
            "cards of the encounter deck, then attach each [[Technique]] attachment " +
            "discarded this way to Nebula."

        assertEquals(
            listOf(
                "Put the Nebula's Ship environment and the Milano support into play.",
                "Attach the Power Stone to Nebula.",
                "Discard the top 2 per player cards of the encounter deck, then attach " +
                    "each TECHNIQUE attachment discarded this way to Nebula.",
            ),
            SchemeSetup.steps(nebula),
        )
    }

    @Test
    fun `the contents section is not setup`() {
        // Contents lists what to fetch out of the box and is already shown as
        // card chips. Letting it through would print it twice, in two forms.
        val steps = SchemeSetup.steps(
            "<b>Contents</b>: Nebula (I) and Nebula (II).\n<b>Setup</b>: Shuffle the deck.",
        )

        assertEquals(listOf("Shuffle the deck."), steps)
    }

    @Test
    fun `reads Hela, whose colon is inside the bold tag`() {
        val hela = "<b> Contents: </b> Villain deck Hela A. \n" +
            "<b> Setup: </b> Attach Odin to the main scheme, [[captive]] side faceup. " +
            "Reveal Gnipahellir and Garm."

        assertEquals(
            listOf(
                "Attach Odin to the main scheme, CAPTIVE side faceup.",
                "Reveal Gnipahellir and Garm.",
            ),
            SchemeSetup.steps(hela),
        )
    }

    @Test
    fun `reads the French heading, spaced colon and all`() {
        val nebula = "<b>Contenu</b> : Nebula (I) et Nebula (II).\n" +
            "<b>Mise en place</b> : mettez en jeu l'environnement Vaisseau de Nebula " +
            "et le soutien Milano. Attachez la Pierre du Pouvoir à Nebula."

        assertEquals(
            listOf(
                "mettez en jeu l'environnement Vaisseau de Nebula et le soutien Milano.",
                "Attachez la Pierre du Pouvoir à Nebula.",
            ),
            SchemeSetup.steps(nebula),
        )
    }

    @Test
    fun `an aside stays with the step it belongs to`() {
        // Mysterio. "(Shuffle.)" is a note about the sentence before it, and as
        // a step of its own it read as an instruction to shuffle something
        // unnamed.
        val steps = SchemeSetup.steps(
            "<b>Setup</b>: Put a shifting Apparition minion into play engaged with " +
                "each player. <i>(Shuffle.)</i>",
        )

        assertEquals(1, steps.size)
        assertTrue(steps.single().endsWith("(Shuffle.)"))
    }

    @Test
    fun `an initial does not end a step`() {
        val steps = SchemeSetup.steps(
            "<b>Setup</b>: Reveal the Agents of S.H.I.E.L.D. set. Shuffle it.",
        )

        assertEquals(
            listOf("Reveal the Agents of S.H.I.E.L.D. set.", "Shuffle it."),
            steps,
        )
    }

    @Test
    fun `a scenario without a printed setup gets nothing`() {
        // Ebony Maw and Thanos put theirs in the rules insert. Inventing one
        // would be worse than the panel not appearing.
        assertEquals(
            emptyList<String>(),
            SchemeSetup.steps("<b>Contents</b>: Ebony Maw (I) and Ebony Maw (II)."),
        )
        assertEquals(emptyList<String>(), SchemeSetup.steps(null))
        assertEquals(emptyList<String>(), SchemeSetup.steps(""))
    }

    @Test
    fun `a step always ends in a full stop`() {
        val steps = SchemeSetup.steps("<b>Setup</b>: Reveal Stryfe's Grasp")

        assertEquals(listOf("Reveal Stryfe's Grasp."), steps)
    }
}
