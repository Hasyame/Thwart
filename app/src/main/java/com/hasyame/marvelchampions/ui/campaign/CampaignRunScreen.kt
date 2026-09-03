package com.hasyame.marvelchampions.ui.campaign

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.domain.play.Encounter
import com.hasyame.marvelchampions.domain.play.EncounterSetup
import com.hasyame.marvelchampions.ui.plays.CorrectTimeDialog
import com.hasyame.marvelchampions.ui.plays.EncounterPanel
import com.hasyame.marvelchampions.ui.util.KeepScreenOn
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.ui.plays.LongBreakPage
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.core.designsystem.component.ComicPanel
import com.hasyame.marvelchampions.core.designsystem.component.comicBurst
import com.hasyame.marvelchampions.core.designsystem.component.halftone
import com.hasyame.marvelchampions.data.repository.CampaignRun
import com.hasyame.marvelchampions.domain.randomizer.Difficulty
import com.hasyame.marvelchampions.ui.util.labelRes
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEngine
import com.hasyame.marvelchampions.domain.campaign.engine.ConditionEvaluator
import com.hasyame.marvelchampions.domain.campaign.engine.EvaluationContext
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState
import com.hasyame.marvelchampions.domain.campaign.engine.amountOf
import com.hasyame.marvelchampions.domain.campaign.template.CounterScope
import com.hasyame.marvelchampions.domain.campaign.template.villainStages
import com.hasyame.marvelchampions.domain.campaign.template.ScenarioTemplate
import com.hasyame.marvelchampions.domain.campaign.template.SetupStep
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignRunScreen(
    runId: String,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit,
    viewModel: CampaignRunViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Told rather than read: the outcome lines are built in the view model,
    // which has no composition to ask.
    val appLanguage = campaignTextLocale
    LaunchedEffect(appLanguage) { viewModel.onAppLanguage(appLanguage) }

    LaunchedEffect(runId) { viewModel.load(runId) }

    LaunchedEffect(state.run?.timer?.isRunning) {
        while (state.run?.timer?.isRunning == true) {
            viewModel.tick()
            delay(1_000)
        }
    }

    val run = state.run
    val scenario = run?.template?.scenarios?.firstOrNull { it.id == run.state.currentScenarioId }

    Scaffold(
        topBar = {
            TopAppBar(
            colors = comicTopBarColors(),
                title = {
                    Text(
                        scenario?.name?.resolve(campaignTextLocale)?.takeIf { it.isNotBlank() }
                            ?: run?.entity?.name.orEmpty(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { CircularProgressIndicator() }

            run == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { Text(stringResource(R.string.campaign_run_not_found)) }

            // The campaign tab is the part of the app that is looked at rather
            // than read, so it gets the printed-paper texture. Card and deck
            // lists deliberately stay plain.
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .halftone(MaterialTheme.colorScheme.onBackground),
            ) {
                when (state.page) {
                    RunPage.BRIEFING -> BriefingPage(
                        run = run,
                        scenario = scenario,
                        onReady = viewModel::beginScenario,
                        onNotReady = onBack,
                        onCardClick = onCardClick,
                        onSetupAction = viewModel::takeSetupAction,
                        onKeepCard = viewModel::keepDrawnCard,
                    )

                    // Written down rather than navigated to, exactly as a
                    // standalone game does it: the scenario has not moved on,
                    // it is being recorded, and cancelling puts the table back.
                    RunPage.PLAYING if state.longBreak != null -> LongBreakPage(
                        draft = requireNotNull(state.longBreak),
                        heroes = run.state.heroes.map { it.heroCardCode to it.name },
                        tracked = state.trackEncounter,
                        photos = emptyList(),
                        photoStore = viewModel.photoStore,
                        onDraft = viewModel::updateLongBreak,
                        onPhoto = { },
                        onRemovePhoto = { },
                        onCancel = viewModel::cancelLongBreak,
                        onSave = { viewModel.saveLongBreak(onBack) },
                    )

                    RunPage.PLAYING -> PlayingPage(
                        isSubmitting = state.isSubmitting,
                        run = run,
                        scenario = scenario,
                        elapsedMillis = state.elapsedMillis,
                        onVictory = viewModel::declareVictory,
                        onDefeat = viewModel::declareDefeat,
                        onPause = viewModel::pauseTimer,
                        onResume = viewModel::resumeTimer,
                        onLongBreak = viewModel::beginLongBreak,
                        onCorrectTime = viewModel::setElapsed,
                        trackEncounter = state.trackEncounter,
                        trackerWanted = state.trackerWanted,
                        encounter = state.encounter,
                        keepAwake = state.keepAwake,
                        onDamageVillain = viewModel::damageVillain,
                        onChangeThreat = viewModel::changeThreat,
                        onAdvanceVillain = viewModel::advanceVillain,
                        onAdvanceScheme = viewModel::advanceScheme,
                        onEndRound = viewModel::endRound,
                        onKeepAwake = viewModel::setKeepAwake,
                    )

                    RunPage.QUESTIONS -> QuestionsPage(
                        isSubmitting = state.isSubmitting,
                        run = run,
                        scenario = scenario,
                        onCardClick = onCardClick,
                        onSubmit = viewModel::submitAnswers,
                    )

                    RunPage.RESULT -> ResultPage(
                        run = run,
                        summary = state.summary,
                        onNext = viewModel::continueFromOutcome,
                        onConcede = viewModel::concedeCampaign,
                        onMarket = { viewModel.goTo(RunPage.MARKET) },
                        onBreak = onBack,
                        onForget = { viewModel.forgetCampaign(onBack) },
                    )

                    RunPage.DEFEAT -> DefeatPage(
                        summary = state.summary,
                        onRetry = viewModel::retryScenario,
                        onContinue = viewModel::continueFromOutcome,
                        onBreak = onBack,
                        onConcede = viewModel::concedeCampaign,
                    )

                    RunPage.CHOICE -> ChoosePage(
                        run = run,
                        onChoose = viewModel::chooseScenario,
                        onBreak = onBack,
                    )

                    RunPage.ENVIRONMENT -> EnvironmentPage(
                        run = run,
                        onContinue = viewModel::acknowledgeEnvironments,
                        onBreak = onBack,
                    )

                    RunPage.CAMPAIGN_LOST -> CampaignLostPage(
                        run = run,
                        onLeave = onBack,
                    )

                    RunPage.MARKET -> MarketPage(
                        run = run,
                        onBuy = viewModel::purchase,
                        onRefund = viewModel::refund,
                        onCardClick = onCardClick,
                        onDone = { viewModel.goTo(RunPage.BRIEFING) },
                    )
                }
            }
        }
    }
}

/**
 * The language the app is being displayed in.
 *
 * What a campaign template says follows this, not the card language: those are
 * two settings on purpose, and a French card list is a normal thing to read
 * with an English app. Card names are unaffected — they come from the card
 * database, in the card language, wherever they are shown.
 *
 * Read from the configuration rather than from settings so it is whatever the
 * app is really showing, including "follow the phone".
 */
private val campaignTextLocale: String
    @Composable get() = LocalConfiguration.current.locales[0].language

/** Ticks a job carries before the villains have taken it for good. */
private const val FALLEN_PRESSURE = 3

/** Page 1. Title, story, what to put on the table. */
@Composable
private fun BriefingPage(
    run: CampaignRun,
    scenario: ScenarioTemplate?,
    onReady: () -> Unit,
    onNotReady: () -> Unit,
    onCardClick: (String) -> Unit,
    onSetupAction: (String, String?) -> Unit,
    onKeepCard: (String, String) -> Unit,
) {
    if (scenario == null) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.campaign_complete))
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The story sits in a caption box, which is both what a comic does with
        // narration and what makes it readable: italic body text straight on
        // the halftone had the dots showing through every line.
        scenario.flavour?.resolve(campaignTextLocale)?.takeIf { it.isNotBlank() }?.let {
            ComicPanel(Modifier.fillMaxWidth()) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        DifficultySets(run)

        // Card chips only. A campaign whose cards are in no database — Fear
        // No Evil — fills none of them, and an empty titled box is worse than
        // no box, so the whole panel goes.
        scenario.baseSetup
            ?.takeIf { setup ->
                setup.villainStages(run.state.difficulty, null).isNotEmpty() ||
                    setup.mainScheme.isNotEmpty() ||
                    setup.encounterSets.isNotEmpty() ||
                    setup.modularSets.isNotEmpty()
            }
            ?.let { setup ->
            ComicPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.campaign_pre_setup),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    setup.villainStages(
                        run.state.difficulty,
                        setup.villainDeckFromDraw?.let { drawId ->
                            CampaignEngine.drawnCards(
                                run.state,
                                run.state.currentScenarioId,
                                drawId,
                            ).firstOrNull()
                        },
                    ).takeIf { it.isNotEmpty() }?.let {
                        CardChips(
                            label = stringResource(R.string.campaign_villain_deck_label),
                            codes = it,
                            run = run,
                            onCardClick = onCardClick,
                        )
                    }
                    setup.mainScheme.takeIf { it.isNotEmpty() }?.let {
                        CardChips(
                            label = stringResource(R.string.campaign_main_scheme_label),
                            codes = it,
                            run = run,
                            onCardClick = onCardClick,
                        )
                    }
                    (setup.encounterSets + setup.modularSets).takeIf { it.isNotEmpty() }?.let { sets ->
                        Column {
                            Text(
                                text = stringResource(R.string.campaign_encounter_sets_label),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(sets.joinToString(", ") { run.names.set(it) })
                        }
                    }
                }
            }
        }

        SetupPanel(
            title = stringResource(R.string.campaign_pre_setup),
            steps = scenario.preSetup,
            run = run,
            scenarioId = scenario.id,
            onCardClick = onCardClick,
            onSetupAction = onSetupAction,
            onKeepCard = onKeepCard,
        )

        SetupPanel(
            title = stringResource(R.string.campaign_setup_label),
            steps = scenario.campaignSetup,
            run = run,
            scenarioId = scenario.id,
            onCardClick = onCardClick,
            onSetupAction = onSetupAction,
            onKeepCard = onKeepCard,
        )

        SetupPanel(
            title = stringResource(R.string.campaign_information),
            steps = scenario.information,
            run = run,
            scenarioId = scenario.id,
            onCardClick = onCardClick,
            onSetupAction = onSetupAction,
            onKeepCard = onKeepCard,
        )

        // Last, because that is the order it is done in: gather the cards, apply
        // whatever the campaign changes, then follow the setup printed on the
        // scheme itself. Read off the card rather than written into the
        // template, so it arrives in the language the cards are in.
        scenario.baseSetup?.let { setup ->
            run.names.setup(setup.mainScheme).takeIf { it.isNotEmpty() }?.let { steps ->
                ComicPanel(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.campaign_scheme_setup),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        steps.forEach { step -> Text(campaignText("• $step")) }
                    }
                }
            }
        }

        Button(onClick = onReady, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.campaign_im_ready))
        }
        OutlinedButton(onClick = onNotReady, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.campaign_not_ready))
        }
    }
}

@Composable
private fun CardChips(
    label: String,
    codes: List<String>,
    run: CampaignRun,
    onCardClick: (String) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            codes.forEach { code ->
                AssistChip(
                    onClick = { onCardClick(code) },
                    label = { Text(run.names.card(code)) },
                )
            }
        }
    }
}


/**
 * The encounter sets the difficulty is made of, named on every briefing.
 *
 * Stated here because the app may have drawn them: a table that asked for a
 * set at random has no other way of knowing which one it is playing, and it is
 * the same one every game of the run.
 */
@Composable
private fun DifficultySets(run: CampaignRun) {
    val names = listOf(run.entity.expertSet, run.entity.standardSet)
        .filter { it.isNotBlank() }
        .mapNotNull { stored ->
            // Reading a stored name back, not offering a menu: which sets a
            // table may pick from was settled by the collection at the start.
            Difficulty.entries.firstOrNull { it.name.lowercase() == stored }
        }
        .map { stringResource(it.labelRes()) }
    if (names.isEmpty()) {
        return
    }
    ComicPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.campaign_difficulty_sets),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(names.joinToString(" + "))
        }
    }
}

/**
 * One titled panel of setup steps.
 *
 * The briefing is three of these: what goes on the table before the game,
 * the setup itself, and anything worth knowing once it is laid out. They
 * render identically, so they are one composable — a step that carries a
 * counter, a draw or a button behaves the same wherever it is listed.
 */
@Composable
private fun SetupPanel(
    title: String,
    steps: List<SetupStep>,
    run: CampaignRun,
    scenarioId: String?,
    onCardClick: (String) -> Unit,
    onSetupAction: (String, String?) -> Unit,
    onKeepCard: (String, String) -> Unit,
) {
    if (steps.isEmpty()) {
        return
    }
        val context = EvaluationContext(run.state, scenarioId)
        ComicPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                steps
                    .filter { ConditionEvaluator.evaluate(it.condition, context) }
                    // A step whose worked-out amount comes to nothing does not
                    // apply this time. Printing "place 0 threat" would leave
                    // the table hunting for something to do.
                    .filter { run.state.amountOf(it.compute) != 0 }
                    // A step can exist only to carry a draw, with the steps
                    // that read it saying everything. Rendering its empty
                    // text would put a bullet with nothing after it on the
                    // briefing.
                    .filter { it.text.resolve(campaignTextLocale).isNotBlank() }
                    .forEach { step ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                campaignText(
                                    "• " + resolveDraws(
                                        resolveAmount(
                                            step.text.resolve(campaignTextLocale),
                                            run.state.amountOf(step.compute),
                                        ),
                                        run,
                                        run.state.currentScenarioId,
                                    ),
                                ),
                            )

                            // Values the campaign log carries forward from
                            // earlier scenarios, so the step can be
                            // followed without leafing back through it.
                            step.showCounter?.let { counterId ->
                                val perHero = run.template.counters
                                    .firstOrNull { it.id == counterId }
                                    ?.counterScope == CounterScope.HERO
                                Text(
                                    text = if (perHero) {
                                        run.state.heroes.joinToString("   ") {
                                            "${it.name} ${run.state.heroCounter(counterId, it.id)}"
                                        }
                                    } else {
                                        // The number alone. Prefixing it with
                                        // the counter's id put "pincerThreat"
                                        // in front of a player who has no
                                        // reason to know the app calls it that;
                                        // the step's own text says what it is.
                                        run.state.counter(counterId).toString()
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            step.showCardList?.let { listId ->
                                val recorded = run.state.cardLists[listId].orEmpty()
                                Text(
                                    // Entries may be card codes or free
                                    // text; a code resolves to its name,
                                    // anything else shows as typed.
                                    text = recorded.takeIf { it.isNotEmpty() }
                                        ?.joinToString(", ") { run.names.card(it) }
                                        ?: stringResource(R.string.campaign_nothing_recorded),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            step.showHeroesWith?.let { counterId ->
                                val holders = run.state.heroes
                                    .filter { run.state.heroCounter(counterId, it.id) > 0 }
                                Text(
                                    text = holders.takeIf { it.isNotEmpty() }
                                        ?.joinToString(", ") { it.name }
                                        ?: stringResource(R.string.campaign_nobody),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }

                            // A drawn step shows what came up and nothing
                            // else. Listing the whole pool beside it would
                            // put the player back to picking one, which is
                            // the job the app just did for them.
                            val draw = step.draw
                            if (draw != null && draw.perHero) {
                                // Dealt to each player in turn, so it is shown
                                // that way: a table of three has three rows and
                                // three separate decisions.
                                run.state.heroes.forEach { hero ->
                                    val heroDrawId = CampaignEngine.heroDrawId(draw.id, hero.id)
                                    DrawnCards(
                                        label = hero.name,
                                        codes = CampaignEngine.drawnCards(
                                            run.state,
                                            run.state.currentScenarioId,
                                            heroDrawId,
                                        ),
                                        offered = draw.offer,
                                        run = run,
                                        onKeep = { code -> onKeepCard(heroDrawId, code) },
                                        onCardClick = onCardClick,
                                    )
                                }
                            } else {
                                DrawnCards(
                                    label = null,
                                    codes = draw?.let {
                                        CampaignEngine.drawnCards(
                                            run.state,
                                            run.state.currentScenarioId,
                                            it.id,
                                        )
                                    } ?: step.cards,
                                    offered = draw?.offer ?: 0,
                                    run = run,
                                    onKeep = { code -> draw?.let { onKeepCard(it.id, code) } },
                                    onCardClick = onCardClick,
                                )
                            }
                            step.action?.let { action ->
                                val enabled =
                                    ConditionEvaluator.evaluate(action.enabledWhen, context)
                                if (action.perHero) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        run.state.heroes.forEach { hero ->
                                            OutlinedButton(
                                                onClick = { onSetupAction(action.id, hero.id) },
                                                enabled = enabled,
                                            ) {
                                                Text(
                                                    action.label.resolve(campaignTextLocale) +
                                                        " — " + hero.name,
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onSetupAction(action.id, null) },
                                        enabled = enabled,
                                    ) { Text(action.label.resolve(campaignTextLocale)) }
                                }
                            }
                        }
                    }
            }
        }
}

/**
 * The cards a draw came up with, as chips.
 *
 * An offer still waiting on the table is tapped to keep one, which returns the
 * others to the pool. Once kept there is a single chip left and it reads as an
 * ordinary card reference again. [label] names the player when the draw was
 * dealt to each of them separately.
 */
@Composable
private fun DrawnCards(
    label: String?,
    codes: List<String>,
    offered: Int,
    run: CampaignRun,
    onKeep: (String) -> Unit,
    onCardClick: (String) -> Unit,
) {
    if (codes.isEmpty()) {
        return
    }
    val undecided = offered > 0 && codes.size > 1
    if (label != null) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (undecided) {
        Text(
            text = stringResource(R.string.campaign_choose_one),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        codes.forEach { code ->
            AssistChip(
                onClick = { if (undecided) onKeep(code) else onCardClick(code) },
                label = { Text(run.names.card(code)) },
            )
        }
    }
}

/** Page 2. The clock, and the two ways a scenario ends. */
@Composable
private fun PlayingPage(
    isSubmitting: Boolean = false,
    run: CampaignRun,
    scenario: ScenarioTemplate?,
    elapsedMillis: Long,
    onVictory: () -> Unit,
    onDefeat: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onLongBreak: () -> Unit,
    onCorrectTime: (Long) -> Unit = {},
    trackEncounter: Boolean = false,
    trackerWanted: Boolean = false,
    encounter: Encounter = Encounter.startOf(EncounterSetup()),
    keepAwake: Boolean = true,
    onDamageVillain: (Int) -> Unit = {},
    onChangeThreat: (Int, Int) -> Unit = { _, _ -> },
    onAdvanceVillain: () -> Unit = {},
    onAdvanceScheme: () -> Unit = {},
    onEndRound: () -> Unit = {},
    onKeepAwake: (Boolean) -> Unit = {},
) {
    var correctingTime by remember { mutableStateOf(false) }

    if (correctingTime) {
        CorrectTimeDialog(
            elapsedMillis = elapsedMillis,
            onDismiss = { correctingTime = false },
            onConfirm = {
                onCorrectTime(it)
                correctingTime = false
            },
        )
    }

    Column(
        // Scrollable since the tracker joined it: the clock and two buttons
        // fit any screen, the counters do not.
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = scenario?.name?.resolve(campaignTextLocale).orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
        )
        // The clock is the control, exactly as in a one-off game. The stop
        // button gets forgotten in a campaign as easily as anywhere else, and
        // the time was the one thing here that could not be put right.
        Text(
            text = TimerState.format(elapsedMillis),
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.clickable(enabled = !isSubmitting) { correctingTime = true },
        )
        Text(
            text = stringResource(R.string.session_tap_to_correct),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = if (run.timer.isRunning) onPause else onResume) {
            Text(
                stringResource(
                    if (run.timer.isRunning) {
                        R.string.campaign_pause
                    } else {
                        R.string.campaign_play
                    },
                ),
            )
        }

        // The other kind of stopping: not the clock, the table. A campaign
        // scenario gets cleared off a table for the same reasons as any other
        // game, and had no way to say so.
        OutlinedButton(
            onClick = onLongBreak,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.session_long_break)) }

        if (trackEncounter) {
            if (keepAwake) {
                KeepScreenOn()
            }
            EncounterPanel(
                encounter = encounter,
                enabled = !isSubmitting,
                keepAwake = keepAwake,
                onDamageVillain = onDamageVillain,
                onChangeThreat = onChangeThreat,
                onAdvanceVillain = onAdvanceVillain,
                onAdvanceScheme = onAdvanceScheme,
                onEndRound = onEndRound,
                onKeepAwake = onKeepAwake,
            )
        } else if (trackerWanted) {
            // Only when the player asked for the tracker. Someone who switched
            // it off does not need telling on every scenario; someone who
            // switched it on and got nothing does, because the alternative is
            // an empty space that reads as a bug.
            Text(
                text = stringResource(R.string.campaign_tracker_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Button(
            onClick = onVictory,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                scenario?.victoryLabel?.resolve(campaignTextLocale)?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.campaign_victory),
            )
        }
        OutlinedButton(
            onClick = onDefeat,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                scenario?.defeatLabel?.resolve(campaignTextLocale)?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.campaign_defeat),
            )
        }
    }
}

/** Page 4. What the scenario awarded, and the three ways onward. */
@Composable
private fun ResultPage(
    run: CampaignRun,
    summary: ScenarioOutcomeSummary?,
    onNext: () -> Unit,
    onConcede: (() -> Unit)? = null,
    onMarket: () -> Unit,
    onBreak: () -> Unit,
    onForget: () -> Unit,
) {
    var confirmForget by remember { mutableStateOf(false) }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text(stringResource(R.string.campaign_delete_title)) },
            text = { Text(stringResource(R.string.campaign_forget_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmForget = false
                        onForget()
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The payoff moment of the whole app, which used to be one line of
        // ordinary heading text above a great deal of empty screen. The burst
        // is drawn behind this headline and nowhere else in the app: it reads
        // as "this is the moment" precisely because it is not reused.
        val burstScale by animateFloatAsState(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "victory-burst",
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                // drawBehind is not clipped to the node, so without this the
                // spokes ran the whole height of the screen and sat behind the
                // buttons. The burst belongs to the headline, not the page.
                .clipToBounds()
                .scale(burstScale)
                .comicBurst(MaterialTheme.colorScheme.primary),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.campaign_bravo),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }

        val gained = summary?.creditsGained.orEmpty()
        val distinct = gained.values.distinct()
        // Prose over halftone is the one thing the texture spoils, so what the
        // scenario awarded goes in a caption box like the story does.
        ComicPanel(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Credits are worth mentioning only while there is still a
                // scenario left to spend them on. Congratulating somebody on
                // nought of a currency the campaign has just retired is noise.
                val hasCredits = run.template.market != null &&
                    summary?.campaignFinished != true
                Text(
                    text = if (hasCredits && distinct.size == 1 && run.state.heroes.size == 1) {
                        pluralStringResource(
                            R.plurals.campaign_result_solo,
                            distinct.single(),
                            distinct.single(),
                            TimerState.format(summary?.elapsedMillis ?: 0),
                            summary?.victoryPoints ?: 0,
                        )
                    } else {
                        pluralStringResource(
                            R.plurals.campaign_result_group,
                            summary?.victoryPoints ?: 0,
                            TimerState.format(summary?.elapsedMillis ?: 0),
                            summary?.victoryPoints ?: 0,
                        )
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )

                if (hasCredits && run.state.heroes.size > 1) {
                    run.state.heroes.forEach { hero ->
                        Text("${hero.name}: +${gained[hero.id] ?: 0}")
                    }
                }
                summary?.outcomeMessage?.let { Text(campaignText(it)) }
            }
        }

        HorizontalDivider()

        if (summary?.campaignFinished == true) {
            Text(
                text = stringResource(R.string.campaign_complete),
                style = MaterialTheme.typography.titleMedium,
            )
            // The whole campaign's figures, here, at the moment it ends. Making
            // somebody navigate to a record screen to find out how their
            // campaign went is asking them to go looking for their own reward.
            CampaignTotals(run)
            Text(
                text = stringResource(R.string.campaign_keep_or_forget),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onBreak, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.campaign_save_it))
            }
            OutlinedButton(
                onClick = { confirmForget = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.campaign_forget_it)) }
        } else {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        R.string.campaign_go_to_next,
                        summary?.nextScenarioName.orEmpty(),
                    ),
                )
            }
        }
        if (run.template.market != null) {
            OutlinedButton(onClick = onMarket, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.campaign_need_to_buy))
            }
        }
        // Stopping is a decision after any scenario, won or lost, so long as
        // there is still a campaign left to stop.
        if (summary?.campaignFinished != true) {
            onConcede?.let { concede ->
                OutlinedButton(onClick = concede, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.campaign_stop_campaign),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        OutlinedButton(onClick = onBreak, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.campaign_take_a_break))
        }
    }
}

/** Page 3, the other branch. */
@Composable
private fun DefeatPage(
    summary: ScenarioOutcomeSummary?,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    onBreak: () -> Unit,
    onConcede: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.campaign_defeat_recorded),
            style = MaterialTheme.typography.headlineMedium,
        )
        ComicPanel(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(
                        R.string.campaign_defeat_time,
                        TimerState.format(summary?.elapsedMillis ?: 0),
                    ),
                )
                // What the loss did to the campaign, in the campaign's own
                // terms: which villain, which environment, what it costs.
                summary?.outcomeMessage?.let { Text(campaignText(it)) }
            }
        }
        // Retrying settles nothing, so it comes first: the job is still
        // there to be won until the players decide otherwise.
        // Each way out says what it costs. Some of these are hard to undo and
        // one of them ends the campaign, so none of them should be a guess.
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.campaign_retry))
        }
        // Only where the campaign has a rule for it. Fear No Evil lets a
        // failed job leave the campaign; everywhere else a defeat is replayed
        // or the campaign is given up, and there is no third thing.
        if (summary?.canContinue == true) {
            WayOut(
                label = stringResource(R.string.campaign_continue),
                detail = stringResource(R.string.campaign_continue_detail),
                onClick = onContinue,
            )
        }
        WayOut(
            label = stringResource(R.string.campaign_take_a_break),
            detail = stringResource(R.string.campaign_break_detail),
            onClick = onBreak,
        )
        // Offered only at the last villain, who may be tried again as often as
        // a table can stand. Stopping there is a decision, so it is a button
        // rather than something the app concludes on their behalf.
        onConcede?.let { concede ->
            WayOut(
                label = stringResource(R.string.campaign_stop_campaign),
                detail = stringResource(R.string.campaign_stop_detail),
                onClick = concede,
                colour = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * A way off this page, with what taking it does written underneath.
 *
 * The ways out of a defeat are not interchangeable: one settles the job,
 * one ends the campaign, and a row of bare verbs made a table guess which
 * was which.
 */
@Composable
private fun WayOut(
    label: String,
    detail: String,
    onClick: () -> Unit,
    colour: Color = MaterialTheme.colorScheme.onSurface,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = label, color = colour)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * What a whole campaign came to, folded from its own event log.
 *
 * The finished-campaign record shows the same figures later; this is the copy
 * the player sees the moment the last villain goes down.
 */
@Composable
private fun CampaignTotals(run: CampaignRun) {
    val results = run.state.completedScenarios
    val won = results.count { it.victory }
    val played = results.size
    val points = results.filter { it.victory }.sumOf { it.answers.numbers["vp"] ?: 0 }

    ComicPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.campaign_finished_message))
            Text("${stringResource(R.string.campaign_stat_time)}: ${TimerState.format(run.state.totalPlayTimeMillis)}")
            Text("${stringResource(R.string.campaign_stat_vp)}: $points")
            Text("${stringResource(R.string.campaign_stat_heroes)}: ${run.state.heroes.joinToString(", ") { it.name }}")
            Text("${stringResource(R.string.campaign_stat_scenarios)}: $played")
            Text("${stringResource(R.string.campaign_stat_wins)}: $won")
            Text("${stringResource(R.string.campaign_stat_defeats)}: ${played - won}")
            Text(
                text = "${stringResource(R.string.campaign_stat_winrate)}: " +
                    "${if (played > 0) won * 100 / played else 0}%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.campaign_finished_cleanup),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Null and zero mean the same thing here: a draw that decides for itself. */


/**
 * Which scenario to play next, when the campaign leaves that to the table.
 *
 * Only what has not been played is offered, and the finale is held back until
 * it is all that is left — a campaign that let you open on its last scenario
 * would not be a campaign.
 */
/**
 * The rotation opens here: two places the villains hit, and the players keep
 * one of them.
 *
 * Both were pushed the moment they were dealt, and that is the whole of it —
 * the rules draw two and tick the jobs they name. Nothing is kept here. An
 * environment leaves the pile by its job being won or pushed over, and the one
 * that ends up on the table is the one belonging to the job the players go on
 * to choose.
 *
 * When only one is left in the pile it is dealt alone and takes the tick twice,
 * which is how a campaign that has run long closes in on itself.
 */
@Composable
private fun EnvironmentPage(
    run: CampaignRun,
    onContinue: () -> Unit,
    onBreak: () -> Unit,
) {
    val offered = run.state.environmentOffer
    val lone = offered.size == 1

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(
                if (lone) R.string.campaign_environment_last else R.string.campaign_environment_title,
            ),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        offered.forEach { environmentId ->
            val scenario = run.template.scenarios.firstOrNull { it.id == environmentId }
            ComicPanel(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = scenario?.name?.resolve(campaignTextLocale).orEmpty()
                            .ifBlank { run.names.card(environmentId) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            if (lone) {
                                R.string.campaign_environment_pushed_twice
                            } else {
                                R.string.campaign_environment_pushed
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.campaign_continue))
        }

        // The whole board, so the choice is made against what it costs rather
        // than against two names in isolation.
        HorizontalDivider()
        Text(
            text = stringResource(R.string.campaign_pressure_board),
            style = MaterialTheme.typography.titleSmall,
        )
        run.template.scenarios.forEach { scenario ->
            val counterId = scenario.pressureCounterId ?: return@forEach
            val pressure = run.state.counter(counterId)
            // Won is ACHIEVED, pushed over is FAILED, and anything else is
            // still in the pile however often it has been played and lost.
            val won = run.state.completedScenarios.any {
                it.scenarioId == scenario.id && it.victory
            }
            val fallen = pressure >= FALLEN_PRESSURE
            val achieved = stringResource(R.string.campaign_environment_achieved)
            val failed = stringResource(R.string.campaign_environment_failed)
            val standing = stringResource(R.string.campaign_pressure, pressure)
            Text(
                // The count stays visible on a job that has fallen — three of
                // three is how it got there — with the face it turned over set
                // apart, because that is the line worth spotting at a glance.
                text = buildAnnotatedString {
                    append(scenario.name?.resolve(campaignTextLocale).orEmpty())
                    append("   ")
                    append(standing)
                    val tag = when {
                        won -> achieved
                        fallen -> failed
                        else -> null
                    }
                    if (tag != null) {
                        append("   ")
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                            ),
                        ) { append(tag) }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    won || fallen -> MaterialTheme.colorScheme.onSurfaceVariant
                    pressure >= 2 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }

        OutlinedButton(onClick = onBreak, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.campaign_take_a_break))
        }
    }
}

/**
 * A place fell, and the campaign fell with it.
 *
 * No replay offered: the run is over and already recorded as a defeat. The
 * screen says which place went, because that is the thing the table will argue
 * about afterwards.
 */
@Composable
private fun CampaignLostPage(
    run: CampaignRun,
    onLeave: () -> Unit,
) {
    val fallen = run.template.scenarios.filter { scenario ->
        scenario.pressureCounterId?.let { run.state.counter(it) >= 3 } == true
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.campaign_lost_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.campaign_lost_message),
            style = MaterialTheme.typography.bodyMedium,
        )
        fallen.forEach { scenario ->
            Text(
                text = "• " + scenario.name?.resolve(campaignTextLocale).orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.campaign_lost_leave))
        }
    }
}

@Composable
private fun ChoosePage(
    run: CampaignRun,
    onChoose: (String) -> Unit,
    onBreak: () -> Unit,
) {
    val choices = CampaignEngine.choosableScenarios(run.template, run.state)

    // Which places the villains pushed this round, drawn by the app before the
    // table decides. Named from the scenarios themselves, so the header reads
    // in whatever language the campaign does.
    val struckThisRound = CampaignEngine.drawnCards(
        run.state,
        CampaignEngine.ENVIRONMENT_DRAW_SCENARIO,
        "r${run.state.completedScenarios.size}",
    ).mapNotNull { id ->
        run.template.scenarios.firstOrNull { it.id == id }?.name?.resolve(campaignTextLocale)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.campaign_choose_scenario),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        if (struckThisRound.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.campaign_villains_struck,
                    struckThisRound.joinToString(" · "),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        choices.forEach { scenario ->
            // How close this place is to falling, so the table can weigh where
            // to spend its one game. Zero shows nothing; three cannot be chosen
            // because a failed scenario is no longer offered.
            val pressure = scenario.pressureCounterId?.let { run.state.counter(it) } ?: 0

            ComicPanel(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onChoose(scenario.id) }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = scenario.name?.resolve(campaignTextLocale).orEmpty().ifBlank { scenario.id },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (pressure > 0) {
                        Text(
                            text = stringResource(
                                if (pressure >= 2) {
                                    R.string.campaign_pressure_critical
                                } else {
                                    R.string.campaign_pressure
                                },
                                pressure,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pressure >= 2) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    scenario.flavour?.resolve(campaignTextLocale)?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        OutlinedButton(onClick = onBreak, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.campaign_take_a_break))
        }
    }
}
