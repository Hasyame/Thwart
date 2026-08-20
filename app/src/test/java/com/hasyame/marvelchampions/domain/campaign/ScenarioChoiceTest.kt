package com.hasyame.marvelchampions.domain.campaign

import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEngine
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignState
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.Condition
import com.hasyame.marvelchampions.domain.campaign.template.LocalizedText
import com.hasyame.marvelchampions.domain.campaign.template.NextStep
import com.hasyame.marvelchampions.domain.campaign.template.Outcome
import com.hasyame.marvelchampions.domain.campaign.template.ScenarioTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A campaign played in whatever order the table likes.
 *
 * Fear No Evil names no order: after each scenario the players pick another
 * they have not played, and the finale is held back until it is all that is
 * left. Every campaign before this one told the app where to go next.
 */
class ScenarioChoiceTest {

    private val engine = CampaignEngine()

    private fun scenario(id: String) = ScenarioTemplate(
        id = id,
        name = LocalizedText(fr = id),
        onVictory = Outcome(next = listOf(NextStep(choose = true))),
        onDefeat = Outcome(next = listOf(NextStep(choose = true))),
    )

    private val template = CampaignTemplate(
        id = "fne",
        schemaVersion = 1,
        name = LocalizedText(fr = "Peur de Rien"),
        chooseFirstScenario = true,
        finaleScenarioId = "caid",
        scenarios = listOf(scenario("racket"), scenario("raft"), scenario("caid")),
    )

    private fun started() = CampaignEvent.CampaignStarted(
        id = "start",
        timestamp = 0,
        templateId = "fne",
        difficulty = "standard",
        heroes = emptyList(),
        startScenarioId = "racket",
    )

    private fun chose(id: String, at: Long) =
        CampaignEvent.ScenarioChosen(id = "c$at", timestamp = at, scenarioId = id)

    private fun played(id: String, at: Long, victory: Boolean = true) =
        CampaignEvent.ScenarioCompleted(
            id = "p$at",
            timestamp = at,
            scenarioId = id,
            victory = victory,
        )

    @Test
    fun `the campaign opens by asking rather than on a scenario`() {
        val state = engine.fold(template, listOf(started()))

        assertTrue(state.awaitingChoice)
        assertNull("nothing is current until they pick", state.currentScenarioId)
    }

    @Test
    fun `choosing opens that scenario`() {
        val state = engine.fold(template, listOf(started(), chose("raft", 1)))

        assertEquals("raft", state.currentScenarioId)
        assertFalse(state.awaitingChoice)
    }

    @Test
    fun `finishing one asks again`() {
        val state = engine.fold(template, listOf(started(), chose("raft", 1), played("raft", 2)))

        assertTrue(state.awaitingChoice)
        assertNull(state.currentScenarioId)
    }

    @Test
    fun `a scenario already played is not offered again`() {
        val state = engine.fold(template, listOf(started(), chose("raft", 1), played("raft", 2)))

        val ids = CampaignEngine.choosableScenarios(template, state).map { it.id }
        assertEquals(listOf("racket"), ids)
    }

    @Test
    fun `losing leaves the scenario on the table`() {
        // Fear No Evil is explicit that losing does not fail a scenario and
        // that it can be played again. A defeat that struck the job off took
        // that decision away from the table.
        val state = engine.fold(
            template,
            listOf(started(), chose("raft", 1), played("raft", 2, victory = false)),
        )

        val ids = CampaignEngine.choosableScenarios(template, state).map { it.id }
        assertTrue("a lost scenario must still be choosable: $ids", "raft" in ids)
    }

    @Test
    fun `the finale is held back until it is all that is left`() {
        val early = engine.fold(template, listOf(started()))
        assertFalse(
            "the finale must not be playable first",
            "caid" in CampaignEngine.choosableScenarios(template, early).map { it.id },
        )

        val late = engine.fold(
            template,
            listOf(
                started(),
                chose("raft", 1), played("raft", 2),
                chose("racket", 3), played("racket", 4),
            ),
        )
        assertEquals(listOf("caid"), CampaignEngine.choosableScenarios(template, late).map { it.id })
    }

    @Test
    fun `nothing is left once the finale is played`() {
        val state = engine.fold(
            template,
            listOf(
                started(),
                chose("raft", 1), played("raft", 2),
                chose("racket", 3), played("racket", 4),
                chose("caid", 5), played("caid", 6),
            ),
        )

        assertTrue(CampaignEngine.choosableScenarios(template, state).isEmpty())
    }

    @Test
    fun `a scenario pushed to its limit is no longer offered`() {
        // Fear No Evil's villains push the places the heroes walk past. Three
        // pushes and that place is gone, played or not — and offering it again
        // would let a table undo the one decision the campaign asks of them.
        val pressured = ScenarioTemplate(
            id = "musee",
            name = LocalizedText(fr = "musee"),
            failedWhen = Condition(counter = "pressionMusee", atLeast = 3),
            onVictory = Outcome(next = listOf(NextStep(choose = true))),
        )
        val template = CampaignTemplate(
            id = "fne",
            schemaVersion = 1,
            name = LocalizedText(fr = "Peur de Rien"),
            chooseFirstScenario = true,
            finaleScenarioId = "caid",
            scenarios = listOf(pressured, scenario("raft"), scenario("caid")),
        )

        val safe = CampaignState(counters = mapOf("pressionMusee" to 2))
        assertTrue(
            "two pushes is not gone yet",
            CampaignEngine.choosableScenarios(template, safe).any { it.id == "musee" },
        )

        val lost = CampaignState(counters = mapOf("pressionMusee" to 3))
        assertFalse(
            "three pushes and it is lost",
            CampaignEngine.choosableScenarios(template, lost).any { it.id == "musee" },
        )
        assertTrue(
            "the others are untouched",
            CampaignEngine.choosableScenarios(template, lost).any { it.id == "raft" },
        )
    }
}
