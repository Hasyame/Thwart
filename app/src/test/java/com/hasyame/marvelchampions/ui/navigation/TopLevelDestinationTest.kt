package com.hasyame.marvelchampions.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun `destinations are in the order specified for the navigation bar`() {
        assertEquals(
            listOf(
                TopLevelDestination.CARDS,
                TopLevelDestination.DECKS,
                // Play holds the random draw, the player's own setup and
                // campaigns. Campaign had a tab of its own, which made six.
                TopLevelDestination.PLAY,
                TopLevelDestination.RULES,
                TopLevelDestination.STATS,
                TopLevelDestination.SETTINGS,
            ),
            TopLevelDestination.entries,
        )
    }

    @Test
    fun `there are at most six destinations`() {
        // Material 3 asks for three to five. Six fits, but only just: on a
        // 1080dp-wide screen in French the last label ("Réglages") reaches the
        // edge, so a seventh tab would start truncating rather than fail to
        // compile. Checked on a Pixel 9a; if this ever needs to grow, the bar
        // has to become a rail or the labels have to go.
        assertTrue(
            "A navigation bar holds at most 6 items, found ${TopLevelDestination.entries.size}",
            TopLevelDestination.entries.size <= 6,
        )
    }

    @Test
    fun `each destination has a distinct graph and start route`() {
        val graphRoutes = TopLevelDestination.entries.map { it.graphRoute }
        val routes = TopLevelDestination.entries.map { it.route }

        assertEquals(graphRoutes.size, graphRoutes.distinct().size)
        assertEquals(routes.size, routes.distinct().size)
        assertTrue(
            "A tab's graph route must differ from its start route, otherwise the " +
                "nested graph collapses and the tab loses its own back stack",
            graphRoutes.none { it in routes },
        )
    }
}
