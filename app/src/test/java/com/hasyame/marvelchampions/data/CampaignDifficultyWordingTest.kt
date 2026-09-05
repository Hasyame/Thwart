package com.hasyame.marvelchampions.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignState
import com.hasyame.marvelchampions.domain.campaign.engine.amountOf
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.Condition
import com.hasyame.marvelchampions.domain.campaign.template.allSetupSteps
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A standard campaign must never be told what an expert one does.
 *
 * The books write both modes into one instruction — "place 1 threat per box
 * ticked, or 2 in an expert campaign" — and copying that into a template puts
 * expert rules in front of a table playing standard, which is at best noise and
 * at worst two threat that should not be there. It was reported from a real
 * game, so it is guarded rather than tidied.
 *
 * There are two honest ways to write one of these: guard the step on the
 * difficulty, or let the app work the number out. Both leave the reader with
 * the instruction that applies to them and nothing else.
 */
@RunWith(RobolectricTestRunner::class)
class CampaignDifficultyWordingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun templates(): List<Pair<String, CampaignTemplate>> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.assets.list("campaigns").orEmpty()
            .filter { it.endsWith(".json") }
            .map { name ->
                val text = context.assets.open("campaigns/$name").bufferedReader()
                    .use { reader -> reader.readText() }
                name to json.decodeFromString(CampaignTemplate.serializer(), text).expanded()
            }
    }

    /** True when this step is only ever read on an expert campaign. */
    private fun Condition?.mentionsExpert(): Boolean {
        if (this == null) {
            return false
        }
        return difficulty.equals("expert", ignoreCase = true) ||
            all.any { it.mentionsExpert() } ||
            any.isNotEmpty() && any.all { it.mentionsExpert() }
    }

    /**
     * Modes the app does not model, and so cannot filter a step by.
     *
     * The game has four; a campaign run here is standard or expert. A step
     * laying all four out is a menu the table picks from, not an expert rule
     * leaking onto a standard game, and rewriting it would take Heroic away
     * from anybody playing it.
     */
    private val UNMODELLED = listOf("Escarmouche", "Héroïque", "Débutant", "Skirmish", "Heroic")

    @Test
    fun `no setup step written for a standard table talks about expert`() {
        val offenders = templates().flatMap { (name, template) ->
            template.scenarios.flatMap { scenario ->
                scenario.allSetupSteps()
                    .filter { step ->
                        listOfNotNull(step.text.fr, step.text.en)
                            .any { it.contains("expert", ignoreCase = true) }
                    }
                    .filterNot { it.condition.mentionsExpert() }
                    .filterNot { step ->
                        listOfNotNull(step.text.fr, step.text.en).any { text ->
                            UNMODELLED.any { text.contains(it, ignoreCase = true) }
                        }
                    }
                    .map { "$name/${scenario.id}: ${it.text.fr ?: it.text.en}" }
            }
        }

        assertEquals(
            "these are read on a standard campaign and mention expert rules",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the tanker takes a token at two pressure, and two on expert`() {
        // The rule as printed: nothing at one box ticked, one token at two,
        // doubled on an expert campaign. Read off the same declaration the
        // briefing prints, so the number on screen is this arithmetic and not
        // a second copy of it.
        val poursuite = templates().first { it.first == "fne.json" }.second
            .scenarios.first { it.id == "s2_poursuite" }
        val step = poursuite.allSetupSteps().first { it.compute != null }
        val amount = step.compute!!

        assertEquals("pressionPoursuite", amount.counter)
        assertEquals(0, amount.amountFor(counterValue = 1, expert = false))
        assertEquals(1, amount.amountFor(counterValue = 2, expert = false))
        assertEquals(2, amount.amountFor(counterValue = 2, expert = true))
    }

    @Test
    fun `the finale counts the settled jobs itself`() {
        // Two of its setup steps used to open with "if three missions or more
        // are ACHEVE", which asks the table to count something the campaign
        // log already knows: those flags are where the count comes from. The
        // step now states the instruction and the number, and the same
        // declaration decides whether it applies at all, so the two cannot
        // disagree.
        val caid = templates().first { it.first == "fne.json" }.second
            .scenarios.first { it.id == "s6_caid" }
        val counted = caid.allSetupSteps().mapNotNull { it.compute }
            .filter { it.flagSet == "acheve" }

        assertEquals(listOf(3, 4), counted.map { it.threshold })

        val tough = counted.first()
        assertEquals("nothing at two settled jobs", 0, tough.amountFor(2, expert = false))
        assertEquals("the count itself at three", 3, tough.amountFor(3, expert = false))
        // Not doubled on expert: how many jobs are settled is a fact about the
        // campaign, not a difficulty.
        assertEquals(4, tough.amountFor(4, expert = true))
    }

    @Test
    fun `every step that prints an amount works one out, and the other way round`() {
        // A `{value}` with nothing to fill it is deleted before anybody sees
        // it, and an amount with nowhere to print gates its step invisibly.
        // Both are silent, so the validator refuses them; this checks the
        // bundled campaigns pass their own rule.
        val offenders = templates().flatMap { (name, template) ->
            template.scenarios.flatMap { scenario ->
                scenario.allSetupSteps()
                    .filter { step ->
                        val prints = listOfNotNull(step.text.fr, step.text.en)
                            .any { it.contains("{value}") }
                        prints != (step.compute != null)
                    }
                    .map { "$name/${scenario.id}: ${it.text.fr ?: it.text.en}" }
            }
        }

        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `a step whose amount comes to nothing is read as not applying`() {
        // Zero and "no amount at all" have to stay different: the briefing
        // hides the first and prints the second as written. Collapsing them
        // would either drop ordinary steps or print "place 0 threat".
        val poursuite = templates().first { it.first == "fne.json" }.second
            .scenarios.first { it.id == "s2_poursuite" }
        val step = poursuite.allSetupSteps().first { it.compute != null }
        val plain = poursuite.allSetupSteps().first { it.compute == null }

        val quiet = CampaignState(difficulty = "standard", counters = mapOf("pressionPoursuite" to 1))
        val pressed = CampaignState(difficulty = "standard", counters = mapOf("pressionPoursuite" to 2))

        assertEquals(0, quiet.amountOf(step.compute))
        assertEquals(1, pressed.amountOf(step.compute))
        assertEquals(null, pressed.amountOf(plain.compute))
    }
}
