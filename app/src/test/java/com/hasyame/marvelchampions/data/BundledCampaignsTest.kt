package com.hasyame.marvelchampions.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.allSetupSteps
import com.hasyame.marvelchampions.domain.campaign.template.TemplateValidator
import com.hasyame.marvelchampions.domain.campaign.template.translationCoverage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import com.hasyame.marvelchampions.domain.campaign.template.PromptType
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Validates every campaign shipped in `assets/campaigns/`.
 *
 * These are hand-written, so a typo is the normal failure. Now that they are
 * committed rather than kept on one machine, CI can catch a broken one before
 * it ever reaches a device.
 */
@RunWith(RobolectricTestRunner::class)
class BundledCampaignsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun templates(): List<Pair<String, CampaignTemplate>> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.assets.list("campaigns").orEmpty()
            .filter { it.endsWith(".json") }
            .map { name ->
                val text = context.assets.open("campaigns/$name").bufferedReader()
                    .use { it.readText() }
                name to json.decodeFromString(CampaignTemplate.serializer(), text).expanded()
            }
    }

    @Test
    fun `at least one campaign is bundled`() {
        assertTrue(templates().isNotEmpty())
    }

    /**
     * Continuing past a defeat is a Fear No Evil rule, not a general one.
     *
     * Its onDefeat outcomes carry onContinue effects, which are what turning
     * the environment over and dropping the job actually costs. No other
     * campaign has that rule, and the defeat page reads exactly this to decide
     * whether to offer the choice at all. If a campaign gains the rule, this
     * test is where it gets said out loud.
     */
    @Test
    fun `only Fear No Evil lets a lost scenario be left behind`() {
        val offering = templates().filter { (_, template) ->
            template.scenarios.any { it.onDefeat?.onContinue.orEmpty().isNotEmpty() }
        }.map { (name, _) -> name }

        assertEquals(listOf("fne.json"), offering)
    }

    @Test
    fun `every bundled campaign validates`() {
        templates().forEach { (name, template) ->
            val errors = TemplateValidator.validate(template)
            assertTrue("$name:\n" + errors.joinToString("\n"), errors.isEmpty())
        }
    }

    @Test
    fun `every campaign names a pack so it is only offered when owned`() {
        templates().forEach { (name, template) ->
            assertTrue("$name has no packCode", !template.packCode.isNullOrBlank())
        }
    }

    @Test
    fun `every scenario is reachable from the start`() {
        // A scenario nothing branches to is almost always a typo in a goto.
        templates().forEach { (name, template) ->
            val start = template.startScenarioId ?: template.scenarios.first().id
            val reachable = mutableSetOf(start)
            var changed = true
            while (changed) {
                changed = false
                template.scenarios.filter { it.id in reachable }.forEach { scenario ->
                    val next = listOfNotNull(scenario.onVictory, scenario.onDefeat)
                        .flatMap { it.next }
                    next.mapNotNull { it.goto }
                        .forEach { if (reachable.add(it)) changed = true }
                    // An outcome that hands the choice to the players reaches
                    // every scenario, which is the whole shape of Fear No Evil:
                    // no goto names anything, because the table decides.
                    if (next.any { it.choose }) {
                        template.scenarios.forEach {
                            if (reachable.add(it.id)) changed = true
                        }
                    }
                }
            }
            assertEquals(
                "$name has unreachable scenarios",
                template.scenarios.map { it.id }.toSet(),
                reachable,
            )
        }
    }

    @Test
    fun `galaxys most wanted has its five scenarios in order`() {
        val gmw = templates().map { it.second }.single { it.id == "gmw" }

        assertEquals(
            listOf("s1_badoon", "s2_museum", "s3_escape", "s4_nebula", "s5_ronan"),
            gmw.scenarios.map { it.id },
        )
        assertEquals(28, gmw.market?.entries?.size)
    }

    @Test
    fun `the rise of red skull carries its four kinds of memory`() {
        val trors = templates().map { it.second }.single { it.id == "trors" }

        assertEquals(
            listOf("s1_crossbones", "s2_absorbing_man", "s3_taskmaster", "s4_zola", "s5_red_skull"),
            trors.scenarios.map { it.id },
        )
        // Four lists, not one pool: the finale treats an ally you rescued and
        // an ally you left behind a prison door as opposite facts, and merging
        // them would lose which was which.
        assertEquals(
            setOf("experimental", "tech", "conditions", "rescued", "imprisoned"),
            trors.cardLists.map { it.id }.toSet(),
        )
        // The delay Absorbing Man bought becomes threat in the finale, so it
        // has to survive three scenarios as a number.
        assertTrue("delay counter missing", trors.counters.any { it.id == "delay" })
    }

    @Test
    fun `the red skull finale reads every fact the campaign recorded`() {
        val trors = templates().map { it.second }.single { it.id == "trors" }
        val finale = trors.scenarios.single { it.id == "s5_red_skull" }

        // Each of these was written down in an earlier scenario. A step that
        // stops reading one is how a campaign quietly forgets.
        assertTrue(finale.campaignSetup.any { it.showCounter == "delay" })
        assertTrue(finale.campaignSetup.any { it.showCardList == "experimental" })
        assertTrue(finale.campaignSetup.any { it.showCardList == "imprisoned" })
        assertTrue(finale.campaignSetup.any { it.showCardList == "conditions" })
        assertTrue(finale.campaignSetup.any { it.showHeroesWith == "engaged" })
    }

    @Test
    fun `scenario blurbs are written for the app, not copied from the book`() {
        // The blurbs were once whole passages lifted from the campaign book —
        // several hundred characters each — while the README claimed the
        // templates held no flavour text. A blurb says where you are in the
        // story; two sentences do that, and anything at paragraph length is
        // somebody else's writing.
        templates().forEach { (name, template) ->
            template.scenarios.forEach { scenario ->
                listOfNotNull(scenario.flavour?.fr, scenario.flavour?.en).forEach { text ->
                    assertTrue(
                        "$name/${scenario.id} blurb is ${text.length} chars, " +
                            "which is passage length rather than a blurb: \"$text\"",
                        text.length <= MAX_FLAVOUR_LENGTH,
                    )
                }
            }
        }
    }

    @Test
    fun `every scenario asks for its victory points`() {
        // `vp` is not merely recorded. The app totals it across the campaign,
        // shows it on the result page, and sends it to BoardGameGeek as the
        // play's score — so a scenario that never asks silently contributes
        // nothing to any of the three, and the campaign total reads low.
        templates().forEach { (name, template) ->
            template.scenarios.forEach { scenario ->
                val prompt = scenario.onVictory?.prompts.orEmpty().firstOrNull { it.id == "vp" }
                assertTrue(
                    "$name/${scenario.id} never asks for victory points",
                    prompt != null,
                )
                assertEquals(
                    "$name/${scenario.id} must record victory points as a number",
                    PromptType.NUMBER,
                    prompt?.promptType,
                )
            }
        }
    }

    @Test
    fun `setup steps stay mechanical rather than restating the rules`() {
        // The line this project works to: no rules text. A short blurb is fine
        // — it tells nobody how to play — but a setup step long enough to be a
        // paragraph is a rule copied from the book, and someone without the
        // book must not be able to play from the app alone.
        templates().forEach { (name, template) ->
            template.scenarios.forEach { scenario ->
                scenario.allSetupSteps().forEach { step ->
                    listOfNotNull(step.text.fr, step.text.en).forEach { text ->
                        // Per line, not per step: a step may lay out a short
                        // table — the four difficulty modes, say — and that is
                        // still setup rather than a paragraph of rules.
                        text.lines().forEach { line ->
                            assertTrue(
                                "$name/${scenario.id} setup line reads like a rule: \"$line\"",
                                line.length <= MAX_STEP_LENGTH,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `every campaign reports a translation coverage the page can show`() {
        // The campaign page prints these two numbers for whichever campaign is
        // selected, so a template that threw or produced nonsense would show up
        // as a crash or a wrong claim in front of a player.
        templates().forEach { (name, template) ->
            val coverage = template.translationCoverage()
            assertTrue(
                "$name reports French ${coverage.frenchPercent}%",
                coverage.frenchPercent in 0..100,
            )
            assertTrue(
                "$name reports English ${coverage.englishPercent}%",
                coverage.englishPercent in 0..100,
            )
            // Every campaign is written in French first; none should be short.
            assertEquals("$name is not fully written in French", 100, coverage.frenchPercent)
        }
    }

    @Test
    fun `setup steps point at cards by code rather than naming them in the text`() {
        // A bare code in the prose would be unreadable and would not translate.
        // `{card:CODE}` is different: the app resolves it against the card
        // database, so the player reads a name in their own language and the
        // template never hard-codes one. Everything else is still rejected.
        val placeholder = Regex("""\{card:\d{5}[a-z]?\}""")
        val codePattern = Regex("""\b\d{5}[a-z]?\b""")
        templates().forEach { (name, template) ->
            template.scenarios.forEach { scenario ->
                val texts = scenario.allSetupSteps().flatMap {
                    listOfNotNull(it.text.fr, it.text.en)
                } + scenario.onVictory?.prompts.orEmpty().flatMap {
                    listOfNotNull(it.label?.fr, it.label?.en)
                }
                texts.forEach { text ->
                    assertTrue(
                        "$name/${scenario.id} has a raw card code in its text: \"$text\"",
                        !codePattern.containsMatchIn(placeholder.replace(text, "")),
                    )
                }
            }
        }
    }

    private companion object {
        /**
         * Long enough for a mechanical instruction that names a condition from
         * the campaign log, short enough that a paragraph of book text cannot
         * hide in one. A step that needs more than this is doing too much and
         * should be split — which is how the four Galactic Artifacts
         * instructions became four steps rather than one.
         */
        const val MAX_STEP_LENGTH = 140

        /**
         * Two sentences of scene-setting. The copied passages this replaced ran
         * from 383 to 659 characters, so the cap is well clear of anything
         * written for the app and well under anything lifted from the book.
         */
        const val MAX_FLAVOUR_LENGTH = 250
    }
}
