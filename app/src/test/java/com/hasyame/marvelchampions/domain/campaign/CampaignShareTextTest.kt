package com.hasyame.marvelchampions.domain.campaign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The campaign summary as it leaves the app.
 *
 * It is read by people who do not have the app, in a chat window, so the shape
 * matters as much as the figures: no label without a value, the same text twice
 * for the same campaign, and the line naming the app always last.
 */
class CampaignShareTextTest {

    private val labels = CampaignShareLabels(
        finished = "Campaign complete",
        inProgress = "Campaign in progress",
        difficulty = "Difficulty",
        totalTime = "Total time",
        victoryPoints = "Victory points",
        heroes = "Heroes",
        scenariosPlayed = "Scenarios played",
        wins = "Scenarios won",
        defeats = "Scenarios lost",
        winRate = "Win rate",
        cardsBought = "Market cards bought",
        creditsLeft = "Units left",
        victory = "Victory",
        defeat = "Defeat",
        footer = "Tracked with Thwart: https://github.com/Hasyame/Thwart",
    )

    private fun format(
        campaignName: String = "",
        templateName: String = "The Rise of Red Skull",
        finished: Boolean = true,
        heroNames: List<String> = listOf("Spider-Man", "Captain Marvel"),
        hasMarket: Boolean = false,
        scenarios: List<CampaignShareScenario> = listOf(
            CampaignShareScenario("Crossbones", victory = true, time = "1:12:04"),
            CampaignShareScenario("Absorbing Man", victory = false, time = "48:33"),
        ),
    ) = CampaignShareText.format(
        campaignName = campaignName,
        templateName = templateName,
        difficulty = "Standard",
        finished = finished,
        totalTime = "6:42:10",
        victoryPoints = 47,
        heroNames = heroNames,
        scenariosWon = 4,
        scenariosLost = 1,
        winRatePercent = 80,
        hasMarket = hasMarket,
        cardsBought = 9,
        creditsRemaining = 3,
        scenarios = scenarios,
        labels = labels,
    )

    @Test
    fun `names the campaign and says it is done`() {
        val text = format()

        assertEquals("The Rise of Red Skull", text.lineSequence().first())
        assertTrue(text.lineSequence().drop(1).first() == "Campaign complete")
    }

    @Test
    fun `an unfinished campaign does not claim to be complete`() {
        val text = format(finished = false)

        assertTrue(text.contains("Campaign in progress"))
        assertFalse(text.contains("Campaign complete"))
    }

    @Test
    fun `a named run keeps both its own name and the campaign's`() {
        // "Second run at Kang" says nothing on its own to somebody reading it
        // in a chat window.
        val text = format(campaignName = "Second run")

        assertEquals("Second run — The Rise of Red Skull", text.lineSequence().first())
    }

    @Test
    fun `a run named after its campaign is not repeated`() {
        val text = format(campaignName = "The Rise of Red Skull")

        assertEquals("The Rise of Red Skull", text.lineSequence().first())
    }

    @Test
    fun `the market figures appear only for the campaign that has a shop`() {
        assertFalse(format().contains("Market cards bought"))
        assertTrue(format(hasMarket = true).contains("Market cards bought"))
    }

    @Test
    fun `a label with nothing to say is left out`() {
        // Heroes is empty on a campaign abandoned before the first scenario,
        // and "Heroes:" followed by nothing reads as a bug.
        val text = format(heroNames = emptyList())

        assertFalse(text.contains("Heroes"))
    }

    @Test
    fun `each scenario is listed with its result and its time`() {
        val text = format()

        assertTrue(text.contains("Crossbones — Victory · 1:12:04"))
        assertTrue(text.contains("Absorbing Man — Defeat · 48:33"))
    }

    @Test
    fun `the app is named on the last line`() {
        // The whole reason the footer exists: somebody reading a friend's
        // summary is exactly the person who would want the app, and this is
        // the only place it advertises itself.
        val text = format()

        assertEquals(labels.footer, text.lineSequence().last())
    }

    @Test
    fun `a campaign with no scenarios still produces something sensible`() {
        val text = format(scenarios = emptyList())

        assertTrue(text.startsWith("The Rise of Red Skull"))
        assertTrue(text.endsWith(labels.footer))
        assertFalse("no dangling separator", text.contains("—  ·"))
    }

    @Test
    fun `the same campaign formats identically twice`() {
        assertEquals(format(), format())
    }
}
