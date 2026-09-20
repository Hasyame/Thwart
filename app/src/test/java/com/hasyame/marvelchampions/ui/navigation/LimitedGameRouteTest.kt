package com.hasyame.marvelchampions.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class LimitedGameRouteTest {
    @Test fun `saved decks open the actual selected mode`() {
        val ids = listOf("sealed-one", "draft-two")
        assertEquals(RandomizerRoute("sealed-one,draft-two"), limitedGameRoute("random", ids))
        assertEquals(StartCampaignRoute("sealed-one,draft-two"), limitedGameRoute("campaign", ids))
        assertEquals(GameSessionRoute(deckIds = "sealed-one,draft-two"), limitedGameRoute("own", ids))
    }
}
