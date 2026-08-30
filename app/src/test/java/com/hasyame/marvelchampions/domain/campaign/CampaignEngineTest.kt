package com.hasyame.marvelchampions.domain.campaign

import com.hasyame.marvelchampions.domain.campaign.engine.AnswerSet
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEngine
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignHero
import com.hasyame.marvelchampions.domain.campaign.engine.HeroCardStats
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.CardListDefinition
import com.hasyame.marvelchampions.domain.campaign.template.Condition
import com.hasyame.marvelchampions.domain.campaign.template.CounterDefinition
import com.hasyame.marvelchampions.domain.campaign.template.Effect
import com.hasyame.marvelchampions.domain.campaign.template.FlagSetDefinition
import com.hasyame.marvelchampions.domain.campaign.template.LocalizedText
import com.hasyame.marvelchampions.domain.campaign.template.NextStep
import com.hasyame.marvelchampions.domain.campaign.template.Outcome
import com.hasyame.marvelchampions.domain.campaign.template.Prompt
import com.hasyame.marvelchampions.domain.campaign.template.ScenarioTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the engine with a template shaped like the worked example in the
 * brief, without using any real campaign content.
 */
class CampaignEngineTest {

    private val engine = CampaignEngine()

    private val heroes = listOf(
        CampaignHero("h1", "deck-1", "16001a", "Hero One"),
        CampaignHero("h2", "deck-2", "16002a", "Hero Two"),
    )

    private val template = CampaignTemplate(
        id = "test",
        schemaVersion = 1,
        name = LocalizedText(fr = "Test"),
        counters = listOf(
            CounterDefinition(id = "credits", scope = "hero", initial = 0, min = 0),
            CounterDefinition(id = "vp", scope = "campaign", initial = 0),
            CounterDefinition(
                id = "hp",
                scope = "hero",
                initial = 0,
                maxFrom = CampaignEngine.HERO_HEALTH_REFERENCE,
            ),
        ),
        flagSets = listOf(FlagSetDefinition(id = "trackerDefeated", scope = "perScenario")),
        cardLists = listOf(CardListDefinition(id = "purchases", scope = "hero")),
        scenarios = listOf(
            ScenarioTemplate(
                id = "s1",
                onVictory = Outcome(
                    prompts = listOf(
                        Prompt(id = "vp", type = "number"),
                        Prompt(id = "schemeAt1B", type = "boolean"),
                        Prompt(id = "trackerInPile", type = "boolean"),
                        Prompt(id = "hpPerHero", type = "perHeroNumber"),
                        Prompt(id = "eliminated", type = "perHeroBoolean"),
                    ),
                    effects = listOf(
                        Effect(op = "addCounter", counter = "credits", value = 1),
                        Effect(op = "addCounter", counter = "credits", from = "vp", max = 3),
                        Effect(
                            op = "addCounter",
                            counter = "credits",
                            value = 1,
                            condition = Condition(answer = "schemeAt1B"),
                        ),
                        Effect(op = "setFlag", flag = "trackerDefeated", from = "trackerInPile"),
                        Effect(
                            op = "setHeroCounter",
                            counter = "hp",
                            from = "hpPerHero",
                            condition = Condition(difficulty = "expert"),
                        ),
                        Effect(op = "addCounter", counter = "vp", from = "vp"),
                    ),
                    next = listOf(
                        NextStep(goto = "s3", condition = Condition(countTrue = "trackerDefeated", countAtLeast = 1)),
                        NextStep(goto = "s2"),
                    ),
                ),
                onDefeat = Outcome(next = listOf(NextStep(goto = "s1"))),
            ),
            ScenarioTemplate(id = "s2", onVictory = Outcome(next = listOf(NextStep(end = true)))),
            ScenarioTemplate(id = "s3", onVictory = Outcome(next = listOf(NextStep(end = true)))),
        ),
        startScenarioId = "s1",
    )

    private val heroStats = mapOf(
        "h1" to HeroCardStats("h1", printedHealth = 10),
        "h2" to HeroCardStats("h2", printedHealth = 12),
    )

    private fun start(difficulty: String = "standard") = CampaignEvent.CampaignStarted(
        id = "e0",
        timestamp = 1L,
        templateId = "test",
        difficulty = difficulty,
        heroes = heroes,
        startScenarioId = "s1",
    )

    private fun victory(
        answers: AnswerSet,
        scenarioId: String = "s1",
        id: String = "e1",
        timestamp: Long = 2L,
    ) = CampaignEvent.ScenarioCompleted(
        id = id,
        timestamp = timestamp,
        scenarioId = scenarioId,
        victory = true,
        answers = answers,
    )

    @Test
    fun `starting a campaign seeds counters for every hero`() {
        val state = engine.fold(template, listOf(start()), heroStats)

        assertTrue(state.started)
        assertEquals(0, state.heroCounter("credits", "h1"))
        assertEquals(0, state.heroCounter("credits", "h2"))
        assertEquals("s1", state.currentScenarioId)
    }

    @Test
    fun `the worked example from the brief produces the right credits`() {
        // 1 flat + min(vp,3) + 1 for the scheme question = 1 + 2 + 1 = 4
        val answers = AnswerSet(
            numbers = mapOf("vp" to 2),
            booleans = mapOf("schemeAt1B" to true, "trackerInPile" to false),
        )

        val state = engine.fold(template, listOf(start(), victory(answers)), heroStats)

        assertEquals(4, state.heroCounter("credits", "h1"))
        assertEquals(4, state.heroCounter("credits", "h2"))
    }

    @Test
    fun `a max caps a single operation rather than the counter`() {
        val answers = AnswerSet(
            numbers = mapOf("vp" to 9),
            booleans = mapOf("schemeAt1B" to false, "trackerInPile" to false),
        )

        val state = engine.fold(template, listOf(start(), victory(answers)), heroStats)

        // 1 flat + min(9,3) = 4, not 10
        assertEquals(4, state.heroCounter("credits", "h1"))
    }

    @Test
    fun `a guarded effect does nothing when its condition fails`() {
        val answers = AnswerSet(
            numbers = mapOf("vp" to 0),
            booleans = mapOf("schemeAt1B" to false, "trackerInPile" to false),
        )

        val state = engine.fold(template, listOf(start(), victory(answers)), heroStats)

        assertEquals(1, state.heroCounter("credits", "h1"))
    }

    @Test
    fun `hero hit points are only set on expert and are capped at printed health`() {
        val answers = AnswerSet(
            numbers = mapOf("vp" to 0),
            booleans = mapOf("schemeAt1B" to false, "trackerInPile" to false),
            perHeroNumbers = mapOf("hpPerHero" to mapOf("h1" to 99, "h2" to 5)),
        )

        val standard = engine.fold(template, listOf(start("standard"), victory(answers)), heroStats)
        assertEquals(0, standard.heroCounter("hp", "h1"))

        val expert = engine.fold(template, listOf(start("expert"), victory(answers)), heroStats)
        assertEquals(10, expert.heroCounter("hp", "h1"))
        assertEquals(5, expert.heroCounter("hp", "h2"))
    }

    @Test
    fun `an eliminated hero is skipped by per-hero victory effects`() {
        val answers = AnswerSet(
            numbers = mapOf("vp" to 2),
            booleans = mapOf("schemeAt1B" to false, "trackerInPile" to false),
            perHeroBooleans = mapOf("eliminated" to mapOf("h2" to true)),
        )

        val state = engine.fold(template, listOf(start(), victory(answers)), heroStats)

        assertEquals(3, state.heroCounter("credits", "h1"))
        assertEquals(0, state.heroCounter("credits", "h2"))
    }

    @Test
    fun `a flag is set from a boolean answer`() {
        val answers = AnswerSet(
            numbers = mapOf("vp" to 0),
            booleans = mapOf("schemeAt1B" to false, "trackerInPile" to true),
        )

        val state = engine.fold(template, listOf(start(), victory(answers)), heroStats)

        assertTrue(state.flag("trackerDefeated", "s1"))
        assertEquals(1, state.countTrue("trackerDefeated"))
    }

    @Test
    fun `branching follows the first guard that holds`() {
        val defeatedTracker = AnswerSet(
            numbers = mapOf("vp" to 0),
            booleans = mapOf("schemeAt1B" to false, "trackerInPile" to true),
        )
        val notDefeated = AnswerSet(
            numbers = mapOf("vp" to 0),
            booleans = mapOf("schemeAt1B" to false, "trackerInPile" to false),
        )

        assertEquals(
            "s3",
            engine.fold(template, listOf(start(), victory(defeatedTracker)), heroStats).currentScenarioId,
        )
        assertEquals(
            "s2",
            engine.fold(template, listOf(start(), victory(notDefeated)), heroStats).currentScenarioId,
        )
    }

    @Test
    fun `a defeat replays the same scenario and is still recorded`() {
        val defeat = CampaignEvent.ScenarioCompleted(
            id = "e1",
            timestamp = 2L,
            scenarioId = "s1",
            victory = false,
        )

        val state = engine.fold(template, listOf(start(), defeat), heroStats)

        assertEquals("s1", state.currentScenarioId)
        assertEquals(1, state.completedScenarios.size)
        assertFalse(state.completedScenarios.single().victory)
    }

    @Test
    fun `end finishes the campaign`() {
        val answers = AnswerSet(
            numbers = mapOf("vp" to 0),
            booleans = mapOf("schemeAt1B" to false, "trackerInPile" to false),
        )
        val second = victory(AnswerSet(), scenarioId = "s2", id = "e2", timestamp = 3L)

        val state = engine.fold(template, listOf(start(), victory(answers), second), heroStats)

        assertTrue(state.finished)
    }

    @Test
    fun `revoking a result removes its effects but keeps the log`() {
        val answers = AnswerSet(
            numbers = mapOf("vp" to 3),
            booleans = mapOf("schemeAt1B" to true, "trackerInPile" to true),
        )
        val revoke = CampaignEvent.EventRevoked(
            id = "e2",
            timestamp = 3L,
            revokedEventId = "e1",
            note = "mis-entered",
        )

        val state = engine.fold(template, listOf(start(), victory(answers), revoke), heroStats)

        assertEquals(0, state.heroCounter("credits", "h1"))
        assertTrue(state.completedScenarios.isEmpty())
        assertFalse(state.flag("trackerDefeated", "s1"))
    }

    @Test
    fun `raw answers survive so a corrected template can be replayed`() {
        val answers = AnswerSet(numbers = mapOf("vp" to 2), booleans = mapOf("schemeAt1B" to true))

        val state = engine.fold(template, listOf(start(), victory(answers)), heroStats)

        assertEquals(answers, state.completedScenarios.single().answers)
    }

    @Test
    fun `folding is deterministic and independent of event order in the list`() {
        val answers = AnswerSet(numbers = mapOf("vp" to 1), booleans = mapOf("trackerInPile" to true))
        val events = listOf(start(), victory(answers))

        val forwards = engine.fold(template, events, heroStats)
        val backwards = engine.fold(template, events.reversed(), heroStats)

        assertEquals(forwards, backwards)
    }

    @Test
    fun `a manual adjustment overrides a counter and is applied in order`() {
        val answers = AnswerSet(numbers = mapOf("vp" to 2))
        val manual = CampaignEvent.ManualAdjustment(
            id = "e2",
            timestamp = 5L,
            counterId = "credits",
            heroId = "h1",
            value = 42,
            note = "table correction",
        )

        val state = engine.fold(template, listOf(start(), victory(answers), manual), heroStats)

        assertEquals(42, state.heroCounter("credits", "h1"))
        assertEquals(3, state.heroCounter("credits", "h2"))
    }

    @Test
    fun `a counter respects its floor`() {
        val manual = CampaignEvent.ManualAdjustment(
            id = "e1",
            timestamp = 2L,
            counterId = "credits",
            heroId = "h1",
            value = 5,
        )
        val purchase = CampaignEvent.MarketPurchase(
            id = "e2",
            timestamp = 3L,
            heroId = "h1",
            cardCode = "card-a",
            cost = 3,
            cardListId = "purchases",
        )

        val state = engine.fold(template, listOf(start(), manual, purchase), heroStats)

        assertEquals(2, state.heroCounter("credits", "h1"))
        assertEquals(listOf("card-a"), state.heroCards("purchases", "h1"))
    }

    @Test
    fun `a refunded purchase returns the credits and the card`() {
        val manual = CampaignEvent.ManualAdjustment(
            id = "e1", timestamp = 2L, counterId = "credits", heroId = "h1", value = 5,
        )
        val purchase = CampaignEvent.MarketPurchase(
            id = "e2", timestamp = 3L, heroId = "h1",
            cardCode = "card-a", cost = 3, cardListId = "purchases",
        )
        val refund = CampaignEvent.MarketRefund(id = "e3", timestamp = 4L, purchaseEventId = "e2")

        val state = engine.fold(template, listOf(start(), manual, purchase, refund), heroStats)

        assertEquals(5, state.heroCounter("credits", "h1"))
        assertTrue(state.heroCards("purchases", "h1").isEmpty())
        assertTrue(state.purchases.isEmpty())
    }

    @Test
    fun `victory points are recorded per scenario and never accumulate`() {
        // They measure how well one scenario was played. They are not spent and
        // do not carry forward — only what they convert into does.
        val first = victory(AnswerSet(numbers = mapOf("vp" to 3)), id = "e1", timestamp = 2L)
        val second = CampaignEvent.ScenarioCompleted(
            id = "e2", timestamp = 3L, scenarioId = "s2", victory = true,
            answers = AnswerSet(numbers = mapOf("vp" to 1)),
        )

        val state = engine.fold(template, listOf(start(), first, second), heroStats)

        assertEquals(
            listOf(3, 1),
            state.completedScenarios.map { it.answers.numbers["vp"] },
        )
        // Credits carried forward: (1 + min(3,3)) then s2 has no effects.
        assertEquals(4, state.heroCounter("credits", "h1"))
    }

    @Test
    fun `a campaign with no victory point counter still converts them to credits`() {
        // The conversion is an effect reading an answer, so no counter is needed
        // to hold the points themselves. This is the shape the blank template
        // ships with.
        val scenario = template.scenarios.first()
        val noVp = template.copy(
            counters = template.counters.filterNot { it.id == "vp" },
            scenarios = listOf(
                scenario.copy(
                    onVictory = scenario.onVictory?.let { victory ->
                        victory.copy(effects = victory.effects.filterNot { it.counter == "vp" })
                    },
                ),
            ) + template.scenarios.drop(1),
        )
        val answers = AnswerSet(numbers = mapOf("vp" to 5))

        val state = engine.fold(noVp, listOf(start(), victory(answers)), heroStats)

        assertEquals(4, state.heroCounter("credits", "h1"))
        assertEquals(5, state.completedScenarios.single().answers.numbers["vp"])
    }

    @Test
    fun `replaying an old log does not resurrect a counter the template dropped`() {
        // Correcting a template and replaying is a design goal, so an effect
        // naming a counter that no longer exists has to be inert rather than
        // conjuring it back onto the screen.
        val withoutVp = template.copy(counters = template.counters.filterNot { it.id == "vp" })
        val answers = AnswerSet(numbers = mapOf("vp" to 5))

        val state = engine.fold(withoutVp, listOf(start(), victory(answers)), heroStats)

        assertEquals(0, state.counter("vp"))
        assertFalse(state.counters.containsKey("vp"))
        // Everything else still applies.
        assertEquals(4, state.heroCounter("credits", "h1"))
    }

    @Test
    fun `play time accumulates across scenarios`() {
        val first = CampaignEvent.ScenarioCompleted(
            id = "e1", timestamp = 2L, scenarioId = "s1", victory = true,
            answers = AnswerSet(), elapsedMillis = 60_000,
        )
        val second = CampaignEvent.ScenarioCompleted(
            id = "e2", timestamp = 3L, scenarioId = "s2", victory = true,
            answers = AnswerSet(), elapsedMillis = 90_000,
        )

        val state = engine.fold(template, listOf(start(), first, second), heroStats)

        assertEquals(150_000, state.totalPlayTimeMillis)
    }
}
