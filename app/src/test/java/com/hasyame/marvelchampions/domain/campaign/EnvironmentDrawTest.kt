package com.hasyame.marvelchampions.domain.campaign

import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEngine
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignHero
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.CounterDefinition
import com.hasyame.marvelchampions.domain.campaign.template.Condition
import com.hasyame.marvelchampions.domain.campaign.template.DrawDefinition
import com.hasyame.marvelchampions.domain.campaign.template.LocalizedText
import com.hasyame.marvelchampions.domain.campaign.template.NextStep
import com.hasyame.marvelchampions.domain.campaign.template.Outcome
import com.hasyame.marvelchampions.domain.campaign.template.ScenarioTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The environment draw that the app makes for the players.
 *
 * Fear No Evil's villains push two places before every game. The app draws
 * them and advances their pressure counters — this is the fold that turns a
 * recorded draw into ticks on the board, checked here so the automation cannot
 * quietly stop counting.
 */
class EnvironmentDrawTest {

    private fun counter(id: String) =
        CounterDefinition(id = id, scope = "campaign", initial = 0, min = 0, max = 3)

    private fun scenario(id: String, pressure: String) = ScenarioTemplate(
        id = id,
        name = LocalizedText(fr = id),
        pressureCounterId = pressure,
        failedWhen = Condition(counter = pressure, atLeast = 3),
        onVictory = Outcome(next = listOf(NextStep(choose = true))),
        onDefeat = Outcome(next = listOf(NextStep(choose = true))),
    )

    private val template = CampaignTemplate(
        id = "fne",
        schemaVersion = 1,
        name = LocalizedText(fr = "Peur de Rien"),
        chooseFirstScenario = true,
        finaleScenarioId = "caid",
        losesWhenScenarioFails = true,
        counters = listOf(counter("pMusee"), counter("pRacket"), counter("pRaft")),
        environmentDraw = DrawDefinition(
            id = "environments",
            from = listOf("musee", "racket", "raft"),
            count = 2,
            counts = mapOf("musee" to "pMusee", "racket" to "pRacket", "raft" to "pRaft"),
        ),
        scenarios = listOf(
            scenario("musee", "pMusee"),
            scenario("racket", "pRacket"),
            scenario("raft", "pRaft"),
            scenario("caid", "caid"),
        ),
    )

    private fun start() = CampaignEvent.CampaignStarted(
        id = "e0",
        timestamp = 0,
        templateId = "fne",
        difficulty = "standard",
        heroes = listOf(CampaignHero(id = "h1", deckId = "d1", heroCardCode = "01001", name = "Hero")),
        startScenarioId = "musee",
    )

    private fun offer(codes: List<String>, ts: Long) = CampaignEvent.EnvironmentsOffered(
        id = "o-$ts",
        timestamp = ts,
        offered = codes,
    )

    private fun keep(code: String, ts: Long) = CampaignEvent.EnvironmentChosen(
        id = "k-$ts",
        timestamp = ts,
        environmentId = code,
    )

    @Test
    fun `two drawn places each take one tick`() {
        val state = CampaignEngine().fold(
            template,
            listOf(start(), offer(listOf("musee", "raft"), 1)),
        )

        assertEquals(1, state.counter("pMusee"))
        assertEquals(1, state.counter("pRaft"))
        assertEquals(0, state.counter("pRacket"))
    }

    @Test
    fun `the last place standing takes both hits`() {
        // Dealt alone because nothing else is left in the pile, so it takes the
        // pressure twice — the rule for a lone environment.
        val state = CampaignEngine().fold(
            template,
            listOf(start(), offer(listOf("racket"), 1)),
        )

        assertEquals(2, state.counter("pRacket"))
    }

    @Test
    fun `pressure never runs past the point of no return`() {
        val state = CampaignEngine().fold(
            template,
            listOf(
                start(),
                offer(listOf("musee"), 1),
                offer(listOf("musee"), 2),
            ),
        )

        // Four hits, capped at three: a scenario cannot be more failed than
        // failed.
        assertEquals(3, state.counter("pMusee"))
    }

    @Test
    fun `keeping one takes it out of the pile and leaves the other`() {
        val state = CampaignEngine().fold(
            template,
            listOf(start(), offer(listOf("musee", "raft"), 1), keep("musee", 2)),
        )

        assertEquals(listOf("musee"), state.environmentsUsed)
        // Nothing is on the table any more: the next rotation deals afresh.
        assertEquals(emptyList<String>(), state.environmentOffer)
    }

    @Test
    fun `a rotation deals once, however often the screen is reloaded`() {
        // The bug this exists for: after keeping one, the table is empty, and
        // an empty table read as "nothing dealt yet". Every reload dealt
        // another pair and the city fell on its own, without a game being
        // played. The flag says the rotation is spent.
        val afterKeeping = CampaignEngine().fold(
            template,
            listOf(start(), offer(listOf("musee", "raft"), 1), keep("musee", 2)),
        )

        assertTrue("the rotation is spent", afterKeeping.environmentPicked)
        assertTrue("and the players still have to choose where to go", afterKeeping.awaitingChoice)
    }

    @Test
    fun `finishing a scenario opens the next rotation`() {
        val afterScenario = CampaignEngine().fold(
            template,
            listOf(
                start(),
                offer(listOf("musee", "raft"), 1),
                keep("musee", 2),
                CampaignEvent.ScenarioChosen(id = "c", timestamp = 3, scenarioId = "raft"),
                CampaignEvent.ScenarioCompleted(
                    id = "r",
                    timestamp = 4,
                    scenarioId = "raft",
                    victory = true,
                ),
            ),
        )

        assertFalse("the villains get to pick again", afterScenario.environmentPicked)
        assertTrue(afterScenario.awaitingChoice)
    }

    @Test
    fun `a place pushed to three takes the campaign with it`() {
        val state = CampaignEngine().fold(
            template,
            listOf(
                start(),
                offer(listOf("musee", "raft"), 1),
                keep("raft", 2),
                offer(listOf("musee", "racket"), 3),
                keep("racket", 4),
                offer(listOf("musee"), 5),
            ),
        )

        assertEquals(3, state.counter("pMusee"))
        assertTrue("a fallen place ends the run", state.campaignLost)
        assertTrue("and the run is over", state.finished)
    }

    @Test
    fun `a campaign that does not work that way survives a fallen place`() {
        // Only Fear No Evil loses the whole run to one place. Every other
        // campaign treats a failed scenario as a setback, and this is the flag
        // that keeps it that way.
        val forgiving = template.copy(losesWhenScenarioFails = false)

        val state = CampaignEngine().fold(
            forgiving,
            listOf(start(), offer(listOf("musee"), 1), offer(listOf("musee"), 2)),
        )

        assertEquals(3, state.counter("pMusee"))
        assertFalse(state.campaignLost)
        assertFalse(state.finished)
    }
}
