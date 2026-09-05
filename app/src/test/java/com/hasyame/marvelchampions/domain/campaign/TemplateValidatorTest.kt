package com.hasyame.marvelchampions.domain.campaign

import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.CardListDefinition
import com.hasyame.marvelchampions.domain.campaign.template.ComputedAmount
import com.hasyame.marvelchampions.domain.campaign.template.Condition
import com.hasyame.marvelchampions.domain.campaign.template.CounterDefinition
import com.hasyame.marvelchampions.domain.campaign.template.Effect
import com.hasyame.marvelchampions.domain.campaign.template.FlagSetDefinition
import com.hasyame.marvelchampions.domain.campaign.template.LocalizedText
import com.hasyame.marvelchampions.domain.campaign.template.MarketDefinition
import com.hasyame.marvelchampions.domain.campaign.template.MarketEntry
import com.hasyame.marvelchampions.domain.campaign.template.NextStep
import com.hasyame.marvelchampions.domain.campaign.template.Outcome
import com.hasyame.marvelchampions.domain.campaign.template.Prompt
import com.hasyame.marvelchampions.domain.campaign.template.ScenarioTemplate
import com.hasyame.marvelchampions.domain.campaign.template.SetupStep
import com.hasyame.marvelchampions.domain.campaign.template.TemplateValidationException
import com.hasyame.marvelchampions.domain.campaign.template.TemplateValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateValidatorTest {

    private fun template(
        scenarios: List<ScenarioTemplate> = listOf(ScenarioTemplate(id = "s1")),
        counters: List<CounterDefinition> = listOf(CounterDefinition(id = "credits", scope = "hero")),
        flagSets: List<FlagSetDefinition> = listOf(FlagSetDefinition(id = "f1")),
        cardLists: List<CardListDefinition> = listOf(CardListDefinition(id = "purchases")),
        market: MarketDefinition? = null,
        startScenarioId: String? = null,
        schemaVersion: Int = 1,
    ) = CampaignTemplate(
        id = "t",
        schemaVersion = schemaVersion,
        name = LocalizedText(fr = "T"),
        counters = counters,
        flagSets = flagSets,
        cardLists = cardLists,
        market = market,
        scenarios = scenarios,
        startScenarioId = startScenarioId,
    )

    @Test
    fun `a minimal template is valid`() {
        assertTrue(TemplateValidator.validate(template()).isEmpty())
    }

    @Test
    fun `a computed amount naming nothing that exists is refused`() {
        // A name that matches no counter and no flag set reads as zero, and a
        // zero amount hides its step: the instruction would simply never
        // appear, which is the quietest way a campaign can be wrong.
        val errors = TemplateValidator.validate(
            template(
                scenarios = listOf(
                    ScenarioTemplate(
                        id = "s1",
                        campaignSetup = listOf(
                            SetupStep(
                                text = LocalizedText(fr = "Posez {value} menaces."),
                                compute = ComputedAmount(counter = "pressionTypo"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(errors.any { "unknown counter" in it.message })
    }

    @Test
    fun `a computed amount counting an unknown flag set is refused`() {
        val errors = TemplateValidator.validate(
            template(
                scenarios = listOf(
                    ScenarioTemplate(
                        id = "s1",
                        campaignSetup = listOf(
                            SetupStep(
                                text = LocalizedText(fr = "{value} missions."),
                                compute = ComputedAmount(flagSet = "acheveTypo"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(errors.any { "unknown flag set" in it.message })
    }

    @Test
    fun `a computed amount reads one source, not two and not none`() {
        val both = TemplateValidator.validate(
            template(
                scenarios = listOf(
                    ScenarioTemplate(
                        id = "s1",
                        campaignSetup = listOf(
                            SetupStep(
                                text = LocalizedText(fr = "{value}"),
                                compute = ComputedAmount(counter = "credits", flagSet = "f1"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(both.any { "exactly one" in it.message })
    }

    @Test
    fun `a placeholder with nothing to fill it is refused, and so is the reverse`() {
        // Both are silent in their own way: an unfilled {value} is stripped
        // before anybody sees it, and an amount with nowhere to print gates
        // the step invisibly.
        val unfilled = TemplateValidator.validate(
            template(
                scenarios = listOf(
                    ScenarioTemplate(
                        id = "s1",
                        campaignSetup = listOf(SetupStep(text = LocalizedText(fr = "Posez {value}."))),
                    ),
                ),
            ),
        )
        val unprinted = TemplateValidator.validate(
            template(
                scenarios = listOf(
                    ScenarioTemplate(
                        id = "s1",
                        campaignSetup = listOf(
                            SetupStep(
                                text = LocalizedText(fr = "Posez des menaces."),
                                compute = ComputedAmount(counter = "credits"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(unfilled.any { "computes nothing" in it.message })
        assertTrue(unprinted.any { "never prints" in it.message })
    }

    @Test
    fun `an unknown effect op is rejected rather than ignored`() {
        // Silently skipping it would mean a campaign quietly not paying out.
        val errors = TemplateValidator.validate(
            template(
                scenarios = listOf(
                    ScenarioTemplate(
                        id = "s1",
                        onVictory = Outcome(effects = listOf(Effect(op = "grantWish", counter = "credits", value = 1))),
                    ),
                ),
            ),
        )

        assertTrue(errors.toString(), errors.any { it.message.contains("unknown effect op") })
    }

    @Test
    fun `an effect referring to a counter that does not exist is rejected`() {
        val errors = TemplateValidator.validate(
            template(
                scenarios = listOf(
                    ScenarioTemplate(
                        id = "s1",
                        onVictory = Outcome(effects = listOf(Effect(op = "addCounter", counter = "gold", value = 1))),
                    ),
                ),
            ),
        )

        assertTrue(errors.any { it.message.contains("unknown counter 'gold'") })
    }

    @Test
    fun `an effect reading an answer that no prompt produces is rejected`() {
        // The classic template typo: renaming a prompt and missing the effect.
        val errors = TemplateValidator.validate(
            template(
                scenarios = listOf(
                    ScenarioTemplate(
                        id = "s1",
                        onVictory = Outcome(
                            prompts = listOf(Prompt(id = "victoryPoints", type = "number")),
                            effects = listOf(Effect(op = "addCounter", counter = "credits", from = "vp")),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(errors.any { it.message.contains("no prompt with id 'vp'") })
    }

    @Test
    fun `a branch to an unknown scenario is rejected`() {
        val errors = TemplateValidator.validate(
            template(
                scenarios = listOf(
                    ScenarioTemplate(
                        id = "s1",
                        onVictory = Outcome(next = listOf(NextStep(goto = "s99"))),
                    ),
                ),
            ),
        )

        assertTrue(errors.any { it.message.contains("unknown scenario 's99'") })
    }

    @Test
    fun `a next step that goes nowhere at all is rejected`() {
        val errors = TemplateValidator.validate(
            template(
                scenarios = listOf(
                    ScenarioTemplate(id = "s1", onVictory = Outcome(next = listOf(NextStep()))),
                ),
            ),
        )

        assertTrue(errors.any { it.message.contains("needs a goto, an end, or choose") })
    }

    @Test
    fun `a next step that hands the choice to the players is accepted`() {
        // Fear No Evil names no scenario because the table picks one. Rejecting
        // that was the validator not knowing about a field the schema already
        // had — and it was written for that campaign.
        val errors = TemplateValidator.validate(
            template(
                scenarios = listOf(
                    ScenarioTemplate(
                        id = "s1",
                        onVictory = Outcome(next = listOf(NextStep(choose = true))),
                    ),
                ),
            ),
        )

        assertTrue(errors.none { it.path.startsWith("scenarios.s1.onVictory.next") })
    }

    @Test
    fun `a condition counting an unknown flag set is rejected`() {
        val errors = TemplateValidator.validate(
            template(
                scenarios = listOf(
                    ScenarioTemplate(
                        id = "s1",
                        onVictory = Outcome(
                            next = listOf(
                                NextStep(goto = "s1", condition = Condition(countTrue = "nope", countAtLeast = 1)),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(errors.any { it.message.contains("unknown flag set 'nope'") })
    }

    @Test
    fun `duplicate ids are rejected`() {
        val errors = TemplateValidator.validate(
            template(
                scenarios = listOf(ScenarioTemplate(id = "s1"), ScenarioTemplate(id = "s1")),
                counters = listOf(
                    CounterDefinition(id = "credits"),
                    CounterDefinition(id = "credits"),
                ),
            ),
        )

        assertTrue(errors.any { it.message.contains("duplicate scenario id") })
        assertTrue(errors.any { it.message.contains("duplicate counter id") })
    }

    @Test
    fun `a market entry pointing at an unknown card list is rejected`() {
        val errors = TemplateValidator.validate(
            template(
                market = MarketDefinition(
                    counterId = "credits",
                    entries = listOf(MarketEntry(cardCode = "m1", cost = 1, cardListId = "nope")),
                ),
            ),
        )

        assertTrue(errors.any { it.message.contains("unknown card list 'nope'") })
    }

    @Test
    fun `a duplicated market card is rejected`() {
        val errors = TemplateValidator.validate(
            template(
                market = MarketDefinition(
                    counterId = "credits",
                    entries = listOf(
                        MarketEntry(cardCode = "m1", cost = 1),
                        MarketEntry(cardCode = "m1", cost = 2),
                    ),
                ),
            ),
        )

        assertTrue(errors.any { it.message.contains("duplicate card 'm1'") })
    }

    @Test
    fun `an unknown prompt type is rejected`() {
        val errors = TemplateValidator.validate(
            template(
                scenarios = listOf(
                    ScenarioTemplate(
                        id = "s1",
                        onVictory = Outcome(prompts = listOf(Prompt(id = "p", type = "freeform"))),
                    ),
                ),
            ),
        )

        assertTrue(errors.any { it.message.contains("unknown prompt type") })
    }

    @Test
    fun `a choice prompt without options is rejected`() {
        val errors = TemplateValidator.validate(
            template(
                scenarios = listOf(
                    ScenarioTemplate(
                        id = "s1",
                        onVictory = Outcome(prompts = listOf(Prompt(id = "p", type = "choice"))),
                    ),
                ),
            ),
        )

        assertTrue(errors.any { it.message.contains("choice prompt needs options") })
    }

    @Test
    fun `a future schema version is rejected rather than half understood`() {
        val errors = TemplateValidator.validate(template(schemaVersion = 99))

        assertTrue(errors.any { it.path == "schemaVersion" })
    }

    @Test
    fun `an unknown start scenario is rejected`() {
        val errors = TemplateValidator.validate(template(startScenarioId = "nope"))

        assertTrue(errors.any { it.path == "startScenarioId" })
    }

    @Test
    fun `validateOrThrow reports every problem at once`() {
        val exception = assertThrows(TemplateValidationException::class.java) {
            TemplateValidator.validateOrThrow(
                template(
                    scenarios = listOf(
                        ScenarioTemplate(
                            id = "s1",
                            onVictory = Outcome(
                                effects = listOf(Effect(op = "nope"), Effect(op = "addCounter", counter = "gold", value = 1)),
                            ),
                        ),
                    ),
                    schemaVersion = 42,
                ),
            )
        }

        assertEquals(3, exception.errors.size)
        assertTrue(exception.message!!.contains("Campaign template is invalid"))
    }
}
