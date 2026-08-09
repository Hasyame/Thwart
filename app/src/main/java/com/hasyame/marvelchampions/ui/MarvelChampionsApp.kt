package com.hasyame.marvelchampions.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hasyame.marvelchampions.ui.navigation.CardsGraph
import com.hasyame.marvelchampions.ui.navigation.CollectionRoute
import com.hasyame.marvelchampions.ui.navigation.DecksGraph
import com.hasyame.marvelchampions.ui.navigation.MarvelChampionsNavHost
import com.hasyame.marvelchampions.ui.navigation.SettingsGraph
import com.hasyame.marvelchampions.ui.navigation.TopLevelDestination
import com.hasyame.marvelchampions.ui.navigation.isOn
import com.hasyame.marvelchampions.ui.navigation.navigateToTopLevelDestination

/**
 * [NavigationSuiteScaffold] picks the navigation container from the window size
 * class on its own: a bottom bar on a phone, a rail on the tablet.
 */
@Composable
fun MarvelChampionsApp(
    /** A MarvelCDB link shared into the app, if it was launched by a share. */
    sharedLink: String? = null,
    onSharedLinkHandled: () -> Unit = {},
    viewModel: AppStartViewModel = hiltViewModel(),
) {
    val startupState by viewModel.startupState.collectAsStateWithLifecycle()

    when (val startup = startupState) {
        StartupState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        is StartupState.Ready -> AppContent(
            // A shared deck link wins over the first-run collection prompt:
            // the user asked for something specific.
            openCollectionFirst = startup.openCollectionFirst && sharedLink.isNullOrBlank(),
            consumeOpenCollection = {
                sharedLink.isNullOrBlank() && viewModel.consumeOpenCollection()
            },
            sharedLink = sharedLink,
            onSharedLinkHandled = onSharedLinkHandled,
        )
    }
}

@Composable
private fun AppContent(
    openCollectionFirst: Boolean,
    consumeOpenCollection: () -> Boolean,
    sharedLink: String?,
    onSharedLinkHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val currentDestination by navController.currentBackStackEntryAsState()

    // A fresh install goes straight to the collection. Navigating rather than
    // making it the start destination keeps Settings underneath it, so the back
    // gesture leads somewhere sensible instead of closing the app.
    //
    // Asked of the view model rather than of this composition, and consumed
    // rather than read: a fold, an unfold or a rotation builds a new
    // composition over the same view model, and reading a plain flag here sent
    // the player back to the collection screen every single time.
    LaunchedEffect(Unit) {
        if (consumeOpenCollection()) {
            navController.navigate(CollectionRoute)
        }
    }

    // Gold pill behind the selected tab. The stock indicator is a pale tint of
    // the primary, which against a red header reads as the bar being a lighter
    // shade rather than as a selection. Built out here because the items lambda
    // below is not composable.
    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onTertiaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    )

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = currentDestination?.destination.isOn(destination),
                    onClick = { navController.navigateToTopLevelDestination(destination) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            // The label sits right next to it, so the icon
                            // carries no extra information for a screen reader.
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                    colors = itemColors,
                )
            }
        },
    ) {
        MarvelChampionsNavHost(
            navController = navController,
            startDestination = when {
                sharedLink != null -> DecksGraph
                openCollectionFirst -> SettingsGraph
                else -> CardsGraph
            },
            sharedLink = sharedLink,
            onSharedLinkHandled = onSharedLinkHandled,
        )
    }
}
