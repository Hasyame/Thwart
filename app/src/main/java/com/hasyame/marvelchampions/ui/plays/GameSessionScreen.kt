package com.hasyame.marvelchampions.ui.plays

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.ComicPanel
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.core.designsystem.component.halftone
import com.hasyame.marvelchampions.data.repository.PlayRecorded
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState
import com.hasyame.marvelchampions.domain.randomizer.Difficulty
import kotlinx.coroutines.delay

/**
 * Set up a game yourself, then have the app time it.
 *
 * The randomiser answers "what shall I play"; this answers "I already know what
 * I am playing, record it properly". They are different acts, so this is its
 * own screen rather than a mode of the draw.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSessionScreen(
    onBack: () -> Unit,
    scenarioCode: String? = null,
    difficulty: String? = null,
    heroes: String? = null,
    modularSets: String? = null,
    autoStart: Boolean = false,
    onOpenPlays: () -> Unit,
    viewModel: GameSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recorded by viewModel.recorded.collectAsStateWithLifecycle()

    // Leaving mid-game throws away a running clock and everything set up, so
    // it asks first. Only while playing: backing out of the setup loses nothing
    // worth confirming.
    var confirmLeave by remember { mutableStateOf(false) }
    val leave = {
        if (state.phase == SessionPhase.PLAYING) confirmLeave = true else onBack()
    }

    // A draw handed over from the randomiser, applied once.
    LaunchedEffect(scenarioCode, difficulty, heroes, modularSets) {
        viewModel.prefill(scenarioCode, difficulty, heroes, modularSets, autoStart)
    }

    // Only while the clock is actually running, so a paused or finished game
    // is not waking the composition once a second for nothing.
    LaunchedEffect(state.phase, state.timer.isRunning) {
        while (state.phase == SessionPhase.PLAYING && state.timer.isRunning) {
            viewModel.tick()
            delay(TICK_MILLIS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.session_title)) },
                navigationIcon = {
                    IconButton(onClick = leave) {
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
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.phase == SessionPhase.SETUP -> SetupPhase(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )

            state.phase == SessionPhase.BRIEFING -> BriefingPhase(
                state = state,
                onPlay = viewModel::beginPlaying,
                onBack = viewModel::backToSetup,
                modifier = Modifier.padding(padding),
            )

            else -> PlayingPhase(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.session_leave_title)) },
            text = { Text(stringResource(R.string.session_leave_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        onBack()
                    },
                ) { Text(stringResource(R.string.session_leave_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text(stringResource(R.string.session_leave_no))
                }
            },
        )
    }

    recorded?.let { outcome ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRecorded,
            title = { Text(stringResource(R.string.session_saved_title)) },
            // Three equal choices, so they live in the body as full-width
            // buttons. A dialog's confirm slot is pinned to the right and
            // sized to its content, which left a stack of three squashed
            // against the right edge.
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (outcome) {
                            is PlayRecorded.SavedOnly -> stringResource(R.string.session_saved)
                            is PlayRecorded.SavedAndReported ->
                                stringResource(R.string.session_saved_sent)

                            is PlayRecorded.SavedAskToReport ->
                                stringResource(R.string.session_saved_can_send)

                            is PlayRecorded.SavedReportFailed ->
                                stringResource(R.string.session_saved_not_sent, outcome.detail)
                        },
                    )

                    Button(
                        onClick = viewModel::reset,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.session_another)) }

                    OutlinedButton(
                        onClick = {
                            viewModel.reset()
                            onOpenPlays()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.session_see_stats)) }

                    OutlinedButton(
                        onClick = {
                            viewModel.reset()
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.session_back_to_menu)) }
                }
            },
            // Required by the dialog, and deliberately empty: every action is
            // in the body above.
            confirmButton = {},
        )
    }
}
@Composable
private fun SetupPhase(
    state: GameSessionUiState,
    viewModel: GameSessionViewModel,
    modifier: Modifier = Modifier,
) {
    var pendingHero by remember { mutableStateOf<String?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Same note as the randomiser, for the same reason: everything on this
        // screen is filtered by the collection, and a player who does not know
        // that concludes something is missing.
        Text(
            text = stringResource(R.string.randomizer_collection_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PickerSection(stringResource(R.string.session_scenario)) {
            state.pools.scenarios.forEach { scenario ->
                FilterChip(
                    selected = state.scenarioCode == scenario.code,
                    onClick = { viewModel.setScenario(scenario.code) },
                    label = {
                        Text(state.names.scenarios[scenario.code] ?: scenario.code)
                    },
                )
            }
        }

        PickerSection(stringResource(R.string.session_difficulty)) {
            // The same rule as the randomiser: a difficulty is a set of
            // encounter cards that came in a box, so only the ones the
            // collection can field are offered.
            state.pools.difficulties.forEach { difficulty ->
                val stored = difficulty.name.lowercase()
                FilterChip(
                    selected = state.difficulty == stored,
                    onClick = { viewModel.setDifficulty(stored) },
                    label = { Text(stringResource(difficulty.labelRes())) },
                )
            }
        }

        PickerSection(stringResource(R.string.session_modular_sets)) {
            state.pools.modularSets.forEach { set ->
                FilterChip(
                    selected = set.code in state.modularSetCodes,
                    onClick = { viewModel.toggleModularSet(set.code) },
                    label = { Text(state.names.modularSets[set.code] ?: set.code) },
                )
            }
        }

        // Hero then aspect, in that order: picking a hero first and an aspect
        // second is how a player actually decides, and it keeps the chip list
        // to one long list rather than every hero-aspect pairing.
        PickerSection(stringResource(R.string.session_hero)) {
            state.pools.heroes.forEach { hero ->
                FilterChip(
                    selected = pendingHero == hero.code,
                    onClick = { pendingHero = if (pendingHero == hero.code) null else hero.code },
                    label = { Text(state.names.heroes[hero.code] ?: hero.code) },
                )
            }
        }

        pendingHero?.let { heroCode ->
            PickerSection(stringResource(R.string.session_aspect)) {
                state.pools.aspects.forEach { aspect ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            viewModel.addHero(heroCode, aspect)
                            pendingHero = null
                        },
                        label = { Text(aspect.replaceFirstChar(Char::uppercase)) },
                    )
                }
            }
        }

        if (state.heroes.isNotEmpty()) {
            Text(
                text = stringResource(R.string.session_table),
                style = MaterialTheme.typography.titleSmall,
            )
            state.heroes.forEachIndexed { index, hero ->
                ListItem(
                    headlineContent = {
                        Text(state.names.heroes[hero.heroCode] ?: hero.heroCode)
                    },
                    supportingContent = { Text(hero.aspect.replaceFirstChar(Char::uppercase)) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeHero(index) }) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    },
                )
            }
        }

        Button(
            onClick = viewModel::start,
            enabled = state.canStart,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.session_start)) }
    }
}

@Composable
private fun PickerSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/**
 * What to put on the table, before the clock starts.
 *
 * The same two halves a campaign briefing has, minus the campaign's own steps
 * because there are none here: what to fetch out of the boxes, then the setup
 * printed on the scenario's own main scheme. The clock waits, because laying a
 * game out takes minutes that are not play.
 */
@Composable
private fun BriefingPhase(
    state: GameSessionUiState,
    onPlay: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ComicPanel(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.campaign_pre_setup),
                    style = MaterialTheme.typography.titleMedium,
                )
                state.scenarioCode?.let { code ->
                    BriefingRow(
                        label = stringResource(R.string.randomizer_scenario),
                        value = state.names.scenarios[code] ?: code,
                    )
                }
                state.briefing.schemeName?.let {
                    BriefingRow(
                        label = stringResource(R.string.campaign_main_scheme_label),
                        value = it,
                    )
                }
                BriefingRow(
                    label = stringResource(R.string.randomizer_difficulty),
                    value = stringResource(
                        Difficulty.entries
                            .firstOrNull { it.name.lowercase() == state.difficulty }
                            ?.labelRes()
                            ?: R.string.difficulty_standard_i,
                    ),
                )
                // The sets that were drawn, which is the part a player cannot
                // work out from the scenario alone.
                state.modularSetCodes.takeIf { it.isNotEmpty() }?.let { sets ->
                    BriefingRow(
                        label = stringResource(R.string.campaign_encounter_sets_label),
                        value = sets.joinToString(", ") { state.names.modularSets[it] ?: it },
                    )
                }
                state.heroes.takeIf { it.isNotEmpty() }?.let { heroes ->
                    BriefingRow(
                        label = stringResource(R.string.randomizer_heroes),
                        value = heroes.joinToString(", ") { hero ->
                            (state.names.heroes[hero.heroCode] ?: hero.heroCode) +
                                " · " + hero.aspect.replaceFirstChar(Char::uppercase)
                        },
                    )
                }
            }
        }

        // Absent for the two scenarios that keep their setup in the rules
        // insert. Nothing is shown rather than a guess.
        state.briefing.steps.takeIf { it.isNotEmpty() }?.let { steps ->
            ComicPanel(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.campaign_scheme_setup),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    steps.forEach { step -> Text("• $step") }
                }
            }
        }

        Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.session_play))
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.session_change_setup))
        }
    }
}

@Composable
private fun BriefingRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PlayingPhase(
    state: GameSessionUiState,
    viewModel: GameSessionViewModel,
    modifier: Modifier = Modifier,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    var correctingTime by remember { mutableStateOf(false) }

    if (correctingTime) {
        var minutes by remember { mutableStateOf((state.elapsedMillis / 60_000L).toString()) }
        AlertDialog(
            onDismissRequest = { correctingTime = false },
            title = { Text(stringResource(R.string.session_correct_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.session_correct_message))
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.session_correct_minutes)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setElapsed((minutes.toLongOrNull() ?: 0L) * 60_000L)
                        correctingTime = false
                    },
                ) { Text(stringResource(R.string.session_correct_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { correctingTime = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.session_discard_title)) },
            text = { Text(stringResource(R.string.session_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        viewModel.discard()
                    },
                ) { Text(stringResource(R.string.session_discard_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.session_leave_no))
                }
            },
        )
    }

    Box(
        modifier
            .fillMaxSize()
            .halftone(MaterialTheme.colorScheme.onBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ComicPanel(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = state.scenarioCode
                            ?.let { state.names.scenarios[it] ?: it }
                            .orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = state.heroes.joinToString(", ") {
                            state.names.heroes[it.heroCode] ?: it.heroCode
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.modularSetCodes.isNotEmpty()) {
                        Text(
                            text = state.modularSetCodes
                                .map { state.names.modularSets[it] ?: it }
                                .sorted()
                                .joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Only at a table with more than one player, and drawn once when
            // the game started rather than here — a value computed during
            // composition would pick a new player on every recomposition, so
            // the clock ticking would keep changing who goes first.
            state.firstPlayerIndex?.let { index ->
                val hero = state.heroes.getOrNull(index)
                if (hero != null) {
                    Text(
                        text = stringResource(
                            R.string.session_first_player,
                            state.names.heroes[hero.heroCode] ?: hero.heroCode,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // The clock is the control. Tapping it corrects the time, which is
            // where anyone would look for that.
            Text(
                text = TimerState.format(state.elapsedMillis),
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.clickable(enabled = !state.isFinishing) {
                    correctingTime = true
                },
            )
            Text(
                text = stringResource(R.string.session_tap_to_correct),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = if (state.timer.isRunning) viewModel::pause else viewModel::resume,
                enabled = !state.isFinishing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.timer.isRunning) {
                            R.string.session_pause
                        } else {
                            R.string.session_resume
                        },
                    ),
                )
            }

            // Both go dead the instant either is tapped, and say why. Without
            // this the screen looked unchanged while the play was saved and
            // sent, which read as "it did not register" and invited another tap.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.finish(won = true) },
                    enabled = !state.isFinishing,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.session_won)) }
                OutlinedButton(
                    onClick = { viewModel.finish(won = false) },
                    enabled = !state.isFinishing,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.session_lost)) }
            }

            TextButton(
                onClick = { confirmDiscard = true },
                enabled = !state.isFinishing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.session_discard)) }

            if (state.isFinishing) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.session_saving),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** The game's own names for its four difficulties. */
private fun Difficulty.labelRes(): Int = when (this) {
    Difficulty.STANDARD_I -> R.string.difficulty_standard_i
    Difficulty.STANDARD_II -> R.string.difficulty_standard_ii
        Difficulty.STANDARD_III -> R.string.difficulty_standard_iii
    Difficulty.EXPERT_I -> R.string.difficulty_expert_i
    Difficulty.EXPERT_II -> R.string.difficulty_expert_ii
}

private const val TICK_MILLIS = 1_000L
