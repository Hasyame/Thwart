package com.hasyame.marvelchampions.ui.plays

import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.PlayHero
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import com.hasyame.marvelchampions.data.repository.RandomizerNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What "play again" reads back out of a game in the history.
 *
 * The history spans every version of the app that ever logged a play, and the
 * rows are not all shaped alike: the roster, the Standard set and the modular
 * set codes each arrived later than the plays before them. Each case here is
 * one of those shapes, and what the setup page should be handed for it.
 */
class ReplaySetupTest {

    private val names = RandomizerNames(
        modularSets = mapOf("01144" to "Bomb Scare", "01151" to "Masters of Evil"),
    )

    private fun play(
        difficulty: String = "expert_i",
        standardSet: String = "standard_ii",
        roster: List<PlayHero> = listOf(
            PlayHero("01001a", "Spider-Man", "justice"),
            PlayHero("01010a", "Captain Marvel", "leadership, aggression"),
        ),
        modularSets: String = "01144,01151",
        notes: String = "",
        campaignRunId: String? = null,
    ) = PlayEntity(
        id = "p1",
        playedAt = 0,
        scenarioCode = "01094",
        scenarioName = "Rhino",
        difficulty = difficulty,
        standardSet = standardSet,
        heroCode = "01001a",
        heroName = "Spider-Man",
        aspects = "justice, leadership",
        otherHeroes = "Captain Marvel",
        roster = roster,
        players = 2,
        won = true,
        modularSets = modularSets,
        notes = notes,
        campaignRunId = campaignRunId,
    )

    private fun deck(id: String, name: String, heroCode: String, heroName: String, aspects: String) =
        SavedDeckEntity(
            id = id,
            marvelCdbId = 0,
            kind = "own",
            url = "",
            name = name,
            heroCode = heroCode,
            heroName = heroName,
            aspects = aspects,
            slots = "{}",
            ignoreDeckLimitSlots = "{}",
            descriptionMd = null,
            version = null,
            tags = null,
            rawJson = "{}",
            lastSyncedAt = 0,
        )

    @Test
    fun `a play recorded today comes back whole`() {
        val setup = replaySetup(play(), names)

        assertEquals("01094", setup.scenarioCode)
        assertEquals("expert_i", setup.difficulty)
        assertEquals("standard_ii", setup.standardSet)
        assertEquals(listOf("01144", "01151"), setup.modularSetCodes)
        assertEquals(
            listOf(
                SessionHero("01001a", "justice", heroName = "Spider-Man"),
                SessionHero("01010a", "leadership, aggression", heroName = "Captain Marvel"),
            ),
            setup.heroes,
        )
    }

    @Test
    fun `a seat takes the saved deck it came from, matched as a set of aspects`() {
        val decks = listOf(
            deck("d-cap", "Shield Wall", "01010a", "Captain Marvel", "aggression,leadership"),
            deck("d-other", "Solo Justice", "01001a", "Spider-Man", "justice,leadership"),
        )

        val setup = replaySetup(play(), names, decks)

        assertEquals(
            listOf(
                // Same hero, other aspects: not that deck, so the hero alone.
                SessionHero("01001a", "justice", heroName = "Spider-Man"),
                SessionHero(
                    "01010a", "leadership, aggression",
                    deckId = "d-cap", deckName = "Shield Wall", heroName = "Captain Marvel",
                ),
            ),
            setup.heroes,
        )
    }

    @Test
    fun `modular set codes are recovered from the notes when the column is empty`() {
        val setup = replaySetup(
            play(modularSets = "", notes = "Went badly.\nModular sets: Bomb Scare, Masters of Evil"),
            names,
        )

        assertEquals(listOf("01144", "01151"), setup.modularSetCodes)
    }

    @Test
    fun `a set name in another language is left out rather than guessed`() {
        val setup = replaySetup(
            play(modularSets = "", notes = "Modular sets: Alerte à la bombe, Masters of Evil"),
            names,
        )

        assertEquals(listOf("01151"), setup.modularSetCodes)
    }

    @Test
    fun `a play from before the roster gives the first seat only`() {
        val setup = replaySetup(play(roster = emptyList()), names)

        assertEquals(listOf(SessionHero("01001a", "justice", heroName = "Spider-Man")), setup.heroes)
    }

    @Test
    fun `the old difficulty words map to the core set's`() {
        assertEquals("standard_i", replaySetup(play(difficulty = "standard"), names).difficulty)
        assertEquals("expert_i", replaySetup(play(difficulty = "expert"), names).difficulty)
    }

    @Test
    fun `a difficulty the page cannot offer is not handed over`() {
        assertNull(replaySetup(play(difficulty = "heroic"), names).difficulty)
    }

    @Test
    fun `an old expert play without its standard set leaves the question open`() {
        assertNull(replaySetup(play(standardSet = ""), names).standardSet)
    }
}
