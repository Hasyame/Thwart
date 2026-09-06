package com.hasyame.marvelchampions.domain.campaign

import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEngine
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.Condition
import com.hasyame.marvelchampions.domain.campaign.template.CounterDefinition
import com.hasyame.marvelchampions.domain.campaign.template.LocalizedText
import com.hasyame.marvelchampions.domain.campaign.template.NextStep
import com.hasyame.marvelchampions.domain.campaign.template.Outcome
import com.hasyame.marvelchampions.domain.campaign.template.ScenarioTemplate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "Scenario 2 of 5", and why it is not always the length of the scenario list.
 *
 * The figure on a campaign box has one job: to be true. Taking it from
 * `template.scenarios.size` would be wrong for the only campaign where it
 * matters — Fear No Evil ships six jobs and pushes them off the board before
 * they are ever played, so a table that lets one go plays five, and one that
 * lets two go plays four. A bar that always ran to six would sit stuck below
 * full at the end of a campaign that was completely finished.
 *
 * The denominator is therefore counted from the run: what has been played, what
 * can still be chosen, and the finale. A job pushed out is in none of the three.
 */
class CampaignProgressTest {

    private val engine = CampaignEngine()

    /** A job that is gone once its pressure counter reaches three. */
    private fun job(id: String) = ScenarioTemplate(
        id = id,
        name = LocalizedText(fr = id),
        failedWhen = Condition(counter = "pressure_$id", atLeast = 3),
        onVictory = Outcome(next = listOf(NextStep(choose = true))),
        onDefeat = Outcome(next = listOf(NextStep(choose = true))),
    )

    private val template = CampaignTemplate(
        id = "fne",
        schemaVersion = 1,
        name = LocalizedText(fr = "Peur de Rien"),
        chooseFirstScenario = true,
        finaleScenarioId = "caid",
        counters = listOf("a", "b", "c").map { CounterDefinition(id = "pressure_$it") },
        scenarios = listOf(
            job("a"),
            job("b"),
            job("c"),
            ScenarioTemplate(
                id = "caid",
                name = LocalizedText(fr = "Le Caïd"),
                onVictory = Outcome(next = listOf(NextStep(end = true))),
            ),
        ),
    )

    private fun started() = CampaignEvent.CampaignStarted(
        id = "start",
        timestamp = 0,
        templateId = "fne",
        difficulty = "standard",
        heroes = emptyList(),
        // Empty rather than a scenario id: this campaign opens by asking.
        startScenarioId = "",
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

    /**
     * Pushes [id] off the board.
     *
     * One event, not three: a manual adjustment sets a counter rather than
     * adding to it, so this is the third push arriving.
     */
    private fun pushedOut(id: String, at: Long) = listOf(
        CampaignEvent.ManualAdjustment(
            id = "push$at",
            timestamp = at,
            counterId = "pressure_$id",
            value = 3,
        ),
    )

    private fun progress(events: List<CampaignEvent>): Pair<Int, Int> {
        val state = engine.fold(template, events)
        return CampaignEngine.settledScenarios(template, state).size to
            CampaignEngine.scenariosToPlay(template, state)
    }

    @Test
    fun `a fresh run counts every job and the finale`() {
        assertEquals(0 to 4, progress(listOf(started())))
    }

    @Test
    fun `winning a job moves the numerator, not the denominator`() {
        assertEquals(
            1 to 4,
            progress(listOf(started(), chose("a", 1), played("a", 2))),
        )
    }

    @Test
    fun `a job pushed off the board leaves the campaign shorter`() {
        // The whole reason this is counted rather than taken from the list.
        // Three jobs and a finale become two jobs and a finale, and the bar is
        // measured against what the table will actually play.
        assertEquals(
            0 to 3,
            progress(listOf(started()) + pushedOut("b", 1)),
        )
    }

    @Test
    fun `a job lost is not settled while it can be played again`() {
        // Fear No Evil is explicit that losing does not fail a job. It is still
        // on the board, so it counts towards what is left rather than what is
        // done.
        assertEquals(
            0 to 4,
            progress(listOf(started(), chose("a", 1), played("a", 2, victory = false))),
        )
    }

    @Test
    fun `a job lost and then pushed out is settled by the pushing, not the loss`() {
        val events = listOf(started(), chose("a", 1), played("a", 2, victory = false)) +
            pushedOut("a", 3)

        // It was played, so it counts in both figures: one of the four this
        // run will amount to. Being pushed out afterwards settles it; it does
        // not unsee the game.
        assertEquals(1 to 4, progress(events))
    }

    @Test
    fun `the finale is counted once, not twice, when it is all that is left`() {
        // It is held out of the choosable list until the end and then appears
        // in it. Summing the two lists would count it twice here.
        val events = listOf(
            started(),
            chose("a", 1), played("a", 2),
            chose("b", 3), played("b", 4),
            chose("c", 5), played("c", 6),
        )

        assertEquals(3 to 4, progress(events))
    }

    @Test
    fun `a finished campaign reads as complete`() {
        val events = listOf(
            started(),
            chose("a", 1), played("a", 2),
            chose("b", 3), played("b", 4),
            chose("c", 5), played("c", 6),
            chose("caid", 7), played("caid", 8),
        )

        assertEquals(4 to 4, progress(events))
    }

    @Test
    fun `a campaign that lost a job still reaches the end of its bar`() {
        // The case a fixed denominator gets wrong. Two jobs played, one pushed
        // out, the finale won: the campaign is over and the bar must say so
        // rather than stopping at four fifths.
        val events = listOf(started()) +
            pushedOut("c", 1) +
            listOf(
                chose("a", 10), played("a", 11),
                chose("b", 12), played("b", 13),
                chose("caid", 14), played("caid", 15),
            )

        val (settled, toPlay) = progress(events)
        assertEquals(3, settled)
        assertEquals(3, toPlay)
    }
}
