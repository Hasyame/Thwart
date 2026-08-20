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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.core.designsystem.component.ComicPanel
import com.hasyame.marvelchampions.core.designsystem.component.comicBurst
import com.hasyame.marvelchampions.core.designsystem.component.halftone
import com.hasyame.marvelchampions.data.repository.CampaignRun
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEngine
import com.hasyame.marvelchampions.domain.campaign.engine.ConditionEvaluator
import com.hasyame.marvelchampions.domain.campaign.engine.EvaluationContext
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState
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
                        scenario?.name?.resolve(run.localeCode)?.takeIf { it.isNotBlank() }
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

                    RunPage.PLAYING -> PlayingPage(
                        isSubmitting = state.isSubmitting,
                        run = run,
                        scenario = scenario,
                        elapsedMillis = state.elapsedMillis,
                        onVictory = viewModel::declareVictory,
                        onDefeat = viewModel::declareDefeat,
                        onPause = viewModel::pauseTimer,
                        onResume = viewModel::resumeTimer,
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
                        onNext = viewModel::continueToNextScenario,
                        onMarket = { viewModel.goTo(RunPage.MARKET) },
                        onBreak = onBack,
                        onForget = { viewModel.forgetCampaign(onBack) },
                    )

                    RunPage.DEFEAT -> DefeatPage(
                        summary = state.summary,
                        onRestart = viewModel::replayScenario,
                        onBreak = onBack,
                        // Only the last villain can be retried for ever, so
                        // only there is stopping a decision worth offering.
                        onConcede = if (
                            run.state.currentScenarioId == run.template.finaleScenarioId
                        ) {
                            viewModel::concedeCampaign
                        } else {
                            null
                        },
                    )

                    RunPage.CHOICE -> ChoosePage(
                        run = run,
                        onChoose = viewModel::chooseScenario,
                        onBreak = onBack,
                    )

                    RunPage.ENVIRONMENT -> EnvironmentPage(
                        run = run,
                        onChoose = viewModel::chooseEnvironment,
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
        scenario.flavour?.resolve(run.localeCode)?.takeIf { it.isNotBlank() }?.let {
            ComicPanel(Modifier.fillMaxWidth()) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

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
                    // A step can exist only to carry a draw, with the steps
                    // that read it saying everything. Rendering its empty
                    // text would put a bullet with nothing after it on the
                    // briefing.
                    .filter { it.text.resolve(run.localeCode).isNotBlank() }
                    .forEach { step ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                campaignText(
                                    "• " + resolveDraws(
                                        step.text.resolve(run.localeCode),
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
                            val chips = step.draw?.let {
                                CampaignEngine.drawnCards(
                                    run.state,
                                    run.state.currentScenarioId,
                                    it.id,
                                )
                            } ?: step.cards

                            // An offer still waiting on the table: tapping a
                            // card keeps it and returns the others to the
                            // pool. Once kept there is only one chip left,
                            // so this reads as an ordinary reference again.
                            val undecided = step.draw?.offer.orEmpty0() > 0 && chips.size > 1

                            if (chips.isNotEmpty()) {
                                if (undecided) {
                                    Text(
                                        text = stringResource(R.string.campaign_choose_one),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    chips.forEach { code ->
                                        AssistChip(
                                            onClick = {
                                                if (undecided) {
                                                    onKeepCard(step.draw!!.id, code)
                                                } else {
                                                    onCardClick(code)
                                                }
                                            },
                                            label = { Text(run.names.card(code)) },
                                        )
                                    }
                                }
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
                                                    action.label.resolve(run.localeCode) +
                                                        " — " + hero.name,
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onSetupAction(action.id, null) },
                                        enabled = enabled,
                                    ) { Text(action.label.resolve(run.localeCode)) }
                                }
                            }
                        }
                    }
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
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = scenario?.name?.resolve(run.localeCode).orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = TimerState.format(elapsedMillis),
            style = MaterialTheme.typography.displayLarge,
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

        Button(
            onClick = onVictory,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                scenario?.victoryLabel?.resolve(run.localeCode)?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.campaign_victory),
            )
        }
        OutlinedButton(
            onClick = onDefeat,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                scenario?.defeatLabel?.resolve(run.localeCode)?.takeIf { it.isNotBlank() }
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
        OutlinedButton(onClick = onBreak, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.campaign_take_a_break))
        }
    }
}

/** Page 3, the other branch. */
@Composable
private fun DefeatPage(
    summary: ScenarioOutcomeSummary?,
    onRestart: () -> Unit,
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
            Text(
                text = stringResource(
                    R.string.campaign_defeat_time,
                    TimerState.format(summary?.elapsedMillis ?: 0),
                ),
                modifier = Modifier.padding(16.dp),
            )
        }
        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.campaign_all_day))
        }
        OutlinedButton(onClick = onBreak, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.campaign_take_a_break))
        }
        // Offered only at the last villain, who may be tried again as often as
        // a table can stand. Stopping there is a decision, so it is a button
        // rather than something the app concludes on their behalf.
        onConcede?.let { concede ->
            OutlinedButton(onClick = concede, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.campaign_concede),
                    color = MaterialTheme.colorScheme.error,
                )
            }
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
private fun Int?.orEmpty0(): Int = this ?: 0


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
 * Both were pushed the moment they were dealt — the board below already counts
 * it — so what the choice decides is which of the two leaves the pile for good.
 * When only one is left it is dealt alone and takes the pressure twice, which
 * is how a campaign that has run long closes in on itself.
 */
@Composable
private fun EnvironmentPage(
    run: CampaignRun,
    onChoose: (String) -> Unit,
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
                    Modifier
                        .fillMaxWidth()
                        .clickable { onChoose(environmentId) }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = scenario?.name?.resolve(run.localeCode).orEmpty()
                            .ifBlank { run.names.card(environmentId) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.campaign_environment_keep),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
            val played = run.state.completedScenarios.any { it.scenarioId == scenario.id }
            Text(
                text = scenario.name?.resolve(run.localeCode).orEmpty() + "   " +
                    if (played) {
                        stringResource(R.string.campaign_pressure_done)
                    } else {
                        stringResource(R.string.campaign_pressure, pressure)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    played -> MaterialTheme.colorScheme.onSurfaceVariant
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
                text = "• " + scenario.name?.resolve(run.localeCode).orEmpty(),
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
        run.template.scenarios.firstOrNull { it.id == id }?.name?.resolve(run.localeCode)
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
                        text = scenario.name?.resolve(run.localeCode).orEmpty().ifBlank { scenario.id },
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
                    scenario.flavour?.resolve(run.localeCode)?.takeIf { it.isNotBlank() }?.let {
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
