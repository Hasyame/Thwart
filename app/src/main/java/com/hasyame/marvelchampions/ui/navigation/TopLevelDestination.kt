package com.hasyame.marvelchampions.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.hasyame.marvelchampions.R
import kotlin.reflect.KClass

/**
 * The five top level destinations, in the order they appear in the navigation bar
 * (or the navigation rail on a wide screen). Settings left the bar when Home
 * arrived, one tap from it behind the gear; the rules stayed, because a rule
 * is looked up mid-game and has to be one tap away.
 *
 * Each one owns its own back stack: switching tabs saves the outgoing stack and
 * restores the incoming one, so leaving Cards for Settings and coming back lands
 * on the card that was open.
 *
 * **Play holds every way of starting a game** — a random draw, a setup chosen by
 * the player, and a campaign. Campaign used to have its own tab and no longer
 * does: a campaign scenario is a game like any other, and the split was a fact
 * about the app's internals rather than about playing. It also pushed the count
 * to six, one more than a phone shows without truncating labels.
 */
enum class TopLevelDestination(
    val route: KClass<*>,
    val graphRoute: KClass<*>,
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
    /**
     * Other graphs that count as being on this tab. Home leads to the
     * settings and to the statistics, which keep their own graphs; while
     * they are open the Home tab stays lit, since that is where the person
     * came from.
     */
    val alsoGraphs: List<KClass<*>> = emptyList(),
) {
    HOME(
        route = HomeRoute::class,
        graphRoute = HomeGraph::class,
        icon = Icons.Filled.Home,
        labelRes = R.string.destination_home,
        alsoGraphs = listOf(SettingsGraph::class, StatsGraph::class),
    ),
    CARDS(
        route = CardsRoute::class,
        graphRoute = CardsGraph::class,
        icon = NavigationIcons.Card,
        labelRes = R.string.destination_cards,
    ),
    DECKS(
        route = DecksRoute::class,
        graphRoute = DecksGraph::class,
        icon = NavigationIcons.Deck,
        labelRes = R.string.destination_decks,
    ),
    PLAY(
        route = PlayRoute::class,
        graphRoute = PlayGraph::class,
        icon = NavigationIcons.Fist,
        labelRes = R.string.destination_play,
    ),
    RULES(
        route = RulesRoute::class,
        graphRoute = RulesGraph::class,
        icon = NavigationIcons.Book,
        labelRes = R.string.destination_rules,
    ),
}
