package com.hasyame.marvelchampions.domain.achievements

import com.hasyame.marvelchampions.data.achievements.AchievementFacts
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.PlayHero
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The phone's records read into facts: docs/spec/achievements/algorithm.md §2, this client's half. */
class AchievementFactsTest {

    private val noCampaign = AchievementFacts.CampaignResolver { null }

    @Test
    fun `a difficulty string names a level by prefix, never standard by default`() {
        assertEquals(DifficultyLevel.STANDARD, DifficultyLevel.of("standard_i"))
        assertEquals(DifficultyLevel.STANDARD, DifficultyLevel.of("Standard II"))
        assertEquals(DifficultyLevel.EXPERT, DifficultyLevel.of(" expert-ii "))
        assertEquals(DifficultyLevel.EXPERT, DifficultyLevel.of("Expert"))
        assertEquals(DifficultyLevel.UNKNOWN, DifficultyLevel.of(""))
        assertEquals(DifficultyLevel.UNKNOWN, DifficultyLevel.of(null))
        assertEquals(DifficultyLevel.UNKNOWN, DifficultyLevel.of("heroic"))
    }

    @Test
    fun `a one-off game keys by its card set, a Fear No Evil job drops its villain`() {
        assertEquals("rhino", AchievementFacts.scenarioKeyOf(play(scenario = "rhino"), noCampaign))
        assertEquals("fne_s1_musee", AchievementFacts.scenarioKeyOf(play(scenario = "fne_s1_musee__fne_villain_electro"), noCampaign))
        assertEquals("fne_s6_caid", AchievementFacts.scenarioKeyOf(play(scenario = "fne_s6_caid"), noCampaign))
        assertEquals("a__b", AchievementFacts.scenarioKeyOf(play(scenario = "a__b"), noCampaign))
    }

    @Test
    fun `a campaign play resolves through its run, or is kept under a prefix`() {
        val resolved = AchievementFacts.CampaignResolver { "crossbones" }
        assertEquals("crossbones", AchievementFacts.scenarioKeyOf(play(scenario = "s1_crossbones", run = "run-1"), resolved))
        assertEquals("campaign:s1_crossbones", AchievementFacts.scenarioKeyOf(play(scenario = "s1_crossbones", run = "run-1"), noCampaign))
    }

    @Test
    fun `seats come from the roster, the flagged one or else the first being the owner`() {
        val unflagged = play(roster = listOf(seat("01001a", "Justice, Leadership"), seat("01010a", "aggression")))
        assertEquals(
            listOf(Seat("01001a", listOf("justice", "leadership"), true), Seat("01010a", listOf("aggression"), false)),
            AchievementFacts.seatsOf(unflagged),
        )

        val flagged = play(roster = listOf(seat("01001a", "justice"), seat("01010a", "aggression", owner = true), seat("01019a", "protection", owner = true)))
        assertEquals(listOf(false, true, false), AchievementFacts.seatsOf(flagged).map { it.isOwner })

        // No roster at all: one seat from the older fields.
        val old = play(roster = emptyList()).copy(heroCode = "01019a", aspects = "Protection")
        assertEquals(listOf(Seat("01019a", listOf("protection"), true)), AchievementFacts.seatsOf(old))
    }

    @Test
    fun `a fact carries the level, the players clamped, and a known mode only`() {
        val fact = AchievementFacts.factOf(play(scenario = "rhino").copy(difficulty = "expert_ii", players = 7, mode = "draft"), noCampaign)!!
        assertEquals(DifficultyLevel.EXPERT, fact.level)
        assertEquals(4, fact.players)
        assertEquals("draft", fact.mode)
        assertNull(AchievementFacts.factOf(play(scenario = "rhino").copy(mode = "tournament"), noCampaign)!!.mode)
    }

    @Test
    fun `a tombstone is no fact`() {
        assertNull(AchievementFacts.factOf(play(scenario = "rhino").copy(deletedAt = 5L), noCampaign))
    }

    @Test
    fun `a run fact takes its level from the run and its loss from the engine`() {
        val run = CampaignRunEntity(
            id = "run-1",
            templateId = "trors",
            templateName = "The Rise of Red Skull",
            difficulty = "expert",
            createdAt = 1L,
            finished = true,
            templateJson = "{}",
        )
        assertEquals(RunFact("run-1", "trors", DifficultyLevel.EXPERT, finished = true, lost = true), AchievementFacts.runFactOf(run, lost = true))
    }

    private fun seat(code: String, aspect: String, owner: Boolean? = null) = PlayHero(code, code, aspect, isOwner = owner)

    private fun play(scenario: String = "rhino", run: String? = null, roster: List<PlayHero> = listOf(seat("01001a", "justice"))) = PlayEntity(
        id = "p1",
        playedAt = 1_700_000_000_000L,
        scenarioCode = scenario,
        scenarioName = scenario,
        difficulty = "standard_i",
        heroCode = "01001a",
        heroName = "Spider-Man",
        aspects = "justice",
        roster = roster,
        players = roster.size.coerceAtLeast(1),
        won = true,
        campaignRunId = run,
    )
}
