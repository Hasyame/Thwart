package com.hasyame.marvelchampions.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination

/**
 * Switches tab while preserving each tab's own back stack.
 *
 * `saveState`/`restoreState` are what make the stacks independent; popping up to
 * the host graph's start destination keeps the bar from growing an unbounded
 * history of tab switches.
 */
fun NavController.navigateToTopLevelDestination(destination: TopLevelDestination) {
    navigate(destination.graphRouteInstance()) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/** True when [this] destination, or any of its parents, is [destination]'s graph. */
fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.graphRoute) } == true

private val NavDestination.hierarchy: Sequence<NavDestination>
    get() = generateSequence(this) { it.parent }

private fun TopLevelDestination.graphRouteInstance(): Any = when (this) {
    TopLevelDestination.CARDS -> CardsGraph
    TopLevelDestination.DECKS -> DecksGraph
    TopLevelDestination.PLAY -> PlayGraph
    TopLevelDestination.RULES -> RulesGraph
    TopLevelDestination.STATS -> StatsGraph
    TopLevelDestination.SETTINGS -> SettingsGraph
}
