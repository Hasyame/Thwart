package com.hasyame.marvelchampions.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.repository.CampaignCardNames
import com.hasyame.marvelchampions.data.repository.CampaignRun
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignState
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.ui.campaign.resolveDraws
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Questions that name the card the app drew.
 *
 * "Was the MISSION defeated?" is answerable but vague — there are five and the
 * app chose one. A placeholder that failed to resolve would leave the question
 * vaguer still, and silently: the text would simply read short.
 */
@RunWith(RobolectricTestRunner::class)
class CampaignQuestionLabelTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun template(): CampaignTemplate {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = context.assets.open("campaigns/aoa.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(CampaignTemplate.serializer(), text).expanded()
    }

    private fun run(state: CampaignState, names: Map<String, String>) = CampaignRun(
        entity = CampaignRunEntity(
            id = "r1",
            templateId = "aoa",
            name = "Age of Apocalypse",
            templateName = "Age of Apocalypse",
            difficulty = "standard",
            createdAt = 0,
            templateJson = "{}",
        ),
        template = template(),
        state = state,
        events = emptyList(),
        timer = TimerState(),
        names = CampaignCardNames(cards = names),
    )

    @Test
    fun `a question names the card that was drawn for this scenario`() {
        val state = CampaignState(
            currentScenarioId = "s1_unus",
            draws = mapOf("s1_unus" to mapOf("mission" to listOf("45168a"))),
        )
        val run = run(state, mapOf("45168a" to "Sabotage the Sea Wall"))

        // Quoted: the name is a card title being picked out of a pile, and a
        // title that runs into the sentence around it is hard to spot.
        assertEquals(
            "Was the MISSION \"Sabotage the Sea Wall\" defeated?",
            resolveDraws("Was the MISSION {mission} defeated?", run, "s1_unus"),
        )
    }

    @Test
    fun `an unresolved placeholder never reaches the player`() {
        val run = run(CampaignState(currentScenarioId = "s1_unus"), emptyMap())

        val text = resolveDraws("Was the MISSION {mission} defeated?", run, "s1_unus")
        assertFalse("braces must not be shown: $text", "{" in text)
        assertEquals("Was the MISSION defeated?", text)
    }

    @Test
    fun `every placeholder in the campaign names a draw that scenario makes`() {
        // The failure this guards against is silent: a typo in a placeholder
        // just removes the card name, leaving a question that still reads.
        val pattern = Regex("""\{([A-Za-z0-9_]+)\}""")
        template().scenarios.forEach { scenario ->
            val drawIds = scenario.campaignSetup.mapNotNull { it.draw?.id }.toSet()
            scenario.onVictory?.prompts.orEmpty().forEach { prompt ->
                listOfNotNull(prompt.label?.fr, prompt.label?.en).forEach { label ->
                    pattern.findAll(label).forEach { match ->
                        assertTrue(
                            "${scenario.id} asks about '{${match.groupValues[1]}}' " +
                                "but draws only $drawIds",
                            match.groupValues[1] in drawIds,
                        )
                    }
                }
            }
        }
    }
}
