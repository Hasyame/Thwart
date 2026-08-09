package com.hasyame.marvelchampions.ui.navigation

import androidx.navigation.createGraph
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A screen inside the Decks tab, standing in for the deck editor.
 *
 * Top level and not private because kotlinx.serialization resolves a route's
 * serializer by reflection, and it cannot see a private nested object.
 */
@Serializable
internal data object DeckDetail

/**
 * The navigation bar's behaviour, as a back stack rather than as pixels.
 *
 * Two bugs reached players through the gap this fills, and neither was visible
 * to a test suite that never navigated: tapping the tab you were already on did
 * nothing at all, and a one-shot navigation re-fired on every rebuilt
 * composition so folding the phone threw the player into Settings.
 *
 * Nothing is drawn. The rules under test live in
 * [navigateToTopLevelDestination] and in the stack it leaves behind, so a
 * controller with a graph is the whole apparatus — no Hilt, no Room, no card
 * database, and it runs in the ordinary test task.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TopLevelNavigationTest {

    private lateinit var nav: TestNavHostController

    @Before
    fun setUp() {
        nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph = nav.createGraph(startDestination = CardsGraph) {
            navigation<CardsGraph>(startDestination = CardsRoute) {
                composable<CardsRoute> {}
            }
            navigation<DecksGraph>(startDestination = DecksRoute) {
                composable<DecksRoute> {}
                composable<DeckDetail> {}
            }
            navigation<PlayGraph>(startDestination = PlayRoute) {
                composable<PlayRoute> {}
            }
        }
    }

    private fun tapTab(destination: TopLevelDestination) =
        nav.navigateToTopLevelDestination(destination)

    private fun where(): String? =
        nav.currentBackStackEntry?.destination?.route?.substringAfterLast('.')

    @Test
    fun `switches between tabs`() {
        tapTab(TopLevelDestination.DECKS)

        assertEquals("DecksRoute", where())
    }

    @Test
    fun `tapping the tab you are on returns to its list`() {
        // Reported after building a deck. The editor lives inside the Decks
        // tab, so Decks was already selected: the tap saved that tab's stack
        // and restored it, landing exactly where it started. The deck list sat
        // one level below with nothing but the back gesture to reach it.
        tapTab(TopLevelDestination.DECKS)
        nav.navigate(DeckDetail)
        assertEquals("DeckDetail", where())

        tapTab(TopLevelDestination.DECKS)

        assertEquals("DecksRoute", where())
    }

    @Test
    fun `leaving a tab and coming back lands where you were`() {
        // The other half of the same rule, and the reason the state is saved at
        // all: a deck left open should still be open on return.
        tapTab(TopLevelDestination.DECKS)
        nav.navigate(DeckDetail)

        tapTab(TopLevelDestination.CARDS)
        assertEquals("CardsRoute", where())
        tapTab(TopLevelDestination.DECKS)

        assertEquals("DeckDetail", where())
    }

    @Test
    fun `switching tabs does not pile up history`() {
        repeat(4) {
            tapTab(TopLevelDestination.DECKS)
            tapTab(TopLevelDestination.PLAY)
            tapTab(TopLevelDestination.CARDS)
        }

        // Popping back to the host's start on every switch is what keeps this
        // bounded. Without it the back gesture walks through every tab the
        // player has ever touched.
        val depth = nav.currentBackStack.value.count { it.destination.route != null }
        assertTrue("back stack grew to $depth entries", depth <= 6)
    }
}
