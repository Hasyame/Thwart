package com.hasyame.marvelchampions.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.hasyame.marvelchampions.R
import kotlin.reflect.KClass

/**
 * The five top level destinations, in the order they appear in the navigation bar
 * (or the navigation rail on a wide screen).
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
) {
    CARDS(
        route = CardsRoute::class,
        graphRoute = CardsGraph::class,
        icon = Icons.Filled.Search,
        labelRes = R.string.destination_cards,
    ),
    DECKS(
        route = DecksRoute::class,
        graphRoute = DecksGraph::class,
        icon = Icons.AutoMirrored.Filled.List,
        labelRes = R.string.destination_decks,
    ),
    PLAY(
        route = PlayRoute::class,
        graphRoute = PlayGraph::class,
        icon = Icons.Filled.PlayArrow,
        labelRes = R.string.destination_play,
    ),
    RULES(
        route = RulesRoute::class,
        graphRoute = RulesGraph::class,
        icon = Icons.Filled.Info,
        labelRes = R.string.destination_rules,
    ),
    STATS(
        route = PlaysRoute::class,
        graphRoute = StatsGraph::class,
        icon = Icons.Filled.Star,
        labelRes = R.string.destination_stats,
    ),
    SETTINGS(
        route = SettingsRoute::class,
        graphRoute = SettingsGraph::class,
        icon = Icons.Filled.Settings,
        labelRes = R.string.destination_settings,
    ),
}
