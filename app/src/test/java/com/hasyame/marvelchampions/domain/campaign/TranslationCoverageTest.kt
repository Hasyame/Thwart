package com.hasyame.marvelchampions.domain.campaign

import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.LocalizedText
import com.hasyame.marvelchampions.domain.campaign.template.ScenarioTemplate
import com.hasyame.marvelchampions.domain.campaign.template.SetupStep
import com.hasyame.marvelchampions.domain.campaign.template.translationCoverage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the campaign page tells a player about how much is in their language. */
class TranslationCoverageTest {

    private fun campaign(vararg steps: SetupStep) = CampaignTemplate(
        id = "t",
        schemaVersion = 1,
        name = LocalizedText(fr = "T", en = "T"),
        scenarios = listOf(ScenarioTemplate(id = "s1", campaignSetup = steps.toList())),
    )

    private fun step(fr: String? = null, en: String? = null) =
        SetupStep(text = LocalizedText(fr = fr, en = en))

    @Test
    fun `a campaign written in both languages is complete`() {
        val coverage = campaign(
            step(fr = "Mélangez", en = "Shuffle"),
            step(fr = "Placez", en = "Place"),
        ).translationCoverage()
        assertEquals(100, coverage.frenchPercent)
        assertEquals(100, coverage.englishPercent)
        assertTrue(coverage.isComplete)
    }

    @Test
    fun `a missing English half is counted, not hidden by the fallback`() {
        // LocalizedText resolves `en ?: fr`, so this still reads for an English
        // player. That is exactly why it has to be reported.
        val coverage = campaign(
            step(fr = "Mélangez", en = "Shuffle"),
            step(fr = "Placez"),
        ).translationCoverage()
        assertEquals(100, coverage.frenchPercent)
        assertTrue("expected under 100, got ${coverage.englishPercent}",
            coverage.englishPercent < 100)
        assertFalse(coverage.isComplete)
    }

    @Test
    fun `steps with no text at all do not count against either language`() {
        // A draw step carries no text: it exists to make the app draw a card.
        val coverage = campaign(
            step(fr = "Mélangez", en = "Shuffle"),
            step(fr = "", en = ""),
        ).translationCoverage()
        assertEquals(100, coverage.frenchPercent)
        assertEquals(100, coverage.englishPercent)
    }

    @Test
    fun `one string short never rounds up to complete`() {
        // 199 of 200 rounds to 100 in plain arithmetic, and a reader would take
        // that as "nothing missing".
        val steps = (1..199).map { step(fr = "fr$it", en = "en$it") } + step(fr = "seul")
        val coverage = campaign(*steps.toTypedArray()).translationCoverage()
        assertEquals(99, coverage.englishPercent)
        assertFalse(coverage.isComplete)
    }

    @Test
    fun `one string in never rounds down to nothing`() {
        val steps = (1..199).map { step(fr = "fr$it") } + step(fr = "fr200", en = "the one")
        val coverage = campaign(*steps.toTypedArray()).translationCoverage()
        assertEquals(1, coverage.englishPercent)
    }
}
