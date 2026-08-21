package com.hasyame.marvelchampions.domain.campaign

import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEngine
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignState
import com.hasyame.marvelchampions.domain.campaign.template.DrawDefinition
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignHero
import com.hasyame.marvelchampions.domain.campaign.template.Effect
import com.hasyame.marvelchampions.domain.campaign.engine.AnswerSet
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.LocalizedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A draw that deals each player from a different pile.
 *
 * Mutant Genesis gives every player a role, and the upgrade they are dealt at
 * the start of a scenario comes from that role's own five cards. One pool for
 * the table cannot express that, so a per-hero draw can carry a pool per
 * marker and pick by what the hero recorded.
 */
class PerHeroPoolTest {

    private val draw = DrawDefinition(
        id = "roleUpgrade",
        perHero = true,
        perHeroPoolList = "role",
        perHeroPools = mapOf(
            "brawler" to listOf("B1", "B2", "B3"),
            "commander" to listOf("C1", "C2"),
        ),
        excludingPerHero = "spentUpgrades",
    )

    private fun stateWith(
        roles: Map<String, String> = emptyMap(),
        spent: Map<String, List<String>> = emptyMap(),
    ) = CampaignState(
        heroCardLists = buildMap {
            put("role", roles.mapValues { listOf(it.value) })
            if (spent.isNotEmpty()) {
                put("spentUpgrades", spent)
            }
        },
    )

    @Test
    fun `each hero draws from the pool their own marker names`() {
        val state = stateWith(roles = mapOf("hero1" to "brawler", "hero2" to "commander"))

        assertEquals(listOf("B1", "B2", "B3"), CampaignEngine.drawPool(draw, state, "hero1"))
        assertEquals(listOf("C1", "C2"), CampaignEngine.drawPool(draw, state, "hero2"))
    }

    @Test
    fun `what one player has spent is not taken from another`() {
        val state = stateWith(
            roles = mapOf("hero1" to "brawler", "hero2" to "brawler"),
            spent = mapOf("hero1" to listOf("B1", "B2")),
        )

        assertEquals(listOf("B3"), CampaignEngine.drawPool(draw, state, "hero1"))
        assertEquals(listOf("B1", "B2", "B3"), CampaignEngine.drawPool(draw, state, "hero2"))
    }

    /**
     * A shared pool refills when it empties, because a scenario that needs a
     * card must still get one. A role's five upgrades are the opposite: running
     * out is the "use it or lose it" rule working, not a pool to reset.
     */
    @Test
    fun `a per-hero pool does not refill when it is spent`() {
        val state = stateWith(
            roles = mapOf("hero1" to "commander"),
            spent = mapOf("hero1" to listOf("C1", "C2")),
        )

        assertTrue(CampaignEngine.drawPool(draw, state, "hero1").isEmpty())
    }

    @Test
    fun `a hero who recorded no marker is dealt nothing rather than somebody else's`() {
        val state = stateWith(roles = mapOf("hero1" to "brawler"))

        assertTrue(CampaignEngine.drawPool(draw, state, "hero2").isEmpty())
    }

    /** Without a hero there is no marker to read, so the shared pool stands. */
    @Test
    fun `a draw with no per-hero pools is unaffected`() {
        val shared = DrawDefinition(id = "d", from = listOf("X", "Y"), excluding = "used")
        val state = CampaignState(cardLists = mapOf("used" to listOf("X")))

        assertEquals(listOf("Y"), CampaignEngine.drawPool(shared, state, "hero1"))
        assertEquals(listOf("Y"), CampaignEngine.drawPool(shared, state))
    }

    /**
     * The round trip: what a player was dealt is what stops coming back.
     *
     * Recording has to be per hero as well as drawing. A shared record would
     * take one player's spent upgrade out of everybody's pile, which is the
     * opposite of "removed from the campaign" for the player who had it.
     */
    @Test
    fun `recording a per-hero draw shrinks only that player's pile`() {
        val engine = CampaignEngine()
        val dealt = CampaignState(
            heroes = listOf(
                CampaignHero(id = "hero1", deckId = null, heroCardCode = "h1", name = "One"),
                CampaignHero(id = "hero2", deckId = null, heroCardCode = "h2", name = "Two"),
            ),
            heroCardLists = mapOf("role" to mapOf("hero1" to listOf("brawler"), "hero2" to listOf("brawler"))),
            draws = mapOf(
                "s1" to mapOf(
                    CampaignEngine.heroDrawId("roleUpgrade", "hero1") to listOf("B1"),
                    CampaignEngine.heroDrawId("roleUpgrade", "hero2") to listOf("B2"),
                ),
            ),
        )

        val recorded = engine.applyEffects(
            template = CampaignTemplate(id = "t", schemaVersion = 1, name = LocalizedText(fr = "T")),
            state = dealt,
            effects = listOf(
                Effect(
                    op = "addDrawnCard",
                    cardList = "spentUpgrades",
                    from = "roleUpgrade",
                    perHero = true,
                ),
            ),
            scenarioId = "s1",
            answers = AnswerSet(),
            heroStats = emptyMap(),
        )

        assertEquals(listOf("B1"), recorded.heroCardLists["spentUpgrades"]?.get("hero1"))
        assertEquals(listOf("B2"), recorded.heroCardLists["spentUpgrades"]?.get("hero2"))
        assertEquals(listOf("B2", "B3"), CampaignEngine.drawPool(draw, recorded, "hero1"))
        assertEquals(listOf("B1", "B3"), CampaignEngine.drawPool(draw, recorded, "hero2"))
    }
}
