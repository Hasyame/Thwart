package com.hasyame.marvelchampions.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.hasyame.marvelchampions.ui.achievements.AchievementsScreen
import com.hasyame.marvelchampions.ui.campaign.CampaignRecordScreen
import com.hasyame.marvelchampions.ui.campaign.CampaignRunScreen
import com.hasyame.marvelchampions.ui.campaign.CampaignScreen
import com.hasyame.marvelchampions.ui.campaign.StartCampaignScreen
import com.hasyame.marvelchampions.ui.cards.CardDetailScreen
import com.hasyame.marvelchampions.ui.cards.CardsScreen
import com.hasyame.marvelchampions.ui.collection.CollectionScreen
import com.hasyame.marvelchampions.ui.decks.DeckScreen
import com.hasyame.marvelchampions.ui.decks.DecksScreen
import com.hasyame.marvelchampions.ui.decks.NewDeckScreen
import com.hasyame.marvelchampions.ui.draft.DraftScreen
import com.hasyame.marvelchampions.ui.history.HistoryScreen
import com.hasyame.marvelchampions.ui.history.PlayDetailScreen
import com.hasyame.marvelchampions.ui.home.HomeScreen
import com.hasyame.marvelchampions.ui.play.PlayScreen
import com.hasyame.marvelchampions.ui.plays.GameSessionScreen
import com.hasyame.marvelchampions.ui.plays.PlaysScreen
import com.hasyame.marvelchampions.ui.randomizer.RandomizerScreen
import com.hasyame.marvelchampions.ui.rules.RulesScreen
import com.hasyame.marvelchampions.ui.settings.AboutScreen
import com.hasyame.marvelchampions.ui.settings.SettingsScreen
import com.hasyame.marvelchampions.ui.settings.sync.SyncAccountScreen
import com.hasyame.marvelchampions.ui.versus.VersusScreen

@Composable
fun MarvelChampionsNavHost(
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier,
    sharedLink: String? = null,
    onSharedLinkHandled: () -> Unit = {},
) {
    val prepareChallenge: (com.hasyame.marvelchampions.domain.achievements.AchievementChallenge) -> Unit = { challenge ->
        when (challenge.destination) {
            "campaign" -> navController.navigate(StartCampaignRoute(expert = challenge.expert))
            "draft", "sealed" -> navController.navigate(DraftRoute(sealed = challenge.destination == "sealed"))
            else -> navController.navigate(GameSessionRoute(challengeJson = kotlinx.serialization.json.Json.encodeToString(challenge)))
        }
    }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        navigation<HomeGraph>(startDestination = HomeRoute) {
            composable<HomeRoute> {
                HomeScreen(
                    onSettings = { navController.navigate(SettingsRoute) },
                    // The account page lives in the settings graph; Home stays
                    // lit while it is open, as it does for Settings itself.
                    onAccount = { create -> navController.navigate(SyncAccountRoute(create)) },
                    onHistory = { navController.navigate(HistoryRoute) },
                    onAchievements = { navController.navigate(AchievementsRoute) },
                    // The statistics keep their own graph, left the bar for
                    // this tile; Home stays lit while they are open.
                    onStats = { navController.navigate(StatsGraph) },
                    onRules = { navController.navigate(RulesRoute) },
                    onCollection = { navController.navigate(CollectionRoute) },
                    onRandomGame = { navController.navigate(RandomizerRoute) },
                    onCard = { code -> navController.navigate(CardDetailRoute(code)) },
                )
            }
            composable<HistoryRoute> {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { playId -> navController.navigate(PlayDetailRoute(playId)) },
                )
            }
            composable<AchievementsRoute> {
                AchievementsScreen(
                    onPrepare = prepareChallenge,
                    onBack = { navController.popBackStack() },
                    onCollection = { navController.navigate(CollectionRoute) },
                    onSettings = { navController.navigate(SettingsRoute) },
                    onHistory = { navController.navigate(HistoryRoute) },
                )
            }
            composable<PlayDetailRoute> { entry ->
                PlayDetailScreen(
                    playId = entry.toRoute<PlayDetailRoute>().playId,
                    onBack = { navController.popBackStack() },
                    onPlayAgain = { playId -> navController.navigate(GameSessionRoute(replayId = playId)) },
                    onOpenCampaign = { runId -> navController.navigate(CampaignRecordRoute(runId)) },
                )
            }
            // A card drawn at random opens inside Home's stack, so back
            // returns to the page it came from.
            composable<CardDetailRoute> { entry ->
                CardDetailScreen(
                    code = entry.toRoute<CardDetailRoute>().code,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        navigation<CardsGraph>(startDestination = CardsRoute) {
            composable<CardsRoute> {
                CardsScreen(
                    onSettings = { navController.navigate(SettingsRoute) },
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
                    onDeckImported = { deckId -> navController.navigate(DeckDetailRoute(deckId)) },
                    onBuildDeck = { navController.navigate(NewDeckRoute) },
                    onEditDeck = { deckId -> navController.navigate(DeckDetailRoute(deckId)) },
                    sharedLink = sharedLink,
                    onSharedLinkHandled = onSharedLinkHandled,
                )
            }
            composable<DeckDetailRoute> { entry ->
                DeckScreen(
                    deckId = entry.toRoute<DeckDetailRoute>().deckId,
                    onBack = { navController.popBackStack() },
                    onCardClick = { code -> navController.navigate(CardDetailRoute(code)) },
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
            // The same screen: looking and building are one page now, so an
            // old link to the editor lands on it too.
            composable<DeckEditorRoute> { entry ->
                DeckScreen(
                    deckId = entry.toRoute<DeckEditorRoute>().deckId,
                    onBack = { navController.popBackStack() },
                    onCardClick = { code -> navController.navigate(CardDetailRoute(code)) },
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
                    onResumePausedGame = { pausedId ->
                        navController.navigate(GameSessionRoute(resumeId = pausedId))
                    },
                    onCampaigns = { navController.navigate(CampaignRoute) },
                    onVersus = { navController.navigate(VersusRoute) },
                    onDraft = { navController.navigate(DraftRoute()) },
                    onResumeCampaign = { runId ->
                        navController.navigate(CampaignRunRoute(runId))
                    },
                    onAchievements = { navController.navigate(AchievementsRoute) },
                )
            }
            composable<VersusRoute> {
                VersusScreen(onBack = { navController.popBackStack() })
            }
            composable<DraftRoute> { entry ->
                DraftScreen(
                    initiallySealed = entry.toRoute<DraftRoute>().sealed,
                    onBack = { navController.popBackStack() },
                    // The decks are in the Decks tab like any other; the
                    // draft itself has nothing left to show.
                    onSaved = { navController.popBackStack() },
                    onPlay = { mode, ids ->
                        val decks = ids.joinToString(",")
                        if (mode == "campaign") navController.navigate(StartCampaignRoute(decks))
                        else navController.navigate(GameSessionRoute(deckIds = decks, randomScenario = mode == "random"))
                    },
                    onCardDetail = { code -> navController.navigate(CardDetailRoute(code)) },
                )
            }
            // A card held on the draft table opens here, inside the play graph,
            // so the back gesture returns to the table.
            composable<CardDetailRoute> { entry ->
                CardDetailScreen(
                    code = entry.toRoute<CardDetailRoute>().code,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<RandomizerRoute> {
                RandomizerScreen(
                    onCollection = { navController.navigate(CollectionRoute) },
                    onBack = { navController.popBackStack() },
                    onPlayDraw = { scenario, heroes, modulars, difficulty, standardSet ->
                        navController.navigate(
                            GameSessionRoute(
                                // Was dropped, so the session had every part of
                                // the draw except the scenario it was for, could
                                // not start, and fell back to the setup page.
                                scenarioCode = scenario.takeIf { it.isNotBlank() },
                                difficulty = difficulty.takeIf { it.isNotBlank() },
                                heroes = heroes,
                                modularSets = modulars,
                                standardSet = standardSet.takeIf { it.isNotBlank() },
                                autoStart = true,
                            ),
                        )
                    },
                )
            }
            composable<GameSessionRoute> { entry ->
                val args = entry.toRoute<GameSessionRoute>()
                GameSessionScreen(
                    challengeJson = args.challengeJson,
                    deckIds = args.deckIds,
                    randomScenario = args.randomScenario,
                    scenarioCode = args.scenarioCode,
                    difficulty = args.difficulty,
                    heroes = args.heroes,
                    modularSets = args.modularSets,
                    standardSet = args.standardSet,
                    autoStart = args.autoStart,
                    resumeId = args.resumeId,
                    replayId = args.replayId,
                    onBack = { navController.popBackStack() },
                    onOpenPlays = { navController.navigate(PlaysRoute) },
                    onOpenAchievements = { navController.navigate(AchievementsRoute) },
                )
            }
            composable<PlaysRoute> {
                PlaysScreen(
                    onBack = { navController.popBackStack() },
                    onPlayAgain = { playId ->
                        navController.navigate(GameSessionRoute(replayId = playId))
                    },
                    onAchievements = { navController.navigate(AchievementsRoute) },
                )
            }
            // Reached from the play hub, a game's result and the statistics:
            // it stays in the Play back stack, so the tab keeps its place.
            composable<AchievementsRoute> {
                AchievementsScreen(
                    onPrepare = prepareChallenge,
                    onBack = { navController.popBackStack() },
                    onCollection = { navController.navigate(CollectionRoute) },
                    onSettings = { navController.navigate(SettingsRoute) },
                    onHistory = { navController.navigate(HistoryRoute) },
                )
            }
            composable<CampaignRoute> {
                CampaignScreen(
                    onBack = { navController.popBackStack() },
                    onOpenRun = { runId -> navController.navigate(CampaignRunRoute(runId)) },
                    onOpenRecord = { runId -> navController.navigate(CampaignRecordRoute(runId)) },
                    onStartCampaign = { navController.navigate(StartCampaignRoute()) },
                )
            }
            composable<CampaignRecordRoute> { entry ->
                CampaignRecordScreen(
                    runId = entry.toRoute<CampaignRecordRoute>().runId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<StartCampaignRoute> { entry ->
                StartCampaignScreen(
                    initiallyExpert = entry.toRoute<StartCampaignRoute>().expert,
                    deckIds = entry.toRoute<StartCampaignRoute>().deckIds,
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
                    onAchievements = { navController.navigate(AchievementsRoute) },
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
        // The statistics keep their own graph and back stack, reached from
        // the home page since they left the bar.
        navigation<StatsGraph>(startDestination = PlaysRoute) {
            composable<PlaysRoute> {
                PlaysScreen(
                    onBack = { navController.popBackStack() },
                    // The session lives in the Play graph, and a game started
                    // from the history goes there: the Play tab lights up
                    // while it runs, and back returns to the history.
                    onPlayAgain = { playId ->
                        navController.navigate(GameSessionRoute(replayId = playId))
                    },
                    onAchievements = { navController.navigate(AchievementsRoute) },
                )
            }
            composable<AchievementsRoute> {
                AchievementsScreen(
                    onPrepare = prepareChallenge,
                    onBack = { navController.popBackStack() },
                    onCollection = { navController.navigate(CollectionRoute) },
                    onSettings = { navController.navigate(SettingsRoute) },
                    onHistory = { navController.navigate(HistoryRoute) },
                )
            }
        }
        navigation<SettingsGraph>(startDestination = SettingsRoute) {
            composable<SettingsRoute> {
                SettingsScreen(
                    onOpenCollection = { navController.navigate(CollectionRoute) },
                    onOpenSyncAccount = { navController.navigate(SyncAccountRoute()) },
                    onOpenAbout = { navController.navigate(AboutRoute) },
                )
            }
            composable<CollectionRoute> {
                CollectionScreen(onBack = { navController.popBackStack() })
            }
            composable<SyncAccountRoute> { entry ->
                SyncAccountScreen(
                    onBack = { navController.popBackStack() },
                    startOnCreate = entry.toRoute<SyncAccountRoute>().create,
                )
            }
            composable<AboutRoute> {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
