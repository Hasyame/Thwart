package com.hasyame.marvelchampions.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.hasyame.marvelchampions.ui.campaign.CampaignRecordScreen
import com.hasyame.marvelchampions.ui.campaign.CampaignRunScreen
import com.hasyame.marvelchampions.ui.campaign.CampaignScreen
import com.hasyame.marvelchampions.ui.campaign.StartCampaignScreen
import com.hasyame.marvelchampions.ui.cards.CardDetailScreen
import com.hasyame.marvelchampions.ui.cards.CardsScreen
import com.hasyame.marvelchampions.ui.collection.CollectionScreen
import com.hasyame.marvelchampions.ui.decks.DeckDetailScreen
import com.hasyame.marvelchampions.ui.decks.DeckEditorScreen
import com.hasyame.marvelchampions.ui.decks.DecksScreen
import com.hasyame.marvelchampions.ui.decks.NewDeckScreen
import com.hasyame.marvelchampions.ui.plays.GameSessionScreen
import com.hasyame.marvelchampions.ui.plays.PlaysScreen
import com.hasyame.marvelchampions.ui.rules.RulesScreen
import com.hasyame.marvelchampions.ui.play.PlayScreen
import com.hasyame.marvelchampions.ui.randomizer.RandomizerScreen
import com.hasyame.marvelchampions.ui.settings.AboutScreen
import com.hasyame.marvelchampions.ui.settings.SettingsScreen

@Composable
fun MarvelChampionsNavHost(
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier,
    sharedLink: String? = null,
    onSharedLinkHandled: () -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        navigation<CardsGraph>(startDestination = CardsRoute) {
            composable<CardsRoute> {
                CardsScreen(
                    onCardClick = { code -> navController.navigate(CardDetailRoute(code)) },
                )
            }
            composable<CardDetailRoute> { entry ->
                CardDetailScreen(
                    code = entry.toRoute<CardDetailRoute>().code,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        navigation<DecksGraph>(startDestination = DecksRoute) {
            composable<DecksRoute> {
                DecksScreen(
                    onDeckClick = { deckId -> navController.navigate(DeckDetailRoute(deckId)) },
                    // A freshly imported deck opens straight in the editor, so
                    // its legality is visible and fixable there and then.
                    onDeckImported = { deckId ->
                        navController.navigate(DeckDetailRoute(deckId))
                        navController.navigate(DeckEditorRoute(deckId))
                    },
                    onBuildDeck = { navController.navigate(NewDeckRoute) },
                    sharedLink = sharedLink,
                    onSharedLinkHandled = onSharedLinkHandled,
                )
            }
            composable<DeckDetailRoute> { entry ->
                DeckDetailScreen(
                    deckId = entry.toRoute<DeckDetailRoute>().deckId,
                    onBack = { navController.popBackStack() },
                    onCardClick = { code -> navController.navigate(CardDetailRoute(code)) },
                    onEdit = { deckId -> navController.navigate(DeckEditorRoute(deckId)) },
                )
            }
            composable<NewDeckRoute> {
                NewDeckScreen(
                    onBack = { navController.popBackStack() },
                    onDeckCreated = { deckId ->
                        // Pop the picker so the back gesture from the editor
                        // returns to the deck list, not to hero selection.
                        navController.popBackStack()
                        navController.navigate(DeckEditorRoute(deckId))
                    },
                )
            }
            composable<DeckEditorRoute> { entry ->
                DeckEditorScreen(
                    deckId = entry.toRoute<DeckEditorRoute>().deckId,
                    onBack = { navController.popBackStack() },
                )
            }
            // A card opened from a deck belongs to the Decks back stack, so it
            // is registered here too rather than jumping the user to Cards.
            composable<CardDetailRoute> { entry ->
                CardDetailScreen(
                    code = entry.toRoute<CardDetailRoute>().code,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        // Play owns every way of starting a game, campaigns included.
        navigation<PlayGraph>(startDestination = PlayRoute) {
            composable<PlayRoute> {
                PlayScreen(
                    onRandomDraw = { navController.navigate(RandomizerRoute) },
                    onOwnSetup = { navController.navigate(GameSessionRoute()) },
                    onCampaigns = { navController.navigate(CampaignRoute) },
                    onResumeCampaign = { runId ->
                        navController.navigate(CampaignRunRoute(runId))
                    },
                )
            }
            composable<RandomizerRoute> {
                RandomizerScreen(
                    onBack = { navController.popBackStack() },
                    onPlayDraw = { scenario, heroes, modulars, difficulty ->
                        navController.navigate(
                            GameSessionRoute(
                                // Was dropped, so the session had every part of
                                // the draw except the scenario it was for, could
                                // not start, and fell back to the setup page.
                                scenarioCode = scenario.takeIf { it.isNotBlank() },
                                difficulty = difficulty.takeIf { it.isNotBlank() },
                                heroes = heroes,
                                modularSets = modulars,
                                autoStart = true,
                            ),
                        )
                    },
                )
            }
            composable<GameSessionRoute> { entry ->
                val args = entry.toRoute<GameSessionRoute>()
                GameSessionScreen(
                    scenarioCode = args.scenarioCode,
                    difficulty = args.difficulty,
                    heroes = args.heroes,
                    modularSets = args.modularSets,
                    autoStart = args.autoStart,
                    onBack = { navController.popBackStack() },
                    onOpenPlays = { navController.navigate(PlaysRoute) },
                )
            }
            composable<PlaysRoute> {
                PlaysScreen(onBack = { navController.popBackStack() })
            }
            composable<CampaignRoute> {
                CampaignScreen(
                    onBack = { navController.popBackStack() },
                    onOpenRun = { runId -> navController.navigate(CampaignRunRoute(runId)) },
                    onOpenRecord = { runId -> navController.navigate(CampaignRecordRoute(runId)) },
                    onStartCampaign = { navController.navigate(StartCampaignRoute) },
                )
            }
            composable<CampaignRecordRoute> { entry ->
                CampaignRecordScreen(
                    runId = entry.toRoute<CampaignRecordRoute>().runId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<StartCampaignRoute> {
                StartCampaignScreen(
                    onBack = { navController.popBackStack() },
                    onStarted = { runId ->
                        navController.popBackStack()
                        navController.navigate(CampaignRunRoute(runId))
                    },
                )
            }
            composable<CampaignRunRoute> { entry ->
                CampaignRunScreen(
                    runId = entry.toRoute<CampaignRunRoute>().runId,
                    onBack = { navController.popBackStack() },
                    onCardClick = { code -> navController.navigate(CardDetailRoute(code)) },
                )
            }
            // A card opened from a campaign stays in the Campaign back stack.
            composable<CardDetailRoute> { entry ->
                CardDetailScreen(
                    code = entry.toRoute<CardDetailRoute>().code,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        // The rules are a tab because they are consulted mid game, and anything
        // behind two taps during somebody else's turn is a rulebook.
        navigation<RulesGraph>(startDestination = RulesRoute) {
            composable<RulesRoute> { RulesScreen() }
        }
        // Stats is its own tab, so its own graph and back stack.
        navigation<StatsGraph>(startDestination = PlaysRoute) {
            composable<PlaysRoute> { PlaysScreen() }
        }
        navigation<SettingsGraph>(startDestination = SettingsRoute) {
            composable<SettingsRoute> {
                SettingsScreen(
                    onOpenCollection = { navController.navigate(CollectionRoute) },
                    onOpenAbout = { navController.navigate(AboutRoute) },
                )
            }
            composable<CollectionRoute> {
                CollectionScreen(onBack = { navController.popBackStack() })
            }
            composable<AboutRoute> {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
